package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class UtilManager implements Listener {

    private BattleRoyale plugin;
    private BorderManager borderManager;
    private final List<Material> disabledCrafting;
    private FileConfiguration config;
    private List<String> supplyDropItemsConfig;
    private int supplyDropIntervalMinutes;
    private final Logger logger;
    private final Random random;
    private long supplyDropTimerTicks; // Add this field

    public UtilManager(BattleRoyale plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = Logger.getLogger("BattleRoyale");
        this.random = new Random();
        this.disabledCrafting = Arrays.asList(
                Material.ENCHANTED_GOLDEN_APPLE,
                Material.END_CRYSTAL,
                Material.RESPAWN_ANCHOR,
                Material.TOTEM_OF_UNDYING
        );

        // Load config values
        this.supplyDropIntervalMinutes = config.getInt("supply_drop.interval_minutes", 4);
        this.supplyDropItemsConfig = config.getStringList("supply_drop.items");
        this.supplyDropTimerTicks = 20L * 60 * supplyDropIntervalMinutes; // Initialize timer

        // 이벤트 리스너 등록
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (GameManager.isIngame()) { // Removed borderManager.getCurrentPhase() >= 1
                    supplyDropTimerTicks -= 20; // Decrement by 1 second (20 ticks)
                    if (supplyDropTimerTicks <= 0) {
                        spawnSupplyDrop();
                        supplyDropTimerTicks = 20L * 60 * supplyDropIntervalMinutes; // Reset timer
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Run every second
    }

    public long getSupplyDropRemainingTicks() {
        return supplyDropTimerTicks;
    }

    public int getSupplyDropIntervalMinutes() {
        return supplyDropIntervalMinutes;
    }

    public long getSupplyDropTotalTicks() {
        return 20L * 60 * supplyDropIntervalMinutes;
    }

    public void setBorderManager(BorderManager borderManager) {
        this.borderManager = borderManager;
    }

    public void updateCompass(Location target) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setCompassTarget(target);
        }
    }

    public void spawnSupplyDrop() {
        if (borderManager == null || borderManager.getBorder() == null) return;

        Random random = new Random();
        double currentBorderRadius = borderManager.getCurrentSize() / 2;
        double centerX = borderManager.getBorderCenterX();
        double centerZ = borderManager.getBorderCenterZ();

        // Calculate random spawn location within the current border
        double spawnX = centerX + (random.nextDouble() * 2 - 1) * (currentBorderRadius * 0.8); // 80% of border to prevent edge spawns
        double spawnZ = centerZ + (random.nextDouble() * 2 - 1) * (currentBorderRadius * 0.8);

        Location spawnLoc = new Location(borderManager.getBorder().getWorld(), spawnX, 256, spawnZ);
        // Find the highest non-air block
        while (spawnLoc.getBlockY() > 0 && spawnLoc.getBlock().getType().isAir()) {
            spawnLoc.subtract(0, 1, 0);
        }
        spawnLoc.add(0, 1, 0); // Move up one block to be on top of the ground

        // 기존 체스트 제거 (같은 위치에 있을 경우)
        if (spawnLoc.getBlock().getType() == Material.CHEST) {
            spawnLoc.getBlock().setType(Material.AIR);
        }

        spawnLoc.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) spawnLoc.getBlock().getState();
        
        // 보급품 아이템 설정
        for (String itemString : supplyDropItemsConfig) {
            ItemStack item = parseItemStack(itemString);
            if (item != null) {
                chest.getInventory().addItem(item);
            }
        }

        Bukkit.broadcastMessage("§6[배틀로얄] §f(" + (int) spawnLoc.getX() + ", " + (int) spawnLoc.getZ() + ") 에 보급이 소환되었습니다!");
    }

    public ItemStack parseItemStack(String itemString) {
        try {
            String[] parts = itemString.split(":");
            Material material = Material.valueOf(parts[0].toUpperCase());
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
                amount = 1; // Default amount if not specified
            }
            ItemStack item = new ItemStack(material, amount);

            // New enchantment parsing logic
            // Format: MATERIAL:AMOUNT:ENCHANT_NAME1=LEVEL1,ENCHANT_NAME2=LEVEL2
            if (parts.length > 2) {
                String[] enchantments = parts[2].split(",");
                for (String enchantmentString : enchantments) {
                    String[] enchantmentParts = enchantmentString.split("="); // Use '=' as separator
                    if (enchantmentParts.length == 2) {
                        try {
                            Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchantmentParts[0].toLowerCase()));
                            int level = Integer.parseInt(enchantmentParts[1]);
                            if (enchantment != null) {
                                item.addUnsafeEnchantment(enchantment, level);
                            } else {
                                plugin.getLogger().warning("Invalid enchantment name in config: " + enchantmentParts[0]);
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid enchantment level in config: " + enchantmentParts[1]);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid enchantment key in config: " + enchantmentParts[0]);
                        }
                    } else {
                        plugin.getLogger().warning("Invalid enchantment format in config: " + enchantmentString);
                    }
                }
            }
            return item;
        } catch (Exception e) { // Catch broader exceptions to prevent server crashes from bad config
            plugin.getLogger().warning("Invalid item configuration in config.yml: " + itemString + " - " + e.getMessage());
            return null;
        }
    }

    

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && 
                event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
                event.setDamage(event.getDamage() / 2.0);
            }
        }
    }

    

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Material blockType = event.getBlock().getType();
        Random random = new Random();
        Location loc = event.getBlock().getLocation();

        // 커스텀 드롭이 필요한 블록들만 처리
        switch (blockType) {
            // Iron Ore
            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
                event.setDropItems(false); // 기존 드롭 비활성화
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.IRON_INGOT, random.nextInt(5) + 1));
                break;

            // Gold Ore
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
                event.setDropItems(false);
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.GOLD_INGOT, 1));
                break;

            // Copper Ore
            case COPPER_ORE:
            case DEEPSLATE_COPPER_ORE:
                event.setDropItems(false);
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.COPPER_INGOT, random.nextInt(10) + 6));
                break;

            // Lapis Ore
            case LAPIS_ORE:
            case DEEPSLATE_LAPIS_ORE:
                event.setDropItems(false);
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.LAPIS_LAZULI, random.nextInt(5) + 4));
                break;

            // Redstone Ore
            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
                event.setDropItems(false);
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.REDSTONE, random.nextInt(2) + 4));
                break;

            // Diamond Ore
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
                event.setDropItems(false);
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.DIAMOND, 1));
                break;

            // Emerald Ore
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                event.setDropItems(false);
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.EMERALD, 1));
                break;

            // Coal Ore
            case COAL_ORE:
            case DEEPSLATE_COAL_ORE:
                event.setDropItems(false);
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.COAL, 1));
                break;

            // Sand
            case SAND:
                event.setDropItems(false);
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.GLASS, 1));
                if (random.nextDouble() < 0.20) {
                    loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.AMETHYST_SHARD, 1));
                }
                break;
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        Material itemType = event.getRecipe().getResult().getType();
        if (disabledCrafting.contains(itemType) || 
            itemType.toString().contains("POTION") || 
            itemType.toString().contains("ARROW") || 
            itemType.toString().contains("BED")) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("§c이 아이템은 제작할 수 없습니다!");
        }
    }
}