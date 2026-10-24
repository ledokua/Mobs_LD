package net.ledok.mobs_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.entity.threat.ThreatTable;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.CombatTargets;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.BiConsumer;

/**
 * Debug commands for mobs running a behaviour (ops only; LdMobs and vanilla hosts alike): {@code play <targets> <clip>}
 * / {@code stop <targets>} preview an animation clip (a host cue on vanilla mobs), {@code ability <targets> <name>}
 * forces a behaviour ability now (telegraphs, hits and all), {@code reset <targets>} runs the boss reset (full
 * heal, phases and adds cleared), {@code threat <targets>} prints the threat table and {@code heads <targets>} what
 * every head and target slot is aimed at.
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
                        }))
                .then(Commands.literal("threat")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> showThreat(ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))))
                .then(Commands.literal("heads")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> showHeads(ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), false,
                                        (mob, combat) -> combat.reset(), "Reset"))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), false,
                                        (mob, combat) -> ((CombatHost) mob).mobs_ld$stopAction(), "Stopped the action on")))));
    }

    /** Prints each mob's threat table, highest first, with its current target. */
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
        LivingEntity aim = CombatTargets.pick(mob, ability.target());
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
            if (combat == null && attach && MobBehaviours.get(mob.getType()) != null && MobBehaviours.canHost(mob)) {
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
