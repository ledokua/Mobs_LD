package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.mob.behaviour.event.SummonEvent;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Summon scaling: an add's target stats from its summoner's, and the one modifier that brings an attribute to its target
 * whatever else (Dungeon Difficulty, Arenas_LD) modified it.
 */
class SummonStatsTest {
    private static final double EPSILON = 1.0e-9;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static AttributeModifier modifier(String id, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath("test", id), amount, operation);
    }

    /** Dungeon Difficulty's kind of scaling: flat and base-multiplied bonuses, plus a total multiplier for good measure. */
    private static List<AttributeModifier> scaled() {
        return List.of(modifier("flat", 6.0, AttributeModifier.Operation.ADD_VALUE),
                modifier("base", 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                modifier("level", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                modifier("total", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @Test
    void valueMatchesTheGame() {
        AttributeInstance health = new AttributeInstance(Attributes.MAX_HEALTH, instance -> {
        });
        health.setBaseValue(20.0);
        scaled().forEach(health::addTransientModifier);
        assertEquals(health.getValue(), SummonStats.value(20.0, scaled()), EPSILON);
        assertEquals(20.0, SummonStats.value(20.0, List.of()), EPSILON);
    }

    @Test
    void theModifierNeutralisesWhateverScaledTheAdd() {
        for (double target : new double[]{29.6, 40.0, 56.0, 8.0}) {
            AttributeInstance health = new AttributeInstance(Attributes.MAX_HEALTH, instance -> {
            });
            health.setBaseValue(20.0 * 3.0); // Arenas_LD multiplies the base of a minion it scales
            scaled().forEach(health::addPermanentModifier);
            double current = health.getValue();
            health.addPermanentModifier(new AttributeModifier(SummonStats.MODIFIER,
                    SummonStats.multiplierFor(current, target), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            assertEquals(target, health.getValue(), 1.0e-6, "target " + target);
        }
    }

    @Test
    void armorFromNothingGetsAFlatBonus() {
        AttributeInstance armor = new AttributeInstance(Attributes.ARMOR, instance -> {
        });
        List<AttributeModifier> others = List.of(modifier("level", 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        others.forEach(armor::addTransientModifier);
        assertEquals(0.0, armor.getValue(), EPSILON);
        armor.addPermanentModifier(new AttributeModifier(SummonStats.MODIFIER, SummonStats.flatFor(0.0, others, 9.0),
                AttributeModifier.Operation.ADD_VALUE));
        assertEquals(9.0, armor.getValue(), 1.0e-6);
    }

    /** Wither skeleton adds (20 HP, 8 attack with the stone sword) of a Wither at simulated levels (design section 5). */
    @Test
    void addsFollowTheWithersRatio() {
        double[][] levels = {{2532.0, 29.6}, {3422.0, 40.0}, {4791.0, 56.0}, {1711.0, 20.0}};
        for (double[] level : levels) {
            assertEquals(level[1], SummonStats.scaled(20.0, 1.0, level[0], 1711.0), 0.05, "Wither HP " + level[0]);
        }
        // Eden Loot keeps the Wither's attack at 2.0 (R = 1): the swing stays vanilla's 8; a DD attack level scales it.
        assertEquals(8.0, SummonStats.scaled(8.0, 1.0, 2.0, 2.0), EPSILON);
        assertEquals(15.2, SummonStats.scaled(8.0, 1.0, 3.8, 2.0), EPSILON);
        // Soft enrage x1.5 carries over to adds raised after it.
        assertEquals(12.0, SummonStats.scaled(8.0, 1.0, 3.0, 2.0), EPSILON);
        assertEquals(10.0, SummonStats.scaled(10.0, 1.0, 5.0, 0.0), EPSILON, "no declared base: ratio 1");
    }

    /** The shipped Grave Call: add HP = a fortieth of the Wither's max health (Eden Loot boss type, owner 2026-10-25). */
    @Test
    void addsTakeAShareOfTheWithersHealth() {
        SummonEvent.StatScale fortieth = new SummonEvent.StatScale(0.025, true);
        double[][] levels = {{2201.0, 55.025}, {3187.0, 79.675}, {4986.0, 124.65}, {1711.0, 42.775}};
        for (double[] level : levels) {
            // The add's own 20 HP and the declared base do not enter it.
            assertEquals(level[1], SummonStats.target(fortieth, 20.0, level[0], 1711.0), EPSILON, "Wither HP " + level[0]);
        }
        SummonEvent.StatScale ratio = new SummonEvent.StatScale(1.0, false);
        assertEquals(SummonStats.scaled(20.0, 1.0, 3422.0, 1711.0), SummonStats.target(ratio, 20.0, 3422.0, 1711.0), EPSILON);
    }
}
