package net.ledok.mobs_ld;

import net.fabricmc.api.ModInitializer;
import net.ledok.mobs_ld.command.MobsLdCommands;
import net.ledok.mobs_ld.entity.threat.ThreatEvents;
import net.ledok.mobs_ld.mob.MobDeclarations;
import net.ledok.mobs_ld.mob.behaviour.HitTracks;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.damage.AttackProfiles;
import net.ledok.mobs_ld.config.DamageConfig;
import net.ledok.mobs_ld.mob.combat.CombatEvents;
import net.ledok.mobs_ld.mob.host.warden.WardenAdapter;
import net.ledok.mobs_ld.mob.host.wither.WitherAdapter;
import net.ledok.mobs_ld.registry.ModAttributes;
import net.ledok.mobs_ld.registry.ModCreativeTabs;
import net.ledok.mobs_ld.registry.ModEffects;
import net.ledok.mobs_ld.registry.ModEntities;
import net.ledok.mobs_ld.registry.ModPotions;

public class MobsLdMod implements ModInitializer {
    public static final String MOD_ID = "mobs_ld";

    @Override
    public void onInitialize() {
        ModAttributes.register();
        ModEntities.register();
        MobDeclarations.register();
        WitherAdapter.register(); // host adapters and their host types, before any datapack is read
        WardenAdapter.register();
        MobBehaviours.register();
        HitTracks.register();
        AttackProfiles.register();
        DamageConfig.get(); // writes config/mobs_ld_damage.json with its defaults on the first start
        ModEffects.register();
        ModPotions.register();
        ModCreativeTabs.register();
        ThreatEvents.register();
        CombatEvents.register();
        MobsLdCommands.register();
    }
}
