package za.co.neroland.nerologistics.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.registry.ModMenuTypes;
import za.co.neroland.nerologistics.storage.DriveBayBlockEntity;
import za.co.neroland.nerologistics.storage.StorageCellItem;

/**
 * Drive Bay menu: six bay slots (storage cells only, one per bay) plus the player inventory.
 * The bays are the drive's real container — pulling a cell removes its contents from the network
 * with it (cells are portable). Slot edits persist through the bay container's
 * {@code setChanged()}, which also bumps the drive's storage version for the network index.
 */
public class DriveBayMenu extends AbstractContainerMenu {

    private static final int BAYS = DriveBayBlockEntity.BAYS;
    private static final int PLAYER_START = BAYS;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final Container bays;
    /** Owning drive on the server; {@code null} on the client mirror. */
    @Nullable
    private final DriveBayBlockEntity blockEntity;

    public DriveBayMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(BAYS), null);
    }

    public DriveBayMenu(int id, Inventory playerInventory, DriveBayBlockEntity be) {
        this(id, playerInventory, be.bays(), be);
    }

    private DriveBayMenu(int id, Inventory playerInventory, Container bays,
            @Nullable DriveBayBlockEntity be) {
        super(ModMenuTypes.DRIVE_BAY.get(), id);
        this.bays = bays;
        this.blockEntity = be;
        for (int col = 0; col < BAYS; col++) {
            this.addSlot(new CellSlot(bays, col, 35 + col * 18, 20));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity == null) {
            return true; // client mirror — the server-side menu is authoritative
        }
        if (this.blockEntity.isRemoved() || this.blockEntity.getLevel() == null
                || this.blockEntity.getLevel().getBlockEntity(this.blockEntity.getBlockPos()) != this.blockEntity) {
            return false;
        }
        return player.distanceToSqr(this.blockEntity.getBlockPos().getX() + 0.5D,
                this.blockEntity.getBlockPos().getY() + 0.5D,
                this.blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < BAYS) {
                if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!(stack.getItem() instanceof StorageCellItem)
                        || !this.moveItemStackTo(stack, 0, BAYS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    /** A bay: one storage cell only. */
    private static final class CellSlot extends Slot {
        CellSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof StorageCellItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
