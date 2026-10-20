package net.ledok.mobs_ld;

import net.fabricmc.api.ModInitializer;
import net.ledok.mobs_ld.entity.threat.ThreatEvents;
import net.ledok.mobs_ld.registry.ModAttributes;
import net.ledok.mobs_ld.registry.ModBlocks;
import net.ledok.mobs_ld.registry.ModCreativeTabs;
import net.ledok.mobs_ld.registry.ModEffects;
import net.ledok.mobs_ld.registry.ModEntities;
import net.ledok.mobs_ld.registry.ModPotions;

public class MobsLdMod implements ModInitializer {
    public static final String MOD_ID = "mobs_ld";

    @Override
    public void onInitialize() {
        ModAttributes.register();
        ModBlocks.register();
        ModEntities.register();
        ModEffects.register();
        ModPotions.register();
        ModCreativeTabs.register();
        ThreatEvents.register();
    }
}
