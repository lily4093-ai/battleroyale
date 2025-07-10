package com.example.battleroyale;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class GameManager {

    private static boolean isIngame = false;
    private static int gametime = 0;
    private static int phase = 0;
    private static int delay = 500;
    private BorderManager borderManager;
    private TeamManager teamManager;
    private final BattleRoyale plugin;

    public GameManager(BattleRoyale plugin, BorderManager borderManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
        this.teamManager = teamManager;
        startGameLoop();
    }

    public void brGameinit(String mode, int teamSize) {
        setIngame(true);
        setGametime(0);
        setPhase(0);
        setDelay(500); // Initial delay for phase 1: 500 seconds (8m20s)
        brBorderinit();
        if (mode.equalsIgnoreCase("default")) {
            teamManager.splitTeam(teamSize);
        } else if (mode.equalsIgnoreCase("im")) {
            teamManager.teamTP(teamSize);
        }
    }

    public void brBorderinit() {
        setPhase(1);
        borderManager.brBorderinit();
    }

    public void brShrinkborder() {
        double prevSize = borderManager.getCurrentSize();
        setPhase(getPhase() + 1);
        double newSize = borderManager.getBorderSize(getPhase());
        borderManager.setCurrentSize(newSize);
        borderManager.makeIngameborder(getPhase(), newSize, prevSize, borderManager.getNextBorderCenter().getX(), borderManager.getNextBorderCenter().getZ());
    }

    private void startGameLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isIngame) {
                    cancel();
                    return;
                }

                if (!borderManager.isShrinking()) {
                    addGametime(1);
                    int timeLeft = delay - gametime;
                    double nextSize = borderManager.getBorderSize(phase + 1);
                    double centerX = borderManager.getNextBorderCenterX();
                    double centerZ = borderManager.getNextBorderCenterZ();

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                            double x = player.getLocation().getX();
                            double z = player.getLocation().getZ();

                            String message = String.format("§7자기장 크기: §c%.0f §f| §7자기장 축소까지: §c%d초 남음 §f| §7다음 자기장 중앙: §c(%.0f,%.0f)",
                                    borderManager.getCurrentSize(), timeLeft, centerX, centerZ);

                            if (borderManager.brIsinnextborder(x, z, phase)) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                            } else {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message + " §f| §4§l현재 다음 자기장 바깥에 있습니다!"));
                            }
                        }
                    }

                    if (gametime >= delay) {
                        if (phase >= 6) { // Last phase finished, handle draw
                            Bukkit.broadcastMessage("§a무승부! 게임이 종료됩니다.");
                            setIngame(false);
                            return;
                        }

                        brShrinkborder(); // Shrinks the border and increments phase
                        setGametime(0);   // Reset timer for the new phase

                        // Set delay for the new, current phase
                        switch (phase) {
                            case 2:
                                setDelay(420); // 7 minutes
                                break;
                            case 3:
                                setDelay(180); // 3 minutes
                                break;
                            case 4:
                                setDelay(100); // 1 minute 40 seconds
                                break;
                            case 5:
                                setDelay(50);  // 50 seconds
                                break;
                            case 6:
                                setDelay(30);  // 30 seconds
                                break;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second
    }

    public static void addGametime(int amount) {
        gametime += amount;
    }

    public static void setIngame(boolean ingame) {
        isIngame = ingame;
    }

    public static boolean isIngame() {
        return isIngame;
    }

    public static void setGametime(int gametime) {
        GameManager.gametime = gametime;
    }

    public static int getGametime() {
        return gametime;
    }

    public static void setPhase(int phase) {
        GameManager.phase = phase;
    }

    public static int getPhase() {
        return phase;
    }

    public static void setDelay(int delay) {
        GameManager.delay = delay;
    }

    public static int getDelay() {
        return delay;
    }
}