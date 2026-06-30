package za.co.neroland.nerologistics.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.conduit.BufferBlockEntity;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

/**
 * Buffer menu. Slot {@code 0} is the ghost <b>target</b> — click it with a held stack to set the item to
 * keep and the stack's <em>count</em> as the level to hold (the held item is not consumed); click with an
 * empty hand to clear. Slots {@code 1–9} are the real buffer; then the player inventory.
 */
public class BufferMenu extends AbstractContainerMenu {

    private static final int BUFFER = BufferBlockEntity.BUFFER_SIZE;
    private static final int BUFFER_START = 1;
    private static final int BUFFER_END = 1 + BUFFER;
    private static final int PLAYER_START = BUFFER_END;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final Container target;
    private final Container buffer;
    @Nullable
    private final BufferBlockEntity blockEntity;

    public BufferMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(1), new SimpleContainer(BUFFER), null);
    }

    public BufferMenu(int id, Inventory playerInventory, BufferBlockEntity be) {
        this(id, playerInventory, be.target(), be.buffer(), be);
    }

    private BufferMenu(int id, Inventory playerInventory, Container target, Container buffer,
            @Nullable BufferBlockEntity be) {
        super(ModMenuTypes.BUFFER.get(), id);
        this.target = target;
        this.buffer = buffer;
        this.blockEntity = be;

        this.addSlot(new GhostSlot(target, 0, 8, 20));
        for (int col = 0; col < BUFFER; col++) {
            this.addSlot(new Slot(buffer, col, 8 + col * 18, 50));
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
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId == 0) {
            ItemStack carried = getCarried();
            if (carried.isEmpty()) {
                this.target.setItem(0, ItemStack.EMPTY);
            } else {
                int count = Math.min(carried.getCount(), carried.getMaxStackSize());
                this.target.setItem(0, carried.copyWithCount(count));
            }
            if (this.blockEntity != null) {
                this.blockEntity.setChanged();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.buffer.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == 0) {
            return ItemStack.EMPTY; // ghost target slot
        }
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < BUFFER_END) {
                if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, BUFFER_START, BUFFER_END, false)) {
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

    /** Display-only target slot: set/cleared via {@link #clicked}, never picked up or placed into. */
    private static final class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
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
