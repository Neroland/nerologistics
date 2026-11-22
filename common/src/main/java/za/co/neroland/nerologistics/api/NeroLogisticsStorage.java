package za.co.neroland.nerologistics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import za.co.neroland.nerologistics.conduit.AbstractConduitBlockEntity;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.storage.StorageNode;

/**
 * Stable public API for other mods that want to put items into NeroLogistics storage (for example a
 * companion bot unloading its cargo). Bind to this class, not to the internal packages: it stays
 * binary-compatible across minor releases, and new methods are only ever added.
 *
 * <p>Every call is server-thread only, owner-free and stateless: nothing is registered, nothing is
 * remembered about the caller, and no player data is read or written (POPIA/GDPR). A position in an
 * unloaded chunk is treated as "nothing there" — these calls never load chunks.</p>
 */
public final class NeroLogisticsStorage {

    /** Bumped when methods are added; compare with {@code >=} to feature-detect. */
    public static final int API_VERSION = 1;

    private NeroLogisticsStorage() {
    }

    /** Whether the block at {@code pos} is an active storage node (a drive bay that is switched on). */
    public static boolean isStorageNode(ServerLevel level, BlockPos pos) {
        StorageNode node = nodeAt(level, pos);
        return node != null && node.storageNodeActive();
    }

    /**
     * Insert {@code stack} into the storage node at {@code pos} (a drive bay). The stack is not
     * mutated; its count is the amount offered.
     *
     * @return how many items were (or, when {@code simulate}, would be) accepted; 0 if there is no
     *         active node there
     */
    public static long insertAt(ServerLevel level, BlockPos pos, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0L;
        }
        StorageNode node = nodeAt(level, pos);
        if (node == null || !node.storageNodeActive()) {
            return 0L;
        }
        return Math.max(0L, node.insertItem(stack, stack.getCount(), simulate));
    }

    /**
     * Insert {@code stack} into the item storage network reached from {@code pos}: {@code pos} may be
     * an item-carrying conduit, or any block next to one (a terminal, a drive bay, the spot a bot is
     * docked at). The network spreads the items over its storage nodes by priority, exactly as a
     * storage terminal would. The stack is not mutated.
     *
     * @return how many items were (or would be) accepted; 0 if no item network is reachable there
     */
    public static long insertIntoNetwork(ServerLevel level, BlockPos pos, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0L;
        }
        ConduitNetwork network = itemNetworkAt(level, pos);
        if (network == null) {
            return 0L;
        }
        return Math.max(0L, network.storageIndex().insertItem(level, stack, stack.getCount(), simulate));
    }

    /** Whether an item storage network is reachable from {@code pos} (see {@link #insertIntoNetwork}). */
    public static boolean hasNetworkAt(ServerLevel level, BlockPos pos) {
        return itemNetworkAt(level, pos) != null;
    }

    private static StorageNode nodeAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof StorageNode node ? node : null;
    }

    private static ConduitNetwork itemNetworkAt(ServerLevel level, BlockPos pos) {
        ConduitNetwork own = conduitNetwork(level, pos);
        if (own != null) {
            return own;
        }
        for (Direction dir : Direction.values()) {
            ConduitNetwork net = conduitNetwork(level, pos.relative(dir));
            if (net != null) {
                return net;
            }
        }
        return null;
    }

    private static ConduitNetwork conduitNetwork(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof AbstractConduitBlockEntity conduit
                && conduit.media().contains(NetworkMedium.ITEM)) {
            return NetworkManager.networkAt(level, NetworkMedium.ITEM, pos);
        }
        return null;
    }
}
