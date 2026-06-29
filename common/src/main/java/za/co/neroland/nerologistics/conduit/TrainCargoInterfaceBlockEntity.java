package za.co.neroland.nerologistics.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Create train cargo interface: a buffered {@link net.minecraft.world.Container} that bridges a
 * NeroLogistics item network to Create's train load/unload. NeroLogistics item ducts treat it as an
 * ordinary inventory endpoint (it implements {@code Container}); Create reaches the same buffer through
 * the standard item capability registered per loader — so trains load/unload network cargo with no hard
 * Create dependency (presented via Core's common capability surface, not a Create import).
 */
public class TrainCargoInterfaceBlockEntity extends AbstractTerminalBlockEntity {

    public static final int BUFFER_SIZE = 18;

    public TrainCargoInterfaceBlockEntity(BlockPos pos, BlockState state) {
        // No energy buffer needed — a passive inventory bridge.
        super(ModBlockEntities.TRAIN_CARGO_INTERFACE.get(), pos, state, BUFFER_SIZE, 0, 0);
    }
}
