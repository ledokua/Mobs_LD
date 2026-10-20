package net.ledok.mobs_ld.registry;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/** Adds the threat test potions to a vanilla creative tab so they can be grabbed without give commands. */
public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(entries -> {
            entries.accept(PotionContents.createItemStack(Items.POTION, ModPotions.TAUNT));
            entries.accept(PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.PROVOKE));
        });
    }
}
