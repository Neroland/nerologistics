package za.co.neroland.nerologistics.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.conduit.AbstractConduitBlockEntity;
import za.co.neroland.nerologistics.menu.StorageTerminalMenu;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Storage Terminal — the browse/insert/extract face of the storage network. Passive (no ticker,
 * no buffer, no power): the block entity only resolves the item/fluid networks on its adjacent
 * conduits, and the open {@link StorageTerminalMenu} does all the work (index consumer refs,
 * payload sync, server-validated grid actions). Network resolution is live per query, so cutting
 * the duct while a menu is open degrades that menu to "no network" instead of dangling.
 *
 * <p>Block/network-scoped state only — nothing persisted beyond the base block entity, and no
 * player data (POPIA/GDPR).</p>
 */
public class StorageTerminalBlockEntity extends BlockEntity
        implements MenuProvider, StorageTerminalMenu.TerminalTarget {

    public StorageTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_TERMINAL.get(), pos, state);
    }

    /**
     * The network carrying {@code medium} on any conduit adjacent to {@code pos}, or {@code null}
     * — the shared attach rule for terminals (same shape as the storage request terminal's;
     * universal ducts match multiple media, so item and fluid may resolve through one duct).
     */
    @Nullable
    public static ConduitNetwork adjacentNetwork(Level level, BlockPos pos, NetworkMedium medium) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (level.getBlockEntity(neighbor) instanceof AbstractConduitBlockEntity conduit
                    && conduit.media().contains(medium)) {
                ConduitNetwork net = NetworkManager.networkAt(level, medium, neighbor);
                if (net != null) {
                    return net;
                }
            }
        }
        return null;
    }

    // --- TerminalTarget ------------------------------------------------------

    @Override
    public Level level() {
        return this.level;
    }

    @Override
    public boolean stillValid(Player player) {
        if (isRemoved() || this.level == null
                || this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Nullable
    @Override
    public ConduitNetwork network(NetworkMedium medium) {
        return this.level == null ? null : adjacentNetwork(this.level, this.worldPosition, medium);
    }

    // --- MenuProvider --------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerologistics.storage_terminal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new StorageTerminalMenu(id, playerInventory, this);
    }
}
