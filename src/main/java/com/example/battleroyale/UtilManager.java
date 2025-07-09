

package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class UtilManager implements Listener {

    private BattleRoyale plugin;
    private BorderManager borderManager;
    private List<Material> disabledCrafting;

    public UtilManager(BattleRoyale plugin, BorderManager borderManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        disabledCrafting = Arrays.asList(
                Material.ENCHANTING_TABLE, Material.ANVIL, Material.BEACON, Material.END_CRYSTAL, Material.TOTEM_OF_UNDYING
        );

        // brSuply every 4 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                if (GameManager.isIngame() && GameManager.getPhase() >= 1 && GameManager.getPhase() <= 6) {
                    spawnSupplyDrop();
                }
            }
        }.runTaskTimer(plugin, 20L * 60 * 4, 20L * 60 * 4); // Every 4 minutes (20 ticks * 60 seconds * 4 minutes)
    }

    public void updateCompass(Location target) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setCompassTarget(target);
        }
    }

    public void spawnSupplyDrop() {
        Location spawnLoc = borderManager.makeRandomcenter(borderManager.getCurrentSize(), 10, borderManager.getNextBorderCenter().getX(), borderManager.getNextBorderCenter().getZ());
        spawnLoc.setY(256);
        while (spawnLoc.getBlock().getType() == Material.AIR && spawnLoc.getY() > 0) {
            spawnLoc.subtract(0, 1, 0);
        }
        spawnLoc.add(0, 1, 0);

        spawnLoc.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) spawnLoc.getBlock().getState();
        Random random = new Random();
        chest.getInventory().setItem(0, new ItemStack(Material.BLAZE_ROD, random.nextInt(5) + 1));
        chest.getInventory().setItem(1, new ItemStack(Material.NETHERITE_INGOT, random.nextInt(3)));
        chest.getInventory().setItem(2, new ItemStack(Material.QUARTZ, random.nextInt(6) + 5));
        chest.getInventory().setItem(3, new ItemStack(Material.GLOWSTONE_DUST, random.nextInt(4) + 5));
        chest.getInventory().setItem(4, new ItemStack(Material.IRON_INGOT, 64));
        chest.getInventory().setItem(5, new ItemStack(Material.IRON_INGOT, random.nextInt(33) + 32));
        chest.getInventory().setItem(6, new ItemStack(Material.GOLD_INGOT, random.nextInt(21) + 10));
        Bukkit.broadcastMessage("§6[배틀로얄] §f(" + (int) spawnLoc.getX() + ", " + (int) spawnLoc.getZ() + ") 에 보급이 소환되었습니다!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().setMaxHealth(20.0);
        event.getPlayer().setHealth(event.getPlayer().getMaxHealth());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                event.getPlayer().setMaxHealth(20.0);
                event.getPlayer().setHealth(event.getPlayer().getMaxHealth());
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
                event.setDamage(event.getDamage() / 2.0);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
            Random random = new Random();
            victim.getWorld().dropItemNaturally(victim.getLocation(), new ItemStack(Material.GUNPOWDER, random.nextInt(3) + 1));
            if (random.nextDouble() < 0.35) { // 35% chance
                victim.getWorld().dropItemNaturally(victim.getLocation(), new ItemStack(Material.AMETHYST_SHARD, 1));
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Material blockType = event.getBlock().getType();
        if (blockType == Material.IRON_ORE || blockType == Material.DEEPSLATE_IRON_ORE) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.IRON_INGOT, new Random().nextInt(5) + 1));
        } else if (blockType == Material.DEEPSLATE_IRON_ORE) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.IRON_INGOT, new Random().nextInt(5) + 1));
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

