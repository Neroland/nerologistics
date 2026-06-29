package za.co.neroland.nerologistics.platform;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.transport.ItemPort;

/**
 * Fabric item bridge. Owns the shared {@code nerologistics:item}
 * {@link BlockApiLookup} over {@link ItemPort}; ducts and machines register their
 * block-entities against {@link #ITEM}. Registered via {@code META-INF/services}.
 * Mirrors Core's {@code FabricEnergyLookup}.
 */
public final class FabricItemLookup implements IItemLookup {

    /** The NeroLogistics item lookup. Endpoints register {@link ItemPort} providers for it. */
    public static final BlockApiLookup<ItemPort, Direction> ITEM =
            BlockApiLookup.get(
                    Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "item"),
                    ItemPort.class, Direction.class);

    @Nullable
    @Override
    public ItemPort find(Level level, BlockPos pos, @Nullable Direction side) {
        return ITEM.find(level, pos, side);
    }
}
