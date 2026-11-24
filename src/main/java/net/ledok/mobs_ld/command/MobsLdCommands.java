package net.ledok.mobs_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.ledok.mobs_ld.config.DamageConfig;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.entity.threat.ThreatTable;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.combat.AttackPreview;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.CombatTargets;
import net.ledok.mobs_ld.mob.combat.DamageAudit;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.host.warden.WardenAdapter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.BiConsumer;

/**
 * Debug commands for mobs running a behaviour (ops only; LdMobs and vanilla hosts alike): {@code play <targets> <clip>}
 * / {@code stop <targets>} preview an animation clip (a host cue on vanilla mobs), {@code ability <targets> <name>}
 * forces a behaviour ability now (telegraphs, hits and all), {@code reset <targets>} runs the boss reset (full
 * heal, phases and adds cleared), {@code threat <targets>} prints the threat table, {@code heads <targets>} what
 * every head and target slot is aimed at, and {@code brain <targets>} a Warden's Brain state.
 */
public final class MobsLdCommands {
    private MobsLdCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mobs_ld")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("play")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("clip", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String clip = StringArgumentType.getString(ctx, "clip");
                                            return apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), false,
                                                    (mob, combat) -> ((CombatHost) mob).mobs_ld$playAction(clip, 1.0),
                                                    "Playing '" + clip + "' on");
                                        }))))
                .then(Commands.literal("ability")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            return apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), true,
                                                    (mob, combat) -> forceAbility(ctx.getSource(), mob, combat, name),
                                                    "Forced '" + name + "' on");
                                        }))))
                .then(Commands.literal("debug")
                        .executes(ctx -> {
                            boolean on = MobCombat.toggleDebug();
                            ctx.getSource().sendSuccess(() -> Component.literal("Mobs_LD ability trace " + (on ? "on" : "off")), true);
                            return 1;
                        })
                        .then(Commands.literal("preview").executes(ctx -> togglePreview(ctx.getSource()))))
                .then(Commands.literal("threat")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> showThreat(ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))))
                .then(Commands.literal("heads")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> showHeads(ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))))
                .then(Commands.literal("brain")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> showBrain(ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), false,
                                        (mob, combat) -> combat.reset(), "Reset"))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), false,
                                        (mob, combat) -> ((CombatHost) mob).mobs_ld$stopAction(), "Stopped the action on"))))
                .then(Commands.literal("damage")
                        .then(Commands.literal("audit")
                                .then(Commands.literal("start").executes(ctx -> auditStart(ctx.getSource())))
                                .then(Commands.literal("stop").executes(ctx -> auditStop(ctx.getSource())))
                                .then(Commands.literal("dump").executes(ctx -> auditDump(ctx.getSource())))
                                .then(Commands.literal("status").executes(ctx -> auditStatus(ctx.getSource())))
                                .then(Commands.literal("attributes").executes(ctx -> auditAttributes(ctx.getSource()))))
                        .then(Commands.literal("config").executes(ctx -> reloadDamageConfig(ctx.getSource())))));
    }

    private static int togglePreview(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!AttackPreview.available(player)) {
            source.sendFailure(Component.literal("Attack preview requires a matching Mobs_LD client"));
            return 0;
        }
        boolean on = AttackPreview.toggle(player);
        source.sendSuccess(() -> Component.literal(on
                ? "Attack preview on · cyan floor, gold ceiling, red hitboxes at impact"
                : "Attack preview off"), false);
        return 1;
    }

    /** Prints each mob's threat table, highest first, with its current target. */
    // --- damage audit ---

    private static int auditStart(CommandSourceStack source) {
        if (!DamageAudit.start()) {
            source.sendFailure(Component.literal("The damage audit is already recording (" + DamageAudit.hits() + " hits)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Damage audit started: every hit on a player is recorded"), true);
        return 1;
    }

    private static int auditStop(CommandSourceStack source) {
        try {
            int hits = DamageAudit.hits();
            Path file = DamageAudit.stop(source.getServer());
            if (file == null) {
                source.sendFailure(Component.literal("The damage audit is not recording"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Damage audit stopped, " + hits + " hits written to " + file), true);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write the damage audit: " + e.getMessage()));
            return 0;
        }
    }

    private static int auditDump(CommandSourceStack source) {
        try {
            int hits = DamageAudit.hits();
            Path file = DamageAudit.dump(source.getServer());
            source.sendSuccess(() -> Component.literal(hits + " hits written to " + file
                    + (DamageAudit.recording() ? " (still recording)" : "")), true);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write the damage audit: " + e.getMessage()));
            return 0;
        }
    }

    private static int auditStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Damage audit " + (DamageAudit.recording() ? "recording" : "stopped")
                + ", " + DamageAudit.hits() + " hits"), false);
        return 1;
    }

    private static int reloadDamageConfig(CommandSourceStack source) {
        DamageConfig config = DamageConfig.reload();
        source.sendSuccess(() -> Component.literal("Reloaded config/mobs_ld_damage.json: caps "
                + (config.capsEnabled ? "on (" + config.capOfTargetMaxHealth + " of max health, bosses "
                + config.bossCapOfTargetMaxHealth + ")" : "off") + "; attack profiles reload with /reload"), true);
        return 1;
    }

    private static int auditAttributes(CommandSourceStack source) {
        try {
            DamageAudit.Missing missing = DamageAudit.missingDamageDealt(source.getServer());
            if (!missing.installed()) {
                source.sendFailure(Component.literal("loot_stage:damage_dealt is not installed (no DD Loot Stage)"));
                return 0;
            }
            String shown = String.join(", ", missing.types().subList(0, Math.min(20, missing.types().size())));
            source.sendSuccess(() -> Component.literal(missing.types().size() + " of " + missing.livingTypes()
                    + " living entity types lack loot_stage:damage_dealt" + (shown.isEmpty() ? "" : ": " + shown)
                    + (missing.types().size() > 20 ? ", ..." : "") + " (all in " + missing.file() + ")"), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write the attribute audit: " + e.getMessage()));
            return 0;
        }
    }

    private static int showThreat(CommandSourceStack source, Collection<? extends Entity> targets) {
        int shown = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof Mob mob) || !(entity instanceof ThreatHolder holder)) {
                continue;
            }
            ThreatTable table = holder.getThreatTable();
            StringBuilder line = new StringBuilder(mob.getName().getString()).append(" -> ")
                    .append(mob.getTarget() == null ? "no target" : mob.getTarget().getName().getString()).append(':');
            table.holders().stream()
                    .sorted(Comparator.comparingDouble(table::get).reversed())
                    .forEach(id -> {
                        Entity holderEntity = ((ServerLevel) mob.level()).getEntity(id);
                        String name = holderEntity != null ? holderEntity.getName().getString() : id.toString().substring(0, 8);
                        line.append(String.format(" %s %.1f;", name, table.get(id)));
                    });
            if (table.isEmpty()) {
                line.append(" (empty)");
            }
            source.sendSuccess(() -> Component.literal(line.toString()), false);
            shown++;
        }
        if (shown == 0) {
            source.sendFailure(Component.literal("No threat-tracking mob among the targets"));
        }
        return shown;
    }

    /**
     * Prints each Warden's Brain as the engine sees it: hosted or not, held or running, its ATTACK_TARGET and the anger
     * behind it, its dig cooldown, pose and burrow state. World Wardens show too (as not hosted).
     */
    private static int showBrain(CommandSourceStack source, Collection<? extends Entity> targets) {
        int shown = 0;
        for (Entity entity : targets) {
            if (entity instanceof Warden warden) {
                for (String line : WardenAdapter.describe(warden)) {
                    source.sendSuccess(() -> Component.literal(line), false);
                }
                shown++;
            }
        }
        if (shown == 0) {
            source.sendFailure(Component.literal("No Warden among the targets"));
        }
        return shown;
    }

    /**
     * Prints each mob's heads: the middle head's target (the mob's main target), then every target slot with its rule,
     * the head it shows on, its holder and the time to its next re-pick.
     */
    private static int showHeads(CommandSourceStack source, Collection<? extends Entity> targets) {
        int shown = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            MobCombat combat = CombatHost.of(mob);
            MobBehaviour behaviour = combat == null ? null : combat.behaviour();
            if (behaviour == null) {
                continue;
            }
            String header = mob.getName().getString() + " " + mob.getId() + ": center -> "
                    + (mob.getTarget() == null ? "nobody" : mob.getTarget().getName().getString()) + " (main target)"
                    + (behaviour.targetSlots().isEmpty() ? "; no target slots" : "");
            source.sendSuccess(() -> Component.literal(header), false);
            for (String line : combat.slots().describe(behaviour)) {
                source.sendSuccess(() -> Component.literal("  " + line), false);
            }
            shown++;
        }
        if (shown == 0) {
            source.sendFailure(Component.literal("No mob running a Mobs_LD behaviour among the targets"));
        }
        return shown;
    }

    /**
     * Starts an ability now, aimed by its own target rule (e.g. a target slot's holder), else at the mob's target, else
     * at the command's runner.
     */
    private static void forceAbility(CommandSourceStack source, Mob mob, MobCombat combat, String name) {
        MobBehaviour behaviour = combat.behaviour();
        MobBehaviour.Ability ability = behaviour == null ? null : behaviour.abilities().get(name);
        if (ability == null) {
            source.sendFailure(Component.literal(mob.getType().getDescription().getString() + " has no ability '" + name + "'"));
            return;
        }
        LivingEntity aim = CombatTargets.pick(mob, ability.target(), ability.includeUnreachable());
        if (aim == null) {
            aim = mob.getTarget() != null ? mob.getTarget()
                    : source.getEntity() instanceof LivingEntity living ? living : null;
        }
        combat.start(name, ability, aim);
    }

    /**
     * Runs {@code action} on every target that runs the ability engine. With {@code attach}, a mob whose type has a
     * behaviour but that has not ticked since it got one is attached on the spot.
     */
    private static int apply(CommandSourceStack source, Collection<? extends Entity> targets, boolean attach,
                             BiConsumer<Mob, MobCombat> action, String verb) {
        int count = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            MobCombat combat = CombatHost.of(mob);
            if (combat == null && attach && MobBehaviours.forMob(mob) != null && MobBehaviours.canHost(mob)) {
                combat = ((CombatHost) mob).mobs_ld$combatOrCreate();
            }
            if (combat != null) {
                action.accept(mob, combat);
                count++;
            }
        }
        if (count == 0) {
            source.sendFailure(Component.literal("No mob running a Mobs_LD behaviour among the targets"));
            return 0;
        }
        int done = count;
        source.sendSuccess(() -> Component.literal(verb + " " + done + " mob(s)"), true);
        return count;
    }
}
