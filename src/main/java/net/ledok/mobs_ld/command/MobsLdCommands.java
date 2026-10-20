package net.ledok.mobs_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Debug commands for data-driven mobs (ops only): {@code play <targets> <clip>} / {@code stop <targets>} preview an
 * animation clip, {@code ability <targets> <name>} forces a behaviour ability now (telegraphs, hits and all), and
 * {@code reset <targets>} runs the boss reset (full heal, phases and adds cleared).
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
                                            return apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"),
                                                    mob -> mob.playAction(clip), "Playing '" + clip + "' on");
                                        }))))
                .then(Commands.literal("ability")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            return apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"),
                                                    mob -> forceAbility(ctx.getSource(), mob, name), "Forced '" + name + "' on");
                                        }))))
                .then(Commands.literal("debug")
                        .executes(ctx -> {
                            boolean on = MobCombat.toggleDebug();
                            ctx.getSource().sendSuccess(() -> Component.literal("Mobs_LD ability trace " + (on ? "on" : "off")), true);
                            return 1;
                        }))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"),
                                        mob -> mob.combat().reset(), "Reset"))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"),
                                        LdMob::stopAction, "Stopped the action on")))));
    }

    /** Starts an ability now, aimed at the mob's target or else the command's runner. */
    private static void forceAbility(CommandSourceStack source, LdMob mob, String name) {
        MobBehaviour behaviour = mob.combat().behaviour();
        MobBehaviour.Ability ability = behaviour == null ? null : behaviour.abilities().get(name);
        if (ability == null) {
            source.sendFailure(Component.literal(mob.getType().getDescription().getString() + " has no ability '" + name + "'"));
            return;
        }
        LivingEntity aim = mob.getTarget() != null ? mob.getTarget()
                : source.getEntity() instanceof LivingEntity living ? living : null;
        mob.combat().start(name, ability, aim);
    }

    private static int apply(CommandSourceStack source, Collection<? extends Entity> targets, Consumer<LdMob> action,
                             String verb) {
        int count = 0;
        for (Entity entity : targets) {
            if (entity instanceof LdMob mob) {
                action.accept(mob);
                count++;
            }
        }
        if (count == 0) {
            source.sendFailure(Component.literal("No data-driven Mobs_LD mob among the targets"));
            return 0;
        }
        int done = count;
        source.sendSuccess(() -> Component.literal(verb + " " + done + " mob(s)"), true);
        return count;
    }
}
