

package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class UtilManager implements Listener {

    private BattleRoyale plugin;
    private List<Material> disabledCrafting;

    public UtilManager(BattleRoyale plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        disabledCrafting = Arrays.asList(
                // ... (disabled crafting list)
        );
    }

    public void updateCompass(Location target) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setCompassTarget(target);
        }
    }

    public void spawnSupplyDrop(Location location) {
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        Random random = new Random();
        chest.getInventory().setItem(0, new ItemStack(Material.BLAZE_ROD, random.nextInt(5) + 1));
        chest.getInventory().setItem(1, new ItemStack(Material.NETHERITE_INGOT, random.nextInt(3)));
        chest.getInventory().setItem(2, new ItemStack(Material.QUARTZ, random.nextInt(6) + 5));
        chest.getInventory().setItem(3, new ItemStack(Material.GLOWSTONE_DUST, random.nextInt(4) + 5));
        chest.getInventory().setItem(4, new ItemStack(Material.IRON_INGOT, 64));
        chest.getInventory().setItem(5, new ItemStack(Material.IRON_INGOT, random.nextInt(33) + 32));
        chest.getInventory().setItem(6, new ItemStack(Material.GOLD_INGOT, random.nextInt(21) + 10));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Material blockType = event.getBlock().getType();
        if (blockType == Material.IRON_ORE || blockType == Material.DEEPSLATE_IRON_ORE) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.IRON_INGOT, new Random().nextInt(5) + 1));
        } else if (blockType == Material.GOLD_ORE || blockType == Material.DEEPSLATE_GOLD_ORE) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT, new Random().nextInt(5) + 1));
        } else if (blockType == Material.COPPER_ORE || blockType == Material.DEEPSLATE_COPPER_ORE) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.COPPER_INGOT, new Random().nextInt(5) + 1));
        } else if (blockType == Material.SAND) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GLASS));
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        Material itemType = event.getRecipe().getResult().getType();
        if (disabledCrafting.contains(itemType) || itemType.toString().contains("POTION") || itemType.toString().contains("ARROW") || itemType.toString().contains("BED")) {
            event.setCancelled(true);
        }
    }
}

