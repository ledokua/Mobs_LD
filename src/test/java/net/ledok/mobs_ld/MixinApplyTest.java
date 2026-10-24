package net.ledok.mobs_ld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Loads (without initialising) every game class Mobs_LD's mixins target, inside Fabric's test launcher: a mixin whose
 * injection point no longer matches (a wrong descriptor or ordinal, a game update) fails here instead of in game.
 * Classes of optional mods (Spell Engine) are not on the test classpath and are not checked.
 */
class MixinApplyTest {
    private static final String[] TARGETS = {
            "net.minecraft.world.entity.Entity",
            "net.minecraft.world.entity.LivingEntity",
            "net.minecraft.world.entity.Mob",
            "net.minecraft.world.entity.player.Player",
            "net.minecraft.world.entity.AreaEffectCloud",
            "net.minecraft.world.effect.HealOrHarmMobEffect",
            "net.minecraft.world.entity.ai.goal.target.TargetGoal",
            "net.minecraft.world.entity.ai.targeting.TargetingConditions",
            "net.minecraft.world.entity.projectile.Projectile",
            "net.minecraft.world.entity.projectile.AbstractHurtingProjectile",
            "net.minecraft.world.entity.projectile.WitherSkull",
            "net.minecraft.world.entity.boss.wither.WitherBoss",
            "net.minecraft.client.Camera"
    };

    @Test
    void everyMixinApplies() {
        ClassLoader loader = MixinApplyTest.class.getClassLoader();
        for (String name : TARGETS) {
            assertDoesNotThrow(() -> Class.forName(name, false, loader), name);
        }
    }
}
