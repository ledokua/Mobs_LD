package net.ledok.mobs_ld.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.entity.mob.CryptGuard;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<CryptGuard> CRYPT_GUARD = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "crypt_guard"),
            EntityType.Builder.of(CryptGuard::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.9F)
                    .clientTrackingRange(8)
                    .build()
    );

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(CRYPT_GUARD, CryptGuard.createAttributes());
    }
}
