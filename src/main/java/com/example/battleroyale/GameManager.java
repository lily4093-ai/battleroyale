package com.example.battleroyale;

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
                if (!isIngame || phase >= 7) {
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
                            
                            // 액션바 전송 방식 수정
                            try {
                                if (borderManager.brIsinnextborder(x, z, phase)) {
                                    player.sendActionBar(message);
                                } else {
                                    player.sendActionBar(message + " §f| §4§l현재 다음 자기장 바깥에 있습니다!");
                                }
                            } catch (Exception e) {
                                // 액션바 전송 실패 시 타이틀로 대체
                                try {
                                    if (borderManager.brIsinnextborder(x, z, phase)) {
                                        player.sendTitle("", message, 0, 20, 0);
                                    } else {
                                        player.sendTitle("", message + " §f| §4§l현재 다음 자기장 바깥에 있습니다!", 0, 20, 0);
                                    }
                                } catch (Exception ex) {
                                    // 타이틀도 실패하면 채팅으로 대체
                                    if (borderManager.brIsinnextborder(x, z, phase)) {
                                        player.sendMessage(message);
                                    } else {
                                        player.sendMessage(message + " §f| §4§l현재 다음 자기장 바깥에 있습니다!");
                                    }
                                }
                            }
                        }
                    }
                    
                    if (gametime >= delay) {
                        setGametime(0);
                        // Set delays based on phase
                        switch (phase) {
                            case 1:
                                setDelay(420); // 7 minutes for phase 2
                                break;
                            case 2:
                                setDelay(180); // 3 minutes for phase 3
                                break;
                            case 3:
                                setDelay(100); // 1 minute 40 seconds for phase 4
                                break;
                            case 4:
                                setDelay(50); // 50 seconds for phase 5
                                break;
                            case 5:
                                setDelay(30); // 30 seconds for phase 6
                                break;
                            default:
                                setDelay(500); // Default
                                break;
                        }
                        brShrinkborder();
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