package za.co.neroland.nerologistics.conduit;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerologistics.filter.FluidFilter;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Fluid duct block-entity. Moves fluids between adjacent {@code NeroFluidStorage}s discovered via
 * Core's {@code FluidLookup}, filtered per face.
 */
public class FluidDuctBlockEntity extends AbstractConduitBlockEntity {

    private final Map<Direction, FluidFilter> filters = new EnumMap<>(Direction.class);

    public FluidDuctBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_DUCT.get(), pos, state);
    }

    @Override
    public NetworkMedium medium() {
        return NetworkMedium.FLUID;
    }

    /** The (mutable) filter for {@code face}, creating an empty one if absent. */
    public FluidFilter filter(Direction face) {
        return this.filters.computeIfAbsent(face, d -> new FluidFilter());
    }

    @Override
    public boolean fluidPasses(Direction face, Fluid fluid) {
        FluidFilter filter = this.filters.get(face);
        return filter == null || filter.test(fluid);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (Map.Entry<Direction, FluidFilter> entry : this.filters.entrySet()) {
            entry.getValue().save(output, "ff_" + entry.getKey().getName() + "_");
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.filters.clear();
        for (Direction dir : Direction.values()) {
            FluidFilter filter = new FluidFilter();
            filter.load(input, "ff_" + dir.getName() + "_");
            if (!filter.isEmpty()) {
                this.filters.put(dir, filter);
            }
        }
    }
}
