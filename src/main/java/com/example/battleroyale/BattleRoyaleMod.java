package com.example.battleroyale;

import com.example.battleroyale.config.BRConfig;
import com.example.battleroyale.util.ItemParser;
import com.example.battleroyale.util.TickScheduler;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.*;

/**
 * Server-side Battle Royale minigame mod. Ported feature-for-feature from the
 * BattleRoyale Paper plugin (see the paper-plugin branch) to run alongside
 * gameplay mods like TACZ on a Forge 1.20.1 dedicated server.
 */
@Mod(BattleRoyaleMod.MODID)
public class BattleRoyaleMod {

    public static final String MODID = "battleroyale";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static BattleRoyaleMod instance;

    private MinecraftServer server;
    private TeamManager teamManager;
    private BorderManager borderManager;
    private GameManager gameManager;
    private UtilManager utilManager;
    private DownedManager downedManager;
    private GlowManager glowManager;

    private final List<ItemStack> defaultItems = new ArrayList<>();
    private final Set<UUID> deadPlayers = new HashSet<>();
    private final Map<UUID, SimpleContainer> openDefaultItemsGuis = new HashMap<>();
    private final Random random = new Random();

    public BattleRoyaleMod(FMLJavaModLoadingContext context) {
        instance = this;
        context.registerConfig(ModConfig.Type.COMMON, BRConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static BattleRoyaleMod getInstance() {
        return instance;
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        this.server = event.getServer();

        this.utilManager = new UtilManager(this);
        this.borderManager = new BorderManager(this);
        this.teamManager = new TeamManager(this, borderManager);
        this.downedManager = new DownedManager(this, teamManager, deadPlayers);
        this.gameManager = new GameManager(this, borderManager, teamManager, downedManager);
        this.utilManager.setBorderManager(borderManager);
        this.glowManager = new GlowManager(this);

        MinecraftForge.EVENT_BUS.register(utilManager);
        MinecraftForge.EVENT_BUS.register(downedManager);

        loadDefaultItems();
        LOGGER.info("BattleRoyale mod initialized.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        TickScheduler.clear();
        GameManager.setIngame(false);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TickScheduler.tick();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BRCommands.register(event.getDispatcher());
    }

    public void loadDefaultItems() {
        defaultItems.clear();
        for (String itemString : BRConfig.DEFAULT_ITEMS.get()) {
            ItemStack item = ItemParser.parse(itemString, random, LOGGER::warn);
            if (item != null) {
                defaultItems.add(item);
            }
        }
        LOGGER.info("Loaded {} default items from config.", defaultItems.size());
    }

    public void openDefaultItemsGui(ServerPlayer player) {
        SimpleContainer container = new SimpleContainer(54);
        for (int i = 0; i < defaultItems.size() && i < 54; i++) {
            container.setItem(i, defaultItems.get(i).copy());
        }
        openDefaultItemsGuis.put(player.getUUID(), container);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> ChestMenu.sixRows(id, inv, container),
                BRText.of("기본템설정")));
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SimpleContainer container = openDefaultItemsGuis.remove(player.getUUID());
        if (container == null) return;

        defaultItems.clear();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack item = container.getItem(i);
            if (!item.isEmpty()) {
                defaultItems.add(item.copy());
            }
        }
        BRText.send(player, "§6[배틀로얄] §f기본템 설정이 저장되었습니다.");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        borderManager.addPlayerToBossBar(player);

        TickScheduler.runLater(() -> {
            if (!player.isAlive()) return;
            double maxHealth = BRConfig.MAX_HEALTH.get();
            var attr = player.getAttribute(Attributes.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(maxHealth);
                player.setHealth((float) maxHealth);
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20f);
            }

            if (GameManager.isIngame() && deadPlayers.contains(player.getUUID())) {
                player.setGameMode(GameType.SPECTATOR);
                BRText.send(player, "§6[배틀로얄] §f당신은 이전에 사망하여 관전 모드로 접속했습니다.");
            }
        }, 1L);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        borderManager.removePlayerFromBossBar(player);

