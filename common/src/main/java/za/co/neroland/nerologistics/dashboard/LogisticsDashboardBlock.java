package za.co.neroland.nerologistics.dashboard;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerologistics.conduit.AbstractConduitBlockEntity;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.ship.ShipmentManager;

/**
 * Logistics dashboard: right-click to read a summary of the logistics state — the adjacent network's
 * node/endpoint counts, this dimension's aggregate throughput (items/fluid/energy), shipment queue +
 * delivered counts, and drones dispatched. All figures are <b>aggregate world data</b> (no player
 * identity); per-player figures are never shown here. Reported to chat (no GUI).
 */
public class LogisticsDashboardBlock extends Block {

    public static final MapCodec<LogisticsDashboardBlock> CODEC = simpleCodec(LogisticsDashboardBlock::new);

    public LogisticsDashboardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            report(level, pos, serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    private void report(Level level, BlockPos pos, ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.header"));

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be instanceof AbstractConduitBlockEntity conduit) {
                NetworkMedium medium = conduit.medium();
                ConduitNetwork net = NetworkManager.networkAt(level, medium, neighbor);
                if (net != null) {
                    player.sendSystemMessage(Component.translatable(
                            "block.nerologistics.logistics_dashboard.network",
                            medium.lowerName(), net.size(), net.endpointCount(level)));
                    break;
                }
            }
        }

        LogisticsMetrics.Counters c = LogisticsMetrics.countersOf(level.dimension());
        player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.throughput",
                c.itemsMoved, c.fluidMoved, c.energyMoved));
        player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.shipping",
                c.shipmentsLaunched, c.shipmentsDelivered, ShipmentManager.pendingCount()));
        player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.drones",
                c.dronesDispatched));
    }
}
