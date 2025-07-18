package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeamManager implements org.bukkit.command.CommandExecutor {

    private Scoreboard scoreboard;
    private BorderManager borderManager;
    private Map<Player, Integer> playerTeams = new HashMap<>();
    private Map<String, List<Player>> customTeams = new HashMap<>();
    private FileConfiguration config;
    private final Logger logger;
    private Set<UUID> deadPlayers;

    public TeamManager(BattleRoyale plugin, BorderManager borderManager, FileConfiguration config, Set<UUID> deadPlayers) {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        this.borderManager = borderManager;
        this.config = config;
        this.logger = Logger.getLogger("BattleRoyale");
        this.deadPlayers = deadPlayers;
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

            List<Player> players = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                    .collect(Collectors.toList());
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

    public Map<String, List<Player>> getCustomTeams() {
        return customTeams;
    }

    public boolean isTeamEliminated(int teamNumber) {
        List<Player> teamMembers = new ArrayList<>();
        for (Map.Entry<Player, Integer> entry : playerTeams.entrySet()) {
            if (entry.getValue() == teamNumber) {
                teamMembers.add(entry.getKey());
            }
        }

        if (teamMembers.isEmpty()) {
            // 팀에 아무도 없으면 (예: 모든 멤버가 오프라인) 전멸로 간주
            return true;
        }

        for (Player player : teamMembers) {
            // 온라인 상태이고, 관전 모드가 아니며, deadPlayers 목록에 없는 플레이어가 한 명이라도 있으면
            // 해당 팀은 아직 살아있는 것입니다.
            if (player.isOnline() && player.getGameMode() != GameMode.SPECTATOR && !deadPlayers.contains(player.getUniqueId())) {
                return false; 
            }
        }

        // 위의 조건을 만족하는 살아있는 플레이어가 없으면 팀은 전멸한 것입니다.
        return true; 
    }

    public Integer getPlayerTeamNumber(Player player) {
        return playerTeams.get(player);
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c플레이어만 이 명령어를 사용할 수 있습니다.");
            return true;
        }
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("brteam")) {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("create")) {
                    if (args.length > 1) {
                        String teamName = args[1];
                        createTeam(teamName);
                        player.sendMessage("§6[배틀로얄] §f" + teamName + " 팀을 생성했습니다.");
                        return true;
                    }
                } else if (args[0].equalsIgnoreCase("add")) {
                    if (args.length > 2) {
                        String teamName = args[1];
                        Player targetPlayer = Bukkit.getPlayer(args[2]);
                        if (targetPlayer != null) {
                            addPlayerToTeam(teamName, targetPlayer);
                            player.sendMessage("§6[배틀로얄] §f" + targetPlayer.getName() + "님을 " + teamName + " 팀에 추가했습니다.");
                            return true;
                        } else {
                            player.sendMessage("§6[배틀로얄] §c플레이어를 찾을 수 없습니다.");
                            return true;
                        }
                    }
                } else if (args[0].equalsIgnoreCase("remove")) {
                    if (args.length > 2) {
                        String teamName = args[1];
                        Player targetPlayer = Bukkit.getPlayer(args[2]);
                        if (targetPlayer != null) {
                            removePlayerFromTeam(teamName, targetPlayer);
                            player.sendMessage("§6[배틀로얄] §f" + targetPlayer.getName() + "님을 " + teamName + " 팀에서 제거했습니다.");
                            return true;
                        } else {
                            player.sendMessage("§6[배틀로얄] §c플레이어를 찾을 수 없습니다.");
                            return true;
                        }
                    } else {
                        player.sendMessage("§6[배틀로얄] §f사용법: /brteam remove <팀이름> <플레이어이름>");
                        return true;
                    }
                } else if (args[0].equalsIgnoreCase("delete")) {
                    if (args.length > 1) {
                        String teamName = args[1];
                        if (customTeams.containsKey(teamName)) {
                            deleteTeam(teamName);
                            player.sendMessage("§6[배틀로얄] §f" + teamName + " 팀을 삭제했습니다.");
                        } else {
                            player.sendMessage("§6[배틀로얄] §c" + teamName + " 팀을 찾을 수 없습니다.");
                        }
                        return true;
                    } else {
                        player.sendMessage("§6[배틀로얄] §f사용법: /brteam delete <팀이름>");
                        return true;
                    }
                } else if (args[0].equalsIgnoreCase("list")) {
                    listTeams(player);
                    return true;
                }
            }
            player.sendMessage("§6[배틀로얄] §f사용법: /brteam [create|add|remove|list]");
            return true;
        } else if (command.getName().equalsIgnoreCase("teamtest")) {
            player.sendMessage("§6[배틀로얄] §f팀 테스트를 시작합니다.");
            splitTeam(2);
            return true;
        } else if (command.getName().equalsIgnoreCase("팀가르기")) {
            if (args.length > 0) {
                try {
                    int size = Integer.parseInt(args[0]);
                    player.sendMessage("§6[배틀로얄] §f" + size + "개의 팀으로 플레이어를 나눕니다.");
                    splitTeam(size);
                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage("§6[배틀로얄] §c유효한 숫자를 입력해주세요.");
                    return true;
                }
            }
            player.sendMessage("§6[배틀로얄] §f사용법: /팀가르기 [팀 개수]");
            return true;
        } else if (command.getName().equalsIgnoreCase("팀참여")) {
            if (args.length > 0) {
                try {
                    int teamNumber = Integer.parseInt(args[0]);
                    joinTeam(player, teamNumber);
                    player.sendMessage("§6[배틀로얄] §f" + teamNumber + " 팀에 참여했습니다!");
                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage("§6[배틀로얄] §c유효한 팀 번호를 입력해주세요.");
                    return true;
                }
            }
            player.sendMessage("§6[배틀로얄] §f사용법: /팀참여 [팀 번호]");
            return true;
        }
        return false;
    }
}