        if (GameManager.isIngame() && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            deadPlayers.add(player.getUUID());
            Integer teamNumber = teamManager.getPlayerTeamNumber(player);
            if (teamNumber != null && teamManager.isTeamEliminated(teamNumber, deadPlayers)) {
                BRText.broadcast(server, "§6[배틀로얄] §c" + teamNumber + " 팀이 전멸했습니다!");
                checkGameEnd();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        double maxHealth = BRConfig.MAX_HEALTH.get();
        var attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(maxHealth);
        player.setHealth((float) maxHealth);

        if (GameManager.isIngame() && deadPlayers.contains(player.getUUID())) {
            TickScheduler.runLater(() -> {
                player.setGameMode(GameType.SPECTATOR);
                player.getAbilities().setFlyingSpeed(0.4f);
                player.onUpdateAbilities();
                BRText.send(player, "§c당신은 사망했습니다. 이제부터 관전 모드입니다.");
            }, 1L);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player)) {
            int gunpowderAmount = random.nextInt(5);
            if (gunpowderAmount > 0 && entity.level() instanceof ServerLevel level) {
                Block.popResource(level, entity.blockPosition(), new ItemStack(Items.GUNPOWDER, gunpowderAmount));
            }
            return;
        }

        if (!(entity instanceof ServerPlayer victim)) return;
        if (!GameManager.isIngame()) return;
        if (downedManager.isDowned(victim)) return;

        deadPlayers.add(victim.getUUID());

        ServerPlayer killer = (event.getSource().getEntity() instanceof ServerPlayer sp) ? sp : null;
        String killMessage;
        if (killer != null) {
            double distance = victim.position().distanceTo(killer.position());
            Integer killerTeam = teamManager.getPlayerTeamNumber(killer);
            Integer victimTeam = teamManager.getPlayerTeamNumber(victim);
            String killerTeamStr = (killerTeam != null) ? "§b[TEAM " + killerTeam + "] §f" : "";
            String victimTeamStr = (victimTeam != null) ? "§c[TEAM " + victimTeam + "] §f" : "";
            killMessage = String.format("§6[배틀로얄] %s%s §f▶ %s%s (§e%.0fm§f)",
                    killerTeamStr, killer.getGameProfile().getName(), victimTeamStr, victim.getGameProfile().getName(), distance);
        } else {
            killMessage = String.format("§6[배틀로얄] §c%s §f님이 사망했습니다.", victim.getGameProfile().getName());
        }
        BRText.broadcast(server, killMessage);

        TickScheduler.runLater(() -> {
            Integer deadPlayerTeamNumber = teamManager.getPlayerTeamNumber(victim);
            if (deadPlayerTeamNumber != null && teamManager.isTeamEliminated(deadPlayerTeamNumber, deadPlayers)) {
                BRText.broadcast(server, "§6[배틀로얄] §c" + deadPlayerTeamNumber + " 팀이 전멸했습니다!");
                checkGameEnd();
            }
        }, 5L);
    }

    // Fatal-damage -> downed transition. Runs after DownedManager's HIGHEST-priority
    // "already downed" handler so it always sees the pre-event downed state.
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDamageTransition(LivingDamageEvent event) {
        if (event.isCanceled()) return;
        if (!GameManager.isIngame()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return;
        if (deadPlayers.contains(player.getUUID())) return;
        if (downedManager.isDowned(player)) return;

        double damageMultiplier = BRConfig.DAMAGE_MULTIPLIER.get();
        double finalDamage = event.getAmount() * damageMultiplier;

        if (player.getHealth() - finalDamage <= 0) {
            event.setCanceled(true);
            downedManager.downPlayer(player);
        }
    }

    private void checkGameEnd() {
        List<Integer> remainingTeams = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && teamManager.getPlayerTeamNumber(p) != null) {
                Integer teamNum = teamManager.getPlayerTeamNumber(p);
                if (!teamManager.isTeamEliminated(teamNum, deadPlayers) && !remainingTeams.contains(teamNum)) {
                    remainingTeams.add(teamNum);
                }
            }
        }

        if (remainingTeams.size() == 1) {
            BRText.broadcast(server, "§6[배틀로얄] §a" + remainingTeams.get(0) + " 팀이 승리했습니다!");
            GameManager.setIngame(false);
        } else if (remainingTeams.isEmpty()) {
            BRText.broadcast(server, "§6[배틀로얄] §e모든 팀이 전멸했습니다. 무승부!");
            GameManager.setIngame(false);
        }
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return LOGGER;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public UtilManager getUtilManager() {
        return utilManager;
    }

    public DownedManager getDownedManager() {
        return downedManager;
    }

    public GlowManager getGlowManager() {
        return glowManager;
    }

    public List<ItemStack> getDefaultItems() {
        return defaultItems;
    }

    public Set<UUID> getDeadPlayers() {
        return deadPlayers;
    }
}
