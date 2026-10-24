package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.entity.threat.StealthCompat;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.entity.threat.ThreatTable;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.host.Head;
import net.ledok.mobs_ld.mob.host.HostAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The mob's target slots ({@code target_slots}): secondary targets besides its main one, each picked by its rule
 * ({@link SlotPicker}) while the mob is engaged, and shown on the head the host adapter maps it to (the Wither's
 * {@code right_head} and {@code left_head}). Also carries {@code look} overrides, which turn heads onto someone for
 * the ticks a look event runs. Ticked by {@link MobCombat} every server tick; cleared when the fight ends.
 *
 * <p>Cheap while idle: a mob without slots or looks does nothing here. Engaged, each slot costs a few field reads per
 * tick, a sight line every {@value #SIGHT_CHECK_INTERVAL} ticks, and the candidate scan only when it picks.
 */
public final class TargetSlots {
    /** Ticks between sight checks of a slot's current target. */
    private static final int SIGHT_CHECK_INTERVAL = 5;
    /** A slot that found nobody looks again this soon. */
    private static final int RETRY_TICKS = 10;

    private final Mob mob;
    private final MobCombat combat;
    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final EnumMap<Head, LivingEntity> looks = new EnumMap<>(Head.class);
    private long lookTick = Long.MIN_VALUE;
    /** Heads this has aimed (slot or look), released when they lose both. */
    private final Set<Head> held = EnumSet.noneOf(Head.class);
    private boolean active;

    TargetSlots(Mob mob, MobCombat combat) {
        this.mob = mob;
        this.combat = combat;
    }

    private static final class Slot {
        final SlotPicker.Timer timer = new SlotPicker.Timer();
        UUID target;
        boolean seen = true;
    }

    // --- Queries ---

    /** Whoever slot {@code name} holds now (alive, in this level), or null. */
    public LivingEntity target(String name) {
        Slot slot = slots.get(name);
        return slot == null ? null : resolve(slot.target);
    }

    /** Whether a {@code look} event holds {@code head} this tick (its own skull fire then waits). */
    public boolean looking(Head head) {
        return lookTick == mob.level().getGameTime() && looks.containsKey(head);
    }

    /**
     * A {@code look} event turns {@code heads} onto {@code target} this tick (call every tick it runs); slots take the
     * heads back the tick it stops.
     */
    public void look(Set<Head> heads, LivingEntity target) {
        long now = mob.level().getGameTime();
        if (lookTick != now) {
            looks.clear();
            lookTick = now;
        }
        for (Head head : heads) {
            looks.put(head, target);
        }
    }

    /** One line per slot for {@code /mobs_ld heads}: rule, holder and the time to its next re-pick. */
    public List<String> describe(MobBehaviour behaviour) {
        long now = mob.level().getGameTime();
        List<String> lines = new ArrayList<>();
        HostAdapter adapter = combat.adapter();
        for (Map.Entry<String, MobBehaviour.TargetSlot> entry : behaviour.targetSlots().entrySet()) {
            String name = entry.getKey();
            MobBehaviour.TargetSlot spec = entry.getValue();
            Slot slot = slots.get(name);
            LivingEntity holder = slot == null ? null : resolve(slot.target);
            StringBuilder line = new StringBuilder(name).append(" [").append(spec.rule().serializedName());
            adapter.slotHead(name).ifPresent(head -> line.append(", ").append(head.name().toLowerCase()).append(" head"));
            line.append("] -> ").append(holder == null ? "nobody" : holder.getName().getString());
            if (!active || slot == null) {
                line.append(" (not fighting)");
            } else {
                Slot lead = behaviour.leadSlot(name).map(slots::get).orElse(null);
                long until = slot.timer.ticksUntilPick(now, lead == null ? null : lead.timer, Math.round(spec.offset() * 20.0));
                line.append(String.format(", re-pick in %.1f s", until / 20.0));
                behaviour.leadSlot(name).ifPresent(leader -> line.append(" (").append(spec.offset()).append(" s after ")
                        .append(leader).append(')'));
                long hidden = slot.timer.ticksHidden(now);
                if (holder != null && hidden >= 0) {
                    line.append(String.format(", out of sight %.1f s", hidden / 20.0));
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    // --- Ticking ---

    /**
     * One server tick: while {@code engaged}, keeps every slot's holder valid and re-picks on schedule (lowest-threat
     * slots before spread slots, so a spread slot sees the others' new picks); then aims the heads.
     */
    void tick(MobBehaviour behaviour, boolean engaged) {
        long now = mob.level().getGameTime();
        Map<String, MobBehaviour.TargetSlot> specs = behaviour == null ? Map.of() : behaviour.targetSlots();
        slots.keySet().removeIf(name -> !specs.containsKey(name));
        if (engaged && !specs.isEmpty()) {
            active = true;
            for (boolean spreading : new boolean[]{false, true}) {
                for (Map.Entry<String, MobBehaviour.TargetSlot> entry : specs.entrySet()) {
                    if ((entry.getValue().rule() == SlotPicker.Rule.SPREAD) == spreading) {
                        tickSlot(behaviour, entry.getKey(), entry.getValue(), now);
                    }
                }
            }
        } else if (active) {
            releaseSlots();
        }
        aimHeads(specs, now);
    }

    /** The fight is over (reset, disengaged, behaviour gone): slots empty, heads handed back to the host. */
    void clear() {
        releaseSlots();
        looks.clear();
        lookTick = Long.MIN_VALUE;
        for (Head head : held) {
            combat.adapter().aimHead(mob, head, null);
        }
        held.clear();
    }

    private void releaseSlots() {
        active = false;
        for (Slot slot : slots.values()) {
            slot.target = null;
            slot.seen = true;
            slot.timer.reset();
        }
    }

    private void tickSlot(MobBehaviour behaviour, String name, MobBehaviour.TargetSlot spec, long now) {
        Slot slot = slots.computeIfAbsent(name, key -> new Slot());
        LivingEntity holder = resolve(slot.target);
        boolean checkDue = (now + mob.getId()) % SIGHT_CHECK_INTERVAL == 0;
        SlotPicker.Status status;
        UUID better = null;
        if (slot.target == null) {
            status = SlotPicker.Status.NONE;
        } else if (holder == null || !SlotPicker.eligible(facts(holder), spec.range(), false)) {
            status = SlotPicker.Status.GONE;
        } else if (!(holder instanceof Player) && checkDue
                && (better = pick(name, spec, eye(name), slot.target)) != null && resolve(better) instanceof Player) {
            status = SlotPicker.Status.FALLBACK; // a pet held it only because no player qualified; one does now
        } else if (!spec.lineOfSight()) {
            status = SlotPicker.Status.SEEN;
        } else {
            if (checkDue) {
                slot.seen = Skulls.clearShot(mob, eye(name), holder);
            }
            status = slot.seen ? SlotPicker.Status.SEEN : SlotPicker.Status.HIDDEN;
        }
        Slot lead = behaviour.leadSlot(name).map(slots::get).orElse(null);
        long offset = Math.round(spec.offset() * 20.0);
        if (!slot.timer.due(now, status, Math.round(spec.lostAfter() * 20.0), lead == null ? null : lead.timer, offset)) {
            return;
        }
        UUID previous = slot.target;
        UUID picked = status == SlotPicker.Status.FALLBACK ? better : pick(name, spec, eye(name), previous);
        slot.target = picked;
        slot.seen = true;
        slot.timer.picked(now, picked != null, spec.repick().rollTicks(mob.getRandom()::nextDouble), offset, RETRY_TICKS);
        if (picked != null && !picked.equals(previous)) {
            LivingEntity chosen = resolve(picked);
            combat.trace(name + " -> " + (chosen == null ? "?" : chosen.getName().getString()));
        } else if (picked == null && previous != null) {
            combat.trace(name + " lost its target; nobody to pick");
        }
    }

    private UUID pick(String name, MobBehaviour.TargetSlot spec, Vec3 eye, UUID previous) {
        ServerLevel level = (ServerLevel) mob.level();
        LivingEntity tank = mob.getTarget();
        UUID tankId = tank == null ? null : tank.getUUID();
        Map<UUID, Integer> headsOn = new HashMap<>();
        if (tankId != null) {
            headsOn.merge(tankId, 1, Integer::sum);
        }
        for (Map.Entry<String, Slot> other : slots.entrySet()) {
            if (!other.getKey().equals(name) && other.getValue().target != null) {
                headsOn.merge(other.getValue().target, 1, Integer::sum);
            }
        }
        List<SlotPicker.Candidate<UUID>> candidates = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            candidates.add(candidate(player, spec, eye));
        }
        if (spec.rule() == SlotPicker.Rule.SPREAD) {
            // Last resort of a spread slot: pets and summons with threat on the table.
            ThreatTable table = ((ThreatHolder) mob).getThreatTable();
            for (UUID id : table.holders()) {
                if (level.getEntity(id) instanceof LivingEntity pet && !(pet instanceof Player)
                        && CombatTargets.isEnemy(mob, pet)) {
                    candidates.add(candidate(pet, spec, eye));
                }
            }
        }
        return SlotPicker.pick(spec.rule(), candidates, spec.range(), spec.lineOfSight(), tankId, headsOn, previous,
                bound -> mob.getRandom().nextInt(bound));
    }

    /** {@link #facts} plus the sight line from {@code eye}, cast only if everything else already qualifies. */
    private SlotPicker.Candidate<UUID> candidate(LivingEntity entity, MobBehaviour.TargetSlot spec, Vec3 eye) {
        SlotPicker.Candidate<UUID> facts = facts(entity);
        if (!spec.lineOfSight() || !SlotPicker.eligible(facts, spec.range(), false)) {
            return facts;
        }
        return new SlotPicker.Candidate<>(facts.id(), facts.player(), facts.threat(), facts.targetable(), facts.hidden(),
                facts.distance(), Skulls.clearShot(mob, eye, entity));
    }

    /** What the picker needs to know about {@code entity}, without the sight line (reported as not visible). */
    private SlotPicker.Candidate<UUID> facts(LivingEntity entity) {
        boolean targetable = entity.level() == mob.level() && CombatTargets.isEnemy(mob, entity)
                && !(entity instanceof Player player && (player.isSpectator() || player.isCreative()));
        double threat = ((ThreatHolder) mob).getThreatTable().get(entity.getUUID());
        return new SlotPicker.Candidate<>(entity.getUUID(), entity instanceof Player, threat, targetable,
                StealthCompat.isHidden(entity), mob.distanceTo(entity), false);
    }

    /** Where slot {@code name} looks from: its head, or the mob's eyes for a slot without one. */
    private Vec3 eye(String name) {
        HostAdapter adapter = combat.adapter();
        return adapter.slotHead(name).flatMap(head -> adapter.muzzle(mob, head)).orElseGet(mob::getEyePosition);
    }

    /** Points every head a look or a slot holds; a head that lost both goes back to the host's own aiming. */
    private void aimHeads(Map<String, MobBehaviour.TargetSlot> specs, long now) {
        HostAdapter adapter = combat.adapter();
        boolean lookNow = lookTick == now;
        for (Head head : Head.values()) {
            LivingEntity want = lookNow ? looks.get(head) : null;
            boolean holds = want != null;
            if (!holds && head != Head.CENTER) {
                // The center head is the host's own (its main target); only a look turns it.
                for (String name : specs.keySet()) {
                    if (adapter.slotHead(name).orElse(null) == head) {
                        holds = true;
                        want = active ? target(name) : null;
                        break;
                    }
                }
            }
            if (holds) {
                adapter.aimHead(mob, head, want);
                held.add(head);
            } else if (held.remove(head)) {
                adapter.aimHead(mob, head, null);
            }
        }
    }

    private LivingEntity resolve(UUID id) {
        if (id == null || !(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(id);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }
}
