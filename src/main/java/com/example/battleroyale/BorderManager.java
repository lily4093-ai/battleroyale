package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class BorderManager {

    private WorldBorder border;
    private World world;
    private double[] borderSizes = {2500, 2000, 1500, 1000, 500, 100, 10, 0};
    private double currentSize;
    private double borderCenterX;
    private double borderCenterZ;
    private double nextBorderCenterX;
    private double nextBorderCenterZ;
    private boolean isShrinking = false;
    private double borderSpeed = 2000.0;
    private BossBar bossBar;
    private int currentPhase;

    public BorderManager() {
        this.world = Bukkit.getWorlds().get(0);
        this.border = world.getWorldBorder();
        this.bossBar = Bukkit.createBossBar("자기장", BarColor.RED, BarStyle.SOLID);
        this.bossBar.setVisible(true);
        Bukkit.getOnlinePlayers().forEach(player -> bossBar.addPlayer(player));
    }

    public double getBorderSize(int phase) {
        if (phase >= 0 && phase < borderSizes.length) {
            return borderSizes[phase];
        }
        return 0;
    }

    public void brBorderinit() {
        currentSize = borderSizes[0];
        borderCenterX = new Random().nextInt(2001) - 1000;
        borderCenterZ = new Random().nextInt(2001) - 1000;
        borderSpeed = 1000.0;
        currentPhase = 0; // Initial phase
        makeIngameborder(currentPhase, currentSize, currentSize, borderCenterX, borderCenterZ);
    }

    public void makeIngameborder(int phase, double newSize, double prevSize, double centerX, double centerZ) {
        this.currentPhase = phase;
        currentSize = newSize;
        Bukkit.broadcastMessage("§6[배틀로얄] §f자기장이 줄어듭니다!");

        long speed = (long) Math.round((prevSize - newSize) / borderSpeed * 20);
        setBorderCenter(prevSize, border.getCenter().getX(), border.getCenter().getZ(), centerX, centerZ, speed);
        border.setSize(newSize, speed / 20);

        bossBar.setTitle("자기장");
        bossBar.setProgress(1.0); // Reset progress to full
        updateBossBarSubtitle(speed / 20); // Update with initial time
    }

    private void updateBossBarSubtitle(long timeRemainingSeconds) {
        String subtitle = String.format("현재 %d단계 | 축소까지 %d초 남음 | 다음 중앙: X:%.0f, Z:%.0f",
                currentPhase + 1, timeRemainingSeconds, nextBorderCenterX, nextBorderCenterZ);
        bossBar.setTitle("자기장 - " + subtitle);
    }

    public void setBorderCenter(double prevSize, double xLoc1, double zLoc1, double xLoc2, double zLoc2, long time) {
        isShrinking = true;
        new BukkitRunnable() {
            double currentX = xLoc1;
            double currentZ = zLoc1;
            double xStep = (xLoc2 - xLoc1) / time;
            double zStep = (zLoc2 - zLoc1) / time;
            long startTime = System.currentTimeMillis();

            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                long totalTimeMillis = time * 50;
                double progress = (double) elapsed / totalTimeMillis;
                long timeRemainingSeconds = (totalTimeMillis - elapsed) / 1000;

                if (progress >= 1.0) {
                    border.setCenter(xLoc2, zLoc2);
                    borderCenterX = xLoc2;
                    borderCenterZ = zLoc2;
                    isShrinking = false;
                    double nextSize = getBorderSize(currentPhase + 1);
                    Location randomCenter = makeRandomcenter(currentSize, nextSize, borderCenterX, borderCenterZ);
                    nextBorderCenterX = randomCenter.getX();
                    nextBorderCenterZ = randomCenter.getZ();
                    bossBar.setProgress(0.0); // Shrinking complete
                    updateBossBarSubtitle(0); // Update with 0 seconds remaining
                    cancel();
                    return;
                }

                currentX += xStep;
                currentZ += zStep;
                border.setCenter(currentX, currentZ);
                bossBar.setProgress(1.0 - progress);
                updateBossBarSubtitle(timeRemainingSeconds);
            }
        }.runTaskTimer(BattleRoyale.getPlugin(BattleRoyale.class), 1L, 1L);
    }

    public Location makeRandomcenter(double prevSize, double newSize, double prevCenterx, double prevCenterz) {
        double xMinBound = prevCenterx - ((prevSize - newSize) / 2);
        double xMaxBound = prevCenterx + ((prevSize - newSize) / 2);
        double zMinBound = prevCenterz - ((prevSize - newSize) / 2);
        double zMaxBound = prevCenterz + ((prevSize - newSize) / 2);

        Random random = new Random();
        double randomX = xMinBound + (xMaxBound - xMinBound) * random.nextDouble();
        double randomZ = zMinBound + (zMaxBound - zMinBound) * random.nextDouble();

        return new Location(world, randomX, 0, randomZ);
    }

    public boolean brIsinnextborder(double x, double z, int phase) {
        double newSize = getBorderSize(phase);
        double centerX = nextBorderCenterX;
        double centerZ = nextBorderCenterZ;

        return x >= (centerX - newSize / 2) && x <= (centerX + newSize / 2) &&
               z >= (centerZ - newSize / 2) && z <= (centerZ + newSize / 2);
    }

    public Location brGetspawnloc(String type) {
        double space = 500;
        double x = 0;
        double z = 0;

        if (type.equals("++")) {
            x = borderCenterX + (currentSize / 2) - space;
            z = borderCenterZ + (currentSize / 2) - space;
        } else if (type.equals("--")) {
            x = borderCenterX - (currentSize / 2) + space;
            z = borderCenterZ - (currentSize / 2) + space;
        } else if (type.equals("+-")) {
            x = borderCenterX + (currentSize / 2) - space;
            z = borderCenterZ - (currentSize / 2) + space;
        } else if (type.equals("-+")) {
            x = borderCenterX - (currentSize / 2) + space;
            z = borderCenterZ + (currentSize / 2) - space;
        }

        Location tempLoc = new Location(world, x, 256, z);
        while (tempLoc.getBlock().getType().isAir() && tempLoc.getY() > 0) {
            tempLoc.subtract(0, 1, 0);
        }
        tempLoc.add(0, 1, 0);
        return tempLoc;
    }

    public WorldBorder getBorder() {
        return border;
    }

    public double getCurrentSize() {
        return currentSize;
    }

    public void setCurrentSize(double currentSize) {
        this.currentSize = currentSize;
    }

    public Location getNextBorderCenter() {
        return new Location(this.world, nextBorderCenterX, 0, nextBorderCenterZ);
    }

    public boolean isShrinking() {
        return isShrinking;
    }

    public BossBar getBossBar() {
        return bossBar;
    }
}