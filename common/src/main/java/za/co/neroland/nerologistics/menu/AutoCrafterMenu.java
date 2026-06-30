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

import za.co.neroland.nerologistics.conduit.AutoCrafterBlockEntity;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

/**
 * Auto-crafter menu. Slots: {@code 0–9} ghost pattern (3×3 inputs + 1 output) — click a ghost slot with
 * a held item to stamp a count-1 copy, click with an empty hand to clear (the held item is never
 * consumed); {@code 10–18} the real output buffer (crafted items, takeable); then the player inventory.
 */
public class AutoCrafterMenu extends AbstractContainerMenu {

    private static final int PATTERN = AutoCrafterBlockEntity.PATTERN_SIZE; // 10 ghost slots (0..9)
    private static final int OUTPUT = AutoCrafterBlockEntity.BUFFER_SIZE;   // 9 real output slots (10..18)
    private static final int OUTPUT_START = PATTERN;
    private static final int OUTPUT_END = PATTERN + OUTPUT;
    private static final int PLAYER_START = OUTPUT_END;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final Container pattern;
    private final Container output;
    @Nullable
    private final AutoCrafterBlockEntity blockEntity;

    public AutoCrafterMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(PATTERN), new SimpleContainer(OUTPUT), null);
    }

    public AutoCrafterMenu(int id, Inventory playerInventory, AutoCrafterBlockEntity be) {
        this(id, playerInventory, be.pattern(), be.buffer(), be);
    }

    private AutoCrafterMenu(int id, Inventory playerInventory, Container pattern, Container output,
            @Nullable AutoCrafterBlockEntity be) {
        super(ModMenuTypes.AUTO_CRAFTER.get(), id);
        this.pattern = pattern;
        this.output = output;
        this.blockEntity = be;

        // Ghost pattern: 3×3 inputs at left, output to the right.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new GhostSlot(pattern, col + row * 3, 26 + col * 18, 18 + row * 18));
            }
        }
        this.addSlot(new GhostSlot(pattern, AutoCrafterBlockEntity.OUTPUT_INDEX, 116, 36));

        // Real output buffer row.
        for (int col = 0; col < OUTPUT; col++) {
            this.addSlot(new Slot(output, col, 8 + col * 18, 78));
        }

        // Player inventory + hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 114 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 172));
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < PATTERN) {
            ItemStack carried = getCarried();
            this.pattern.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            if (this.blockEntity != null) {
                this.blockEntity.setChanged();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.output.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < OUTPUT_START) {
            return ItemStack.EMPTY; // ghost slots never move stacks
        }
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < OUTPUT_END) {
                if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, OUTPUT_START, OUTPUT_END, false)) {
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

    /** Display-only pattern slot: set/cleared via {@link #clicked}, never picked up or placed into. */
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

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
