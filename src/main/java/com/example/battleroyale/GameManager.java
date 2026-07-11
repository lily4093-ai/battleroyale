package com.example.battleroyale;

import com.example.battleroyale.config.BRConfig;
import com.example.battleroyale.util.TickScheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Ported from the Paper plugin's GameManager: game start/init and the post-start
 * temporary invincibility countdown.
 */
public class GameManager {

    private static volatile boolean isIngame = false;

    private final BattleRoyaleMod mod;
    private final BorderManager borderManager;
    private final TeamManager teamManager;
    private final DownedManager downedManager;

    public GameManager(BattleRoyaleMod mod, BorderManager borderManager, TeamManager teamManager, DownedManager downedManager) {
        this.mod = mod;
        this.borderManager = borderManager;
        this.teamManager = teamManager;
        this.downedManager = downedManager;
    }

    public void brGameinit(String mode, int teamSize) {
        long nonSpectatorCount = mod.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR)
                .count();
        if (nonSpectatorCount < teamSize) {
            String message = "§c[배틀로얄] 게임 시작 실패: " + teamSize + "개의 팀을 만들기에 플레이어가 부족합니다. (필요: " + teamSize + "명, 현재: " + nonSpectatorCount + "명)";
            BRText.broadcast(mod.getServer(), message);
            return;
        }

        String startMessage = String.format("§6[배틀로얄] §f%s 배틀로얄 게임을 시작합니다. (팀 수: §c%d§f)",
                mode.equalsIgnoreCase("default") ? "기본" : "팀", teamSize);
        BRText.broadcast(mod.getServer(), startMessage);

        setIngame(true);
        downedManager.clearAll();
        borderManager.brBorderinit();
        mod.getUtilManager().initializeFirstSupplyDrop();

        double maxHealth = BRConfig.MAX_HEALTH.get();

        for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                player.getInventory().clearContent();
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 4));
                scheduleResistanceCountdown(player);

                var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealthAttr != null) maxHealthAttr.setBaseValue(maxHealth);
                player.setHealth((float) maxHealth);
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20f);

                for (ItemStack item : mod.getDefaultItems()) {
                    player.getInventory().add(item.copy());
                }
            }
        }

        if (mode.equalsIgnoreCase("default")) {
            teamManager.splitTeam(teamSize);
        } else if (mode.equalsIgnoreCase("im")) {
            teamManager.teamTP(teamSize);
        }
    }

    private void scheduleResistanceCountdown(ServerPlayer player) {
        int warningTicks = 5800;

        TickScheduler.runLater(() -> {
            if (isIngame && player.isAlive() && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                BRText.send(player, "§c§l[배틀로얄] §e10초 후 저항이 풀립니다!");
            }
        }, warningTicks);

        for (int i = 3; i >= 1; i--) {
            final int count = i;
            int countdownTicks = 6000 - (count * 20);
            TickScheduler.runLater(() -> {
                if (isIngame && player.isAlive() && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    BRText.send(player, "§c§l[배틀로얄] §f저항 해제까지 §c" + count + "§f초!");
                }
            }, countdownTicks);
        }

        TickScheduler.runLater(() -> {
            if (isIngame && player.isAlive() && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                BRText.send(player, "§c§l[배틀로얄] §4저항이 풀렸습니다! 전투 시작!");
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
