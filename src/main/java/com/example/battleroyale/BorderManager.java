package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

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
    private double borderSpeed = 3.5;
    private BossBar bossBar;
    private int currentPhase;
    private double shrinkStartSize; // Corrected declaration location
    private long totalShrinkTicks;  // Corrected declaration location

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
        currentPhase = 1;
        currentSize = borderSizes[currentPhase];
        borderCenterX = new Random().nextInt(2001) - 1000;
        borderCenterZ = new Random().nextInt(2001) - 1000;
        borderSpeed = 1000.0;
        makeIngameborder(1, currentSize, currentSize, borderCenterX, borderCenterZ);
    }

    public void makeIngameborder(int phase, double newSize, double prevSize, double centerX, double centerZ) {
        this.currentPhase = phase;
        currentSize = newSize;
        Bukkit.broadcastMessage("§6[배틀로얄] §f자기장이 줄어듭니다!");

        long speed = Math.round((prevSize - newSize) / borderSpeed * 20);
        this.totalShrinkTicks = speed;
        this.shrinkStartSize = prevSize;
        setBorderCenter(prevSize, border.getCenter().getX(), border.getCenter().getZ(), centerX, centerZ, speed);
        border.setSize(newSize, (long)((prevSize - newSize) / borderSpeed));

        bossBar.setTitle("자기장");
        bossBar.setProgress(1.0);
    }

    public void setBorderCenter(double prevSize, double xLoc1, double zLoc1, double xLoc2, double zLoc2, long time) {
        isShrinking = true;
        final long finalTime = Math.round(time);
        
        new BukkitRunnable() {
            double currentX = xLoc1;
            double currentZ = zLoc1;
            double xStep = (xLoc2 - xLoc1) / finalTime;
            double zStep = (zLoc2 - zLoc1) / finalTime;
            long loopNumber = 0;

            @Override
            public void run() {
                loopNumber++;
                double perc = Math.round((double)loopNumber / finalTime * 100);
                
                // 매 3초마다 (60틱) 플레이어들에게 자기장 경고 메시지 전송
                if (loopNumber % 60 == 0) {
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        double xc = player.getLocation().getX();
                        double zc = player.getLocation().getZ();
                        if (!brIsinnextborder(xc, zc, currentPhase)) {
                            player.sendTitle("§f", "§4§l[!] 자기장안으로 진입해야합니다!", 0, 20, 0);
                        }
                    });
                }
                
                
                
                if (loopNumber >= finalTime) {
                    border.setCenter(xLoc2, zLoc2);
                    borderCenterX = xLoc2;
                    borderCenterZ = zLoc2;
                    isShrinking = false;
                    
                    double nextSize = getBorderSize(currentPhase + 1);
                    Location randomCenter = makeRandomcenter(currentSize, nextSize, borderCenterX, borderCenterZ);
                    nextBorderCenterX = randomCenter.getX();
                    nextBorderCenterZ = randomCenter.getZ();
                    bossBar.setProgress(0.0);
                    cancel();
                    return;
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
        double offset = (prevSize - newSize) / 2.0;
        Random random = new Random();
        double randomX = prevCenterx + (random.nextDouble() * 2 - 1) * offset;
        double randomZ = prevCenterz + (random.nextDouble() * 2 - 1) * offset;

        return new Location(world, randomX, 0, randomZ);
    }

    public boolean brIsinnextborder(double x, double z, int phase) {
        double newSize = getBorderSize(phase);
        double centerX = nextBorderCenterX;
        double centerZ = nextBorderCenterZ;

        return x >= (centerX - newSize / 2) && x <= (centerX + newSize / 2) &&
               z >= (centerZ - newSize / 2) && z <= (centerZ + newSize / 2);
    }

    public void brShrinkborder() {
        double prevSize = currentSize;
        currentPhase = currentPhase + 1;
        double newSize = getBorderSize(currentPhase);
        borderSpeed = 3.5;
        currentSize = newSize;
        makeIngameborder(currentPhase, newSize, prevSize, nextBorderCenterX, nextBorderCenterZ);
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

    public double getShrinkStartSize() {
        return shrinkStartSize;
    }

    public long getTotalShrinkTicks() {
        return totalShrinkTicks;
    }
}