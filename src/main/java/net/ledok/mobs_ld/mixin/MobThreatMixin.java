package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.entity.ai.ThreatTargetGoal;
import net.ledok.mobs_ld.entity.threat.ThreatEvents;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.entity.threat.ThreatTable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes every {@link Mob} — vanilla and modded — a {@link ThreatHolder} carrying its own threat table, and
 * installs the threat-targeting goal on the mobs that should use it. This is what lets the aggro system track
 * damage against any mob: the {@code AFTER_DAMAGE} hook records threat for anything that is a ThreatHolder.
 * The table is empty (its inner map stays null) until the mob actually takes player damage, so idle mobs cost
 * just this one reference.
 */
@Mixin(Mob.class)
public class MobThreatMixin implements ThreatHolder {
    @Shadow
    @Final
    protected GoalSelector targetSelector;

    @Unique
    private final ThreatTable mobs_ld$threatTable = new ThreatTable();

    @Override
    public ThreatTable getThreatTable() {
        return mobs_ld$threatTable;
    }

    /**
     * Runs at the tail of the Mob constructor — after {@code registerGoals()} has populated the selectors, so
     * our goal lands at priority 0 above the vanilla target goals. Server-side only (client mobs never tick AI).
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mobs_ld$installThreatGoal(EntityType<? extends Mob> entityType, Level level, CallbackInfo ci) {
        if (level == null || level.isClientSide()) {
            return;
        }
        Mob self = (Mob) (Object) this;
        if (ThreatEvents.shouldThreatTarget(self)) {
            this.targetSelector.addGoal(0, new ThreatTargetGoal(self));
        }
    }
}
