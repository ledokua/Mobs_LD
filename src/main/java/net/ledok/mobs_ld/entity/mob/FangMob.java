package net.ledok.mobs_ld.entity.mob;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class FangMob extends BaseDungeonMob {
    public FangMob(EntityType<? extends FangMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return BaseDungeonMob.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected AttackZone attackZone() {
        return new AttackZone.Cone(8.0F, 180.0F);
    }

    @Override
    protected int windupTicks() {
        return 20;
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
        return 3.0;
    }

    @Override
    protected AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.SWEEP, 0xFF3A0060, 0xFFAA00FF);
    }
}
