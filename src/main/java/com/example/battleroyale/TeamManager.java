
package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TeamManager {

    private Scoreboard scoreboard;

    public TeamManager() {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    public void splitTeam(int size) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);

        for (int i = 0; i < size; i++) {
            String teamName = "team" + (i + 1);
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
        }

        int playerIndex = 0;
        for (Player player : players) {
            String teamName = "team" + ((playerIndex % size) + 1);
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.addEntry(player.getName());
            }
            playerIndex++;
        }
    }
}
