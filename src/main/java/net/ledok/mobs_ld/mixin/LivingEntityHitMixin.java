package net.ledok.mobs_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ledok.mobs_ld.mob.combat.DamageAudit;
import net.ledok.mobs_ld.mob.damage.AttackShaping;
import net.ledok.mobs_ld.mob.damage.HitCapHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Wraps the whole of {@code LivingEntity.hurt} for players, so it runs before every other mod's injections there (DD
 * Loot Stage's damage_dealt, the Arenas tier): an attack profile resizes the hit ({@link AttackShaping}), its cap is
 * handed to the player's {@code actuallyHurt} (PlayerHitCapMixin), and a running {@link DamageAudit} records it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHitMixin implements HitCapHolder {
    @Unique
    private double mobs_ld$hitCap = Double.NaN;

    @WrapMethod(method = "hurt")
    private boolean mobs_ld$shapeHit(DamageSource source, float amount, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return original.call(source, amount);
        }
        AttackShaping.Result shaped = AttackShaping.shape(player, source, amount);
        double outerCap = mobs_ld$hitCap; // a hit inside a hit (thorns, a mod's chain) keeps the outer one's cap
        mobs_ld$hitCap = shaped.cap();
        float before = player.getHealth() + player.getAbsorptionAmount();
        boolean landed;
        try {
            landed = original.call(source, shaped.amount());
        } finally {
            mobs_ld$hitCap = outerCap;
        }
        if (DamageAudit.recording()) {
            DamageAudit.record(player, source, amount, shaped.amount(), landed,
                    before - (player.getHealth() + player.getAbsorptionAmount()));
        }
        return landed;
    }

    @Override
    public double mobs_ld$hitCap() {
        return mobs_ld$hitCap;
    }

    @Override
    public void mobs_ld$setHitCap(double cap) {
        mobs_ld$hitCap = cap;
    }
}
