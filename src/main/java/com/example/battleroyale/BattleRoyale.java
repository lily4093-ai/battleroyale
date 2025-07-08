
package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class BattleRoyale extends JavaPlugin implements Listener {

    private GameManager gameManager;
    private BorderManager borderManager;
    private TeamManager teamManager;
    private UtilManager utilManager;
    private List<ItemStack> defaultItems = new ArrayList<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        borderManager = new BorderManager();
        teamManager = new TeamManager(borderManager);
        utilManager = new UtilManager(this, borderManager);
        gameManager = new GameManager(this, borderManager, teamManager);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c플레이어만 이 명령어를 사용할 수 있습니다.");
            return true;
        }
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("br")) {
            if (args.length > 1) {
                int size = Integer.parseInt(args[1]);
                if (args[0].equalsIgnoreCase("startdefault")) {
                    player.sendMessage("§6[배틀로얄] §f기본 배틀로얄 게임을 시작합니다. 자기장 크기: §c" + size);
                    gameManager.brGameinit("default", size);
                    return true;
                } else if (args[0].equalsIgnoreCase("startim")) {
                    player.sendMessage("§6[배틀로얄] §f팀 배틀로얄 게임을 시작합니다. 자기장 크기: §c" + size);
                    gameManager.brGameinit("im", size);
                    return true;
                }
            }
            player.sendMessage("§6[배틀로얄] §f사용법: /br [startdefault|startim] [size]");
            return true;
        } else if (command.getName().equalsIgnoreCase("chd") || command.getName().equalsIgnoreCase("총")) {
            player.sendMessage("§6[배틀로얄] §f총 상점을 엽니다.");
            player.performCommand("setblock ~ ~ ~ tacz:gun_smith_table"); // Assuming this command works
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
        }
        return false;
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
}
