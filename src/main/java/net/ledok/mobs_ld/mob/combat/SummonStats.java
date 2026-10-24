package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.behaviour.event.SummonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A summon's {@code scale} block: the add's stats are set from its summoner's, so an add raised by a levelled boss is
 * as strong as that boss's level, not its own spot's. Each stat gets a target:
 * <ul>
 *   <li>{@code max_health} / {@code attack_damage}: the add's own value x factor x (the summoner's live value / the base
 *       its behaviour declares in {@code base_attributes}, else its own base value), or with {@code of_summoner} the
 *       summoner's live value x that fraction;</li>
 *   <li>{@code armor}: the add's own value + the summoner's armor above its base ({@code summoner_bonus}) or a fixed
 *       bonus;</li>
 *   <li>{@code damage_dealt: copy}: the summoner's {@code loot_stage:damage_dealt} (DD Loot Stage), looked up by id, so
 *       nothing breaks without that mod.</li>
 * </ul>
 * "Own" is the add as vanilla made it (its base, its modifiers and its equipment's, which the game itself only applies
 * on the add's first tick), read before it is added to the level. Once it is in the level (Dungeon Difficulty and
 * Arenas_LD scale new mobs inside {@code addFreshEntity}, before it returns), one modifier {@value #MODIFIER_PATH} per
 * stat brings the live value exactly to the target ({@code target / current - 1}, multiplied on the total), whatever
 * scaled it before, so it is never scaled twice; the add is then healed to its new maximum. Its equipment's modifiers
 * are counted in up front, so the value is right once vanilla applies them; nothing is re-checked later (a later
 * check would bake temporary effects such as Weakness into the permanent modifier). The modifiers are permanent:
 * they are saved with the add.
 */
public final class SummonStats {
    private static final String MODIFIER_PATH = "summoner_scale";
    public static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, MODIFIER_PATH);
    /** DD Loot Stage's outgoing damage multiplier; Eden Loot sets a boss's level through it. */
    public static final ResourceLocation DAMAGE_DEALT = ResourceLocation.fromNamespaceAndPath("loot_stage", "damage_dealt");
    private static final double EPSILON = 1.0e-9;

    private SummonStats() {
    }

    /** Stat targets for one add (NaN = leave that stat alone). */
    public record Targets(UUID add, double maxHealth, double attackDamage, double armor, double damageDealt) {
    }

    // --- Pure math (unit-tested) ---

    /**
     * What an attribute comes to from {@code base} and {@code modifiers}, as the game computes it (before clamping to
     * the attribute's range): flat bonuses, then multiples of that sum, then each total multiplier in turn.
     */
    public static double value(double base, Collection<AttributeModifier> modifiers) {
        double sum = base;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                sum += modifier.amount();
            }
        }
        double value = sum;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                value += sum * modifier.amount();
            }
        }
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                value *= 1.0 + modifier.amount();
            }
        }
        return value;
    }

    /** The total multiplier that takes {@code current} (positive) to {@code target}. */
    public static double multiplierFor(double current, double target) {
        return target / current - 1.0;
    }

    /**
     * The flat bonus that takes an attribute with nothing to multiply (its value 0, e.g. armor) to {@code target}:
     * flat bonuses are multiplied by whatever multiplies the sum, so the bonus is divided by that factor.
     */
    public static double flatFor(double base, Collection<AttributeModifier> others, double target) {
        double without = value(base, others);
        List<AttributeModifier> probe = new ArrayList<>(others);
        probe.add(new AttributeModifier(MODIFIER, 1.0, AttributeModifier.Operation.ADD_VALUE));
        double perPoint = value(base, probe) - without;
        return perPoint > EPSILON ? (target - without) / perPoint : 0.0;
    }

    /** {@code own x factor x live / declared}; the ratio is 1 when the declared base is not positive. */
    public static double scaled(double own, double factor, double live, double declared) {
        return own * factor * (declared > EPSILON ? live / declared : 1.0);
    }

    /** The target {@code rule} gives a stat: the summoner's live value x the fraction, else {@link #scaled}. */
    public static double target(SummonEvent.StatScale rule, double own, double live, double declared) {
        return rule.ofSummoner() ? live * rule.factor() : scaled(own, rule.factor(), live, declared);
    }

    // --- Planning (before the add enters the level) ---

    /** The targets {@code scale} gives {@code add}, read from it and its summoner now (call before adding it). */
    public static Targets plan(SummonEvent.Scale scale, Mob summoner, LivingEntity add) {
        double maxHealth = scale.maxHealth().map(rule -> scaledFrom(summoner, add, Attributes.MAX_HEALTH, rule))
                .orElse(Double.NaN);
        double attackDamage = scale.attackDamage().map(rule -> scaledFrom(summoner, add, Attributes.ATTACK_DAMAGE, rule))
                .orElse(Double.NaN);
        double armor = scale.armor().map(rule -> ownValue(add, Attributes.ARMOR) + rule.bonus(summoner))
                .orElse(Double.NaN);
        double damageDealt = Double.NaN;
        if (scale.copiesDamageDealt()) {
            Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(DAMAGE_DEALT);
            AttributeInstance own = attribute.map(add::getAttribute).orElse(null);
            AttributeInstance source = attribute.map(summoner::getAttribute).orElse(null);
            if (own != null && source != null) {
                damageDealt = source.getValue();
                own.setBaseValue(damageDealt); // the copy itself; the modifier below undoes the add's own scaling
            }
        }
        return new Targets(add.getUUID(), maxHealth, attackDamage, armor, damageDealt);
    }

    private static double scaledFrom(Mob summoner, LivingEntity add, Holder<Attribute> attribute, SummonEvent.StatScale rule) {
        AttributeInstance live = summoner.getAttribute(attribute);
        return live == null ? Double.NaN
                : target(rule, ownValue(add, attribute), live.getValue(), declaredBase(summoner, attribute));
    }

    /**
     * The base value of {@code attribute} the summoner's behaviour declares in {@code base_attributes}, else its own
     * base value (0 if it lacks the attribute). A raid boss with an overridden base then still scales its adds.
     */
    public static double declaredBase(Mob mob, Holder<Attribute> attribute) {
        MobBehaviour behaviour = MobBehaviours.get(mob.getType());
        ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
        Double declared = behaviour == null ? null : behaviour.baseAttributes().get(id);
        if (declared != null) {
            return declared;
        }
        AttributeInstance instance = mob.getAttribute(attribute);
        return instance == null ? 0.0 : instance.getBaseValue();
    }

    /** The summoner's live value of {@code attribute} above its declared base, never below 0. */
    public static double bonusAboveBase(Mob mob, Holder<Attribute> attribute) {
        AttributeInstance instance = mob.getAttribute(attribute);
        return instance == null ? 0.0 : Math.max(0.0, instance.getValue() - declaredBase(mob, attribute));
    }

    /**
     * The add's own value of {@code attribute}: its base and modifiers, plus its equipment's modifiers (the game only
     * applies those on its first tick), leaving out this class's own modifier. NaN if it lacks the attribute.
     */
    public static double ownValue(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return Double.NaN;
        }
        return attribute.value().sanitizeValue(value(instance.getBaseValue(), ownModifiers(entity, instance, attribute)));
    }

    private static Collection<AttributeModifier> ownModifiers(LivingEntity entity, AttributeInstance instance,
                                                              Holder<Attribute> attribute) {
        Map<ResourceLocation, AttributeModifier> byId = new LinkedHashMap<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!modifier.id().equals(MODIFIER)) {
                byId.put(modifier.id(), modifier);
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                // Once applied they replace themselves by id, so counting them again changes nothing.
                stack.forEachModifier(slot, (holder, modifier) -> {
                    if (holder.value() == attribute.value()) {
                        byId.put(modifier.id(), modifier);
                    }
                });
            }
        }
        return byId.values();
    }

    // --- Applying (once the add is in the level) ---

    /** Brings each planned stat of {@code add} to its target (right after the spawn) and heals it to the new maximum. */
    public static void apply(LivingEntity add, Targets targets) {
        normalize(add, Attributes.MAX_HEALTH, targets.maxHealth());
        normalize(add, Attributes.ATTACK_DAMAGE, targets.attackDamage());
        normalize(add, Attributes.ARMOR, targets.armor());
        if (!Double.isNaN(targets.damageDealt())) {
            BuiltInRegistries.ATTRIBUTE.getHolder(DAMAGE_DEALT)
                    .ifPresent(attribute -> normalize(add, attribute, targets.damageDealt()));
        }
        if (!Double.isNaN(targets.maxHealth())) {
            add.setHealth(add.getMaxHealth());
        }
    }

    /** One modifier that brings {@code attribute} to {@code target} from whatever it is now without it. */
    private static void normalize(LivingEntity entity, Holder<Attribute> attribute, double target) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || !Double.isFinite(target)) {
            return;
        }
        instance.removeModifier(MODIFIER);
        Collection<AttributeModifier> others = ownModifiers(entity, instance, attribute);
        double current = value(instance.getBaseValue(), others);
        if (Math.abs(target - current) <= EPSILON * Math.max(1.0, Math.abs(target))) {
            return;
        }
        AttributeModifier modifier = current > EPSILON
                ? new AttributeModifier(MODIFIER, multiplierFor(current, target), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                : new AttributeModifier(MODIFIER, flatFor(instance.getBaseValue(), others, target), AttributeModifier.Operation.ADD_VALUE);
        instance.addPermanentModifier(modifier);
    }
}
