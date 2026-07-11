package com.example.battleroyale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Ported from the Paper plugin's TeamManager: scoreboard-team based squad management,
 * auto-split and custom teams, plus team spawn teleport.
 */
public class TeamManager {

    private final BattleRoyaleMod mod;
    private final BorderManager borderManager;
    private final Map<UUID, Integer> playerTeams = new HashMap<>();
    private final Map<String, List<UUID>> customTeams = new LinkedHashMap<>();

    public TeamManager(BattleRoyaleMod mod, BorderManager borderManager) {
        this.mod = mod;
        this.borderManager = borderManager;
    }

    private Scoreboard scoreboard() {
        return mod.getServer().getScoreboard();
    }

    public void splitTeam(int size) {
        MinecraftServer server = mod.getServer();
        if (customTeams.isEmpty()) {
            Scoreboard scoreboard = scoreboard();
            for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
                if (team.getName().startsWith("team")) {
                    scoreboard.removePlayerTeam(team);
                }
            }
            playerTeams.clear();

            List<ServerPlayer> players = server.getPlayerList().getPlayers().stream()
                    .filter(p -> p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR)
                    .collect(Collectors.toList());
            Collections.shuffle(players);

            for (int i = 0; i < size; i++) {
                getOrCreateTeam(scoreboard, "team" + (i + 1), i + 1);
            }

            int playerIndex = 0;
            for (ServerPlayer player : players) {
                int teamNumber = (playerIndex % size) + 1;
                String teamName = "team" + teamNumber;
                PlayerTeam team = scoreboard.getPlayerTeam(teamName);
                if (team != null) {
                    scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                    playerTeams.put(player.getUUID(), teamNumber);
                    player.sendSystemMessage(BRText.of("§6[배틀로얄] §f당신은 §c" + teamNumber + " §f팀 입니다!"));
                }
                playerIndex++;
            }

            for (int i = 0; i < size; i++) {
                String teamName = "team" + (i + 1);
                PlayerTeam team = scoreboard.getPlayerTeam(teamName);
                if (team != null) {
                    String members = String.join(", ", team.getPlayers());
                    BRText.broadcast(server, "§6[배틀로얄] §f" + (i + 1) + " 팀 - " + members);
                }
            }
        } else {
            Scoreboard scoreboard = scoreboard();
            int teamNumber = 1;
            for (Map.Entry<String, List<UUID>> entry : customTeams.entrySet()) {
                String teamName = entry.getKey();
                PlayerTeam team = getOrCreateTeam(scoreboard, teamName, teamNumber);
                for (UUID uuid : entry.getValue()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player == null) continue;
                    scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                    playerTeams.put(uuid, teamNumber);
                    player.sendSystemMessage(BRText.of("§6[배틀로얄] §f당신은 §c" + teamName + " §f팀 입니다!"));
                }
                teamNumber++;
            }
        }
        teamTP(size);
    }

    private PlayerTeam getOrCreateTeam(Scoreboard scoreboard, String teamName, int teamNumber) {
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setNameTagVisibility(Team.Visibility.NEVER);
            team.setColor(getTeamColor(teamNumber));
        }
        return team;
    }

    public void teamTP(int size) {
        MinecraftServer server = mod.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(40.0);
            }
            player.setHealth(40.0f);
            player.giveExperienceLevels(180 - player.experienceLevel);

            Integer teamNumber = playerTeams.get(player.getUUID());
            if (teamNumber != null) {
                BlockPos spawnLoc;
                switch (teamNumber) {
                    case 1 -> spawnLoc = borderManager.getSpawnLocation("++");
                    case 2 -> spawnLoc = borderManager.getSpawnLocation("--");
                    case 3 -> spawnLoc = borderManager.getSpawnLocation("+-");
                    case 4 -> spawnLoc = borderManager.getSpawnLocation("-+");
                    default -> spawnLoc = borderManager.getSpawnLocation("++");
                }
                if (spawnLoc != null) {
                    player.teleportTo(borderManager.getLevel(), spawnLoc.getX() + 0.5, spawnLoc.getY(), spawnLoc.getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                }
            }
        }
    }

    public void joinTeam(ServerPlayer player, int teamNumber) {
        Scoreboard scoreboard = scoreboard();
        PlayerTeam existing = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (existing != null) {
            scoreboard.removePlayerFromTeam(player.getScoreboardName(), existing);
        }

        String teamName = "team" + teamNumber;
        PlayerTeam team = getOrCreateTeam(scoreboard, teamName, teamNumber);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        playerTeams.put(player.getUUID(), teamNumber);
    }

    public Map<UUID, Integer> getPlayerTeams() {
        return playerTeams;
    }

    public Map<String, List<UUID>> getCustomTeams() {
        return customTeams;
    }

    public boolean isTeamEliminated(int teamNumber, Set<UUID> deadPlayers) {
        MinecraftServer server = mod.getServer();
        List<UUID> teamMembers = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : playerTeams.entrySet()) {
            if (entry.getValue() == teamNumber) {
                teamMembers.add(entry.getKey());
            }
        }

        if (teamMembers.isEmpty()) {
            return true;
        }

        for (UUID uuid : teamMembers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                    && !deadPlayers.contains(uuid)) {
                return false;
            }
        }
        return true;
    }

    public Integer getPlayerTeamNumber(ServerPlayer player) {
        return playerTeams.get(player.getUUID());
    }

    public Integer getPlayerTeamNumber(UUID uuid) {
        return playerTeams.get(uuid);
    }

    public ChatFormatting getTeamColor(int teamNumber) {
        return switch (teamNumber) {
            case 1 -> ChatFormatting.RED;
            case 2 -> ChatFormatting.BLUE;
            case 3 -> ChatFormatting.GREEN;
            case 4 -> ChatFormatting.YELLOW;
            default -> ChatFormatting.WHITE;
        };
    }

    public void createTeam(String teamName) {
        customTeams.put(teamName, new ArrayList<>());
    }

    public void addPlayerToTeam(String teamName, ServerPlayer player) {
        List<UUID> list = customTeams.get(teamName);
        if (list != null) list.add(player.getUUID());
    }

    public void removePlayerFromTeam(String teamName, ServerPlayer player) {
        List<UUID> list = customTeams.get(teamName);
        if (list != null) list.remove(player.getUUID());
    }

    public void listTeams(ServerPlayer player) {
        MinecraftServer server = mod.getServer();
        for (Map.Entry<String, List<UUID>> entry : customTeams.entrySet()) {
            String members = entry.getValue().stream()
                    .map(uuid -> {
                        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                        return p != null ? p.getGameProfile().getName() : uuid.toString();
                    })
                    .collect(Collectors.joining(" "));
            player.sendSystemMessage(BRText.of("§6[배틀로얄] §f" + entry.getKey() + ": " + members));
        }
    }

    public void deleteTeam(String teamName) {
        if (customTeams.containsKey(teamName)) {
            Scoreboard scoreboard = scoreboard();
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                for (UUID uuid : customTeams.get(teamName)) {
                    ServerPlayer p = mod.getServer().getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        scoreboard.removePlayerFromTeam(p.getScoreboardName(), team);
                    }
                    playerTeams.remove(uuid);
                }
                scoreboard.removePlayerTeam(team);
            }
            customTeams.remove(teamName);
        }
    }
}
