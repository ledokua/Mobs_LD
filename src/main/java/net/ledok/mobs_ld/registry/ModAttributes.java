package net.ledok.mobs_ld.registry;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class ModAttributes {
    /**
     * Per-player multiplier on threat generated from damage dealt. 1.0 = the config's flat rate; gear, spells,
     * or roles can push it up (a tank set granting 1.5x) or down. Added to every player via
     * {@code net.ledok.mobs_ld.mixin.PlayerThreatAttributeMixin}.
     */
    public static final Holder<Attribute> THREAT_MULTIPLIER = Registry.registerForHolder(
            BuiltInRegistries.ATTRIBUTE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "threat_multiplier"),
            new RangedAttribute("attribute.name.mobs_ld.threat_multiplier", 1.0, 0.0, 1024.0).setSyncable(true)
    );

    private ModAttributes() {
    }

    /** Forces class-load so the static field registers. */
    public static void register() {
    }
}
