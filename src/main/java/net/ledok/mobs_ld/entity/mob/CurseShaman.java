package net.ledok.mobs_ld.entity.mob;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.ledok.mobs_ld.entity.ai.FlyAroundTargetGoal;
import net.ledok.mobs_ld.entity.ai.TelegraphedAttackGoal;
import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CurseShaman extends BaseDungeonMob {
    private static final double HOVER_HEIGHT = 1.5;
    private static final double HOVER_STEP_PER_TICK = 0.25;

    public CurseShaman(EntityType<? extends CurseShaman> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return BaseDungeonMob.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 45.0)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new TelegraphedAttackGoal(this));
        goalSelector.addGoal(3, new FlyAroundTargetGoal(this, 1.2D));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 10.0F));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected AttackZone attackZone() {
        return new AttackZone.CircleTarget(10.0F, 2.5F);
    }

    @Override
    protected int windupTicks() {
        return 15;
    }

    @Override
    protected int attackCooldownTicks() {
        return 70;
    }

    @Override
    protected int damagePersistTicks() {
        return 60;
    }

    @Override
    protected double idealAttackDistance() {
        return 6.0;
    }

    @Override
    protected boolean canMoveWhilePersisting() {
        return true;
    }

    @Override
    protected boolean cooldownStartsAtWindupStart() {
        return true;
    }

    @Override
    protected AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.GROW, 0xFF1A5C1A, 0xFF00FF44);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.setNoGravity(true);
        this.fallDistance = 0.0F;
        LivingEntity target = getTarget();
        double targetY;
        if (target != null) {
            targetY = target.getY() + HOVER_HEIGHT;
        } else {
            double groundTop = findGroundTopY();
            targetY = Double.isFinite(groundTop) ? groundTop + HOVER_HEIGHT : getY();
        }

        double dy = Mth.clamp(targetY - getY(), -HOVER_STEP_PER_TICK, HOVER_STEP_PER_TICK);
        setDeltaMovement(getDeltaMovement().x, 0.0, getDeltaMovement().z);
        setPos(getX(), getY() + dy, getZ());
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions base = super.getDefaultDimensions(pose);
        return base.withEyeHeight(base.height() * 0.5F);
    }

    private double findGroundTopY() {
        int x = Mth.floor(getX());
        int z = Mth.floor(getZ());
        int minY = level().getMinBuildHeight();
        int maxY = level().getMaxBuildHeight() - 1;
        int startY = Mth.clamp(Mth.floor(getY()) + 3, minY, maxY);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, startY, z);

        for (int y = startY; y >= minY; y--) {
            cursor.setY(y);
            VoxelShape shape = level().getBlockState(cursor).getCollisionShape(level(), cursor);
            if (shape.isEmpty()) {
                continue;
            }
            return y + shape.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }
}
