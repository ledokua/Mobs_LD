package net.ledok.mobs_ld.entity.mob;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class SpinnerMob extends BaseDungeonMob {
    public SpinnerMob(EntityType<? extends SpinnerMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return BaseDungeonMob.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 55.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected AttackZone attackZone() {
        return new AttackZone.Circle(5.5F);
    }

    @Override
    protected int windupTicks() {
        return 40;
    }

    @Override
    protected int attackCooldownTicks() {
        return 100;
    }

    @Override
    protected int damagePersistTicks() {
        return 0;
    }

    @Override
    protected double idealAttackDistance() {
        return 0.0;
    }

    @Override
    protected AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.GROW, 0xFF7A3000, 0xFFFF6600);
    }
}
