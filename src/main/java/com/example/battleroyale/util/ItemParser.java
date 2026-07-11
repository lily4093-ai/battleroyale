package com.example.battleroyale.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Parses config item strings of the form "ITEM_ID:AMOUNT_OR_RANGE:ENCHANT=LEVEL,ENCHANT=LEVEL"
 * e.g. "DIAMOND_SWORD:1:SHARPNESS=5,UNBREAKING=3" or "ENDER_PEARL:4-8".
 * Mirrors the original Paper plugin's UtilManager#parseItemStack behavior, including its
 * quirk of attempting enchantment lookups even for items that aren't enchantable.
 */
public final class ItemParser {

    private ItemParser() {
    }

    public static ItemStack parse(String itemString, Random random, Consumer<String> warn) {
        try {
            String[] parts = itemString.split(":");
            ResourceLocation id = new ResourceLocation(parts[0].toLowerCase(Locale.ROOT));
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) {
                warn.accept("Unknown item id in config: " + itemString);
                return null;
            }

            int amount;
            if (parts.length > 1) {
                String[] amountParts = parts[1].split("-");
                if (amountParts.length == 2) {
                    int min = Integer.parseInt(amountParts[0]);
                    int max = Integer.parseInt(amountParts[1]);
                    amount = random.nextInt(max - min + 1) + min;
                } else {
                    amount = Integer.parseInt(parts[1]);
                }
            } else {
                amount = 1;
            }

            ItemStack stack = new ItemStack(item, amount);

            if (parts.length > 2) {
                String[] enchantments = parts[2].split(",");
                for (String enchantmentString : enchantments) {
                    String[] enchantmentParts = enchantmentString.split("=");
                    if (enchantmentParts.length == 2) {
                        try {
                            ResourceLocation enchId = new ResourceLocation(enchantmentParts[0].toLowerCase(Locale.ROOT));
                            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(enchId);
                            int level = Integer.parseInt(enchantmentParts[1]);
                            if (enchantment != null) {
                                stack.enchant(enchantment, level);
                            } else {
                                warn.accept("Invalid enchantment name in config: " + enchantmentParts[0]);
                            }
                        } catch (NumberFormatException e) {
                            warn.accept("Invalid enchantment level in config: " + enchantmentParts[1]);
                        }
                    } else {
                        warn.accept("Invalid enchantment format in config: " + enchantmentString);
                    }
                }
            }
            return stack;
        } catch (Exception e) {
            warn.accept("Invalid item configuration in config: " + itemString + " - " + e.getMessage());
            return null;
        }
    }
}
