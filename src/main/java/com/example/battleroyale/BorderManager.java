package com.example.battleroyale;

import com.example.battleroyale.config.BRConfig;
import com.example.battleroyale.util.TickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

/**
 * Ported from the Paper plugin's BorderManager: phased shrinking world border,
 * countdown timer, per-player boss bar / action bar readouts.
 *
 * Note: the original used Paper's Player#setCompassTarget, a Paper/Spigot-only
 * protocol trick that has no vanilla/Forge equivalent for plain clients (a vanilla
 * compass can only point at the world spawn or a bound lodestone). updateCompass()
 * is therefore a no-op here; the distance/direction readout is still fully available
 * via the boss bar and action bar when a player holds a compass.
 */
public class BorderManager {

    private final BattleRoyaleMod mod;
    private final ServerLevel level;
    private final WorldBorder border;

    private List<Double> borderSizes;
    private List<Integer> countdownTimes;
    private List<Double> borderSpeed;

    private double currentSize;
    private double borderCenterX;
    private double borderCenterZ;
    private double nextBorderCenterX;
    private double nextBorderCenterZ;
    private double compassTargetX;
    private double compassTargetZ;
    private boolean isShrinking = false;
    private int currentPhase;

    private int countdownSeconds = 0;
    private TickScheduler.TaskHandle countdownTask;
    private boolean isCountdownActive = false;

    private TickScheduler.TaskHandle bossBarUpdateTask;
    private TickScheduler.TaskHandle shrinkTask;

    private final Map<UUID, ServerBossEvent> playerBossBars = new HashMap<>();
    private final Set<UUID> playersWarnedOutsideBorder = new HashSet<>();

