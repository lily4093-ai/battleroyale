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
import org.bukkit.event.entity.EntityDamageEvent;
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
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Logger;

public final class BattleRoyale extends JavaPlugin implements Listener, TabExecutor {

    private TeamManager teamManager;
    private BorderManager borderManager;
    private GameManager gameManager;
    private UtilManager utilManager;
    private List<ItemStack> defaultItems = new ArrayList<>();
    private Random random = new Random();
    private Set<UUID> deadPlayers = new HashSet<>();
    private final Logger logger = Logger.getLogger("BattleRoyale");

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();

        getLogger().info("BattleRoyale Plugin Enabled!");

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Initialize managers
        utilManager = new UtilManager(this, getConfig());
        borderManager = new BorderManager(this, utilManager, getConfig());
        teamManager = new TeamManager(this, borderManager, getConfig(), deadPlayers);
        gameManager = new GameManager(this, borderManager, teamManager, utilManager, getConfig());
        utilManager.setBorderManager(borderManager); // Set BorderManager in UtilManager

        // Load default items from config
        loadDefaultItems();

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

    public void loadDefaultItems() {
        defaultItems.clear();
        List<String> itemStrings = getConfig().getStringList("default_items");
        for (String itemString : itemStrings) {
            ItemStack item = utilManager.parseItemStack(itemString);
            if (item != null) {
                defaultItems.add(item);
            }
        }
        getLogger().info("Loaded " + defaultItems.size() + " default items from config.");
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
                        deadPlayers.clear();
                        gameManager.brGameinit("default", size);
                        return true;
                    } else if (args[0].equalsIgnoreCase("startim")) {
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
                return Arrays.asList("create", "add", "remove", "delete", "list");
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete")) {
                    return new ArrayList<>(teamManager.getCustomTeams().keySet());
                }
            } else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove")) {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                }
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
        if (!GameManager.isIngame()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        event.setDeathMessage(null);
        logger.info("Player " + victim.getName() + " died.");

        deadPlayers.add(victim.getUniqueId());

        String killMessage;
        if (killer != null) {
            logger.info("Killer is " + killer.getName());
            Location killerLoc = killer.getLocation();
            Location playerLoc = victim.getLocation();
            double distance = playerLoc.distance(killerLoc);

            Integer killerTeam = teamManager.getPlayerTeamNumber(killer);
            Integer victimTeam = teamManager.getPlayerTeamNumber(victim);

            String killerTeamStr = (killerTeam != null) ? "§b[TEAM " + killerTeam + "] §f" : "";
            String victimTeamStr = (victimTeam != null) ? "§c[TEAM " + victimTeam + "] §f" : "";

            killMessage = String.format("§6[배틀로얄] %s%s §f▶ %s%s (§e%.0fm§f)",
                    killerTeamStr, killer.getName(), victimTeamStr, victim.getName(), distance);
            Bukkit.broadcastMessage(killMessage);
            logger.info("Broadcasted kill message: " + killMessage);
        } else {
            killMessage = String.format("§6[배틀로얄] §c%s §f님이 사망했습니다.", victim.getName());
            Bukkit.broadcastMessage(killMessage);
            logger.info("Broadcasted death message: " + killMessage);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            Integer deadPlayerTeamNumber = teamManager.getPlayerTeamNumber(victim);
            logger.info("Victim's team number: " + deadPlayerTeamNumber);
            if (deadPlayerTeamNumber != null) {
                if (teamManager.isTeamEliminated(deadPlayerTeamNumber)) {
                    Bukkit.broadcastMessage("§6[배틀로얄] §c" + deadPlayerTeamNumber + " 팀이 전멸했습니다!");
                    logger.info("Team " + deadPlayerTeamNumber + " eliminated. Checking game end.");
                    checkGameEnd();
                }
            }
        }, 5L);
        
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (victim.isDead()) {
                victim.spigot().respawn();
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        double maxHealth = getConfig().getDouble("game.max_health", 40.0);
        player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        player.setHealth(maxHealth);

        if (GameManager.isIngame() && deadPlayers.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.setSpectatorTarget(null);
                player.setFlySpeed(0.4f);
                player.sendMessage("§c당신은 사망했습니다. 이제부터 관전 모드입니다.");
            }, 1L);
        }
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        borderManager.addPlayerToBossBar(player);

        double maxHealth = getConfig().getDouble("game.max_health", 40.0);
        player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        player.setHealth(maxHealth);
        player.setFoodLevel(20);
        player.setSaturation(20);

        if (GameManager.isIngame() && deadPlayers.contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage("§6[배틀로얄] §f당신은 이전에 사망하여 관전 모드로 접속했습니다.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        borderManager.removePlayerFromBossBar(player);
        logger.info("Player " + player.getName() + " quit.");
        if (GameManager.isIngame() && player.getGameMode() != GameMode.SPECTATOR) {
            deadPlayers.add(player.getUniqueId());
            Integer teamNumber = teamManager.getPlayerTeamNumber(player);
            logger.info("Player " + player.getName() + " (Team " + teamNumber + ") quit during game. Added to deadPlayers.");
            if (teamNumber != null) {
                if (teamManager.isTeamEliminated(teamNumber)) {
                    Bukkit.broadcastMessage("§6[배틀로얄] §c" + teamNumber + " 팀이 전멸했습니다!");
                    logger.info("Team " + teamNumber + " eliminated due to player quit. Checking game end.");
                    checkGameEnd();
                }
            }
        }
    }

    private void checkGameEnd() {
        logger.info("Checking game end conditions...");
        List<Integer> remainingTeams = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR && teamManager.getPlayerTeamNumber(p) != null) {
                Integer teamNum = teamManager.getPlayerTeamNumber(p);
                if (!teamManager.isTeamEliminated(teamNum) && !remainingTeams.contains(teamNum)) {
                    remainingTeams.add(teamNum);
                }
            }
        }
        logger.info("Remaining active teams: " + remainingTeams.size());

        if (remainingTeams.size() == 1) {
            Bukkit.broadcastMessage("§6[배틀로얄] §a" + remainingTeams.get(0) + " 팀이 승리했습니다!");
            logger.info("Team " + remainingTeams.get(0) + " won the game.");
            GameManager.setIngame(false);
        } else if (remainingTeams.isEmpty()) {
            Bukkit.broadcastMessage("§6[배틀로얄] §e모든 팀이 전멸했습니다. 무승부!");
            logger.info("All teams eliminated. Game is a draw.");
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

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!GameManager.isIngame()) {
            return;
        }

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.getGameMode() != GameMode.SPECTATOR && !deadPlayers.contains(player.getUniqueId())) {
                double damageMultiplier = getConfig().getDouble("game.damage_multiplier", 1.0);
                event.setDamage(event.getDamage() * damageMultiplier);
            }
        }
    }
}