
package com.example.battleroyale;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class BattleRoyale extends JavaPlugin {

    private GameManager gameManager;
    private BorderManager borderManager;
    private TeamManager teamManager;
    private UtilManager utilManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        borderManager = new BorderManager();
        teamManager = new TeamManager();
        utilManager = new UtilManager(this);
        gameManager = new GameManager(borderManager, teamManager);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("br")) {
            if (args.length > 1) {
                if (args[0].equalsIgnoreCase("startdefault")) {
                    // Start default game
                    sender.sendMessage("Starting default game...");
                    int size = Integer.parseInt(args[1]);
                    gameManager.brGameinit(size);
                    return true;
                } else if (args[0].equalsIgnoreCase("startim")) {
                    // Start immediate game
                    sender.sendMessage("Starting immediate game...");
                    int size = Integer.parseInt(args[1]);
                    gameManager.brGameinit(size);
                    return true;
                }
            } else {
                sender.sendMessage("Usage: /br [startdefault|startim] [size]");
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("chd")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.performCommand("give @p tacz:gun_smith_table");
            }
        } else if (command.getName().equalsIgnoreCase("총")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.performCommand("give @p tacz:gun_smith_table");
            }
        } else if (command.getName().equalsIgnoreCase("기본템설정")) {
            // Handle 기본템설정 command
        } else if (command.getName().equalsIgnoreCase("기본템")) {
            // Handle 기본템 command
        } else if (command.getName().equalsIgnoreCase("rlqhsxpa")) {
            // Handle rlqhsxpa command
        } else if (command.getName().equalsIgnoreCase("teamtest")) {
            teamManager.splitTeam(2);
        } else if (command.getName().equalsIgnoreCase("팀가르기")) {
            if (args.length > 0) {
                int size = Integer.parseInt(args[0]);
                teamManager.splitTeam(size);
            }
        } else if (command.getName().equalsIgnoreCase("팀참여")) {
            // Handle 팀참여 command
        } else if (command.getName().equalsIgnoreCase("suplytest")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                utilManager.spawnSupplyDrop(player.getLocation());
            }
        } else if (command.getName().equalsIgnoreCase("밥")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.getInventory().addItem(new ItemStack(Material.BREAD, 64));
            }
        }
        return false;
    }
}
