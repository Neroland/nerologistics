package za.co.neroland.nerologistics.conduit;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.item.ConfiguratorItem;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.ship.PortSchedule;
import za.co.neroland.nerologistics.ship.RouteDestination;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShippingClass;
import za.co.neroland.nerologistics.world.AttributionOptOutState;

/**
 * Rocket cargo port block. Empty hand: right-click cycles the destination (and, under Nerospace routing,
 * makes the clicking player the port's dispatcher) then prints the port status; sneak-right-click cycles
 * the channel (stub) or toggles return-empty (Nerospace — channels do not apply to pad destinations).
 * With the Configurator: right-click cycles the shipping class (STANDARD → EXPRESS → BULK),
 * sneak-right-click cycles the launch schedule (every interval → when full → manual) and prints the
 * status. In the manual schedule a redstone pulse into the port launches once.
 */
public class RocketCargoPortBlock extends BaseEntityBlock {

    public static final MapCodec<RocketCargoPortBlock> CODEC = BlockCodecs.simple(RocketCargoPortBlock::new);

    public RocketCargoPortBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<RocketCargoPortBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RocketCargoPortBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Record ownership ONLY when per-player attribution is opted in (POPIA/GDPR: default off = no
        // player data stored). UUID only, erasable via Core's shared data-erasure hook.
        if (!level.isClientSide() && NeroLogisticsConfig.perPlayerThroughputAttribution()
                && placer instanceof ServerPlayer player && level.getServer() != null
                && !AttributionOptOutState.get(level.getServer()).isOptedOut(player.getUUID())
                && level.getBlockEntity(pos) instanceof RocketCargoPortBlockEntity port) {
            port.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof RocketCargoPortBlockEntity port) {
            boolean nerospace = RouteProviders.get().needsDispatcher();
            boolean configurator = player.getMainHandItem().getItem() instanceof ConfiguratorItem;
            if (configurator && player.isShiftKeyDown()) {
                // Normally unreachable (vanilla skips block use while sneaking with an item — the
                // Configurator handles that case itself); kept for loaders that do call it.
                // Read-only status (the Configurator's sneak-use reads, as on conduits).
                cycleSchedule(serverLevel, serverPlayer, port);
            } else if (configurator) {
                if (!NeroLogisticsConfig.enableShippingQos()) {
                    serverPlayer.sendSystemMessage(Component.translatable(
                            "block.nerologistics.rocket_cargo_port.shipping_class.disabled"));
                } else {
                    ShippingClass shippingClass = port.cycleShippingClass();
                    serverPlayer.sendSystemMessage(Component.translatable(
                            "block.nerologistics.rocket_cargo_port.shipping_class",
                            Component.translatable(shippingClass.translationKey())));
                }
            } else if (player.isShiftKeyDown() && nerospace) {
                // Channels do not apply to Nerospace flights (the destination is a pad), so the sneak-click
                // toggles return-empty instead.
                boolean on = port.toggleReturnEmpty();
                serverPlayer.sendSystemMessage(Component.translatable(
                        "block.nerologistics.rocket_cargo_port.return_empty",
                        Component.translatable(on ? "nerologistics.ship.status.on" : "nerologistics.ship.status.off")));
            } else if (player.isShiftKeyDown()) {
                int channel = port.cycleChannel();
                serverPlayer.sendSystemMessage(
                        Component.translatable("block.nerologistics.rocket_cargo_port.channel", channel));
            } else {
                Optional<RouteDestination> dest = port.cycleDestination(serverLevel, serverPlayer.getUUID());
                serverPlayer.sendSystemMessage(Component.translatable("block.nerologistics.rocket_cargo_port.destination",
                        dest.map(RouteDestination::displayName)
                                .orElse(Component.translatable("nerologistics.ship.status.none"))));
                if (dest.isEmpty() && nerospace) {
                    serverPlayer.sendSystemMessage(Component.translatable(
                            "block.nerologistics.rocket_cargo_port.no_pads"));
                }
                sendStatus(serverLevel, serverPlayer, port);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RocketCargoPortBlockEntity port) {
            port.onRedstone(level.hasNeighborSignal(pos)); // MANUAL schedule: rising edge = one launch
        }
    }

    /** Configurator sneak-use: cycle the launch schedule, then print the status. */
    public static void cycleSchedule(ServerLevel level, ServerPlayer player, RocketCargoPortBlockEntity port) {
        PortSchedule schedule = port.cycleSchedule();
        player.sendSystemMessage(Component.translatable("block.nerologistics.rocket_cargo_port.schedule",
                Component.translatable(schedule.translationKey())));
        sendStatus(level, player, port);
    }

    /** Print the port status to {@code player} (also used by the Configurator's sneak-use). */
    public static void sendStatus(ServerLevel level, ServerPlayer player, RocketCargoPortBlockEntity port) {
        for (Component line : port.statusLines(level, player.getUUID())) {
            player.sendSystemMessage(line);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.ROCKET_CARGO_PORT.get(),
                (lvl, pos, st, be) -> RocketCargoPortBlockEntity.serverTick(lvl, pos, st, be));
    }
}
