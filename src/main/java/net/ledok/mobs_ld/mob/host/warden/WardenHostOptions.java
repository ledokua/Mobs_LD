package net.ledok.mobs_ld.mob.host.warden;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.mob.host.HostOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

/**
 * The Warden's {@code host} options ({@code "type": "mobs_ld:warden"}). They apply only to a hosted Warden (one its
 * file's {@code only_tagged} lets in); every other Warden is fully vanilla whatever they say. The defaults are the
 * boss's (the Warden had no hosted behaviour before), not vanilla's. Phases may override any of them.
 *
 * @param darknessPulse  {@code vanilla}: its Darkness pulse every 6 s within 20 blocks; {@code off} (default): none, so
 *                       Darkness only comes from the behaviour's own {@code effect} events
 * @param disableShields true = a shield that blocks one of its hits is disabled, as vanilla's swing does; false
 *                       (default): never
 * @param followRange    base {@code generic.follow_range} it gets when the engine attaches (vanilla 16, too short for
 *                       an arena: threat targeting and abilities only reach players within it); set on that mob only
 * @param heartbeat      while it fights, the anger level its clients' heartbeat shows (0 = the calmest, one beat every
 *                       2 s; 80 or more = the fastest, two a second): a phase's tempo; empty (default) = its real
 *                       anger, as vanilla
 */
public record WardenHostOptions(DarknessPulse darknessPulse, boolean disableShields, double followRange,
                                Optional<Integer> heartbeat) implements HostOptions {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "warden");
    /** A hosted Warden whose file has no (Warden) host block. */
    public static final WardenHostOptions DEFAULTS = new WardenHostOptions(DarknessPulse.OFF, false, 48.0,
            Optional.empty());

    public static final MapCodec<WardenHostOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DarknessPulse.CODEC.optionalFieldOf("darkness_pulse", DEFAULTS.darknessPulse()).forGetter(WardenHostOptions::darknessPulse),
            Codec.BOOL.optionalFieldOf("disable_shields", DEFAULTS.disableShields()).forGetter(WardenHostOptions::disableShields),
            Codec.doubleRange(1.0, 2048.0).optionalFieldOf("follow_range", DEFAULTS.followRange()).forGetter(WardenHostOptions::followRange),
            Codec.intRange(0, 150).optionalFieldOf("heartbeat").forGetter(WardenHostOptions::heartbeat)
    ).apply(i, WardenHostOptions::new));

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    public enum DarknessPulse implements StringRepresentable {
        VANILLA("vanilla"),
        OFF("off");

        public static final Codec<DarknessPulse> CODEC = StringRepresentable.fromEnum(DarknessPulse::values);
        private final String name;

        DarknessPulse(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
