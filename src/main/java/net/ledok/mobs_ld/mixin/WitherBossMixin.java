package net.ledok.mobs_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.mobs_ld.registry.ModDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Summon charge fixes. A summoned Wither starts at a third of its max health and its 11 s charge heals a fixed 10
 * every 10 ticks, which only fills a vanilla 300 bar: with health scaled up (Dungeon Difficulty), it would start the
 * fight at about half. The charge now ends at full health. The blast it lets off then deals
 * {@link ModDamageTypes#WITHER_SPAWN_BLAST}, so per-boss damage multipliers can leave it alone.
 */
@Mixin(WitherBoss.class)
public class WitherBossMixin {
    @Unique
    private boolean mobs_ld$charging;

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void mobs_ld$noteCharge(CallbackInfo ci) {
        mobs_ld$charging = ((WitherBoss) (Object) this).getInvulnerableTicks() > 0;
    }

    // RETURN, not TAIL: the charge branch returns early, and TAIL only matches the method's last return.
    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void mobs_ld$fillAfterCharge(CallbackInfo ci) {
        WitherBoss self = (WitherBoss) (Object) this;
        if (mobs_ld$charging && self.getInvulnerableTicks() <= 0) {
            self.setHealth(self.getMaxHealth());
        }
    }

    /** The only explosion in this method is the end-of-charge blast; same blast, own damage type. */
    @WrapOperation(method = "customServerAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"))
    private Explosion mobs_ld$spawnBlastType(Level level, Entity source, double x, double y, double z, float radius,
                                             boolean fire, Level.ExplosionInteraction interaction,
                                             Operation<Explosion> original) {
        DamageSource damage = source.damageSources().source(ModDamageTypes.WITHER_SPAWN_BLAST, source, source);
        return level.explode(source, damage, null, x, y, z, radius, fire, interaction);
    }
}
