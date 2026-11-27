package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.host.Head;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A cue at one moment: a sound, a puff of particles and/or a line of text, all sent from the server (no client code).
 * Text goes to the action bar of every player fighting or near the mob, because Arenas_LD's timed runs hide boss bars.
 */
public record FxEvent(double at, Optional<Sound> sound, Optional<Particles> particles, Optional<Message> message,
                      boolean swing)
        implements AbilityEvent.Instant {
    /** Players this close to the mob (or on its threat table) see its messages. */
    private static final double MESSAGE_RADIUS = 64.0;

    public static final MapCodec<FxEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(FxEvent::at),
            Sound.CODEC.optionalFieldOf("sound").forGetter(FxEvent::sound),
            Particles.CODEC.optionalFieldOf("particles").forGetter(FxEvent::particles),
            Message.CODEC.optionalFieldOf("message").forGetter(FxEvent::message),
            Codec.BOOL.optionalFieldOf("swing", false).forGetter(FxEvent::swing)
    ).apply(i, FxEvent::new));

    @Override
    public String type() {
        return "fx";
    }

    @Override
    public void fire(AbilityRun run) {
        Mob mob = run.mob();
        ServerLevel level = run.level();
        if (swing) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        sound.ifPresent(cue -> level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), cue.event(), SoundSource.HOSTILE,
                cue.volume(), cue.pitch()));
        particles.ifPresent(cue -> {
            if (cue.at() == Where.ALONG) {
                // At the end of the tick: the hits landing on this beat have locked their zones by then, whatever
                // order the events are listed in.
                run.afterEvents(() -> {
                    for (Vec3 point : alongPoints(run, at, cue.every())) {
                        cue.send(level, point);
                    }
                });
                return;
            }
            for (Vec3 point : cue.at().points(run)) {
                cue.send(level, point);
            }
        });
        message.ifPresent(cue -> {
            Component text = Component.translatable(cue.key());
            for (ServerPlayer player : audience(mob, level)) {
                player.displayClientMessage(text, true);
            }
        });
    }

    /** Whether two event times are the same beat. */
    public static boolean sameMoment(double a, double b) {
        return Math.abs(a - b) < 1.0e-6;
    }

    /**
     * Points every {@code every} blocks along the zones of the ability's {@code hit} events that land at {@code at}
     * (their centre lines: every ray, a strip's middle), at the height of the mob's chest (its center muzzle) above
     * each zone's floor.
     */
    private static List<Vec3> alongPoints(AbilityRun run, double at, double every) {
        Mob mob = run.mob();
        double height = run.combat().adapter().muzzle(mob, Head.CENTER).map(muzzle -> muzzle.y - mob.getY())
                .orElse((double) mob.getEyeHeight());
        List<Vec3> points = new ArrayList<>();
        for (AbilityEvent event : run.ability().events()) {
            if (!(event instanceof HitEvent hit) || !sameMoment(hit.at(), at)) {
                continue;
            }
            for (AbilityRun.HitZone zone : run.lockedZones(hit)) {
                Vec3 raised = zone.origin().add(0.0, height, 0.0);
                for (double[] local : zone.shape().centreLine(every)) {
                    points.add(AbilityRun.local(raised, zone.yaw(), local[0], local[1]));
                }
            }
        }
        return points;
    }

    /** Players near the mob, plus those on its threat table further away in the same level. */
    private static List<ServerPlayer> audience(Mob mob, ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) <= MESSAGE_RADIUS * MESSAGE_RADIUS
                    || ((ThreatHolder) mob).getThreatTable().get(player.getUUID()) > 0.0) {
                players.add(player);
            }
        }
        return players;
    }

    /** A sound by id; ids no mod registers still play if a resource pack defines them (like /playsound). */
    public record Sound(ResourceLocation id, float pitch, float volume) {
        public static final Codec<Sound> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Sound::id),
                Codec.floatRange(0.0F, 2.0F).optionalFieldOf("pitch", 1.0F).forGetter(Sound::pitch),
                Codec.floatRange(0.0F, 16.0F).optionalFieldOf("volume", 1.0F).forGetter(Sound::volume)
        ).apply(i, Sound::new));

        Holder<SoundEvent> event() {
            Optional<Holder.Reference<SoundEvent>> registered = BuiltInRegistries.SOUND_EVENT.getHolder(id);
            return registered.<Holder<SoundEvent>>map(holder -> holder)
                    .orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(id)));
        }
    }

    /**
     * A puff of a simple particle type (one that needs no extra settings, e.g. {@code minecraft:soul_fire_flame}).
     *
     * @param spread how far around the point they scatter (blocks, each axis)
     * @param speed  their speed
     * @param column 0 = a puff at the point; else a column this many blocks tall rising from the point, the
     *               {@code count} spread over its height ({@code spread} only sideways)
     * @param every  with {@code at: along}: blocks between two points along the zones ({@code count} at each)
     */
    public record Particles(SimpleParticleType type, Where at, int count, double spread, double speed, double column,
                            double every) {
        /** Height of one layer of a column. */
        private static final double LAYER = 0.5;

        /** A particle type that needs no extra settings, by id (also used by {@code listen}). */
        static final Codec<SimpleParticleType> TYPE = ResourceLocation.CODEC.comapFlatMap(id -> {
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id);
            if (type == null) {
                return DataResult.error(() -> "Unknown particle type " + id);
            }
            return type instanceof SimpleParticleType simple ? DataResult.success(simple)
                    : DataResult.error(() -> "Particle type " + id + " needs extra settings, which fx particles cannot give");
        }, BuiltInRegistries.PARTICLE_TYPE::getKey);

        public static final Codec<Particles> CODEC = RecordCodecBuilder.create(i -> i.group(
                TYPE.fieldOf("id").forGetter(Particles::type),
                Where.CODEC.optionalFieldOf("at", Where.SELF).forGetter(Particles::at),
                Codec.intRange(1, 512).optionalFieldOf("count", 8).forGetter(Particles::count),
                Codec.doubleRange(0.0, 16.0).optionalFieldOf("spread", 0.3).forGetter(Particles::spread),
                Codec.doubleRange(0.0, 4.0).optionalFieldOf("speed", 0.02).forGetter(Particles::speed),
                Codec.doubleRange(0.0, 64.0).optionalFieldOf("column", 0.0).forGetter(Particles::column),
                Codec.doubleRange(0.25, 64.0).optionalFieldOf("every", 1.0).forGetter(Particles::every)
        ).apply(i, Particles::new));

        void send(ServerLevel level, Vec3 point) {
            if (column <= 0.0) {
                level.sendParticles(type, point.x, point.y, point.z, count, spread, spread, spread, speed);
                return;
            }
            int layers = Math.max(1, (int) Math.ceil(column / LAYER));
            int perLayer = Math.max(1, Math.round((float) count / layers));
            double step = column / layers;
            for (int layer = 0; layer < layers; layer++) {
                level.sendParticles(type, point.x, point.y + (layer + 0.5) * step, point.z, perLayer, spread,
                        step / 2.0, spread, speed);
            }
        }
    }

    /** Where particles appear. */
    public enum Where implements StringRepresentable {
        /** Around the middle of the mob's body. */
        SELF("self"),
        /** At every head (the Wither's three mouths; the eyes of a mob with one head). */
        HEADS("heads"),
        /** Around the middle of the aim's body (where it stood when tracking ended, if it is gone). */
        AIM("aim"),
        /** On the floor under the mob. */
        GROUND("ground"),
        /**
         * Along the zones of the ability's {@code hit} events that land at the same {@code at} as this fx, every
         * {@code every} blocks, at the mob's chest height (sonic lines along all eight Circle lanes).
         */
        ALONG("along");

        public static final Codec<Where> CODEC = StringRepresentable.fromEnum(Where::values);
        private final String name;

        Where(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        List<Vec3> points(AbilityRun run) {
            Mob mob = run.mob();
            return switch (this) {
                case SELF -> List.of(mob.position().add(0.0, mob.getBbHeight() / 2.0, 0.0));
                case HEADS -> {
                    List<Vec3> mouths = new ArrayList<>();
                    for (Head head : Head.values()) {
                        run.combat().adapter().muzzle(mob, head).ifPresent(mouths::add);
                    }
                    yield mouths;
                }
                case AIM -> {
                    LivingEntity aim = run.aim();
                    yield List.of(aim != null && aim.isAlive() ? aim.position().add(0.0, aim.getBbHeight() / 2.0, 0.0)
                            : run.aimPosition().add(0.0, 1.0, 0.0));
                }
                case GROUND -> List.of(run.groundAt(mob.position()));
                case ALONG -> List.of(); // sent by FxEvent.fire, once the beat's zones are locked
            };
        }
    }

    /**
     * A translated line ({@code key} from the lang files) shown in the action bar.
     *
     * @param where where it shows; only {@code actionbar} so far
     */
    public record Message(String key, Display where) {
        public static final Codec<Message> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Message::key),
                Display.CODEC.optionalFieldOf("where", Display.ACTIONBAR).forGetter(Message::where)
        ).apply(i, Message::new));
    }

    public enum Display implements StringRepresentable {
        ACTIONBAR("actionbar");

        public static final Codec<Display> CODEC = StringRepresentable.fromEnum(Display::values);
        private final String name;

        Display(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
