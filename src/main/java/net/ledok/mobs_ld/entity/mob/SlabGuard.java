package net.ledok.mobs_ld.entity.mob;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class SlabGuard extends BaseDungeonMob {
    public SlabGuard(EntityType<? extends SlabGuard> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return BaseDungeonMob.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected AttackZone attackZone() {
        return new AttackZone.Rectangle(3.0F, 4.0F, 0.5F);
    }

    @Override
    protected int windupTicks() {
        return 25;
    }

    @Override
    protected int attackCooldownTicks() {
        return 60;
    }

    @Override
    protected int damagePersistTicks() {
        return 0;
    }

    @Override
    protected double idealAttackDistance() {
        return 1.5;
    }
}
