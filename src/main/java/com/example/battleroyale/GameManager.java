package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;

public class GameManager {

    private static boolean isIngame = false;
    
    private BorderManager borderManager;
    private TeamManager teamManager;
    private FileConfiguration config;
    private final Logger logger;

    public GameManager(BorderManager borderManager, TeamManager teamManager, FileConfiguration config) {
        this.borderManager = borderManager;
        this.teamManager = teamManager;
        this.config = config;
        this.logger = Logger.getLogger("BattleRoyale");
    }

    public void brGameinit(String mode, int teamSize) {
        int minPlayersPerTeam = config.getInt("game.min_players_per_team", 2);
        int requiredPlayers = teamSize * minPlayersPerTeam;
        if (Bukkit.getOnlinePlayers().size() < requiredPlayers) {
            logger.warning("Not enough players to start the game with " + teamSize + " teams. Required: " + requiredPlayers + ", Online: " + Bukkit.getOnlinePlayers().size());
            Bukkit.broadcastMessage("§c[배틀로얄] 게임 시작 실패: 팀당 최소 " + minPlayersPerTeam + "명 필요. 현재 플레이어 수: " + Bukkit.getOnlinePlayers().size() + ", 필요한 플레이어 수: " + requiredPlayers);
            return;
        }

        setIngame(true);
        borderManager.brBorderinit();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0)); // Infinite Night Vision
                player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 6000, 4)); // Resistance 5 for 5 minutes (6000 ticks)
            }
        }

        if (mode.equalsIgnoreCase("default")) {
            teamManager.splitTeam(teamSize);
        } else if (mode.equalsIgnoreCase("im")) {
            teamManager.teamTP(teamSize);
        }
        logger.info("Battle Royale game initialized in " + mode + " mode with " + teamSize + " teams.");
    }

    public static void setIngame(boolean ingame) {
        isIngame = ingame;
    }

    public static boolean isIngame() {
        return isIngame;
    }

    

    

    

    

    

    
}