
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
    }

    public void brGameinit(String mode, int teamSize) {
        setDelay(500);  // 첫 페이즈 대기시간
        
        // 게임 루프에서 페이즈별 delay 설정 수정
        if (gametime >= delay) {
            setGametime(0);
            if (phase == 1) setDelay(500);      // 첫 페이즈: 8분 20초
            else if (phase == 2) setDelay(420); // 두 번째: 7분
            else if (phase == 3) setDelay(180); // 세 번째: 3분
            else if (phase == 4) setDelay(100); // 네 번째: 1분 40초
            else if (phase == 5) setDelay(50);  // 다섯 번째: 50초
            else if (phase == 6) setDelay(30);  // 여섯 번째: 30초
            brShrinkborder();
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
