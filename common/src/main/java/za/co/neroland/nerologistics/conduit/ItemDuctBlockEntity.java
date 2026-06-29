package za.co.neroland.nerologistics.conduit;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerologistics.filter.ItemFilter;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Item duct block-entity. Moves items between adjacent vanilla {@code Container}/{@code WorldlyContainer}
 * inventories (covers vanilla blocks and every Nero machine), filtered per face.
 */
public class ItemDuctBlockEntity extends AbstractConduitBlockEntity {

    private final Map<Direction, ItemFilter> filters = new EnumMap<>(Direction.class);

    public ItemDuctBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_DUCT.get(), pos, state);
    }

    @Override
    public NetworkMedium medium() {
        return NetworkMedium.ITEM;
    }

    /** The (mutable) filter for {@code face}, creating an empty one if absent. */
    public ItemFilter filter(Direction face) {
        return this.filters.computeIfAbsent(face, d -> new ItemFilter());
    }

    @Override
    public boolean itemPasses(Direction face, ItemStack stack) {
        ItemFilter filter = this.filters.get(face);
        return filter == null || filter.test(stack);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (Map.Entry<Direction, ItemFilter> entry : this.filters.entrySet()) {
            entry.getValue().save(output, "if_" + entry.getKey().getName() + "_");
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.filters.clear();
        for (Direction dir : Direction.values()) {
            ItemFilter filter = new ItemFilter();
            filter.load(input, "if_" + dir.getName() + "_");
            if (!filter.isEmpty()) {
                this.filters.put(dir, filter);
            }
        }
    }
}
