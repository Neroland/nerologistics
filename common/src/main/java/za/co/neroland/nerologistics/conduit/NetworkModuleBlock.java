package za.co.neroland.nerologistics.conduit;

import net.minecraft.world.level.block.Block;

/**
 * Network Module — a passive, cheap capacity block. The {@link NetworkControllerBlockEntity} counts the
 * modules connected to it (bounded flood-fill) and raises the managed network's throughput accordingly.
 * Holds no block-entity and does nothing on its own; it only matters next to a controller.
 */
public class NetworkModuleBlock extends Block {

    public NetworkModuleBlock(Properties properties) {
        super(properties);
    }
}
