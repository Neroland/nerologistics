package za.co.neroland.nerologistics.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.menu.FilterMenu;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Item duct block-entity. Moves items between adjacent vanilla inventories, filtered by a single
 * <b>whitelist</b> the player sets in the duct's GUI (bare-hand right-click): drop example items into
 * the filter slots and only those item types pass; an empty filter passes everything. The filter slots
 * are "ghost" — items dropped in are samples, never consumed (see {@link FilterMenu}). Per-face
 * direction (input/output/disabled) is set separately with the Configurator.
 */
public class ItemDuctBlockEntity extends AbstractConduitBlockEntity implements MenuProvider {

    public static final int FILTER_SLOTS = 9;

    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS);

    public ItemDuctBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_DUCT.get(), pos, state);
    }

    @Override
    public NetworkMedium medium() {
        return NetworkMedium.ITEM;
    }

    public SimpleContainer filter() {
        return this.filter;
    }

    @Override
    public boolean itemPasses(Direction face, ItemStack stack) {
        boolean anyRule = false;
        for (int i = 0; i < this.filter.getContainerSize(); i++) {
            ItemStack rule = this.filter.getItem(i);
            if (!rule.isEmpty()) {
                anyRule = true;
                if (ItemStack.isSameItem(rule, stack)) {
                    return true;
                }
            }
        }
        return !anyRule; // no rules => whitelist is empty => everything passes
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerologistics.item_duct");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new FilterMenu(id, playerInventory, this.filter);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            output.store("Filter" + i, ItemStack.OPTIONAL_CODEC, this.filter.getItem(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            this.filter.setItem(i, input.read("Filter" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        }
    }
}
