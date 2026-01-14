package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class GameManager {

    private static boolean isIngame = false;
    
    private BorderManager borderManager;
    private TeamManager teamManager;
    private UtilManager utilManager;
    private DownedManager downedManager;
    private FileConfiguration config;
    private final Logger logger;
    private World world;

    private BattleRoyale plugin; // Add plugin field

    public GameManager(BattleRoyale plugin, BorderManager borderManager, TeamManager teamManager, UtilManager utilManager, FileConfiguration config, DownedManager downedManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
        this.teamManager = teamManager;
        this.utilManager = utilManager;
        this.downedManager = downedManager;
        this.config = config;
        this.logger = Logger.getLogger("BattleRoyale");
        this.world = Bukkit.getWorlds().get(0); // Initialize world
    }

    public void brGameinit(String mode, int teamSize) {
        long nonSpectatorCount = Bukkit.getOnlinePlayers().stream().filter(p -> p.getGameMode() != GameMode.SPECTATOR).count();
        if (nonSpectatorCount < teamSize) {
            String message = "§c[배틀로얄] 게임 시작 실패: " + teamSize + "개의 팀을 만들기에 플레이어가 부족합니다. (필요: " + teamSize + "명, 현재: " + nonSpectatorCount + "명)";
            Bukkit.broadcastMessage(message);
            logger.warning(message);
            return;
        }

        String startMessage = String.format("§6[배틀로얄] §f%s 배틀로얄 게임을 시작합니다. (팀 수: §c%d§f)", 
            mode.equalsIgnoreCase("default") ? "기본" : "팀", teamSize);
        Bukkit.broadcastMessage(startMessage);

        setIngame(true);
        downedManager.clearAll(); // 기절 상태 초기화
        borderManager.brBorderinit();
        utilManager.updateCompass(new Location(world, borderManager.getBorderCenterX(), 0, borderManager.getBorderCenterZ()));
        utilManager.initializeFirstSupplyDrop();

        double maxHealth = config.getDouble("game.max_health", 40.0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.getInventory().clear();
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 6000, 4)); // Resistance 5 for 5 minutes (initial invincibility)
                scheduleResistanceCountdown(player);

                player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
                player.setHealth(maxHealth);
                player.setFoodLevel(20);
                player.setSaturation(20);

                // Give default items from the loaded list in the main plugin class
                for (ItemStack item : plugin.getDefaultItems()) {
                    player.getInventory().addItem(item.clone());
                }
            }
        }

        if (mode.equalsIgnoreCase("default")) {
            teamManager.splitTeam(teamSize);
        } else if (mode.equalsIgnoreCase("im")) {
            teamManager.teamTP(teamSize);
        }
        logger.info("Battle Royale game initialized in " + mode + " mode with " + teamSize + " teams.");
    }

    private void scheduleResistanceCountdown(Player player) {
        // 6000 ticks = 300 seconds (5 minutes)
        // 10초 전 경고: 6000 - 200 = 5800 ticks (290초 후)
        int warningTicks = 5800; // 290초 후 (10초 전)

        // 10초 전 경고
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isIngame && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                player.sendMessage("§c§l[배틀로얄] §e10초 후 저항이 풀립니다!");
            }
        }, warningTicks);

        // 3, 2, 1 카운트다운
        for (int i = 3; i >= 1; i--) {
            final int count = i;
            // 5800 + (10-count)*20 = 5800, 5940, 5960, 5980 for 10, 3, 2, 1
            int countdownTicks = 6000 - (count * 20); // 3초전=5940, 2초전=5960, 1초전=5980
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isIngame && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                    player.sendMessage("§c§l[배틀로얄] §f저항 해제까지 §c" + count + "§f초!");
                }
            }, countdownTicks);
        }

        // 저항 풀림 알림
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isIngame && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                player.sendMessage("§c§l[배틀로얄] §4저항이 풀렸습니다! 전투 시작!");
            }
        }, 6000);
    }

    public static void setIngame(boolean ingame) {
        isIngame = ingame;
    }

    public static boolean isIngame() {
        return isIngame;
    }
}