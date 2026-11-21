package net.ledok.mobs_ld.mob.damage;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

import java.util.List;
import java.util.Optional;

/**
 * How one mob type's attacks are sized before anything scales them (a file in
 * {@code data/<ns>/mobs_ld/attack_profiles/}). {@code reference_hit} is the mob's one normal hit at level 0 on
 * Normal, the unit of the file; each of {@code attacks} matches hits by damage type, direct entity, Mobs_LD ability or
 * (for mods that drop the attacker) a hit with no attacker near a mob of this type, and resizes them. The first
 * matching attack wins; a hit nothing matches keeps its own number. {@code cap_of_target_max_health} limits one hit of
 * this mob to that share of the player's max health (see {@link AttackShaping}).
 *
 * @param referenceAttackDamage the attack_damage {@code reference_hit} was measured at; the type's default when absent
 */
public record AttackProfile(ResourceLocation entity, Optional<Double> referenceHit,
                            Optional<Double> referenceAttackDamage, List<Attack> attacks,
                            Optional<Double> capOfTargetMaxHealth) {
    public static final Codec<AttackProfile> CODEC = RecordCodecBuilder.<AttackProfile>create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(AttackProfile::entity),
            Codec.doubleRange(0.0, 1.0e6).optionalFieldOf("reference_hit").forGetter(AttackProfile::referenceHit),
            Codec.doubleRange(0.0, 1.0e6).optionalFieldOf("reference_attack_damage")
                    .forGetter(AttackProfile::referenceAttackDamage),
            Attack.CODEC.listOf().optionalFieldOf("attacks", List.of()).forGetter(AttackProfile::attacks),
            Codec.doubleRange(0.0, 100.0).optionalFieldOf("cap_of_target_max_health")
                    .forGetter(AttackProfile::capOfTargetMaxHealth)
    ).apply(i, AttackProfile::new)).validate(profile -> profile.referenceHit.isEmpty()
            && profile.attacks.stream().anyMatch(attack -> attack.hits.isPresent())
            ? DataResult.error(() -> "An attack profile with \"hits\" needs a reference_hit")
            : DataResult.success(profile));

    /** The first of its attacks that a hit with a dealer matches ({@code no_attacker} ones never do), if any. */
    public Optional<Attack> match(DamageSource source, ResourceLocation direct, String ability) {
        return attacks.stream().filter(attack -> !attack.match.noAttacker() && attack.match.matches(source, direct, ability))
                .findFirst();
    }

    /**
     * Which hits an attack is for; every field set must hold. {@code damage_type}: an id or a {@code #tag};
     * {@code direct}: the direct source's entity type (a projectile); {@code ability}: the Mobs_LD ability the mob runs;
     * {@code no_attacker}: only hits with no living attacker or direct source, on a player within {@code within} blocks
     * of a mob of this type.
     */
    public record Match(Optional<DamageTypeMatch> damageType, Optional<ResourceLocation> direct,
                        Optional<String> ability, boolean noAttacker, double within) {
        public static final Match ANY = new Match(Optional.empty(), Optional.empty(), Optional.empty(), false, 16.0);
        public static final Codec<Match> CODEC = RecordCodecBuilder.create(i -> i.group(
                DamageTypeMatch.CODEC.optionalFieldOf("damage_type").forGetter(Match::damageType),
                ResourceLocation.CODEC.optionalFieldOf("direct").forGetter(Match::direct),
                Codec.STRING.optionalFieldOf("ability").forGetter(Match::ability),
                Codec.BOOL.optionalFieldOf("no_attacker", false).forGetter(Match::noAttacker),
                Codec.doubleRange(0.0, 128.0).optionalFieldOf("within", 16.0).forGetter(Match::within)
        ).apply(i, Match::new));

        /** {@code direct}: the direct entity's type id or null; {@code ability}: the dealer's running ability or null. */
        public boolean matches(DamageSource source, ResourceLocation direct, String ability) {
            return damageType.map(type -> type.matches(source)).orElse(true)
                    && this.direct.map(id -> id.equals(direct)).orElse(true)
                    && this.ability.map(name -> name.equals(ability)).orElse(true);
        }
    }

    /** A damage type id, or a damage type tag written {@code #namespace:path}. */
    public record DamageTypeMatch(Either<TagKey<DamageType>, ResourceKey<DamageType>> type) {
        public static final Codec<DamageTypeMatch> CODEC = Codec.STRING.comapFlatMap(DamageTypeMatch::parse,
                match -> match.type.map(tag -> "#" + tag.location(), key -> key.location().toString()));

        private static DataResult<DamageTypeMatch> parse(String text) {
            boolean tag = text.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? text.substring(1) : text);
            if (id == null) {
                return DataResult.error(() -> "Not a damage type id or #tag: " + text);
            }
            return DataResult.success(new DamageTypeMatch(tag
                    ? Either.left(TagKey.create(Registries.DAMAGE_TYPE, id))
                    : Either.right(ResourceKey.create(Registries.DAMAGE_TYPE, id))));
        }

        public boolean matches(DamageSource source) {
            return type.map(source::is, source::is);
        }
    }

    /**
     * One rule: {@code match} (every hit when absent) and exactly one action: {@code hits} (N x reference_hit x R, R =
     * the mob's live attack_damage over its reference, so attack_damage scaling still carries), {@code multiply} (x the
     * hit's own number), {@code set} (this number) or {@code ignore: true} (keep the number, stop looking).
     */
    public record Attack(Match match, Optional<Double> hits, Optional<Double> multiply, Optional<Double> set,
                         boolean ignore) {
        public static final Codec<Attack> CODEC = RecordCodecBuilder.<Attack>create(i -> i.group(
                Match.CODEC.optionalFieldOf("match", Match.ANY).forGetter(Attack::match),
                Codec.doubleRange(0.0, 1.0e4).optionalFieldOf("hits").forGetter(Attack::hits),
                Codec.doubleRange(0.0, 1.0e4).optionalFieldOf("multiply").forGetter(Attack::multiply),
                Codec.doubleRange(0.0, 1.0e6).optionalFieldOf("set").forGetter(Attack::set),
                Codec.BOOL.optionalFieldOf("ignore", false).forGetter(Attack::ignore)
        ).apply(i, Attack::new)).validate(attack -> {
            int actions = (attack.hits.isPresent() ? 1 : 0) + (attack.multiply.isPresent() ? 1 : 0)
                    + (attack.set.isPresent() ? 1 : 0) + (attack.ignore ? 1 : 0);
            return actions == 1 ? DataResult.success(attack)
                    : DataResult.error(() -> "An attack needs exactly one of hits, multiply, set, ignore");
        });

        /** The hit's new amount; {@code r} = the dealer's attack_damage over the profile's reference. */
        public float apply(float amount, double referenceHit, double r) {
            if (hits.isPresent()) {
                return (float) (hits.get() * referenceHit * r);
            }
            if (multiply.isPresent()) {
                return (float) (amount * multiply.get());
            }
            if (set.isPresent()) {
                return set.get().floatValue();
            }
            return amount;
        }
    }
}
