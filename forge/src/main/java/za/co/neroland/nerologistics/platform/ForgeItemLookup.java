package za.co.neroland.nerologistics.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.transport.ItemPort;

/**
 * Forge item bridge. Owns the shared {@link ItemPort} capability; ducts and machines
 * attach providers to their block-entities via {@code AttachCapabilitiesEvent} (wired
 * in Stage 2 once endpoints exist). Registered via {@code META-INF/services}. Mirrors
 * Core's {@code ForgeEnergyLookup}.
 */
public final class ForgeItemLookup implements IItemLookup {

    /** The NeroLogistics item capability. Endpoints attach {@link ItemPort} providers for it. */
    public static final Capability<ItemPort> ITEM =
            CapabilityManager.get(new CapabilityToken<>() { });

    @Nullable
    @Override
    public ItemPort find(Level level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        return be.getCapability(ITEM, side).orElse(null);
    }
}
