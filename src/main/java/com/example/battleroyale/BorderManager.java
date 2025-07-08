
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class BorderManager {

    private WorldBorder border;
    private double[] borderSizes = {2500, 2000, 1500, 1000, 500, 100, 10, 0};
    private double currentSize;
    private double borderCenterX;
    private double borderCenterZ;
    private double nextBorderCenterX;
    private double nextBorderCenterZ;
    private boolean isShrinking = false;
    private double borderSpeed = 1000.0; // Initial speed, will be updated

    public BorderManager() {
        World world = Bukkit.getWorlds().get(0);
        this.border = world.getWorldBorder();
    }

    public double getBorderSize(int phase) {
        if (phase >= 0 && phase < borderSizes.length) {
            return borderSizes[phase];
        }
        return 0;
    }

    public void brBorderinit() {
        currentSize = borderSizes[0];
        borderCenterX = new Random().nextInt(2001) - 1000; // -1000 to 1000
        borderCenterZ = new Random().nextInt(2001) - 1000; // -1000 to 1000
        borderSpeed = 1000.0;
        makeIngameborder(1, currentSize, currentSize, borderCenterX, borderCenterZ);
    }

    public void makeIngameborder(int phase, double newSize, double prevSize, double centerX, double centerZ) {
        currentSize = newSize;
        Bukkit.broadcastMessage("§6[배틀로얄] §f자기장이 줄어듭니다!");

        long speed = (long) Math.round((prevSize - newSize) / borderSpeed * 20);
        setBorderCenter(prevSize, border.getCenter().getX(), border.getCenter().getZ(), centerX, centerZ, speed);
        border.setSize(newSize, speed / 20); // Convert ticks to seconds
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
                if (System.currentTimeMillis() - startTime >= time * 50) { // time is in ticks, 50ms per tick
                    border.setCenter(xLoc2, zLoc2);
                    borderCenterX = xLoc2;
                    borderCenterZ = zLoc2;
                    isShrinking = false;
                    double nextSize = getBorderSize(GameManager.getPhase() + 1);
                    Location randomCenter = makeRandomcenter(currentSize, nextSize, borderCenterX, borderCenterZ);
                    nextBorderCenterX = randomCenter.getX();
                    nextBorderCenterZ = randomCenter.getZ();
                    cancel();
                    return;
                }

                currentX += xStep;
                currentZ += zStep;
                border.setCenter(currentX, currentZ);
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

        return new Location(border.getWorld(), randomX, 0, randomZ);
    }

    public boolean brIsinnextborder(double x, double z, int phase) {
        double newSize = getBorderSize(phase);
        double centerX = nextBorderCenterX;
        double centerZ = nextBorderCenterZ;

        return x >= (centerX - newSize / 2) && x <= (centerX + newSize / 2) &&
               z >= (centerZ - newSize / 2) && z <= (centerZ + newSize / 2);
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
        return new Location(border.getWorld(), nextBorderCenterX, 0, nextBorderCenterZ);
    }

    public boolean isShrinking() {
        return isShrinking;
    }
}
