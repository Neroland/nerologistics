package za.co.neroland.nerologistics.conduit;

import java.util.Set;

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
 * Universal duct block-entity: a single duct that carries <b>items and fluids</b> on one network
 * (energy stays on cables). It joins both the {@code ITEM} and {@code FLUID} per-medium networks at the
 * same member positions ({@link #media()}), so one physical line moves both, content-routed by the
 * existing per-medium transport — items flow to item inventories, fluids to fluid storages. Faces are
 * configured once (Configurator) and apply to both media. Items are filtered by a single whitelist set
 * in the duct's GUI (bare-hand right-click), like the legacy item duct; fluids pass freely (per-face
 * fluid filters are a later refinement).
 *
 * <p>This is the Stage-8 default duct. The legacy item/fluid ducts remain for migration and still
 * interoperate — a universal duct placed against an item duct merges with its {@code ITEM} network, and
 * against a fluid duct merges with its {@code FLUID} network.
 */
public class UniversalDuctBlockEntity extends AbstractConduitBlockEntity implements MenuProvider {

    public static final int FILTER_SLOTS = 9;

    private static final Set<NetworkMedium> MEDIA = Set.of(NetworkMedium.ITEM, NetworkMedium.FLUID);

    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS);

    public UniversalDuctBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNIVERSAL_DUCT.get(), pos, state);
    }

    @Override
    public NetworkMedium medium() {
        return NetworkMedium.ITEM; // primary channel for face config; media() carries the full set
    }

    @Override
    public Set<NetworkMedium> media() {
        return MEDIA;
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
        return !anyRule; // empty whitelist => everything passes
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerologistics.universal_duct");
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
