package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
            private long tickCounter = 0;
            private long shrinkTickCounter = 0;

            @Override
            public void run() {
                if (!isIngame) {
                    cancel();
                    return;
                }

                tickCounter++;

                if (borderManager.isShrinking()) {
                    shrinkTickCounter++;

                    double prevSize = borderManager.getShrinkStartSize();
                    double currentTargetSize = borderManager.getCurrentSize();
                    long totalShrinkTicks = borderManager.getTotalShrinkTicks();
                    double currentActualSize = borderManager.getBorder().getSize();

                    double perc = 0;
                    if (totalShrinkTicks > 0) {
                        perc = (double)shrinkTickCounter / totalShrinkTicks * 100;
                        if (perc > 100) perc = 100;
                    }
                    
                    double currentBorderCenterX = borderManager.getBorder().getCenter().getX();
                    double currentBorderCenterZ = borderManager.getBorder().getCenter().getZ();

                    String message = String.format("§7자기장 크기: §c%.0f §7> §c%.0f §f| §7자기장 축소 진행률: §c%.0f%% §f| §7자기장 중앙: §c( %.0f, %.0f )",
                            prevSize, currentTargetSize, perc, currentBorderCenterX, currentBorderCenterZ);
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                         player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                    }

                } else {
                    shrinkTickCounter = 0;

                    if (tickCounter % 20 == 0) {
                        addGametime(1);

                        if (gametime >= delay) {
                            setGametime(0);
                            switch (phase) {
                                case 1: setDelay(420); break;
                                case 2: setDelay(180); break;
                                case 3: setDelay(100); break;
                                case 4: setDelay(50);  break;
                                case 5: setDelay(30);  break;
                                case 6: setDelay(30);  break;
                            }
                            if (phase < 7) {
                                brShrinkborder();
                            }
                        }
                    }
                    
                    int timeLeft = delay - gametime;
                    double nextSize = borderManager.getBorderSize(phase + 1);
                    double centerX = borderManager.getNextBorderCenter().getX();
                    double centerZ = borderManager.getNextBorderCenter().getZ();
                    String message = String.format("§7자기장 크기: §c%.0f §f| §7자기장 축소까지: §c%d초 남음 §f| §7다음 자기장 중앙: §c(%.0f,%.0f)",
                            borderManager.getCurrentSize(), timeLeft, centerX, centerZ);

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        double x = player.getLocation().getX();
                        double z = player.getLocation().getZ();
                        if (borderManager.brIsinnextborder(x, z, phase + 1)) {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                        } else {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message + " §f| §4§l[!] 다음 자기장 바깥에 있습니다!"));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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