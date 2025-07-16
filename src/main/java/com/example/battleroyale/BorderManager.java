package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.attribute.Attribute;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.List;

public class BorderManager {

    private WorldBorder border;
    private World world;
    private List<Double> borderSizes;
    private List<Integer> countdownTimes;
    private double currentSize;
    private double borderCenterX;
    private double borderCenterZ;
    private double nextBorderCenterX;
    private double nextBorderCenterZ;
    private double compassTargetX;
    private double compassTargetZ;
    private boolean isShrinking = false;
    private List<Double> borderSpeed;
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private int currentPhase;
    
    // 타이머 관련 변수들
    private int countdownSeconds = 0;
    private BukkitRunnable countdownTask;
    private boolean isCountdownActive = false;
    
    // 보스바 업데이트 태스크
    private BukkitRunnable bossBarUpdateTask;
    private UtilManager utilManager;
    private BattleRoyale plugin; // Add plugin field
    private FileConfiguration config;
    private final Logger logger;

    public BorderManager(BattleRoyale plugin, UtilManager utilManager, FileConfiguration config) {
        this.plugin = plugin;
        this.utilManager = utilManager;
        this.config = config;
        this.logger = Logger.getLogger("BattleRoyale");
        this.world = Bukkit.getWorlds().get(0);
        this.border = world.getWorldBorder();

        // Load border sizes and countdown times from config
        this.borderSizes = config.getDoubleList("border.sizes");
        this.countdownTimes = config.getIntegerList("border.countdown_times");
        this.borderSpeed = config.getDoubleList("border.speed");

        this.currentSize = borderSizes.get(0); // Set initial size to the first value in borderSizes list
        this.borderCenterX = 0; // Default center X
        this.borderCenterZ = 0; // Default center Z
        
        // Initialize BossBars for currently online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            addPlayerToBossBar(player);
        }
        startBossBarUpdater(); // 보스바 업데이트 시작
    }

    public double getBorderSize(int phase) {
        if (phase >= 0 && phase < borderSizes.size()) {
            return borderSizes.get(phase);
        }
        return 0;
    }

    public void brBorderinit() {
        currentPhase = 0; // Start from phase 0
        currentSize = borderSizes.get(currentPhase); // Initial size 2500
        borderCenterX = 0; // Start at center 0,0
        borderCenterZ = 0;
        border.setCenter(borderCenterX, borderCenterZ);
        border.setSize(currentSize);

        // Calculate the center for the *first* shrink (from 2500 to 2000)
        double nextSize = getBorderSize(currentPhase + 1);
        Location nextCenter = makeRandomcenter(currentSize, nextSize, borderCenterX, borderCenterZ);
        nextBorderCenterX = nextCenter.getX();
        nextBorderCenterZ = nextCenter.getZ();
        compassTargetX = nextBorderCenterX;
        compassTargetZ = nextBorderCenterZ;

        // Stop the waiting boss bar updater and start the first countdown
        stopCountdown(); // Ensure no other countdown is running
        startCountdown(countdownTimes.get(currentPhase)); // Start countdown to the first shrink
        
        // 즉시 보스바 업데이트 (게임 시작 직후)
        updateBossBarForAllPlayers(currentSize);
        utilManager.updateCompass(new Location(world, compassTargetX, 0, compassTargetZ)); // Update compass to next center
    }

    public void makeIngameborder(int phase, double newSize, double prevSize, double centerX, double centerZ) {
        this.currentPhase = phase;
        currentSize = newSize;
        Bukkit.broadcastMessage("§6[배틀로얄] §f자기장이 줄어듭니다!");

        // 카운트다운 중지
        stopCountdown();

        long shrinkDurationTicks = (long) ((prevSize - newSize) / borderSpeed.get(Math.min(currentPhase, borderSpeed.size() - 1)) * 20);
        border.setSize(newSize, (long)((prevSize - newSize) / borderSpeed.get(Math.min(currentPhase, borderSpeed.size() - 1))));
        setBorderCenter(prevSize, border.getCenter().getX(), border.getCenter().getZ(), centerX, centerZ, shrinkDurationTicks);
    }

    public void setBorderCenter(double prevSize, double xLoc1, double zLoc1, double xLoc2, double zLoc2, long time) {
        isShrinking = true;
        final long finalTime = Math.max(1, time);

        new BukkitRunnable() {
            double currentX = xLoc1;
            double currentZ = zLoc1;
            double xStep = (finalTime > 0) ? (xLoc2 - xLoc1) / finalTime : 0;
            double zStep = (finalTime > 0) ? (zLoc2 - zLoc1) / finalTime : 0;
            long loopNumber = 0;

            @Override
            public void run() {
                if (loopNumber >= finalTime) {
                    border.setCenter(xLoc2, zLoc2);
                    borderCenterX = xLoc2;
                    borderCenterZ = zLoc2;
                    isShrinking = false;

                    Bukkit.broadcastMessage("§6[배틀로얄] §a자기장 축소가 완료되었습니다!");

                    if (currentPhase + 1 < borderSizes.size()) {
                        double nextSize = getBorderSize(currentPhase + 1);
                        Location randomCenter = makeRandomcenter(currentSize, nextSize, borderCenterX, borderCenterZ);
                        nextBorderCenterX = randomCenter.getX();
                        nextBorderCenterZ = randomCenter.getZ();
                        compassTargetX = nextBorderCenterX;
                        compassTargetZ = nextBorderCenterZ;
                        utilManager.updateCompass(new Location(world, compassTargetX, 0, compassTargetZ)); // Update compass to next center
                    }
                    
                    // 자기장 축소 완료 후 다음 축소까지 대기 시간 설정
                    if (currentPhase < countdownTimes.size()) {
                        startCountdown(countdownTimes.get(currentPhase));
                    } else {
                        // Handle case where there are no more countdowns defined (e.g., game end)
                    }
                    cancel();
                    return;
                }

                loopNumber++;
                double progress = (double) loopNumber / finalTime;
                double progressPercent = Math.round(progress * 100);
                double currentBorderSize = prevSize - ((prevSize - currentSize) * progress);

                if (loopNumber % 60 == 0) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.isOnline()) {
                            if (!brIsinCurrentBorder(player.getLocation().getX(), player.getLocation().getZ())) {
                                player.sendTitle("§c§l위험!", "§4§l[!] 자기장 안으로 진입해야 합니다!", 10, 40, 10);
                            }
                        }
                    }
                }

                if (loopNumber % 10 == 0) {
                    String actionBar = String.format("§7자기장 크기: §c%.0f §7→ §c%.0f §f| §7축소 진행률: §c%.0f%% §f| §7자기장 중심: §c(%.0f, %.0f) §f| §7현재 크기: §e%.0f",
                            prevSize, currentSize, progressPercent, currentX, currentZ, currentBorderSize);

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.isOnline()) {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBar));
                        }
                    }
                    updateBossBarForAllPlayers(currentBorderSize);
                }
                
                if (loopNumber == finalTime / 4 || loopNumber == finalTime / 2 || loopNumber == (finalTime * 3) / 4) {
                    String message = String.format("§6[배틀로얄] §f자기장 축소 §c%.0f%% §f완료!", progressPercent);
                    Bukkit.broadcastMessage(message);
                }

                currentX += xStep;
                currentZ += zStep;
                border.setCenter(currentX, currentZ);
                borderCenterX = currentX;
                borderCenterZ = currentZ;
                compassTargetX = xLoc2;
                compassTargetZ = zLoc2;
                utilManager.updateCompass(new Location(world, compassTargetX, 0, compassTargetZ)); // Update compass to final destination during shrink
            }
        }.runTaskTimer(BattleRoyale.getPlugin(BattleRoyale.class), 1L, 1L);
    }

    public Location makeRandomcenter(double prevSize, double newSize, double prevCenterx, double prevCenterz) {
        double maxDist = (prevSize - newSize) / 2;
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = random.nextDouble() * maxDist;
        double randomX = prevCenterx + Math.cos(angle) * dist;
        double randomZ = prevCenterz + Math.sin(angle) * dist;
        return new Location(world, randomX, 0, randomZ);
    }

    public boolean brIsinCurrentBorder(double x, double z) {
        double halfSize = currentSize / 2;
        return x >= (borderCenterX - halfSize) && x <= (borderCenterX + halfSize) &&
               z >= (borderCenterZ - halfSize) && z <= (borderCenterZ + halfSize);
    }

    public void brShrinkborder() {
        // No longer hardcoding borderSpeed based on phase, using config value

        double prevSize = currentSize;
        currentPhase++;
        double newSize = getBorderSize(currentPhase);
        makeIngameborder(currentPhase, newSize, prevSize, nextBorderCenterX, nextBorderCenterZ);
    }

    public Location brGetspawnloc(String type) {
        double space = 500;
        double x = 0, z = 0;
        if (type.equals("++")) { x = borderCenterX + (currentSize / 2) - space; z = borderCenterZ + (currentSize / 2) - space; }
        else if (type.equals("--")) { x = borderCenterX - (currentSize / 2) + space; z = borderCenterZ - (currentSize / 2) + space; }
        else if (type.equals("+-")) { x = borderCenterX + (currentSize / 2) - space; z = borderCenterZ - (currentSize / 2) + space; }
        else if (type.equals("-+")) { x = borderCenterX - (currentSize / 2) + space; z = borderCenterZ + (currentSize / 2) - space; }
        Location tempLoc = new Location(world, x, 256, z);
        while (tempLoc.getBlockY() > 0 && tempLoc.getBlock().getType().isAir()) {
            tempLoc.subtract(0, 1, 0);
        }
        tempLoc.add(0, 1, 0);
        return tempLoc;
    }

    // 카운트다운 시작
    public void startCountdown(int seconds) {
        stopCountdown(); // 기존 카운트다운 중지
        countdownSeconds = seconds;
        isCountdownActive = true;
        
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdownSeconds <= 0) {
                    isCountdownActive = false;
                    // 자동으로 다음 자기장 축소 시작
                    if (currentPhase + 1 < borderSizes.size()) {
                        brShrinkborder();
                    } else {
                        // All phases completed, end the game
                        endGame();
                    }
                    cancel();
                    return;
                }
                
                // 카운트다운 중 액션바 업데이트
                updateCountdownActionBar();
                countdownSeconds--;
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    // 카운트다운 중지
    public void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        isCountdownActive = false;
    }

    // 카운트다운 중 액션바 업데이트
    private void updateCountdownActionBar() {
        if (!isCountdownActive) return;
        
        String actionBar = String.format("§7자기장 크기: §c%.0f §f| §7자기장 축소까지: §c%d초 남음 §f| §7다음 자기장 중앙: §c(%.0f,%.0f)",
                currentSize, countdownSeconds, nextBorderCenterX, nextBorderCenterZ);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBar));
            }
        }
    }

    private void updateBossBarForAllPlayers(double currentBorderSize) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateBossBarForPlayer(player, currentBorderSize);
        }
    }

    private void updateBossBarForPlayer(Player player, double borderSize) {
        BossBar bar = playerBossBars.get(player.getUniqueId());
        if (bar == null) return;

        String title;
        BarColor color = BarColor.BLUE;
        BarStyle style = BarStyle.SOLID;
        double progress = 1.0;
        boolean showBar = true;

        // Get actual border size and center
        double actualBorderSize = border.getSize();
        double actualBorderCenterX = border.getCenter().getX();
        double actualBorderCenterZ = border.getCenter().getZ();

        Material heldItem = player.getInventory().getItemInMainHand().getType();

        if (heldItem == Material.COMPASS) {
            // Compass: Show border info
            double playerX = player.getLocation().getX();
            double playerZ = player.getLocation().getZ();
            double dx = Math.abs(playerX - actualBorderCenterX);
            double dz = Math.abs(playerZ - actualBorderCenterZ);
            double distance = Math.sqrt(dx * dx + dz * dz); // Use Euclidean distance for accuracy
            double halfSize = actualBorderSize / 2;
            progress = (distance <= halfSize) ? 1.0 - (distance / halfSize) : 0.0;

            if (progress > 0.7) color = BarColor.GREEN;
            else if (progress > 0.4) color = BarColor.YELLOW;
            else if (progress > 0.1) color = BarColor.RED;
            else color = BarColor.PURPLE;

            title = (distance <= halfSize)
                    ? String.format("자기장 중심까지: %.0fm (크기: %.0fm)", distance, actualBorderSize)
                    : String.format("§c자기장 밖! 중심까지: %.0fm (크기: %.0fm)", distance, actualBorderSize);

        } else if (heldItem == Material.CLOCK) {
            // Clock: Show supply drop countdown
            long remainingTicks = utilManager.getSupplyDropRemainingTicks();
            if (remainingTicks > 0) {
                long minutes = remainingTicks / (20 * 60);
                long seconds = (remainingTicks / 20) % 60;
                title = String.format("§e다음 보급까지: %02d분 %02d초", minutes, seconds);
                progress = (double) remainingTicks / utilManager.getSupplyDropTotalTicks();
            } else {
                title = "§a보급품 대기 중...";
                progress = 1.0;
            }
            color = BarColor.YELLOW;
        } else {
            // Default: Show basic border info
            title = String.format("§7자기장 크기: §e%.0fm §7| §7다음 중심: §e(%.0f, %.0f)", actualBorderSize, compassTargetX, compassTargetZ);
            color = BarColor.BLUE;
            progress = actualBorderSize / borderSizes.get(0); // Show progress relative to initial size
        }

        bar.setVisible(showBar);
        bar.setColor(color);
        bar.setTitle(title);
        bar.setStyle(style);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    // 보스바 업데이트 태스크 시작 (게임 중 항상 실행)
    public void startBossBarUpdater() {
        stopBossBarUpdater(); // 기존 태스크 중지
        
        bossBarUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 카운트다운 중이면 액션바 업데이트
                if (isCountdownActive) {
                    updateCountdownActionBar();
                }
                
                // 모든 플레이어의 보스바 업데이트 (들고 있는 아이템에 따라)
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateBossBarForPlayer(player, currentSize);
                }
            }
        };
        bossBarUpdateTask.runTaskTimer(plugin, 0L, 5L);
    }

    // 보스바 업데이트 태스크 중지
    public void stopBossBarUpdater() {
        if (bossBarUpdateTask != null) {
            bossBarUpdateTask.cancel();
            bossBarUpdateTask = null;
        }
    }

    public void addPlayerToBossBar(Player player) {
        BossBar bar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
        bar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bar);
        updateBossBarForPlayer(player, currentSize);
    }

    public void removePlayerFromBossBar(Player player) {
        BossBar bar = playerBossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    // 수동으로 카운트다운 시작하는 메서드 (필요시 사용)
    public void startManualCountdown(int seconds) {
        startCountdown(seconds);
    }
    
    // Getters
    public WorldBorder getBorder() { return border; }
    public double getCurrentSize() { return currentSize; }
    public void setCurrentSize(double currentSize) { this.currentSize = currentSize; }
    public Location getNextBorderCenter() { return new Location(this.world, nextBorderCenterX, 0, nextBorderCenterZ); }
    public boolean isShrinking() { return isShrinking; }
    public double getBorderCenterX() { return borderCenterX; }
    public double getBorderCenterZ() { return borderCenterZ; }
    public double getNextBorderCenterX() { return nextBorderCenterX; }
    public double getNextBorderCenterZ() { return nextBorderCenterZ; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public boolean isCountdownActive() { return isCountdownActive; }
    public int getCurrentPhase() { return currentPhase; }
    public double getCompassTargetX() { return compassTargetX; }
    public double getCompassTargetZ() { return compassTargetZ; }

    private void endGame() {
        Bukkit.broadcastMessage("§6[배틀로얄] §a게임이 종료되었습니다! 모든 플레이어를 자기장 중심으로 이동시킵니다.");
        Location centerLoc = new Location(world, borderCenterX, world.getHighestBlockYAt((int)borderCenterX, (int)borderCenterZ) + 1, borderCenterZ);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(centerLoc);
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue()); // Full health
            player.setFoodLevel(20); // Full food
            player.setSaturation(20); // Full saturation
            player.getInventory().clear(); // Clear inventory
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType())); // Clear potion effects
        }
        // Optionally, reset game state in GameManager or other managers
        GameManager.setIngame(false);
    }
    
}