    public BorderManager(BattleRoyaleMod mod) {
        this.mod = mod;
        this.level = mod.getServer().overworld();
        this.border = level.getWorldBorder();

        reloadConfig();

        this.currentSize = borderSizes.get(0);
        this.borderCenterX = 0;
        this.borderCenterZ = 0;

        for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
            addPlayerToBossBar(player);
        }
        startBossBarUpdater();
    }

    public void reloadConfig() {
        this.borderSizes = new ArrayList<>(BRConfig.BORDER_SIZES.get());
        this.countdownTimes = new ArrayList<>(BRConfig.BORDER_COUNTDOWN_TIMES.get());
        this.borderSpeed = new ArrayList<>(BRConfig.BORDER_SPEED.get());
    }

    public ServerLevel getLevel() {
        return level;
    }

    public double getBorderSize(int phase) {
        if (phase >= 0 && phase < borderSizes.size()) {
            return borderSizes.get(phase);
        }
        return 0;
    }

    public void brBorderinit() {
        currentPhase = 0;
        currentSize = borderSizes.get(currentPhase);
        borderCenterX = 0;
        borderCenterZ = 0;
        border.setCenter(borderCenterX, borderCenterZ);
        border.setSize(currentSize);

        double nextSize = getBorderSize(currentPhase + 1);
        double[] nextCenter = makeRandomCenter(currentSize, nextSize, borderCenterX, borderCenterZ);
        nextBorderCenterX = nextCenter[0];
        nextBorderCenterZ = nextCenter[1];
        compassTargetX = borderCenterX;
        compassTargetZ = borderCenterZ;

        stopCountdown();
        startCountdown(countdownTimes.get(currentPhase));

        updateBossBarForAllPlayers(currentSize);
        updateCompass();
    }

    public void makeIngameborder(int phase, double newSize, double prevSize, double centerX, double centerZ) {
        this.currentPhase = phase;
        this.currentSize = newSize;
        BRText.broadcast(mod.getServer(), "§6[배틀로얄] §f자기장이 줄어듭니다!");

        stopCountdown();

        double speed = borderSpeed.get(Math.min(currentPhase, borderSpeed.size() - 1));
        double shrinkDurationSeconds = speed > 0 ? Math.abs(prevSize - newSize) / speed : 0;
        long shrinkDurationTicks = Math.max(1, Math.round(shrinkDurationSeconds * 20));

        border.lerpSizeBetween(prevSize, newSize, Math.round(shrinkDurationSeconds * 1000));
        runCenterAnimation(prevSize, border.getCenterX(), border.getCenterZ(), centerX, centerZ, shrinkDurationTicks);
    }

    private void runCenterAnimation(double prevSize, double xLoc1, double zLoc1, double xLoc2, double zLoc2, long finalTime) {
        isShrinking = true;
        if (shrinkTask != null) shrinkTask.cancel();

        double xStep = finalTime > 0 ? (xLoc2 - xLoc1) / finalTime : 0;
        double zStep = finalTime > 0 ? (zLoc2 - zLoc1) / finalTime : 0;

        double[] currentPos = {xLoc1, zLoc1};
        long[] loopNumber = {0};

        shrinkTask = TickScheduler.runTimer(() -> {
            if (loopNumber[0] >= finalTime) {
                border.setCenter(xLoc2, zLoc2);
                borderCenterX = xLoc2;
                borderCenterZ = zLoc2;
                isShrinking = false;

                BRText.broadcast(mod.getServer(), "§6[배틀로얄] §a자기장 축소가 완료되었습니다!");

                if (currentPhase + 1 < borderSizes.size()) {
                    double nextSize = getBorderSize(currentPhase + 1);
                    double[] randomCenter = makeRandomCenter(currentSize, nextSize, borderCenterX, borderCenterZ);
                    nextBorderCenterX = randomCenter[0];
                    nextBorderCenterZ = randomCenter[1];
                    compassTargetX = borderCenterX;
                    compassTargetZ = borderCenterZ;
                    updateCompass();
                }

                if (currentPhase < countdownTimes.size()) {
                    startCountdown(countdownTimes.get(currentPhase));
                } else {
                    endGame();
                }
                shrinkTask.cancel();
                return;
            }

            loopNumber[0]++;
            double progress = (double) loopNumber[0] / finalTime;
            double progressPercent = Math.round(progress * 100);
            double currentBorderSize = prevSize - ((prevSize - currentSize) * progress);

            if (loopNumber[0] % 60 == 0) {
                for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
                    boolean isOutsideBorder = !brIsinCurrentBorder(player.getX(), player.getZ());
                    boolean hasCompass = player.getMainHandItem().is(Items.COMPASS);

                    if (isOutsideBorder) {
                        if (hasCompass || !playersWarnedOutsideBorder.contains(player.getUUID())) {
                            sendWarningTitle(player);
                            playersWarnedOutsideBorder.add(player.getUUID());
                        }
                    } else {
                        playersWarnedOutsideBorder.remove(player.getUUID());
                    }
                }
            }

            if (loopNumber[0] % 10 == 0) {
                String actionBar = String.format("§7자기장 크기: §c%.0f §7→ §c%.0f §f| §7축소 진행률: §c%.0f%% §f| §7자기장 중심: §c(%.0f, %.0f) §f| §7현재 크기: §e%.0f",
                        prevSize, currentSize, progressPercent, currentPos[0], currentPos[1], currentBorderSize);
                for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
                    BRText.actionBar(player, actionBar);
                }
                updateBossBarForAllPlayers(currentBorderSize);
            }

            if (loopNumber[0] == finalTime / 4 || loopNumber[0] == finalTime / 2 || loopNumber[0] == (finalTime * 3) / 4) {
                BRText.broadcast(mod.getServer(), String.format("§6[배틀로얄] §f자기장 축소 §c%.0f%% §f완료!", progressPercent));
            }

            currentPos[0] += xStep;
            currentPos[1] += zStep;
            border.setCenter(currentPos[0], currentPos[1]);
            borderCenterX = currentPos[0];
            borderCenterZ = currentPos[1];
            compassTargetX = currentPos[0];
            compassTargetZ = currentPos[1];
            updateCompass();
        }, 1L, 1L);
    }

    private void sendWarningTitle(ServerPlayer player) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(BRText.of("§c§l위험!")));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(BRText.of("§4§l[!] 자기장 안으로 진입해야 합니다!")));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 40, 10));
    }

    private double[] makeRandomCenter(double prevSize, double newSize, double prevCenterX, double prevCenterZ) {
        double maxDist = (prevSize - newSize) / 2;
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = random.nextDouble() * maxDist;
        double randomX = prevCenterX + Math.cos(angle) * dist;
        double randomZ = prevCenterZ + Math.sin(angle) * dist;
        return new double[]{randomX, randomZ};
    }

    public boolean brIsinCurrentBorder(double x, double z) {
        double halfSize = currentSize / 2;
        return x >= (borderCenterX - halfSize) && x <= (borderCenterX + halfSize) &&
                z >= (borderCenterZ - halfSize) && z <= (borderCenterZ + halfSize);
    }

    public void brShrinkborder() {
        double prevSize = currentSize;
        currentPhase++;
        double newSize = getBorderSize(currentPhase);
        makeIngameborder(currentPhase, newSize, prevSize, nextBorderCenterX, nextBorderCenterZ);
    }

    public BlockPos getSpawnLocation(String type) {
        double space = 500;
        double x = 0, z = 0;
        switch (type) {
            case "++" -> {
                x = borderCenterX + (currentSize / 2) - space;
                z = borderCenterZ + (currentSize / 2) - space;
            }
            case "--" -> {
                x = borderCenterX - (currentSize / 2) + space;
                z = borderCenterZ - (currentSize / 2) + space;
            }
            case "+-" -> {
                x = borderCenterX + (currentSize / 2) - space;
                z = borderCenterZ - (currentSize / 2) + space;
            }
            case "-+" -> {
                x = borderCenterX - (currentSize / 2) + space;
                z = borderCenterZ + (currentSize / 2) - space;
            }
        }
        int y = findHighestSolidY((int) Math.floor(x), (int) Math.floor(z));
        return new BlockPos((int) Math.floor(x), y + 1, (int) Math.floor(z));
    }

    private int findHighestSolidY(int x, int z) {
        int y = Math.min(255, level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos).isAir()) {
            pos.move(0, -1, 0);
        }
        return pos.getY();
    }

    public void startCountdown(int seconds) {
        stopCountdown();
        countdownSeconds = seconds;
        isCountdownActive = true;

        countdownTask = TickScheduler.runTimer(() -> {
            if (countdownSeconds <= 0) {
                isCountdownActive = false;
                if (currentPhase + 1 < borderSizes.size()) {
                    brShrinkborder();
                } else {
                    endGame();
                }
                countdownTask.cancel();
                return;
            }
            updateCountdownActionBar();
            countdownSeconds--;
        }, 0L, 20L);
    }

    public void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        isCountdownActive = false;
    }

    private void updateCountdownActionBar() {
        if (!isCountdownActive) return;
        String actionBar = String.format("§7자기장 크기: §c%.0f §f| §7자기장 축소까지: §c%d초 남음 §f| §7다음 자기장 중앙: §c(%.0f,%.0f)",
                currentSize, countdownSeconds, nextBorderCenterX, nextBorderCenterZ);
        for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
            BRText.actionBar(player, actionBar);
        }
    }

    private void updateBossBarForAllPlayers(double currentBorderSize) {
        for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
            updateBossBarForPlayer(player, currentBorderSize);
        }
    }

    private void updateBossBarForPlayer(ServerPlayer player, double borderSizeIgnored) {
        ServerBossEvent bar = playerBossBars.get(player.getUUID());
        if (bar == null) return;

        Component title;
        BossEvent.BossBarColor color;
        double progress = 1.0;

        double actualBorderSize = border.getSize();
        double actualBorderCenterX = border.getCenterX();
        double actualBorderCenterZ = border.getCenterZ();

        boolean holdingCompass = player.getMainHandItem().is(Items.COMPASS);
        boolean holdingClock = player.getMainHandItem().is(Items.CLOCK);

        if (holdingCompass) {
            double dx = Math.abs(player.getX() - actualBorderCenterX);
            double dz = Math.abs(player.getZ() - actualBorderCenterZ);
            double distance = Math.max(dx, dz);
            double halfSize = actualBorderSize / 2;
            progress = (distance <= halfSize) ? 1.0 - (distance / halfSize) : 0.0;

            if (progress > 0.7) color = BossEvent.BossBarColor.GREEN;
            else if (progress > 0.4) color = BossEvent.BossBarColor.YELLOW;
            else if (progress > 0.1) color = BossEvent.BossBarColor.RED;
            else color = BossEvent.BossBarColor.PURPLE;

            title = (distance <= halfSize)
                    ? BRText.of(String.format("자기장 중심까지: %.0fm (크기: %.0fm)", distance, actualBorderSize))
                    : BRText.of(String.format("§c자기장 밖! 중심까지: %.0fm (크기: %.0fm)", distance, actualBorderSize));
        } else if (holdingClock) {
            long remainingTicks = mod.getUtilManager().getSupplyDropRemainingTicks();
            if (remainingTicks > 0) {
                long minutes = remainingTicks / (20 * 60);
                long seconds = (remainingTicks / 20) % 60;
                String distanceRange = mod.getUtilManager().getSupplyDropDistanceRange(player);
                title = BRText.of(String.format("§e다음 보급까지: %02d분 %02d초 §7| §a%s", minutes, seconds, distanceRange));
                progress = (double) remainingTicks / mod.getUtilManager().getSupplyDropTotalTicks();
            } else {
                title = BRText.of("§a보급품 대기 중...");
                progress = 1.0;
            }
            color = BossEvent.BossBarColor.YELLOW;
        } else {
            title = BRText.of(String.format("§7자기장 크기: §e%.0fm §7| §7다음 중심: §e(%.0f, %.0f)", actualBorderSize, compassTargetX, compassTargetZ));
            color = BossEvent.BossBarColor.BLUE;
            progress = actualBorderSize / borderSizes.get(0);
        }

        bar.setName(title);
        bar.setColor(color);
        bar.setProgress((float) Math.max(0.0, Math.min(1.0, progress)));
    }

    public void startBossBarUpdater() {
        stopBossBarUpdater();
        bossBarUpdateTask = TickScheduler.runTimer(() -> {
            if (isCountdownActive) {
                updateCountdownActionBar();
            }
            for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
                updateBossBarForPlayer(player, currentSize);
            }
        }, 0L, 5L);
    }

    public void stopBossBarUpdater() {
        if (bossBarUpdateTask != null) {
            bossBarUpdateTask.cancel();
            bossBarUpdateTask = null;
        }
    }

    public void addPlayerToBossBar(ServerPlayer player) {
        ServerBossEvent bar = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(player);
        playerBossBars.put(player.getUUID(), bar);
        updateBossBarForPlayer(player, currentSize);
    }

    public void removePlayerFromBossBar(ServerPlayer player) {
        ServerBossEvent bar = playerBossBars.remove(player.getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /**
     * No vanilla-compatible equivalent to Paper's per-player fake compass target exists
     * for a server-side-only mod; kept as a hook point for a future lodestone-based
     * implementation. Distance/direction info remains available via the boss bar.
     */
    public void updateCompass() {
    }

    public WorldBorder getBorder() {
        return border;
    }

    public double getCurrentSize() {
        return currentSize;
    }

    public boolean isShrinking() {
        return isShrinking;
    }

    public double getBorderCenterX() {
        return borderCenterX;
    }

    public double getBorderCenterZ() {
        return borderCenterZ;
    }

    public int getCurrentPhase() {
        return currentPhase;
    }

    private void endGame() {
        BRText.broadcast(mod.getServer(), "§6[배틀로얄] §a게임이 종료되었습니다! 모든 플레이어를 자기장 중심으로 이동시킵니다.");
        int y = findHighestSolidY((int) borderCenterX, (int) borderCenterZ);

        for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
            player.teleportTo(level, borderCenterX + 0.5, y + 1, borderCenterZ + 0.5, player.getYRot(), player.getXRot());
            player.setGameMode(GameType.SURVIVAL);
            var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                player.setHealth((float) maxHealthAttr.getValue());
            }
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20f);
            player.getInventory().clearContent();
            for (var effect : new ArrayList<>(player.getActiveEffects())) {
                player.removeEffect(effect.getEffect());
            }
        }
        GameManager.setIngame(false);
    }
}
