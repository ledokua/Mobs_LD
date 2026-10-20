package net.ledok.mobs_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.ledok.mobs_ld.mob.LdMob;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * {@code /mobs_ld play <targets> <clip>} and {@code /mobs_ld stop <targets>}: drive a data-driven mob's action
 * layer by hand, to preview ability clips in game while the art and the AI are built. Ops only.
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
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> apply(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"),
                                        LdMob::stopAction, "Stopped the action on")))));
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
