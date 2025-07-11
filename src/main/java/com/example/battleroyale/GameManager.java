package com.example.battleroyale;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class GameManager {

    private static boolean isIngame = false;
    
    private BorderManager borderManager;
    private TeamManager teamManager;
    private final BattleRoyale plugin;

    public GameManager(BattleRoyale plugin, BorderManager borderManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
        this.teamManager = teamManager;
    }

    public void brGameinit(String mode, int teamSize) {
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
    }

    public static void setIngame(boolean ingame) {
        isIngame = ingame;
    }

    public static boolean isIngame() {
        return isIngame;
    }

    

    

    

    

    

    
}