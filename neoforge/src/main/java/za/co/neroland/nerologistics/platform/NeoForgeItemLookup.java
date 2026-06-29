package za.co.neroland.nerologistics.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.transport.ItemPort;

/**
 * NeoForge item bridge. Owns the shared {@code nerologistics:item}
 * {@link BlockCapability} over {@link ItemPort}; ducts and machines register their
 * block-entities against {@link #ITEM} during {@code RegisterCapabilitiesEvent} (wired
 * in Stage 2 once endpoints exist). Registered via {@code META-INF/services}. Mirrors
 * Core's {@code NeoForgeEnergyLookup}.
 */
public final class NeoForgeItemLookup implements IItemLookup {

    /** The NeroLogistics item capability. Endpoints register {@link ItemPort} providers for it. */
    public static final BlockCapability<ItemPort, Direction> ITEM =
            BlockCapability.createSided(
                    Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "item"),
                    ItemPort.class);

    @Nullable
    @Override
    public ItemPort find(Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(ITEM, pos, side);
    }
}
