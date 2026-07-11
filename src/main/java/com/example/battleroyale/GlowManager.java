package com.example.battleroyale;

import com.example.battleroyale.util.TickScheduler;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Per-viewer "glowing" outlines for players, without applying the real vanilla
 * Glowing effect (which is broadcast to every nearby client regardless of who
 * should see it). Instead this overrides the Entity shared-flags synced data
 * for one specific observer's connection at a time — a well-known server-only
 * technique, no client mod required, since vanilla already knows how to render
 * a glow outline once told an entity is glowing. The outline color follows the
 * target's real scoreboard team color automatically.
 *
 * Visibility rules (only while a game is in progress):
 *  - Teammates glow to each other.
 *  - A dead/spectating player sees every teamed player glow, since they're
 *    just watching and it's no longer competitively sensitive.
 */
public class GlowManager {

    private static final EntityDataAccessor<Byte> SHARED_FLAGS;
    private static final byte FLAG_ONFIRE = 0x01;
    private static final byte FLAG_CROUCHING = 0x02;
    private static final byte FLAG_SPRINTING = 0x08;
    private static final byte FLAG_SWIMMING = 0x10;
    private static final byte FLAG_INVISIBLE = 0x20;
    private static final byte FLAG_GLOWING = 0x40;
    private static final byte FLAG_FALL_FLYING = (byte) 0x80;

    // Looked up by type (the one static EntityDataAccessor<Byte> field on Entity), not by
    // name: a production Forge server jar doesn't necessarily expose Mojang-mapped field
    // names via reflection the way the ForgeGradle dev environment does, so a hardcoded
    // "DATA_SHARED_FLAGS_ID" lookup that works at compile/dev time can still throw
    // NoSuchFieldException at real dedicated-server runtime.
    static {
        EntityDataAccessor<Byte> found = null;
        for (Field field : Entity.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !EntityDataAccessor.class.equals(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                EntityDataAccessor<?> accessor = (EntityDataAccessor<?>) field.get(null);
                if (accessor.getSerializer() == EntityDataSerializers.BYTE) {
                    @SuppressWarnings("unchecked")
                    EntityDataAccessor<Byte> byteAccessor = (EntityDataAccessor<Byte>) accessor;
                    found = byteAccessor;
                    break;
                }
            } catch (ReflectiveOperationException ignored) {
                // keep scanning other fields
            }
        }
        if (found == null) {
            throw new RuntimeException("Could not locate Entity's shared-flags EntityDataAccessor<Byte> for GlowManager");
        }
        SHARED_FLAGS = found;
    }

    private final BattleRoyaleMod mod;

    public GlowManager(BattleRoyaleMod mod) {
        this.mod = mod;
        TickScheduler.runTimer(this::tick, 0L, 10L);
    }

    private void tick() {
        List<ServerPlayer> players = mod.getServer().getPlayerList().getPlayers();
        if (players.size() < 2) return;

        boolean active = GameManager.isIngame();
        TeamManager teamManager = mod.getTeamManager();

        for (ServerPlayer observer : players) {
            boolean observerSpectating = active && isSpectating(observer);
            Integer observerTeam = teamManager.getPlayerTeamNumber(observer);

            for (ServerPlayer target : players) {
                if (target == observer) continue;
                Integer targetTeam = teamManager.getPlayerTeamNumber(target);

                boolean shouldGlow = active && (observerSpectating
                        ? targetTeam != null
                        : (targetTeam != null && targetTeam.equals(observerTeam)));

                sendGlowState(observer, target, shouldGlow);
            }
        }
    }

    private boolean isSpectating(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                || mod.getDeadPlayers().contains(player.getUUID());
    }

    private void sendGlowState(ServerPlayer observer, ServerPlayer target, boolean glowing) {
        byte value = glowing ? (byte) (baseFlags(target) | FLAG_GLOWING) : baseFlags(target);
        observer.connection.send(new ClientboundSetEntityDataPacket(target.getId(),
                List.of(SynchedEntityData.DataValue.create(SHARED_FLAGS, value))));
    }

    private byte baseFlags(ServerPlayer target) {
        byte flags = 0;
        if (target.isOnFire()) flags |= FLAG_ONFIRE;
        if (target.isCrouching()) flags |= FLAG_CROUCHING;
        if (target.isSprinting()) flags |= FLAG_SPRINTING;
        if (target.isSwimming()) flags |= FLAG_SWIMMING;
        if (target.isInvisible()) flags |= FLAG_INVISIBLE;
        if (target.isFallFlying()) flags |= FLAG_FALL_FLYING;
        return flags;
    }
}
