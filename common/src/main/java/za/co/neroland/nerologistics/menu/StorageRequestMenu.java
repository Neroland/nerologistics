package za.co.neroland.nerologistics.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerologistics.conduit.StorageRequestTerminalBlockEntity;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

/** Menu for the storage request terminal: a row of output slots backed by the terminal buffer + player inventory. */
public class StorageRequestMenu extends AbstractContainerMenu {

    private static final int BUFFER = StorageRequestTerminalBlockEntity.BUFFER_SIZE;

    private final Container container;

    public StorageRequestMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(BUFFER));
    }

    public StorageRequestMenu(int id, Inventory playerInventory, Container container) {
        super(ModMenuTypes.STORAGE_REQUEST.get(), id);
        this.container = container;
        for (int col = 0; col < BUFFER; col++) {
            this.addSlot(new Slot(container, col, 8 + col * 18, 18));
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
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int playerStart = BUFFER;
            int playerEnd = playerStart + 36;
            if (index < playerStart) {
                if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, playerStart, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }
}
