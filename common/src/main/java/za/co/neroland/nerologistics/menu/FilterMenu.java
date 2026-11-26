package za.co.neroland.nerologistics.menu;

import java.util.Locale;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.sideconfig.SideMode;
import za.co.neroland.nerologistics.conduit.AbstractConduitBlockEntity;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.filter.FluidFilter;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

public class FilterMenu extends AbstractContainerMenu {

    public static final int FACES = 6;
    public static final int ITEM_FILTER_SLOTS = 9;
    public static final int FACE_X = 8;
    public static final int FACE_Y = 20;
    public static final int FACE_WIDTH = 72;
    public static final int FACE_HEIGHT = 18;
    public static final int COLUMN_STEP = 112;
    public static final int ROW_STEP = 21;
    private static final int GHOST_SLOTS = FACES + ITEM_FILTER_SLOTS;
    private static final int MEDIA = FACES;
    private static final int FLOW = FACES + 1;
    private static final int ITEM = 1;
    private static final int FLUID = 2;
    private static final int ENERGY = 4;
    private static final SideMode[] MODES = SideMode.values();

    private final SimpleContainerData data = new SimpleContainerData(FACES + 3);
    private final Container itemFilter;
    @Nullable
    private final AbstractConduitBlockEntity duct;

    public FilterMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(ITEM_FILTER_SLOTS), null);
    }

    public FilterMenu(int id, Inventory inventory, Container itemFilter, @Nullable AbstractConduitBlockEntity duct) {
        super(ModMenuTypes.FILTER.get(), id);
        this.duct = duct;
        this.itemFilter = itemFilter;
        Container fluidSamples = duct == null ? new SimpleContainer(FACES) : duct.fluidSamples();
        for (int face = 0; face < FACES; face++) {
            this.addSlot(new GhostSlot(fluidSamples, face, faceX(face) + FACE_WIDTH + 5, faceY(face) + 1, FLUID));
        }
        for (int col = 0; col < ITEM_FILTER_SLOTS; col++) {
            this.addSlot(new GhostSlot(itemFilter, col, 8 + col * 18, 100, ITEM));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 198));
        }
        updateData();
        addDataSlots(this.data);
    }

    public static int faceX(int face) {
        return FACE_X + (face / 3) * COLUMN_STEP;
    }

    public static int faceY(int face) {
        return FACE_Y + (face % 3) * ROW_STEP;
    }

    public Component faceLabel(int index) {
        Direction direction = Direction.from3DDataValue(index);
        SideMode mode = MODES[this.data.get(index)];
        return Component.translatable("gui.nerologistics.duct.face",
                Component.translatable("gui.nerologistics.duct.face." + direction.getName()),
                Component.translatable("gui.nerologistics.duct.mode." + mode.name().toLowerCase(Locale.ROOT)));
    }

    public boolean showsItems() {
        return shows(ITEM);
    }

    public boolean showsFluids() {
        return shows(FLUID);
    }

    public boolean showsEnergy() {
        return shows(ENERGY);
    }

    public int energyRate() {
        return (this.data.get(FLOW + 1) << 16) | (this.data.get(FLOW) & 0xffff);
    }

    private boolean shows(int medium) {
        return (this.data.get(MEDIA) & medium) != 0;
    }

    private void updateData() {
        if (this.duct != null) {
            int flags = 0;
            if (this.duct.media().contains(NetworkMedium.ITEM)) flags |= ITEM;
            if (this.duct.media().contains(NetworkMedium.FLUID)) flags |= FLUID;
            if (this.duct.media().contains(NetworkMedium.ENERGY)) flags |= ENERGY;
            this.data.set(MEDIA, flags);
            for (Direction face : Direction.values()) {
                this.data.set(face.get3DDataValue(), this.duct.faceMode(face).ordinal());
            }
            int rate = (int) Math.min(Integer.MAX_VALUE, Math.round(this.duct.energyRate()));
            this.data.set(FLOW, rate & 0xffff);
            this.data.set(FLOW + 1, rate >>> 16);
        }
    }

    @Override
    public void broadcastChanges() {
        updateData();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.duct == null || id < 0 || id >= FACES || !stillValid(player)) {
            return false;
        }
        this.duct.cycleFaceMode(Direction.from3DDataValue(id));
        updateData();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.duct == null) {
            return true;
        }
        var level = this.duct.getLevel();
        var pos = this.duct.getBlockPos();
        return !this.duct.isRemoved() && level instanceof ServerLevel serverLevel && player.level() == level
                && level.getBlockEntity(pos) == this.duct && player.mayInteract(serverLevel, pos)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId < 0 || slotId >= GHOST_SLOTS) {
            super.clicked(slotId, button, input, player);
            return;
        }
        Slot slot = this.slots.get(slotId);
        if (!slot.isActive() || !stillValid(player)) {
            return;
        }
        if (input == ContainerInput.QUICK_MOVE) {
            slot.set(ItemStack.EMPTY);
            filterChanged();
        } else if (input == ContainerInput.PICKUP) {
            ItemStack sample = getCarried();
            if (sample.isEmpty() || slotId >= FACES || FluidFilter.sampleFluid(sample) != Fluids.EMPTY) {
                slot.set(sample.copyWithCount(1));
                filterChanged();
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < GHOST_SLOTS || index >= this.slots.size() || !showsItems() || !stillValid(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.slots.get(index).getItem();
        if (!stack.isEmpty()) {
            for (int i = 0; i < ITEM_FILTER_SLOTS; i++) {
                if (this.itemFilter.getItem(i).isEmpty()) {
                    this.itemFilter.setItem(i, stack.copyWithCount(1));
                    filterChanged();
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private void filterChanged() {
        if (this.duct != null) {
            this.duct.setChanged();
        }
    }

    private final class GhostSlot extends Slot {
        private final int medium;

        GhostSlot(Container container, int index, int x, int y, int medium) {
            super(container, index, x, y);
            this.medium = medium;
        }

        @Override
        public boolean isActive() {
            return shows(this.medium);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
