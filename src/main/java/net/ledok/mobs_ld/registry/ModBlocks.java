package net.ledok.mobs_ld.registry;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class ModBlocks {
    public static final BooleanProperty PHASE = BooleanProperty.create("phase");

    public static final Block ATTACK_ZONE_RECT = register("attack_zone_rect");
    public static final Block ATTACK_ZONE_CIRCLE = register("attack_zone_circle");
    public static final Block ATTACK_ZONE_CONE = register("attack_zone_cone");

    private ModBlocks() {
    }

    private static Block register(String name) {
        Block block = new Block(BlockBehaviour.Properties.of()
                .noCollission()
                .noOcclusion()
                .strength(-1.0F, 3600000.0F)) {
            @Override
            protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                builder.add(PHASE);
            }
        };
        return Registry.register(
                BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, name),
                block
        );
    }

    public static void register() {
        // Triggers static registration.
    }
}
