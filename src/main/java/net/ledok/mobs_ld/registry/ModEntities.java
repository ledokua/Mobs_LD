package net.ledok.mobs_ld.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.entity.attack.AttackZoneVisualEntity;
import net.ledok.mobs_ld.entity.boss.SandGolemGuardian;
import net.ledok.mobs_ld.entity.mob.Ball;
import net.ledok.mobs_ld.entity.mob.CurseShaman;
import net.ledok.mobs_ld.entity.mob.CryptGuard;
import net.ledok.mobs_ld.entity.mob.DungeonSkeleton;
import net.ledok.mobs_ld.entity.mob.FangMob;
import net.ledok.mobs_ld.entity.mob.Mummy;
import net.ledok.mobs_ld.entity.mob.SlabGuard;
import net.ledok.mobs_ld.entity.mob.SpinnerMob;
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
    public static final EntityType<SlabGuard> SLAB_GUARD = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "slab_guard"),
            EntityType.Builder.of(SlabGuard::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.9F)
                    .clientTrackingRange(8)
                    .build()
    );
    public static final EntityType<SpinnerMob> SPINNER_MOB = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "spinner_mob"),
            EntityType.Builder.of(SpinnerMob::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.9F)
                    .clientTrackingRange(8)
                    .build()
    );
    public static final EntityType<FangMob> FANG_MOB = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "fang_mob"),
            EntityType.Builder.of(FangMob::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.9F)
                    .clientTrackingRange(8)
                    .build()
    );
    public static final EntityType<CurseShaman> CURSE_SHAMAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "curse_shaman"),
            EntityType.Builder.of(CurseShaman::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(8)
                    .build()
    );
    public static final EntityType<AttackZoneVisualEntity> ATTACK_ZONE_VISUAL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "attack_zone_visual"),
            EntityType.Builder.of(AttackZoneVisualEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(64)
                    .build()
    );
    public static final EntityType<SandGolemGuardian> SAND_GOLEM_GUARDIAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "sand_golem_guardian"),
            EntityType.Builder.of(SandGolemGuardian::new, MobCategory.MONSTER)
                    .sized(2.2F, 4.6F)
                    .eyeHeight(4.4F)
                    .clientTrackingRange(16)
                    .build()
    );

    public static final EntityType<Mummy> MUMMY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "mummy"),
            EntityType.Builder.of(Mummy::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build()
    );

    public static final EntityType<DungeonSkeleton> DUNGEON_SKELETON = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "dungeon_skeleton"),
            EntityType.Builder.of(DungeonSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .clientTrackingRange(8)
                    .build()
    );

    public static final EntityType<Ball> BALL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "ball"),
            EntityType.Builder.of(Ball::new, MobCategory.MONSTER)
                    .sized(1.6F, 1.6F)
                    .clientTrackingRange(8)
                    .build()
    );

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(CRYPT_GUARD, CryptGuard.createAttributes());
        FabricDefaultAttributeRegistry.register(SLAB_GUARD, SlabGuard.createAttributes());
        FabricDefaultAttributeRegistry.register(SPINNER_MOB, SpinnerMob.createAttributes());
        FabricDefaultAttributeRegistry.register(FANG_MOB, FangMob.createAttributes());
        FabricDefaultAttributeRegistry.register(CURSE_SHAMAN, CurseShaman.createAttributes());
        FabricDefaultAttributeRegistry.register(SAND_GOLEM_GUARDIAN, SandGolemGuardian.createAttributes());
        FabricDefaultAttributeRegistry.register(MUMMY, Mummy.createAttributes());
        FabricDefaultAttributeRegistry.register(BALL, Ball.createAttributes());
        FabricDefaultAttributeRegistry.register(DUNGEON_SKELETON, DungeonSkeleton.createAttributes());
    }
}
