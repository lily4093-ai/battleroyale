package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.GameMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeamManager {

    private Scoreboard scoreboard;
    private BorderManager borderManager;
    private Map<Player, Integer> playerTeams = new HashMap<>();
    private Map<String, List<Player>> customTeams = new HashMap<>();

    public TeamManager(BorderManager borderManager) {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        this.borderManager = borderManager;
    }

    public void splitTeam(int size) {
        if (customTeams.isEmpty()) {
            // Clear existing teams
            for (String entry : scoreboard.getEntries()) {
                Team team = scoreboard.getEntryTeam(entry);
                if (team != null && team.getName().startsWith("team")) {
                    team.removeEntry(entry);
                }
            }
            for (Team team : scoreboard.getTeams()) {
                if (team.getName().startsWith("team")) {
                    team.unregister();
                }
            }
            playerTeams.clear();

            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            Collections.shuffle(players);

            for (int i = 0; i < size; i++) {
                String teamName = "team" + (i + 1);
                Team team = scoreboard.getTeam(teamName);
                if (team == null) {
                    team = scoreboard.registerNewTeam(teamName);
                    team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                }
            }

            int playerIndex = 0;
            for (Player player : players) {
                String teamName = "team" + ((playerIndex % size) + 1);
                Team team = scoreboard.getTeam(teamName);
                if (team != null) {
                    team.addEntry(player.getName());
                    playerTeams.put(player, (playerIndex % size) + 1);
                    player.sendMessage("§6[배틀로얄] §f당신은 §c" + ((playerIndex % size) + 1) + " §f팀 입니다!");
                }
                playerIndex++;
            }

            for (int i = 0; i < size; i++) {
                String teamName = "team" + (i + 1);
                Team team = scoreboard.getTeam(teamName);
                if (team != null) {
                    StringBuilder teamMembers = new StringBuilder();
                    for (String entry : team.getEntries()) {
                        if (teamMembers.length() > 0) {
                            teamMembers.append(", ");
                        }
                        teamMembers.append(entry);
                    }
                    Bukkit.broadcastMessage("§6[배틀로얄] §f" + (i + 1) + " 팀 - " + teamMembers.toString());
                }
            }
        } else {
            int teamNumber = 1;
            for (Map.Entry<String, List<Player>> entry : customTeams.entrySet()) {
                String teamName = entry.getKey();
                Team team = scoreboard.getTeam(teamName);
                if (team == null) {
                    team = scoreboard.registerNewTeam(teamName);
                    team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                }
                for (Player player : entry.getValue()) {
                    team.addEntry(player.getName());
                    playerTeams.put(player, teamNumber);
                    player.sendMessage("§6[배틀로얄] §f당신은 §c" + teamName + " §f팀 입니다!");
                }
                teamNumber++;
            }
        }
        teamTP(size);
    }

    public void teamTP(int size) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(40.0);
            player.setHealth(40.0);
            player.setLevel(180);
            Integer teamNumber = playerTeams.get(player);
            if (teamNumber != null) {
                Location spawnLoc = null;
                switch (teamNumber) {
                    case 1:
                        spawnLoc = borderManager.brGetspawnloc("++");
                        break;
                    case 2:
                        spawnLoc = borderManager.brGetspawnloc("--");
                        break;
                    case 3:
                        spawnLoc = borderManager.brGetspawnloc("+-");
                        break;
                    case 4:
                        spawnLoc = borderManager.brGetspawnloc("-+");
                        break;
                    default:
                        spawnLoc = borderManager.brGetspawnloc("++");
                        break;
                }
                if (spawnLoc != null) {
                    player.teleport(spawnLoc);
                }
            }
        }
    }

    public void joinTeam(Player player, int teamNumber) {
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }

        String teamName = "team" + teamNumber;
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        team.addEntry(player.getName());
        playerTeams.put(player, teamNumber);
    }

    public Map<Player, Integer> getPlayerTeams() {
        return playerTeams;
    }

    public boolean isTeamEliminated(int teamNumber) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerTeams.containsKey(player) && playerTeams.get(player) == teamNumber) {
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    return false; // Found a living player, so team is not eliminated.
                }
            }
        }
        return true; // No living players found in the team, so team is eliminated.
    }

    public void createTeam(String teamName) {
        customTeams.put(teamName, new ArrayList<>());
    }

    public void addPlayerToTeam(String teamName, Player player) {
        customTeams.get(teamName).add(player);
    }

    public void removePlayerFromTeam(String teamName, Player player) {
        customTeams.get(teamName).remove(player);
    }

    public void listTeams(Player player) {
        for (Map.Entry<String, List<Player>> entry : customTeams.entrySet()) {
            StringBuilder members = new StringBuilder();
            for (Player p : entry.getValue()) {
                members.append(p.getName()).append(" ");
            }
            player.sendMessage("§6[배틀로얄] §f" + entry.getKey() + ": " + members.toString());
        }
    }
}