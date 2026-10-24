package net.ledok.mobs_ld.mob.host.wither;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.mob.behaviour.SkullSpec;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.host.HostOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Mob;

import java.util.Map;

/**
 * The Wither's {@code host} options ({@code "type": "mobs_ld:wither"}). Every default is the vanilla behaviour, so a
 * hosted Wither changes only what its file sets. Phases may override any of them (e.g. {@code hover_height}).
 *
 * @param hoverHeight       how high above its target it hovers between abilities while not powered (vanilla 5.0;
 *                          below half health vanilla brings it down to the target's height whatever this says)
 * @param regenWhileEngaged false = no vanilla 1 HP/s regeneration while it is fighting (it still regenerates idle)
 * @param arrowImmunity     false = arrows and wind charges hit it below half health too (vanilla: immune)
 * @param blockBreaking     when it may break the blocks around it 1 s after being hurt (vanilla: always, with
 *                          mobGriefing)
 * @param skulls            the skulls it fires, by name ({@link SkullSpec}); {@code center} and {@code side} are fired
 *                          by the heads on their own cadence, any name by {@code projectile} events. Non-empty =
 *                          Mobs_LD fires all its skulls: vanilla's shots and idle blue skulls stop. Empty = vanilla
 */
public record WitherHostOptions(double hoverHeight, boolean regenWhileEngaged, boolean arrowImmunity,
                                BlockBreaking blockBreaking, Map<String, SkullSpec> skulls) implements HostOptions {
    /** The spec the middle head fires on its own. */
    public static final String CENTER_SKULL = "center";
    /** The spec each side head fires on its own. */
    public static final String SIDE_SKULL = "side";

    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "wither");
    /** A hosted Wither whose file has no (Wither) host block. */
    public static final WitherHostOptions VANILLA = new WitherHostOptions(5.0, true, true, BlockBreaking.ALWAYS, Map.of());

    public static final MapCodec<WitherHostOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(0.0, 64.0).optionalFieldOf("hover_height", VANILLA.hoverHeight()).forGetter(WitherHostOptions::hoverHeight),
            Codec.BOOL.optionalFieldOf("regen_while_engaged", true).forGetter(WitherHostOptions::regenWhileEngaged),
            Codec.BOOL.optionalFieldOf("arrow_immunity", true).forGetter(WitherHostOptions::arrowImmunity),
            BlockBreaking.CODEC.optionalFieldOf("block_breaking", BlockBreaking.ALWAYS).forGetter(WitherHostOptions::blockBreaking),
            Codec.unboundedMap(Codec.STRING, SkullSpec.CODEC).optionalFieldOf("skulls", Map.of()).forGetter(WitherHostOptions::skulls)
    ).apply(i, WitherHostOptions::new));

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    public enum BlockBreaking implements StringRepresentable {
        /** Vanilla: breaks out of its spot 1 s after any hit (if mobGriefing allows). */
        ALWAYS("always"),
        NEVER("never"),
        /** Vanilla outside Arenas_LD runs; never inside one, where it would wreck the arena. */
        OUTSIDE_RUNS("outside_runs");

        public static final Codec<BlockBreaking> CODEC = StringRepresentable.fromEnum(BlockBreaking::values);
        private final String name;

        BlockBreaking(String name) {
            this.name = name;
        }

        public boolean allows(Mob wither) {
            return switch (this) {
                case ALWAYS -> true;
                case NEVER -> false;
                case OUTSIDE_RUNS -> !MobCombat.inArenasRun(wither);
            };
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
