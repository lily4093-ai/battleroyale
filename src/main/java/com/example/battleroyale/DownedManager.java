package com.example.battleroyale;

import com.example.battleroyale.util.TickScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

/**
 * Ported from the Paper plugin's DownedManager: knockdown/revive mechanic.
 *
 * The original blocked inventory clicks / hotbar switching / offhand swap via Bukkit
 * events that have no direct Forge equivalent for vanilla containers. This port achieves
 * the same practical effect by snapshotting the player's inventory and hotbar selection
 * when they go down and re-asserting it every tick until they are revived or killed.
 */
public class DownedManager {

    private final BattleRoyaleMod mod;
    private final TeamManager teamManager;
    private final Set<UUID> deadPlayers;

    private final Map<UUID, Long> downedPlayers = new HashMap<>();
    private final Map<UUID, TickScheduler.TaskHandle> downedTimers = new HashMap<>();
    private final Map<UUID, Integer> reviveProgress = new HashMap<>();
    private final Map<UUID, UUID> reviveTarget = new HashMap<>();
    private final Map<UUID, FrozenInventory> frozenInventories = new HashMap<>();

    private static final int REVIVE_TIME_TICKS = 200;
    private static final int DOWNED_TIMEOUT_SECONDS = 100;
    private static final double REVIVE_DISTANCE = 1.5;
    private static final double REVIVE_HEALTH = 5.0;

    private TickScheduler.TaskHandle reviveCheckTask;

    private record FrozenInventory(ItemStack[] items, ItemStack[] armor, ItemStack offhand, int selected) {
    }

    public DownedManager(BattleRoyaleMod mod, TeamManager teamManager, Set<UUID> deadPlayers) {
        this.mod = mod;
        this.teamManager = teamManager;
        this.deadPlayers = deadPlayers;
        startReviveCheckTask();
    }

    public boolean isDowned(ServerPlayer player) {
        return downedPlayers.containsKey(player.getUUID());
    }

    public boolean isDowned(UUID uuid) {
        return downedPlayers.containsKey(uuid);
    }

    public void downPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (downedPlayers.containsKey(uuid)) return;

        downedPlayers.put(uuid, System.currentTimeMillis());
        player.setHealth(1.0f);

        frozenInventories.put(uuid, snapshot(player));
        applyDownedEffects(player);

        Integer teamNum = teamManager.getPlayerTeamNumber(player);
        String teamStr = (teamNum != null) ? "§c[TEAM " + teamNum + "] " : "";
        BRText.broadcast(mod.getServer(), "§6[배틀로얄] " + teamStr + "§f" + player.getGameProfile().getName() + " §7님이 기절했습니다! (소생 가능)");

        notifyTeammates(player);

