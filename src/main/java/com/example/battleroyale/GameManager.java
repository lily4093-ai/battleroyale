
package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

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
    }

    public void brGameinit(String mode, int teamSize) {
        setIngame(true);
        setGametime(0);
        brBorderinit();
        if (mode.equalsIgnoreCase("default")) {
            teamManager.splitTeam(teamSize);
        } else if (mode.equalsIgnoreCase("im")) {
            teamManager.teamTP(teamSize);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isIngame || phase >= 7) {
                    this.cancel();
                    return;
                }

                if (!borderManager.isShrinking()) {
                    addGametime(1);
                    int timeLeft = delay - gametime;

                    Location nextBorderCenter = borderManager.getNextBorderCenter();
                    double currentSize = borderManager.getCurrentSize();

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (borderManager.brIsinnextborder(player.getLocation().getX(), player.getLocation().getZ(), phase + 1)) {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§7자기장 크기: §c" + (int) currentSize + " §f| §7자기장 축소까지: §c" + timeLeft + "초 남음 §f| §7다음 자기장 중앙: §c(" + (int) nextBorderCenter.getX() + "," + (int) nextBorderCenter.getZ() + ")"));
                        } else {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§7자기장 크기: §c" + (int) currentSize + " §f| §7자기장 축소까지: §c" + timeLeft + "초 남음 §f| §7다음 자기장 중앙: §c(" + (int) nextBorderCenter.getX() + "," + (int) nextBorderCenter.getZ() + ") §f| §4§l현재 다음 자기장 바깥에 있습니다!"));
                        }
                    }
                }

                if (gametime >= delay) {
                    setGametime(0);
                    if (phase == 2) {
                        setDelay(420);
                    } else if (phase == 3) {
                        setDelay(180);
                    } else if (phase == 4) {
                        setDelay(100);
                    } else if (phase == 5) {
                        setDelay(50);
                    } else if (phase == 6) {
                        setDelay(30);
                    }
                    brShrinkborder();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1초마다 실행 (20 ticks = 1 second)
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
