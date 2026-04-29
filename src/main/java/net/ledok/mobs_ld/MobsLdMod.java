package net.ledok.mobs_ld;

import net.fabricmc.api.ModInitializer;
import net.ledok.mobs_ld.registry.ModBlocks;
import net.ledok.mobs_ld.registry.ModEntities;

public class MobsLdMod implements ModInitializer {
    public static final String MOD_ID = "mobs_ld";

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModEntities.register();
    }
}
