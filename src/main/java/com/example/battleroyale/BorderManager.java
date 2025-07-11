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
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BorderManager {

    private WorldBorder border;
    private World world;
    private double[] borderSizes = {2500, 2000, 1500, 1000, 500, 100, 10, 0};
    private int[] countdownTimes = {400, 250, 150, 100, 60, 60, 60};
    private double currentSize;
    private double borderCenterX;
    private double borderCenterZ;
    private double nextBorderCenterX;
    private double nextBorderCenterZ;
    private boolean isShrinking = false;
    private double borderSpeed = 1.0;
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private int currentPhase;
    
    // 타이머 관련 변수들
    private int countdownSeconds = 0;
    private BukkitRunnable countdownTask;
    private boolean isCountdownActive = false;

    public BorderManager() {
        this.world = Bukkit.getWorlds().get(0);
        this.border = world.getWorldBorder();
        this.currentSize = borderSizes[0]; // Set initial size to the first value in borderSizes array
        this.borderCenterX = 0; // Default center X
        this.borderCenterZ = 0; // Default center Z
        
        // Initialize BossBars for currently online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            addPlayerToBossBar(player);
        }
        updateBossBarWhileWaiting(); // Start the waiting updater
    }

    public double getBorderSize(int phase) {
        if (phase >= 0 && phase < borderSizes.length) {
            return borderSizes[phase];
        }
        return 0;
    }

    public void brBorderinit() {
        currentPhase = 0; // Start from phase 0
        currentSize = borderSizes[currentPhase]; // Initial size 2500
        borderCenterX = 0; // Start at center 0,0
        borderCenterZ = 0;
        border.setCenter(borderCenterX, borderCenterZ);
        border.setSize(currentSize);

        // Calculate the center for the *first* shrink (from 2500 to 2000)
        double nextSize = getBorderSize(currentPhase + 1);
        Location nextCenter = makeRandomcenter(currentSize, nextSize, borderCenterX, borderCenterZ);
        nextBorderCenterX = nextCenter.getX();
        nextBorderCenterZ = nextCenter.getZ();

        // Stop the waiting boss bar updater and start the first countdown
        stopCountdown(); // Ensure no other countdown is running
        updateBossBarWhileWaiting(); // Start the proper updater
        startCountdown(countdownTimes[currentPhase]); // Start countdown to the first shrink
    }

    public void makeIngameborder(int phase, double newSize, double prevSize, double centerX, double centerZ) {
        this.currentPhase = phase;
        currentSize = newSize;
        Bukkit.broadcastMessage("§6[배틀로얄] §f자기장이 줄어듭니다!");

        // 카운트다운 중지
        stopCountdown();

        long shrinkDurationTicks = (long) ((prevSize - newSize) / borderSpeed * 20);
        border.setSize(newSize, (long)((prevSize - newSize) / borderSpeed));
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

                    if (currentPhase + 1 < borderSizes.length) {
                        double nextSize = getBorderSize(currentPhase + 1);
                        Location randomCenter = makeRandomcenter(currentSize, nextSize, borderCenterX, borderCenterZ);
                        nextBorderCenterX = randomCenter.getX();
                        nextBorderCenterZ = randomCenter.getZ();
                    }
                    
                    // 자기장 축소 완료 후 다음 축소까지 대기 시간 설정
                    if (currentPhase < countdownTimes.length) {
                        startCountdown(countdownTimes[currentPhase]);
                    } else {
                        // Handle case where there are no more countdowns defined (e.g., game end)
                    }
                    updateBossBarWhileWaiting();
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
        if (currentPhase == 1) borderSpeed = 2.5;      // 0.5 * 5
        else if (currentPhase == 2) borderSpeed = 3.5;  // 0.7 * 5
        else if (currentPhase == 3) borderSpeed = 5.0;  // 1.0 * 5
        else if (currentPhase == 4) borderSpeed = 7.5;  // 1.5 * 5
        else if (currentPhase >= 5) borderSpeed = 10.0; // 2.0 * 5

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
                    if (currentPhase + 1 < borderSizes.length) {
                        brShrinkborder();
                    }
                    cancel();
                    return;
                }
                
                // 카운트다운 중 액션바 업데이트
                updateCountdownActionBar();
                countdownSeconds--;
            }
        };
        countdownTask.runTaskTimer(BattleRoyale.getPlugin(BattleRoyale.class), 0L, 20L);
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

        double playerX = player.getLocation().getX();
        double playerZ = player.getLocation().getZ();
        double playerYaw = player.getLocation().getYaw();

        // Chebyshev distance for square border
        double dx = Math.abs(playerX - borderCenterX);
        double dz = Math.abs(playerZ - borderCenterZ);
        double distance = Math.max(dx, dz);

        double halfSize = borderSize / 2;
        double progress = (distance <= halfSize) ? 1.0 - (distance / halfSize) : 0.0;

        BarColor color;
        if (progress > 0.7) color = BarColor.GREEN;
        else if (progress > 0.4) color = BarColor.YELLOW;
        else if (progress > 0.1) color = BarColor.RED;
        else color = BarColor.PURPLE;

        String directionArrow = getDirectionArrow(playerX, playerZ, playerYaw, borderCenterX, borderCenterZ);

        String title = (distance <= halfSize)
                ? String.format("자기장 중심까지: %.0fm (크기: %.0fm) %s", distance, borderSize, directionArrow)
                : String.format("§c자기장 밖! 중심까지: %.0fm (크기: %.0fm) %s", distance, borderSize, directionArrow);

        bar.setColor(color);
        bar.setTitle(title);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    private String getDirectionArrow(double playerX, double playerZ, double playerYaw, double targetX, double targetZ) {
        // 플레이어에서 타겟까지의 방향 벡터
        double dx = targetX - playerX;
        double dz = targetZ - playerZ;
        
        // 타겟 방향의 각도 계산 (북쪽 기준, 시계방향)
        double targetAngle = Math.toDegrees(Math.atan2(dx, -dz));
        
        // 플레이어의 yaw 값 정규화 (북쪽 기준, 시계방향)
        double normalizedYaw = ((playerYaw % 360) + 360) % 360;
        
        // 플레이어 시점에서 타겟까지의 상대 각도
        double relativeAngle = ((targetAngle - normalizedYaw) % 360 + 360) % 360;
        
        // 8방향 화살표 결정
        if (relativeAngle >= 337.5 || relativeAngle < 22.5) return "↓";      // 뒤
        else if (relativeAngle >= 22.5 && relativeAngle < 67.5) return "↙";  // 뒤-왼쪽
        else if (relativeAngle >= 67.5 && relativeAngle < 112.5) return "←"; // 왼쪽
        else if (relativeAngle >= 112.5 && relativeAngle < 157.5) return "↖"; // 앞-왼쪽
        else if (relativeAngle >= 157.5 && relativeAngle < 202.5) return "↑"; // 앞
        else if (relativeAngle >= 202.5 && relativeAngle < 247.5) return "↗"; // 앞-오른쪽
        else if (relativeAngle >= 247.5 && relativeAngle < 292.5) return "→"; // 오른쪽
        else if (relativeAngle >= 292.5 && relativeAngle < 337.5) return "↘"; // 뒤-오른쪽
        return "↓";
    }

    public void updateBossBarWhileWaiting() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isShrinking) {
                    cancel();
                    return;
                }
                
                // 카운트다운 중이면 액션바만 업데이트하고 보스바는 그대로 유지
                if (isCountdownActive) {
                    updateCountdownActionBar();
                } else {
                    updateBossBarForAllPlayers(currentSize);
                }
            }
        }.runTaskTimer(BattleRoyale.getPlugin(BattleRoyale.class), 0L, 20L);
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
    
}