        int[] seconds = {DOWNED_TIMEOUT_SECONDS};
        TickScheduler.TaskHandle[] handleRef = new TickScheduler.TaskHandle[1];
        handleRef[0] = TickScheduler.runTimer(() -> {
            if (!downedPlayers.containsKey(uuid)) {
                handleRef[0].cancel();
                return;
            }
            ServerPlayer p = mod.getServer().getPlayerList().getPlayer(uuid);
            if (p == null) {
                handleRef[0].cancel();
                return;
            }
            seconds[0]--;
            if (seconds[0] <= 10 || seconds[0] % 10 == 0) {
                BRText.actionBar(p, "§c기절 상태! 남은 시간: §e" + seconds[0] + "초");
            }
            if (seconds[0] <= 0) {
                killDownedPlayer(p, null);
                handleRef[0].cancel();
            }
        }, 20L, 20L);
        downedTimers.put(uuid, handleRef[0]);
    }

    private FrozenInventory snapshot(ServerPlayer player) {
        var inv = player.getInventory();
        ItemStack[] items = new ItemStack[inv.items.size()];
        for (int i = 0; i < items.length; i++) items[i] = inv.items.get(i).copy();
        ItemStack[] armor = new ItemStack[inv.armor.size()];
        for (int i = 0; i < armor.length; i++) armor[i] = inv.armor.get(i).copy();
        return new FrozenInventory(items, armor, inv.offhand.get(0).copy(), inv.selected);
    }

    private void restoreFrozen(ServerPlayer player, FrozenInventory frozen) {
        var inv = player.getInventory();
        for (int i = 0; i < frozen.items().length && i < inv.items.size(); i++) {
            inv.items.set(i, frozen.items()[i].copy());
        }
        for (int i = 0; i < frozen.armor().length && i < inv.armor.size(); i++) {
            inv.armor.set(i, frozen.armor()[i].copy());
        }
        inv.offhand.set(0, frozen.offhand().copy());
        inv.selected = frozen.selected();
    }

    private void applyDownedEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        player.setSwimming(true);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 4, false, false, true));
    }

    private void removeDownedEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.setSwimming(false);
    }

    private void notifyTeammates(ServerPlayer downedPlayer) {
        Integer teamNum = teamManager.getPlayerTeamNumber(downedPlayer);
        if (teamNum == null) return;

        for (Map.Entry<UUID, Integer> entry : teamManager.getPlayerTeams().entrySet()) {
            if (!entry.getValue().equals(teamNum) || entry.getKey().equals(downedPlayer.getUUID())) continue;
            ServerPlayer teammate = mod.getServer().getPlayerList().getPlayer(entry.getKey());
            if (teammate == null) continue;
            BRText.send(teammate, "§6[배틀로얄] §e팀원 " + downedPlayer.getGameProfile().getName() + " 님이 기절했습니다! 가서 소생시켜주세요!");
            teammate.level().playSound(null, teammate.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    public void killDownedPlayer(ServerPlayer player, ServerPlayer killer) {
        UUID uuid = player.getUUID();
        if (!downedPlayers.containsKey(uuid)) return;

        downedPlayers.remove(uuid);
        removeDownedEffects(player);
        frozenInventories.remove(uuid);

        TickScheduler.TaskHandle timer = downedTimers.remove(uuid);
        if (timer != null) timer.cancel();

        reviveProgress.values().removeIf(uuid::equals);
        reviveTarget.entrySet().removeIf(e -> e.getValue().equals(uuid));

        deadPlayers.add(uuid);

        String killMessage;
        if (killer != null) {
            double distance = player.position().distanceTo(killer.position());
            Integer killerTeam = teamManager.getPlayerTeamNumber(killer);
            Integer victimTeam = teamManager.getPlayerTeamNumber(player);
            String killerTeamStr = (killerTeam != null) ? "§b[TEAM " + killerTeam + "] §f" : "";
            String victimTeamStr = (victimTeam != null) ? "§c[TEAM " + victimTeam + "] §f" : "";
            killMessage = String.format("§6[배틀로얄] %s%s §f▶ %s%s (§e%.0fm§f) §7[처형]",
                    killerTeamStr, killer.getGameProfile().getName(), victimTeamStr, player.getGameProfile().getName(), distance);
        } else {
            killMessage = String.format("§6[배틀로얄] §c%s §f님이 사망했습니다. §7[기절 시간 초과]", player.getGameProfile().getName());
        }
        BRText.broadcast(mod.getServer(), killMessage);

        for (ItemStack item : player.getInventory().items) {
            if (!item.isEmpty()) {
                player.drop(item, true, false);
            }
        }
        player.getInventory().clearContent();

        player.setGameMode(GameType.SPECTATOR);
        BRText.send(player, "§c당신은 사망했습니다. 이제부터 관전 모드입니다.");

        Integer teamNum = teamManager.getPlayerTeamNumber(player);
        if (teamNum != null && isTeamFullyDowned(teamNum)) {
            TickScheduler.runLater(() -> checkTeamElimination(teamNum), 5L);
        }
    }

    private void checkTeamElimination(int teamNumber) {
        if (teamManager.isTeamEliminated(teamNumber, deadPlayers)) {
            BRText.broadcast(mod.getServer(), "§6[배틀로얄] §c" + teamNumber + " 팀이 전멸했습니다!");
            checkGameEnd();
        }
    }

    private boolean isTeamFullyDowned(int teamNumber) {
        for (Map.Entry<UUID, Integer> entry : teamManager.getPlayerTeams().entrySet()) {
            if (entry.getValue() != teamNumber) continue;
            ServerPlayer p = mod.getServer().getPlayerList().getPlayer(entry.getKey());
            if (p != null && p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                    && !deadPlayers.contains(entry.getKey())
                    && !downedPlayers.containsKey(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    private void checkGameEnd() {
        List<Integer> remainingTeams = new ArrayList<>();
        for (ServerPlayer p : mod.getServer().getPlayerList().getPlayers()) {
            if (p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && teamManager.getPlayerTeamNumber(p) != null) {
                Integer teamNum = teamManager.getPlayerTeamNumber(p);
                if (!teamManager.isTeamEliminated(teamNum, deadPlayers) && !remainingTeams.contains(teamNum)) {
                    remainingTeams.add(teamNum);
                }
            }
        }

        if (remainingTeams.size() == 1) {
            BRText.broadcast(mod.getServer(), "§6[배틀로얄] §a" + remainingTeams.get(0) + " 팀이 승리했습니다!");
            GameManager.setIngame(false);
        } else if (remainingTeams.isEmpty()) {
            BRText.broadcast(mod.getServer(), "§6[배틀로얄] §e모든 팀이 전멸했습니다. 무승부!");
            GameManager.setIngame(false);
        }
    }

    public void revivePlayer(ServerPlayer downedPlayer, ServerPlayer reviver) {
        UUID uuid = downedPlayer.getUUID();
        if (!downedPlayers.containsKey(uuid)) return;

        downedPlayers.remove(uuid);
        removeDownedEffects(downedPlayer);
        frozenInventories.remove(uuid);

        TickScheduler.TaskHandle timer = downedTimers.remove(uuid);
        if (timer != null) timer.cancel();

        downedPlayer.setHealth((float) REVIVE_HEALTH);

        BRText.broadcast(mod.getServer(), "§6[배틀로얄] §a" + downedPlayer.getGameProfile().getName() + " §f님이 §e" + reviver.getGameProfile().getName() + " §f님에 의해 소생되었습니다!");
        BRText.send(downedPlayer, "§a소생되었습니다!");
        downedPlayer.level().playSound(null, downedPlayer.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.5f);
        BRText.send(reviver, "§a" + downedPlayer.getGameProfile().getName() + " 님을 소생시켰습니다!");

        ((net.minecraft.server.level.ServerLevel) downedPlayer.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                downedPlayer.getX(), downedPlayer.getY() + 1, downedPlayer.getZ(), 10, 0.5, 0.5, 0.5, 0.0);
    }

    private void startReviveCheckTask() {
        reviveCheckTask = TickScheduler.runTimer(() -> {
            for (UUID uuid : new ArrayList<>(downedPlayers.keySet())) {
                ServerPlayer p = mod.getServer().getPlayerList().getPlayer(uuid);
                if (p == null) continue;
                if (!p.isSwimming()) p.setSwimming(true);
                FrozenInventory frozen = frozenInventories.get(uuid);
                if (frozen != null) restoreFrozen(p, frozen);
            }

            if (!GameManager.isIngame()) return;

            for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
                if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) continue;
                if (downedPlayers.containsKey(player.getUUID())) continue;
                if (deadPlayers.contains(player.getUUID())) continue;

                UUID playerUUID = player.getUUID();
                Integer playerTeam = teamManager.getPlayerTeamNumber(player);
                if (playerTeam == null) continue;

                if (!player.isShiftKeyDown()) {
                    if (reviveProgress.containsKey(playerUUID)) {
                        reviveProgress.remove(playerUUID);
                        reviveTarget.remove(playerUUID);
                        BRText.actionBar(player, "§7소생 취소됨");
                    }
                    continue;
                }

                ServerPlayer nearbyDowned = findNearbyDownedTeammate(player, playerTeam);
                if (nearbyDowned == null) {
                    reviveProgress.remove(playerUUID);
                    reviveTarget.remove(playerUUID);
                    continue;
                }

                UUID targetUUID = reviveTarget.get(playerUUID);
                if (targetUUID == null || !targetUUID.equals(nearbyDowned.getUUID())) {
                    reviveProgress.put(playerUUID, 0);
                    reviveTarget.put(playerUUID, nearbyDowned.getUUID());
                }

                int progress = reviveProgress.getOrDefault(playerUUID, 0) + 1;
                reviveProgress.put(playerUUID, progress);

                int progressPercent = (progress * 100) / REVIVE_TIME_TICKS;
                String progressBar = createProgressBar(progressPercent);
                BRText.actionBar(player, "§e소생 중... " + progressBar + " §f" + progressPercent + "%");
                BRText.actionBar(nearbyDowned, "§a소생 받는 중... " + progressBar + " §f" + progressPercent + "%");

                if (progress % 5 == 0) {
                    ((net.minecraft.server.level.ServerLevel) nearbyDowned.level()).sendParticles(ParticleTypes.END_ROD,
                            nearbyDowned.getX(), nearbyDowned.getY() + 0.5, nearbyDowned.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
                }

                if (progress >= REVIVE_TIME_TICKS) {
                    revivePlayer(nearbyDowned, player);
                    reviveProgress.remove(playerUUID);
                    reviveTarget.remove(playerUUID);
                }
            }
        }, 0L, 1L);
    }

    private String createProgressBar(int percent) {
        StringBuilder bar = new StringBuilder("§8[");
        int filled = percent / 10;
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "§a■" : "§7■");
        }
        bar.append("§8]");
        return bar.toString();
    }

    private ServerPlayer findNearbyDownedTeammate(ServerPlayer player, int teamNumber) {
        for (UUID downedUUID : downedPlayers.keySet()) {
            ServerPlayer downed = mod.getServer().getPlayerList().getPlayer(downedUUID);
            if (downed == null) continue;
            Integer downedTeam = teamManager.getPlayerTeamNumber(downed);
            if (downedTeam == null || !downedTeam.equals(teamNumber)) continue;
            if (downed.position().distanceTo(player.position()) <= REVIVE_DISTANCE) {
                return downed;
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (downedPlayers.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (downedPlayers.containsKey(event.getPlayer().getUUID())) {
            event.setCanceled(true);
        }
    }

    // Must run before BREvents' fatal-damage -> downed transition handler (which uses a
    // lower priority), so this always observes the player's downed state from *before*
    // the current damage event, exactly like the original plugin's Bukkit HIGH/HIGHEST order.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!downedPlayers.containsKey(player.getUUID())) return;

        event.setCanceled(true);

        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            Integer attackerTeam = teamManager.getPlayerTeamNumber(attacker);
            Integer victimTeam = teamManager.getPlayerTeamNumber(player);
            if (attackerTeam != null && attackerTeam.equals(victimTeam)) {
                BRText.send(attacker, "§c팀원은 처형할 수 없습니다!");
                return;
            }
            killDownedPlayer(player, attacker);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        if (downedPlayers.containsKey(uuid)) {
            downedPlayers.remove(uuid);
            deadPlayers.add(uuid);
            frozenInventories.remove(uuid);

            TickScheduler.TaskHandle timer = downedTimers.remove(uuid);
            if (timer != null) timer.cancel();

            BRText.broadcast(mod.getServer(), "§6[배틀로얄] §c" + player.getGameProfile().getName() + " §f님이 기절 상태로 게임을 나갔습니다. §7[사망 처리]");

            Integer teamNum = teamManager.getPlayerTeamNumber(player);
            if (teamNum != null) {
                TickScheduler.runLater(() -> checkTeamElimination(teamNum), 5L);
            }
        }

        reviveProgress.remove(uuid);
        reviveTarget.remove(uuid);
    }

    public void clearAll() {
        for (UUID uuid : new ArrayList<>(downedPlayers.keySet())) {
            ServerPlayer player = mod.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) removeDownedEffects(player);
        }
        downedPlayers.clear();

        for (TickScheduler.TaskHandle task : downedTimers.values()) task.cancel();
        downedTimers.clear();

        reviveProgress.clear();
        reviveTarget.clear();
        frozenInventories.clear();
    }

    public Map<UUID, Long> getDownedPlayers() {
        return downedPlayers;
    }
}
