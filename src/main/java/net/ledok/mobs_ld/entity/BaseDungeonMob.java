package net.ledok.mobs_ld.entity;

import net.ledok.mobs_ld.entity.ai.MoveToIdealDistanceGoal;
import net.ledok.mobs_ld.entity.ai.TelegraphedAttackGoal;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public abstract class BaseDungeonMob extends Monster {
    private int attackCooldown = 0;
    private boolean windingUp = false;

    protected BaseDungeonMob(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    protected abstract AttackZone attackZone();

    protected abstract int windupTicks();

    protected abstract int attackCooldownTicks();

    protected abstract double idealAttackDistance();

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public void setAttackCooldown(int attackCooldown) {
        this.attackCooldown = attackCooldown;
    }

    public boolean isWindingUp() {
        return windingUp;
    }

    public void setWindingUp(boolean windingUp) {
        this.windingUp = windingUp;
    }

    public static AttributeSupplier.Builder createBaseAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new TelegraphedAttackGoal(this));
        goalSelector.addGoal(3, new MoveToIdealDistanceGoal(this, 1.0D));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            if (attackCooldown > 0) {
                attackCooldown--;
            }
            LivingEntity target = getTarget();
            if (target != null) {
                getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
        }
    }

    public AttackZone getAttackZone() {
        return attackZone();
    }

    public int getWindupTicks() {
        return windupTicks();
    }

    public int getAttackCooldownTicks() {
        return attackCooldownTicks();
    }

    public double getIdealAttackDistance() {
        return idealAttackDistance();
    }
}
