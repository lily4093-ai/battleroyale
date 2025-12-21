package com.example.battleroyale;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class DownedManager implements Listener {

    private final BattleRoyale plugin;
    private final TeamManager teamManager;
    private final Set<UUID> deadPlayers;
    private final Logger logger;

    // 기절 상태인 플레이어 (UUID -> 기절 시작 시간)
    private final Map<UUID, Long> downedPlayers = new HashMap<>();
    // 기절 타이머 태스크
    private final Map<UUID, BukkitTask> downedTimers = new HashMap<>();
    // 소생 진행도 (소생하는 플레이어 UUID -> 소생 진행 틱)
    private final Map<UUID, Integer> reviveProgress = new HashMap<>();
    // 소생 대상 (소생하는 플레이어 UUID -> 기절 플레이어 UUID)
    private final Map<UUID, UUID> reviveTarget = new HashMap<>();
    // 저장된 인벤토리 (기절 시 핫바 아이템 저장)
    private final Map<UUID, ItemStack[]> savedInventory = new HashMap<>();

    private static final int REVIVE_TIME_TICKS = 200; // 10초 = 200틱
    private static final int DOWNED_TIMEOUT_SECONDS = 100; // 100초
    private static final double REVIVE_DISTANCE = 1.5; // 1.5블럭
    private static final double REVIVE_HEALTH = 5.0; // 체력 5 (하트 2.5개)

    private BukkitTask reviveCheckTask;

    public DownedManager(BattleRoyale plugin, TeamManager teamManager, Set<UUID> deadPlayers) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.deadPlayers = deadPlayers;
        this.logger = Logger.getLogger("BattleRoyale");

        // 소생 체크 태스크 시작
        startReviveCheckTask();
    }

    /**
     * 플레이어가 기절 상태인지 확인
     */
    public boolean isDowned(Player player) {
        return downedPlayers.containsKey(player.getUniqueId());
    }

    public boolean isDowned(UUID uuid) {
        return downedPlayers.containsKey(uuid);
    }

    /**
     * 플레이어를 기절 상태로 만듦
     */
    public void downPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        if (downedPlayers.containsKey(uuid)) {
            return; // 이미 기절 상태
        }

        logger.info("Player " + player.getName() + " is now downed.");
        downedPlayers.put(uuid, System.currentTimeMillis());

        // 체력 1로 설정 (죽지 않게)
        player.setHealth(1.0);

        // 기절 효과 적용
        applyDownedEffects(player);

        // 기절 메시지
        Integer teamNum = teamManager.getPlayerTeamNumber(player);
        String teamStr = (teamNum != null) ? "§c[TEAM " + teamNum + "] " : "";
        Bukkit.broadcastMessage("§6[배틀로얄] " + teamStr + "§f" + player.getName() + " §7님이 기절했습니다! (소생 가능)");

        // 팀원에게 알림
        notifyTeammates(player);

        // 100초 타이머 시작
        BukkitTask timer = new BukkitRunnable() {
            int seconds = DOWNED_TIMEOUT_SECONDS;

            @Override
            public void run() {
                if (!downedPlayers.containsKey(uuid)) {
                    cancel();
                    return;
                }

                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    cancel();
                    return;
                }

                seconds--;

                // 남은 시간 액션바로 표시
                if (seconds <= 10 || seconds % 10 == 0) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c기절 상태! 남은 시간: §e" + seconds + "초"));
                }

                if (seconds <= 0) {
                    // 시간 초과 - 완전 사망
                    killDownedPlayer(p, null);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        downedTimers.put(uuid, timer);
    }

    /**
     * 기절 효과 적용
     */
    private void applyDownedEffects(Player player) {
        // 구속 2 효과 (무한 지속)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 1, false, false, true));

        // 실명 효과 (약간) - 시야 제한
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));

        // 수영 포즈로 만들기 위해 Swimming 상태 설정
        player.setSwimming(true);

        // 글로우 효과 (팀원이 찾기 쉽게)
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false, false));
    }

    /**
     * 기절 효과 제거
     */
    private void removeDownedEffects(Player player) {
        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.setSwimming(false);
    }

    /**
     * 팀원에게 기절 알림
     */
    private void notifyTeammates(Player downedPlayer) {
        Integer teamNum = teamManager.getPlayerTeamNumber(downedPlayer);
        if (teamNum == null) return;

        for (Map.Entry<Player, Integer> entry : teamManager.getPlayerTeams().entrySet()) {
            Player teammate = entry.getKey();
            if (entry.getValue().equals(teamNum) && !teammate.equals(downedPlayer) && teammate.isOnline()) {
                teammate.sendMessage("§6[배틀로얄] §e팀원 " + downedPlayer.getName() + " 님이 기절했습니다! 가서 소생시켜주세요!");
                teammate.playSound(teammate.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1.0f, 1.0f);
            }
        }
    }

    /**
     * 기절 플레이어를 완전히 죽임
     */
    public void killDownedPlayer(Player player, Player killer) {
        UUID uuid = player.getUniqueId();

        if (!downedPlayers.containsKey(uuid)) {
            return;
        }

        logger.info("Downed player " + player.getName() + " died completely.");

        // 기절 상태 제거
        downedPlayers.remove(uuid);
        removeDownedEffects(player);

        // 타이머 취소
        BukkitTask timer = downedTimers.remove(uuid);
        if (timer != null) {
            timer.cancel();
        }

        // 소생 진행도 제거
        reviveProgress.values().removeIf(targetUUID -> targetUUID.equals(uuid));
        reviveTarget.values().removeIf(targetUUID -> targetUUID.equals(uuid));

        // 사망 처리
        deadPlayers.add(uuid);

        // 사망 메시지
        String killMessage;
        if (killer != null) {
            Location killerLoc = killer.getLocation();
            Location playerLoc = player.getLocation();
            double distance = playerLoc.distance(killerLoc);

            Integer killerTeam = teamManager.getPlayerTeamNumber(killer);
            Integer victimTeam = teamManager.getPlayerTeamNumber(player);

            String killerTeamStr = (killerTeam != null) ? "§b[TEAM " + killerTeam + "] §f" : "";
            String victimTeamStr = (victimTeam != null) ? "§c[TEAM " + victimTeam + "] §f" : "";

            killMessage = String.format("§6[배틀로얄] %s%s §f▶ %s%s (§e%.0fm§f) §7[처형]",
                    killerTeamStr, killer.getName(), victimTeamStr, player.getName(), distance);
        } else {
            killMessage = String.format("§6[배틀로얄] §c%s §f님이 사망했습니다. §7[기절 시간 초과]", player.getName());
        }
        Bukkit.broadcastMessage(killMessage);

        // 아이템 드롭
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
        player.getInventory().clear();

        // 관전 모드로 전환
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage("§c당신은 사망했습니다. 이제부터 관전 모드입니다.");

        // 팀 전멸 체크
        Integer teamNum = teamManager.getPlayerTeamNumber(player);
        if (teamNum != null && isTeamFullyDowned(teamNum)) {
            // 팀 전체가 기절 또는 사망 상태면 팀 전멸
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                checkTeamElimination(teamNum);
            }, 5L);
        }
    }

    /**
     * 팀 전멸 체크
     */
    private void checkTeamElimination(int teamNumber) {
        if (teamManager.isTeamEliminated(teamNumber)) {
            Bukkit.broadcastMessage("§6[배틀로얄] §c" + teamNumber + " 팀이 전멸했습니다!");
            checkGameEnd();
        }
    }

    /**
     * 팀 전체가 기절 또는 사망 상태인지 확인
     */
    private boolean isTeamFullyDowned(int teamNumber) {
        for (Map.Entry<Player, Integer> entry : teamManager.getPlayerTeams().entrySet()) {
            if (entry.getValue() == teamNumber) {
                Player p = entry.getKey();
                if (p.isOnline() && p.getGameMode() != GameMode.SPECTATOR
                    && !deadPlayers.contains(p.getUniqueId())
                    && !downedPlayers.containsKey(p.getUniqueId())) {
                    return false; // 살아있는 팀원이 있음
                }
            }
        }
        return true;
    }

    /**
     * 게임 종료 체크
     */
    private void checkGameEnd() {
        java.util.List<Integer> remainingTeams = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR && teamManager.getPlayerTeamNumber(p) != null) {
                Integer teamNum = teamManager.getPlayerTeamNumber(p);
                if (!teamManager.isTeamEliminated(teamNum) && !remainingTeams.contains(teamNum)) {
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

    /**
     * 플레이어 소생
     */
    public void revivePlayer(Player downedPlayer, Player reviver) {
        UUID uuid = downedPlayer.getUniqueId();

        if (!downedPlayers.containsKey(uuid)) {
            return;
        }

        logger.info("Player " + downedPlayer.getName() + " was revived by " + reviver.getName());

        // 기절 상태 제거
        downedPlayers.remove(uuid);
        removeDownedEffects(downedPlayer);

        // 타이머 취소
        BukkitTask timer = downedTimers.remove(uuid);
        if (timer != null) {
            timer.cancel();
        }

        // 체력 5로 설정 (하트 2.5개)
        downedPlayer.setHealth(REVIVE_HEALTH);

        // 소생 메시지
        Bukkit.broadcastMessage("§6[배틀로얄] §a" + downedPlayer.getName() + " §f님이 §e" + reviver.getName() + " §f님에 의해 소생되었습니다!");

        downedPlayer.sendMessage("§a소생되었습니다!");
        downedPlayer.playSound(downedPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        reviver.sendMessage("§a" + downedPlayer.getName() + " 님을 소생시켰습니다!");

        // 파티클 효과
        downedPlayer.getWorld().spawnParticle(Particle.HEART, downedPlayer.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
    }

    /**
     * 소생 체크 태스크 시작
     */
    private void startReviveCheckTask() {
        reviveCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!GameManager.isIngame()) {
                    return;
                }

                // 모든 온라인 플레이어에 대해 소생 체크
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) continue;
                    if (downedPlayers.containsKey(player.getUniqueId())) continue; // 기절 상태면 스킵
                    if (deadPlayers.contains(player.getUniqueId())) continue;

                    UUID playerUUID = player.getUniqueId();
                    Integer playerTeam = teamManager.getPlayerTeamNumber(player);
                    if (playerTeam == null) continue;

                    // 쉬프트 누르고 있는지 확인
                    if (!player.isSneaking()) {
                        // 쉬프트 안 누르면 소생 진행도 리셋
                        if (reviveProgress.containsKey(playerUUID)) {
                            reviveProgress.remove(playerUUID);
                            reviveTarget.remove(playerUUID);
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§7소생 취소됨"));
                        }
                        continue;
                    }

                    // 근처에 기절한 팀원 찾기
                    Player nearbyDowned = findNearbyDownedTeammate(player, playerTeam);

                    if (nearbyDowned == null) {
                        // 근처에 기절한 팀원 없음
                        if (reviveProgress.containsKey(playerUUID)) {
                            reviveProgress.remove(playerUUID);
                            reviveTarget.remove(playerUUID);
                        }
                        continue;
                    }

                    UUID targetUUID = reviveTarget.get(playerUUID);

                    // 대상이 바뀌었으면 진행도 리셋
                    if (targetUUID == null || !targetUUID.equals(nearbyDowned.getUniqueId())) {
                        reviveProgress.put(playerUUID, 0);
                        reviveTarget.put(playerUUID, nearbyDowned.getUniqueId());
                    }

                    // 소생 진행
                    int progress = reviveProgress.getOrDefault(playerUUID, 0) + 1;
                    reviveProgress.put(playerUUID, progress);

                    // 진행도 표시
                    int progressPercent = (progress * 100) / REVIVE_TIME_TICKS;
                    String progressBar = createProgressBar(progressPercent);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§e소생 중... " + progressBar + " §f" + progressPercent + "%"));
                    nearbyDowned.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a소생 받는 중... " + progressBar + " §f" + progressPercent + "%"));

                    // 파티클 효과
                    if (progress % 5 == 0) {
                        nearbyDowned.getWorld().spawnParticle(Particle.END_ROD,
                            nearbyDowned.getLocation().add(0, 0.5, 0), 3, 0.3, 0.3, 0.3, 0.02);
                    }

                    // 소생 완료
                    if (progress >= REVIVE_TIME_TICKS) {
                        revivePlayer(nearbyDowned, player);
                        reviveProgress.remove(playerUUID);
                        reviveTarget.remove(playerUUID);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 매 틱마다 실행
    }

    /**
     * 진행도 바 생성
     */
    private String createProgressBar(int percent) {
        StringBuilder bar = new StringBuilder("§8[");
        int filled = percent / 10;
        for (int i = 0; i < 10; i++) {
            if (i < filled) {
                bar.append("§a■");
            } else {
                bar.append("§7■");
            }
        }
        bar.append("§8]");
        return bar.toString();
    }

    /**
     * 근처에 기절한 팀원 찾기
     */
    private Player findNearbyDownedTeammate(Player player, int teamNumber) {
        Location loc = player.getLocation();

        for (UUID downedUUID : downedPlayers.keySet()) {
            Player downed = Bukkit.getPlayer(downedUUID);
            if (downed == null || !downed.isOnline()) continue;

            Integer downedTeam = teamManager.getPlayerTeamNumber(downed);
            if (downedTeam == null || !downedTeam.equals(teamNumber)) continue;

            if (downed.getLocation().distance(loc) <= REVIVE_DISTANCE) {
                return downed;
            }
        }
        return null;
    }

    /**
     * 기절 플레이어 수영 포즈 유지
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (downedPlayers.containsKey(player.getUniqueId())) {
            // 수영 포즈 유지
            if (!player.isSwimming()) {
                player.setSwimming(true);
            }
        }
    }

    /**
     * 기절 상태에서 아이템 사용 차단
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (downedPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * 기절 상태에서 아이템 드롭 차단
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (downedPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * 기절 상태에서 인벤토리 클릭 차단
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (downedPlayers.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 기절 상태에서 핫바 슬롯 변경 차단
     */
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (downedPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * 기절 상태에서 오프핸드 스왑 차단
     */
    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (downedPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * 기절 상태에서 추가 데미지 받으면 처형
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (!downedPlayers.containsKey(player.getUniqueId())) return;

        // 데미지 캔슬 (기절 상태에서는 죽지 않음)
        event.setCancelled(true);

        // 플레이어에 의한 공격인 경우 처형
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageByEntity = (EntityDamageByEntityEvent) event;
            if (damageByEntity.getDamager() instanceof Player) {
                Player attacker = (Player) damageByEntity.getDamager();

                // 같은 팀이면 무시
                Integer attackerTeam = teamManager.getPlayerTeamNumber(attacker);
                Integer victimTeam = teamManager.getPlayerTeamNumber(player);
                if (attackerTeam != null && attackerTeam.equals(victimTeam)) {
                    attacker.sendMessage("§c팀원은 처형할 수 없습니다!");
                    return;
                }

                // 적팀이 공격하면 처형
                killDownedPlayer(player, attacker);
            }
        }
    }

    /**
     * 플레이어 퇴장 시 처리
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (downedPlayers.containsKey(uuid)) {
            // 기절 상태로 나가면 사망 처리
            downedPlayers.remove(uuid);
            deadPlayers.add(uuid);

            BukkitTask timer = downedTimers.remove(uuid);
            if (timer != null) {
                timer.cancel();
            }

            Bukkit.broadcastMessage("§6[배틀로얄] §c" + player.getName() + " §f님이 기절 상태로 게임을 나갔습니다. §7[사망 처리]");

            Integer teamNum = teamManager.getPlayerTeamNumber(player);
            if (teamNum != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    checkTeamElimination(teamNum);
                }, 5L);
            }
        }

        // 소생 진행도 정리
        reviveProgress.remove(uuid);
        reviveTarget.remove(uuid);
    }

    /**
     * 모든 기절 상태 초기화 (게임 종료 시)
     */
    public void clearAll() {
        for (UUID uuid : downedPlayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                removeDownedEffects(player);
            }
        }
        downedPlayers.clear();

        for (BukkitTask task : downedTimers.values()) {
            task.cancel();
        }
        downedTimers.clear();

        reviveProgress.clear();
        reviveTarget.clear();
        savedInventory.clear();
    }

    /**
     * 소생 체크 태스크 중지
     */
    public void stopReviveCheckTask() {
        if (reviveCheckTask != null) {
            reviveCheckTask.cancel();
        }
    }

    /**
     * 기절 플레이어 목록 반환
     */
    public Map<UUID, Long> getDownedPlayers() {
        return downedPlayers;
    }
}
