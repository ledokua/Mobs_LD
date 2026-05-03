package net.ledok.mobs_ld.entity.mob;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class CryptGuard extends BaseDungeonMob {
    public CryptGuard(EntityType<? extends CryptGuard> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return BaseDungeonMob.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected AttackZone attackZone() {
        return new AttackZone.CircleRays(8, 20F, 0.5F);
    }

    @Override
    protected int windupTicks() {
        return 10;
    }

    @Override
    protected int attackCooldownTicks() {
        return 20;
    }

    @Override
    protected int damagePersistTicks() {
        return 10;
    }

    @Override
    protected double idealAttackDistance() {
        return 5;
    }

    @Override
    protected AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.SWEEP, 0xFF3A0060, 0xFFAA00FF);
    }
}
