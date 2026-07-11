package com.example.battleroyale;

import com.example.battleroyale.config.BRConfig;
import com.example.battleroyale.util.ItemParser;
import com.example.battleroyale.util.TickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Ported from the Paper plugin's UtilManager: supply drops, custom ore/sand drops,
 * environmental-damage halving and crafting restrictions.
 *
 * The two separate ore-drop handlers in the original plugin (one small one in
 * BattleRoyale.java, one complete one in UtilManager.java) both fired on the same
 * BlockBreakEvent and would have double-dropped iron/gold ore; this port keeps only
 * the complete version to avoid that duplicate-drop bug.
 */
public class UtilManager {

    private final BattleRoyaleMod mod;
    private BorderManager borderManager;
    private final Random random = new Random();

    private long supplyDropTimerTicks;
    private double nextSupplyDropX = 0;
    private double nextSupplyDropZ = 0;
    private boolean nextSupplyDropCalculated = false;

    private static final List<String> DISABLED_CRAFTING_IDS = List.of(
            "enchanted_golden_apple", "end_crystal", "respawn_anchor", "totem_of_undying"
    );

    public UtilManager(BattleRoyaleMod mod) {
        this.mod = mod;
        this.supplyDropTimerTicks = 20L * 60 * BRConfig.SUPPLY_DROP_INTERVAL_MINUTES.get();

        TickScheduler.runTimer(() -> {
            if (GameManager.isIngame()) {
                supplyDropTimerTicks -= 20;
                if (supplyDropTimerTicks <= 0) {
                    spawnSupplyDrop();
                    supplyDropTimerTicks = 20L * 60 * BRConfig.SUPPLY_DROP_INTERVAL_MINUTES.get();
                }
            }
        }, 0L, 20L);
    }

    public void setBorderManager(BorderManager borderManager) {
        this.borderManager = borderManager;
    }

    public long getSupplyDropRemainingTicks() {
        return supplyDropTimerTicks;
    }

    public long getSupplyDropTotalTicks() {
        return 20L * 60 * BRConfig.SUPPLY_DROP_INTERVAL_MINUTES.get();
    }

    private void calculateNextSupplyDropLocation() {
        if (borderManager == null) return;
        double currentBorderRadius = borderManager.getCurrentSize() / 2;
        double centerX = borderManager.getBorderCenterX();
        double centerZ = borderManager.getBorderCenterZ();

        nextSupplyDropX = centerX + (random.nextDouble() * 2 - 1) * (currentBorderRadius * 0.8);
        nextSupplyDropZ = centerZ + (random.nextDouble() * 2 - 1) * (currentBorderRadius * 0.8);
        nextSupplyDropCalculated = true;
    }

    public double getDistanceToNextSupplyDrop(ServerPlayer player) {
        if (!nextSupplyDropCalculated) return -1;
        double dx = player.getX() - nextSupplyDropX;
        double dz = player.getZ() - nextSupplyDropZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public String getSupplyDropDistanceRange(ServerPlayer player) {
        double distance = getDistanceToNextSupplyDrop(player);
        if (distance < 0) return "계산 중...";
        if (distance <= 250) return "250블럭 안";
        if (distance <= 500) return "500블럭 안";
        if (distance <= 1000) return "1000블럭 안";
        return "1000블럭 밖";
    }

    public void initializeFirstSupplyDrop() {
        calculateNextSupplyDropLocation();
    }

    public void spawnSupplyDrop() {
        if (borderManager == null) return;
        ServerLevel level = borderManager.getLevel();

        double spawnX, spawnZ;
        if (nextSupplyDropCalculated) {
            spawnX = nextSupplyDropX;
            spawnZ = nextSupplyDropZ;
        } else {
            double currentBorderRadius = borderManager.getCurrentSize() / 2;
            double centerX = borderManager.getBorderCenterX();
            double centerZ = borderManager.getBorderCenterZ();
            spawnX = centerX + (random.nextDouble() * 2 - 1) * (currentBorderRadius * 0.8);
            spawnZ = centerZ + (random.nextDouble() * 2 - 1) * (currentBorderRadius * 0.8);
        }

        int x = (int) Math.floor(spawnX);
        int z = (int) Math.floor(spawnZ);
        int y = Math.min(255, level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos).isAir()) {
            pos.move(0, -1, 0);
        }
        BlockPos chestPos = pos.above().immutable();

        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            for (String itemString : BRConfig.SUPPLY_DROP_ITEMS.get()) {
                ItemStack item = ItemParser.parse(itemString, random, w -> mod.getLogger().warn(w));
                if (item != null) {
                    chest.setItem(findFreeSlot(chest), item);
                }
            }
        }

        BRText.broadcast(mod.getServer(), "§6[배틀로얄] §f(" + chestPos.getX() + ", " + chestPos.getZ() + ") 에 보급이 소환되었습니다!");
        calculateNextSupplyDropLocation();
    }

    private int findFreeSlot(ChestBlockEntity chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).isEmpty()) return i;
        }
        return 0;
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        DamageSource source = event.getSource();
        boolean isEntityOrProjectile = source.getDirectEntity() != null;
        if (!isEntityOrProjectile) {
            event.setAmount(event.getAmount() / 2.0f);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        Block block = state.getBlock();
        var levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();

        ItemStack drop = null;
        int extra = 0;

        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
            drop = new ItemStack(Items.IRON_INGOT, random.nextInt(5) + 1);
        } else if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
            drop = new ItemStack(Items.GOLD_INGOT, 1);
        } else if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) {
            drop = new ItemStack(Items.COPPER_INGOT, random.nextInt(10) + 6);
        } else if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
            drop = new ItemStack(Items.LAPIS_LAZULI, random.nextInt(5) + 4);
        } else if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
            drop = new ItemStack(Items.REDSTONE, random.nextInt(2) + 4);
        } else if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            drop = new ItemStack(Items.DIAMOND, 1);
        } else if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
            drop = new ItemStack(Items.EMERALD, 1);
        } else if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
            drop = new ItemStack(Items.COAL, 1);
        } else if (block == Blocks.SAND) {
            drop = new ItemStack(Items.GLASS, 1);
            if (random.nextDouble() < 0.20) {
                extra = 1;
            }
        } else {
            return;
        }

        int exp = event.getExpToDrop();
        event.setCanceled(true);

        level.removeBlock(pos, false);
        level.levelEvent(2001, pos, Block.getId(state));

        Block.popResource(level, pos, drop);
        if (extra > 0) {
            Block.popResource(level, pos, new ItemStack(Items.AMETHYST_SHARD, extra));
        }
        if (exp > 0) {
            ExperienceOrb.award(level, pos.getCenter(), exp);
        }
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        var key = ForgeRegistries.ITEMS.getKey(crafted.getItem());
        if (key == null) return;
        String path = key.getPath();
        String upper = path.toUpperCase(Locale.ROOT);

        if (DISABLED_CRAFTING_IDS.contains(path) || upper.contains("POTION") || upper.contains("ARROW") || upper.contains("BED")) {
            crafted.setCount(0);
            event.getEntity().sendSystemMessage(BRText.of("§c이 아이템은 제작할 수 없습니다!"));
        }
    }
}
