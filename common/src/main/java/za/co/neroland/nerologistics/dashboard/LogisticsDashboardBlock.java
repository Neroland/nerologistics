package za.co.neroland.nerologistics.dashboard;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerologistics.conduit.AbstractConduitBlockEntity;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.ship.RocketFlightsState;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShipmentManager;
import za.co.neroland.nerologistics.world.QuestPerksState;

/**
 * Logistics dashboard: right-click to read a summary of the logistics state — the adjacent network's
 * node/endpoint counts, this dimension's aggregate throughput (items/fluid/energy), shipment queue +
 * delivered counts (plus held / crated counts for Nerospace flights), drones dispatched, and the
 * {@link ShippingReport} for everything within 128 blocks: in-transit shipments with state and ETA,
 * ports with their last refusal, and recently crated cargo. All figures are <b>aggregate world data</b>
 * (no player identity); per-player figures are never shown here. Reported to chat (no GUI).
 */
public class LogisticsDashboardBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<LogisticsDashboardBlock> CODEC = BlockCodecs.simple(LogisticsDashboardBlock::new);

    public LogisticsDashboardBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    protected MapCodec<LogisticsDashboardBlock> codec() {
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
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.header"));
        // Cosmetic quest badge: shown only to the player who earned it, never to anyone else.
        if (QuestPerksState.get(server).hasDashboardBadge(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.badge"));
        }

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

        LogisticsMetrics.Counters c = LogisticsMetrics.countersOf(server, level.dimension());
        player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.throughput",
                c.itemsMoved, c.fluidMoved, c.energyMoved));
        // Stub manifests + tracked Nerospace flights (the latter only ever non-zero with Nerospace).
        int inTransit = ShipmentManager.pendingCount(server)
                + (RouteProviders.nerospaceBound() ? RocketFlightsState.get(server).count() : 0);
        player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.shipping",
                c.shipmentsLaunched, c.shipmentsDelivered, inTransit));
        if (c.shipmentsHeld > 0 || c.shipmentsDropped > 0) {
            player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.shipping_warnings",
                    c.shipmentsHeld, c.shipmentsDropped));
        }
        if (level instanceof ServerLevel serverLevel) {
            ShippingReport.send(serverLevel, pos, player);
        }
        player.sendSystemMessage(Component.translatable("block.nerologistics.logistics_dashboard.drones",
                c.dronesDispatched));
    }
}
