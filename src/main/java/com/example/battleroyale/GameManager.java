
package com.example.battleroyale;

public class GameManager {

    private boolean isIngame = false;
    private int gametime = 0;
    private int phase = 0;
    private int delay = 500;
    private BorderManager borderManager;
    private TeamManager teamManager;

    public GameManager(BorderManager borderManager, TeamManager teamManager) {
        this.borderManager = borderManager;
        this.teamManager = teamManager;
    }

    public void brGameinit(int teamSize) {
        setIngame(true);
        setGametime(0);
        brBorderinit();
        teamInit(teamSize);
    }

    public void brBorderinit() {
        setPhase(1);
        borderManager.setBorder(getPhase());
    }

    public void teamInit(int size) {
        teamManager.splitTeam(size);
    }

    public void setIngame(boolean ingame) {
        isIngame = ingame;
    }

    public boolean isIngame() {
        return isIngame;
    }

    public void setGametime(int gametime) {
        this.gametime = gametime;
    }

    public int getGametime() {
        return gametime;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    public int getPhase() {
        return phase;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }
}
