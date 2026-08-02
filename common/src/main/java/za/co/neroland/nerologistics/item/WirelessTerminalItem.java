package za.co.neroland.nerologistics.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.conduit.NetworkControllerBlockEntity;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.menu.MenuOpener;
import za.co.neroland.nerologistics.menu.StorageTerminalMenu;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModDataComponents;
import za.co.neroland.nerologistics.storage.StorageTerminalBlockEntity;

/**
 * Wireless Terminal — the portable face of the storage network. <b>Sneak-use on a Network
 * Controller</b> binds the terminal to it (the binding — dimension + position — lives on the
 * stack's {@code wireless_target} component); a plain <b>use anywhere within
 * {@code wirelessTerminalRange}</b> (default 64 blocks, same dimension, {@code -1} = unlimited)
 * of the bound controller opens the same {@link StorageTerminalMenu} against the networks on that
 * controller's conduits. The controller must still exist and touch a conduit network — otherwise
 * the player gets a friendly message instead of a dead screen, and the open menu itself
 * re-validates every action through {@link WirelessTarget#stillValid} (moving out of range closes
 * it, vanilla-style).
 *
 * <p>The binding is a block position, never an identity — no player data (POPIA/GDPR).</p>
 */
public class WirelessTerminalItem extends Item {

    public WirelessTerminalItem(Properties properties) {
        super(properties);
    }

    /** The bound controller of {@code stack}, or {@code null} when unbound. */
    @Nullable
    public static GlobalPos boundTarget(ItemStack stack) {
        return stack.get(ModDataComponents.WIRELESS_TARGET.get());
    }

    /** Sneak-use on a network controller: bind this terminal to it. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!context.isSecondaryUseActive()
                || !(level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            context.getItemInHand().set(ModDataComponents.WIRELESS_TARGET.get(),
                    GlobalPos.of(level.dimension(), pos.immutable()));
            Player player = context.getPlayer();
            if (player != null) {
                player.sendSystemMessage(Component.translatable(
                        "item.nerologistics.wireless_terminal.bound",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Use (not sneaking): open the storage terminal against the bound controller's network. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS; // sneak is the binding gesture (useOn)
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!NeroLogisticsConfig.enableStorageTerminal() || !NeroLogisticsConfig.enableStorageNetwork()) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("block.nerologistics.storage_terminal.disabled"));
            return InteractionResult.SUCCESS;
        }
        GlobalPos target = boundTarget(stack);
        if (target == null) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("item.nerologistics.wireless_terminal.unbound"));
            return InteractionResult.SUCCESS;
        }
        if (!level.dimension().equals(target.dimension()) || !(level instanceof ServerLevel serverLevel)) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("item.nerologistics.wireless_terminal.wrong_dimension"));
            return InteractionResult.SUCCESS;
        }
        WirelessTarget menuTarget = new WirelessTarget(serverLevel, target.pos(), hand);
        if (!menuTarget.inRange(player)) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("item.nerologistics.wireless_terminal.out_of_range",
                            NeroLogisticsConfig.wirelessTerminalRange()));
            return InteractionResult.SUCCESS;
        }
        if (!(serverLevel.getBlockEntity(target.pos()) instanceof NetworkControllerBlockEntity)) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("item.nerologistics.wireless_terminal.no_controller"));
            return InteractionResult.SUCCESS;
        }
        if (menuTarget.network(NetworkMedium.ITEM) == null
                && menuTarget.network(NetworkMedium.FLUID) == null) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("item.nerologistics.wireless_terminal.no_network"));
            return InteractionResult.SUCCESS;
        }
        MenuOpener.open(serverPlayer, new SimpleMenuProvider(
                (id, inventory, p) -> new StorageTerminalMenu(id, inventory, menuTarget),
                Component.translatable("item.nerologistics.wireless_terminal")));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        GlobalPos target = boundTarget(stack);
        if (target == null) {
            tooltip.accept(Component.translatable("item.nerologistics.wireless_terminal.tooltip.unbound")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.accept(Component.translatable("item.nerologistics.wireless_terminal.tooltip.bound",
                    target.pos().getX(), target.pos().getY(), target.pos().getZ(),
                    target.dimension().identifier().toString()).withStyle(ChatFormatting.GRAY));
            int range = NeroLogisticsConfig.wirelessTerminalRange();
            tooltip.accept((range < 0
                    ? Component.translatable("item.nerologistics.wireless_terminal.tooltip.range_unlimited")
                    : Component.translatable("item.nerologistics.wireless_terminal.tooltip.range", range))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("item.nerologistics.wireless_terminal.tooltip.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The wireless {@link StorageTerminalMenu.TerminalTarget}: everything re-resolves against the
     * bound controller position, and {@link #stillValid} re-checks the whole chain (held + bound +
     * controller present + in range) every menu tick, so breaking any link closes the menu.
     */
    public static final class WirelessTarget implements StorageTerminalMenu.TerminalTarget {

        private final ServerLevel level;
        private final BlockPos controllerPos;
        private final InteractionHand hand;

        public WirelessTarget(ServerLevel level, BlockPos controllerPos, InteractionHand hand) {
            this.level = level;
            this.controllerPos = controllerPos.immutable();
            this.hand = hand;
        }

        /** Range gate: within {@code wirelessTerminalRange} of the controller ({@code -1} = unlimited). */
        public boolean inRange(Player player) {
            int range = NeroLogisticsConfig.wirelessTerminalRange();
            if (range < 0) {
                return true;
            }
            return player.distanceToSqr(this.controllerPos.getX() + 0.5D,
                    this.controllerPos.getY() + 0.5D, this.controllerPos.getZ() + 0.5D)
                    <= (double) range * (double) range;
        }

        @Override
        public Level level() {
            return this.level;
        }

        @Override
        public boolean stillValid(Player player) {
            if (player.level() != this.level || !inRange(player)) {
                return false;
            }
            ItemStack held = player.getItemInHand(this.hand);
            GlobalPos bound = held.getItem() instanceof WirelessTerminalItem
                    ? boundTarget(held) : null;
            if (bound == null || !bound.dimension().equals(this.level.dimension())
                    || !bound.pos().equals(this.controllerPos)) {
                return false;
            }
            return this.level.getBlockEntity(this.controllerPos) instanceof NetworkControllerBlockEntity;
        }

        @Nullable
        @Override
        public ConduitNetwork network(NetworkMedium medium) {
            return StorageTerminalBlockEntity.adjacentNetwork(this.level, this.controllerPos, medium);
        }
    }
}
