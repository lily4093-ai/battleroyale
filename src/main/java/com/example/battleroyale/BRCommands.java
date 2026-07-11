package com.example.battleroyale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Ported from the Paper plugin's command handling in BattleRoyale.java and TeamManager.java.
 * Commands that required OP in the original (isOp() check) require permission level 2 here;
 * the rest are usable by any player, matching the original's allow-list.
 */
public final class BRCommands {

    private BRCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("br")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("startdefault")
                        .then(Commands.argument("size", IntegerArgumentType.integer(1))
                                .executes(ctx -> startGame(ctx, "default"))))
                .then(Commands.literal("startim")
                        .then(Commands.argument("size", IntegerArgumentType.integer(1))
                                .executes(ctx -> startGame(ctx, "im")))));

        dispatcher.register(Commands.literal("brteam")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(BRCommands::teamCreate)))
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BRCommands::teamAdd))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BRCommands::teamRemove))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(BRCommands::teamDelete)))
                .then(Commands.literal("list").executes(BRCommands::teamList)));

        dispatcher.register(Commands.literal("chd").executes(BRCommands::giveGunSmithTable));
        dispatcher.register(Commands.literal("총").executes(BRCommands::giveGunSmithTable));

        dispatcher.register(Commands.literal("기본템설정")
                .requires(src -> src.hasPermission(2))
                .executes(BRCommands::openDefaultItemsGui));

        dispatcher.register(Commands.literal("기본템").executes(BRCommands::giveDefaultItems));
        dispatcher.register(Commands.literal("rlqhsxpa").executes(BRCommands::giveDefaultItems));

        dispatcher.register(Commands.literal("teamtest")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    BattleRoyaleMod.getInstance().getTeamManager().splitTeam(2);
                    ctx.getSource().sendSuccess(() -> BRText.of("§6[배틀로얄] §f팀 테스트를 시작합니다."), false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("팀가르기")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("size", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int size = IntegerArgumentType.getInteger(ctx, "size");
                            BattleRoyaleMod.getInstance().getTeamManager().splitTeam(size);
                            ctx.getSource().sendSuccess(() -> BRText.of("§6[배틀로얄] §f" + size + "개의 팀으로 플레이어를 나눕니다."), false);
                            return 1;
                        })));

        dispatcher.register(Commands.literal("팀참여")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("team", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int team = IntegerArgumentType.getInteger(ctx, "team");
                            BattleRoyaleMod.getInstance().getTeamManager().joinTeam(player, team);
                            BRText.send(player, "§6[배틀로얄] §f" + team + " 팀에 참여했습니다!");
                            return 1;
                        })));

        dispatcher.register(Commands.literal("suplytest")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> BRText.of("§6[배틀로얄] §f보급을 소환합니다."), false);
                    BattleRoyaleMod.getInstance().getUtilManager().spawnSupplyDrop();
                    return 1;
                }));

        dispatcher.register(Commands.literal("밥").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            player.getInventory().add(new ItemStack(Items.BREAD, 64));
            BRText.send(player, "§6[배틀로얄] §f빵 64개를 지급합니다.");
            return 1;
        }));

        dispatcher.register(Commands.literal("강제종료")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> BRText.of("§c서버를 강제 종료합니다..."), true);
                    ctx.getSource().getServer().halt(false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("top").executes(BRCommands::teleportTop));
        dispatcher.register(Commands.literal("탑").executes(BRCommands::teleportTop));
    }

    private static int startGame(CommandContext<CommandSourceStack> ctx, String mode) {
        int size = IntegerArgumentType.getInteger(ctx, "size");
        BattleRoyaleMod.getInstance().getDeadPlayers().clear();
        BattleRoyaleMod.getInstance().getGameManager().brGameinit(mode, size);
        return 1;
    }

    private static int teamCreate(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        BattleRoyaleMod.getInstance().getTeamManager().createTeam(name);
        ctx.getSource().sendSuccess(() -> BRText.of("§6[배틀로얄] §f" + name + " 팀을 생성했습니다."), false);
        return 1;
    }

    private static int teamAdd(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        BattleRoyaleMod.getInstance().getTeamManager().addPlayerToTeam(name, target);
        ctx.getSource().sendSuccess(() -> BRText.of("§6[배틀로얄] §f" + target.getGameProfile().getName() + "님을 " + name + " 팀에 추가했습니다."), false);
        return 1;
    }

    private static int teamRemove(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        BattleRoyaleMod.getInstance().getTeamManager().removePlayerFromTeam(name, target);
        ctx.getSource().sendSuccess(() -> BRText.of("§6[배틀로얄] §f" + target.getGameProfile().getName() + "님을 " + name + " 팀에서 제거했습니다."), false);
        return 1;
    }

    private static int teamDelete(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        TeamManager tm = BattleRoyaleMod.getInstance().getTeamManager();
        if (tm.getCustomTeams().containsKey(name)) {
            tm.deleteTeam(name);
            ctx.getSource().sendSuccess(() -> BRText.of("§6[배틀로얄] §f" + name + " 팀을 삭제했습니다."), false);
        } else {
            ctx.getSource().sendFailure(BRText.of("§6[배틀로얄] §c" + name + " 팀을 찾을 수 없습니다."));
        }
        return 1;
    }

    private static int teamList(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BattleRoyaleMod.getInstance().getTeamManager().listTeams(player);
        return 1;
    }

    private static int giveGunSmithTable(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "gun_smith_table"));
        if (item == null) {
            BRText.send(player, "§c[배틀로얄] TACZ 모드가 설치되어 있지 않아 총기 작업대를 지급할 수 없습니다.");
            return 0;
        }
        player.getInventory().add(new ItemStack(item));
        BRText.send(player, "§6[배틀로얄] §f총기 작업대를 지급합니다.");
        return 1;
    }

    private static int openDefaultItemsGui(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BRText.send(player, "§6[배틀로얄] §f기본템 설정 인벤토리를 엽니다.");
        BattleRoyaleMod.getInstance().openDefaultItemsGui(player);
        return 1;
    }

    private static int giveDefaultItems(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BRText.send(player, "§6[배틀로얄] §f기본템을 지급합니다.");
        for (ItemStack item : BattleRoyaleMod.getInstance().getDefaultItems()) {
            player.getInventory().add(item.copy());
        }
        return 1;
    }

    private static int teleportTop(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var level = player.serverLevel();
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int y = Math.min(255, level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos).isAir()) {
            pos.move(0, -1, 0);
        }
        player.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        BRText.send(player, "§6[배틀로얄] §f가장 높은 블록으로 이동했습니다.");
        return 1;
    }
}
