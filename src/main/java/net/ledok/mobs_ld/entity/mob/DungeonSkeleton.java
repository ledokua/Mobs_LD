package net.ledok.mobs_ld.entity.mob;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A stripped-down vanilla skeleton for dungeon use. Reuses the vanilla skeleton model, texture and combat
 * "brain" (bow/melee via {@code reassessWeaponGoal}) but drops the idle behaviours that cost server CPU:
 * <ul>
 *   <li>no {@code WaterAvoidingRandomStrollGoal} — idle skeletons stand still instead of pathfinding to random spots;</li>
 *   <li>no sun mechanics ({@code RestrictSunGoal}/{@code FleeSunGoal} and sunburn) — dungeon mobs never see daylight;</li>
 *   <li>no wolf-avoidance or iron-golem/turtle target scans.</li>
 * </ul>
 * Targeting is driven entirely by the mod's threat system: the priority-0 {@code ThreatTargetGoal} is installed on
 * every mob by {@code MobThreatMixin}, and this class lives in the mod package so it always qualifies. A cheap,
 * scan-free {@code HurtByTargetGoal} remains as a reactive fallback when the threat table is empty.
 */
public class DungeonSkeleton extends AbstractSkeleton {
    public DungeonSkeleton(EntityType<? extends AbstractSkeleton> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes()
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        // Deliberately omit stroll, sun and extra target goals. The bow/melee attack goal is added
        // separately by AbstractSkeleton.reassessWeaponGoal() (called from the constructor).
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // Reactive fallback only; the priority-0 threat goal (MobThreatMixin) drives real targeting.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** Dungeon skeletons never burn in daylight. Disabling this also skips the sunburn branch in aiStep(). */
    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    // Made public by the access widener (package-private in Mojmap), so the override must widen to match.
    @Override
    public SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }
}
