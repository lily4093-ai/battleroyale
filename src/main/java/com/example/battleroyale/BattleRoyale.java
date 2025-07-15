package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class BattleRoyale extends JavaPlugin implements Listener, TabExecutor {

    private TeamManager teamManager;
    private BorderManager borderManager;
    private GameManager gameManager;
    private UtilManager utilManager;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        getLogger().info("BattleRoyale Plugin Enabled!");

        // Initialize managers
        utilManager = new UtilManager(this, getConfig());
        borderManager = new BorderManager(this, utilManager, getConfig());
        teamManager = new TeamManager(this, borderManager, getConfig());
        gameManager = new GameManager(borderManager, teamManager, getConfig());
        utilManager.setBorderManager(borderManager); // Set BorderManager in UtilManager

        // Register commands
        getCommand("brteam").setExecutor(teamManager);
        getCommand("br").setExecutor(this);
        getCommand("chd").setExecutor(this);
        getCommand("총").setExecutor(this);
        getCommand("기본템설정").setExecutor(this);
        getCommand("기본템").setExecutor(this);
        getCommand("rlqhsxpa").setExecutor(this);
        getCommand("teamtest").setExecutor(teamManager);
        getCommand("팀가르기").setExecutor(teamManager);
        getCommand("팀참여").setExecutor(teamManager);
        getCommand("suplytest").setExecutor(this);
        getCommand("밥").setExecutor(this);
        getCommand("강제종료").setExecutor(this);
        getCommand("top").setExecutor(this);
        getCommand("탑").setExecutor(this);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c플레이어만 이 명령어를 사용할 수 있습니다.");
            return true;
        }
        Player player = (Player) sender;

        List<String> allowedCommands = List.of("총", "chd", "빵", "기본템", "rlqhsxpa", "밥", "top", "탑");

        if (!allowedCommands.contains(command.getName().toLowerCase()) && !player.isOp()) {
            player.sendMessage("§c이 명령어는 OP만 사용할 수 있습니다.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("br")) {
            if (args.length > 1) {
                try {
                    int size = Integer.parseInt(args[1]);
                    if (args[0].equalsIgnoreCase("startdefault")) {
                        player.sendMessage("§6[배틀로얄] §f기본 배틀로얄 게임을 시작합니다. 자기장 크기: §c" + size);
                        deadPlayers.clear();
                        gameManager.brGameinit("default", size);
                        return true;
                    } else if (args[0].equalsIgnoreCase("startim")) {
                        player.sendMessage("§6[배틀로얄] §f팀 배틀로얄 게임을 시작합니다. 자기장 크기: §c" + size);
                        deadPlayers.clear();
                        gameManager.brGameinit("im", size);
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§6[배틀로얄] §c유효한 숫자를 입력해주세요.");
                    return true;
                }
            }
            player.sendMessage("§6[배틀로얄] §f사용법: /br [startdefault|startim] [size]");
            return true;
        } else if (command.getName().equalsIgnoreCase("chd") || command.getName().equalsIgnoreCase("총")) {
            player.sendMessage("§6[배틀로얄] §f총기 작업대를 지급합니다.");
            player.performCommand("give @s tacz:gun_smith_table");
            return true;
        } else if (command.getName().equalsIgnoreCase("기본템설정")) {
            player.sendMessage("§6[배틀로얄] §f기본템 설정 인벤토리를 엽니다.");
            Inventory inv = Bukkit.createInventory(null, 54, "기본템설정");
            for (int i = 0; i < defaultItems.size(); i++) {
                inv.setItem(i, defaultItems.get(i));
            }
            player.openInventory(inv);
            return true;
        } else if (command.getName().equalsIgnoreCase("기본템") || command.getName().equalsIgnoreCase("rlqhsxpa")) {
            player.sendMessage("§6[배틀로얄] §f기본템을 지급합니다.");
            for (ItemStack item : defaultItems) {
                player.getInventory().addItem(item);
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("teamtest")) {
            player.sendMessage("§6[배틀로얄] §f팀 테스트를 시작합니다.");
            teamManager.splitTeam(2);
            return true;
        } else if (command.getName().equalsIgnoreCase("팀가르기")) {
            if (args.length > 0) {
                try {
                    int size = Integer.parseInt(args[0]);
                    player.sendMessage("§6[배틀로얄] §f" + size + "개의 팀으로 플레이어를 나눕니다.");
                    teamManager.splitTeam(size);
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
                    teamManager.joinTeam(player, teamNumber);
                    player.sendMessage("§6[배틀로얄] §f" + teamNumber + " 팀에 참여했습니다!");
                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage("§6[배틀로얄] §c유효한 팀 번호를 입력해주세요.");
                    return true;
                }
            }
            player.sendMessage("§6[배틀로얄] §f사용법: /팀참여 [팀 번호]");
            return true;
        } else if (command.getName().equalsIgnoreCase("suplytest")) {
            player.sendMessage("§6[배틀로얄] §f보급을 소환합니다.");
            utilManager.spawnSupplyDrop();
            return true;
        } else if (command.getName().equalsIgnoreCase("밥")) {
            player.sendMessage("§6[배틀로얄] §f빵 64개를 지급합니다.");
            player.getInventory().addItem(new ItemStack(Material.BREAD, 64));
            return true;
        } else if (command.getName().equalsIgnoreCase("강제종료")) {
            if (player.isOp()) {
                player.sendMessage("§c서버를 강제 종료합니다...");
                Bukkit.shutdown();
            } else {
                player.sendMessage("§c이 명령어는 OP만 사용할 수 있습니다.");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("top") || command.getName().equalsIgnoreCase("탑")) {
            Location loc = player.getLocation();
            Location highestBlock = player.getWorld().getHighestBlockAt(loc.getBlockX(), loc.getBlockZ()).getLocation();
            player.teleport(highestBlock.add(0, 1, 0));
            player.sendMessage("§6[배틀로얄] §f가장 높은 블록으로 이동했습니다.");
            return true;
        } else if (command.getName().equalsIgnoreCase("brteam")) {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("create")) {
                    if (args.length > 1) {
                        String teamName = args[1];
                        teamManager.createTeam(teamName);
                        player.sendMessage("§6[배틀로얄] §f" + teamName + " 팀을 생성했습니다.");
                        return true;
                    }
                } else if (args[0].equalsIgnoreCase("add")) {
                    if (args.length > 2) {
                        String teamName = args[1];
                        Player targetPlayer = Bukkit.getPlayer(args[2]);
                        if (targetPlayer != null) {
                            teamManager.addPlayerToTeam(teamName, targetPlayer);
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
                            teamManager.removePlayerFromTeam(teamName, targetPlayer);
                            player.sendMessage("§6[배틀로얄] §f" + targetPlayer.getName() + "님을 " + teamName + " 팀에서 제거했습니다.");
                            return true;
                        } else {
                            player.sendMessage("§6[배틀로얄] §c플레이어를 찾을 수 없습니다.");
                            return true;
                        }
                    }
                } else if (args[0].equalsIgnoreCase("list")) {
                    teamManager.listTeams(player);
                    return true;
                }
            }
            player.sendMessage("§6[배틀로얄] §f사용법: /brteam [create|add|remove|list]");
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("br")) {
            if (args.length == 1) {
                return Arrays.asList("startdefault", "startim");
            }
            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("startdefault") || args[0].equalsIgnoreCase("startim")) {
                    return Arrays.asList("2", "3", "4");
                }
            }
        } else if (command.getName().equalsIgnoreCase("brteam")) {
            if (args.length == 1) {
                return Arrays.asList("create", "add", "remove", "list");
            }
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals("기본템설정")) {
            defaultItems.clear();
            for (ItemStack item : event.getInventory().getContents()) {
                if (item != null) {
                    defaultItems.add(item);
                }
            }
            event.getPlayer().sendMessage("§6[배틀로얄] §f기본템 설정이 저장되었습니다.");
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player)) {
            int gunpowderAmount = random.nextInt(5);
            if (gunpowderAmount > 0) {
                event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), new ItemStack(Material.GUNPOWDER, gunpowderAmount));
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();

        player.setGameMode(GameMode.SPECTATOR);
        deadPlayers.add(player.getUniqueId());
        player.sendMessage("§6[배틀로얄] §f당신은 사망하여 관전 모드로 전환됩니다.");

        if (killer != null) {
            Location killerLoc = killer.getLocation();
            Location playerLoc = player.getLocation();
            double distance = playerLoc.distance(killerLoc);
            
            // 거리와 좌표 정보
            player.sendMessage(String.format("§6[배틀로얄] §c%s§f님에게 살해당했습니다. (거리: %.1f미터)", killer.getName(), distance));
            player.sendMessage(String.format("§6[배틀로얄] §f살해 위치: §eX: %d, Y: %d, Z: %d", 
                playerLoc.getBlockX(), playerLoc.getBlockY(), playerLoc.getBlockZ()));
            
            // 살해자가 들고 있던 아이템 정보
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            String weaponName = weapon.getType().toString();
            if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()) {
                weaponName = weapon.getItemMeta().getDisplayName();
            }
            player.sendMessage(String.format("§6[배틀로얄] §f살해자가 사용한 무기: §e%s", weaponName));
            
            // 살해자에게도 메시지 전송
            killer.sendMessage(String.format("§6[배틀로얄] §a%s§f님을 처치했습니다! (거리: %.1f미터)", player.getName(), distance));
            
            player.teleport(killer);
        }

        Integer deadPlayerTeamNumber = teamManager.getPlayerTeams().get(player);
        if (deadPlayerTeamNumber != null) {
            if (teamManager.isTeamEliminated(deadPlayerTeamNumber)) {
                Bukkit.broadcastMessage("§6[배틀로얄] §c" + deadPlayerTeamNumber + " 팀이 전멸했습니다!");
                checkGameEnd();
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        borderManager.addPlayerToBossBar(player);
        if (GameManager.isIngame() && deadPlayers.contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage("§6[배틀로얄] §f당신은 이전에 사망하여 관전 모드로 접속했습니다.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        borderManager.removePlayerFromBossBar(player);
        if (GameManager.isIngame() && player.getGameMode() != GameMode.SPECTATOR) {
            deadPlayers.add(player.getUniqueId());
            Integer teamNumber = teamManager.getPlayerTeams().get(player);
            if (teamNumber != null) {
                if (teamManager.isTeamEliminated(teamNumber)) {
                    Bukkit.broadcastMessage("§6[배틀로얄] §c" + teamNumber + " 팀이 전멸했습니다!");
                    checkGameEnd();
                }
            }
        }
    }

    private void checkGameEnd() {
        List<Integer> remainingTeams = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                Integer teamNum = teamManager.getPlayerTeams().get(p);
                if (teamNum != null && !remainingTeams.contains(teamNum)) {
                    remainingTeams.add(teamNum);
                }
            }
        }

        if (remainingTeams.size() == 1) {
            Bukkit.broadcastMessage("§6[배틀로얄] §a" + remainingTeams.get(0) + " 팀이 승리했습니다!");
            GameManager.setIngame(false);
        } else if (remainingTeams.isEmpty()) {
            Bukkit.broadcastMessage("§6[배틀로얄] §e모든 팀이 전멸했습니다. 무승부!");
            GameManager.setIngame(false);
        }
    }

    public List<ItemStack> getDefaultItems() {
        return defaultItems;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (type == Material.IRON_ORE) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.IRON_INGOT, 1));
        } else if (type == Material.GOLD_ORE) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT, 1));
        }
    }
}