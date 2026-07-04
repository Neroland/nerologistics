package za.co.neroland.nerologistics.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerologistics.conduit.DronePortBlockEntity;
import za.co.neroland.nerologistics.registry.ModItems;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

/**
 * Drone-port menu: cargo buffer ({@code 0–8}), drone slots ({@code 9–12}, accept only the drone item),
 * upgrade slots ({@code 13–15}, accept only upgrade cards), then the player inventory. Shift-click routes
 * drones to the drone slots, cards to the upgrade slots, and everything else to the cargo buffer.
 */
public class DronePortMenu extends AbstractContainerMenu {

    private static final int CARGO = DronePortBlockEntity.BUFFER_SIZE;     // 9 (0..8)
    private static final int DRONES = DronePortBlockEntity.DRONE_SLOTS;    // 4 (9..12)
    private static final int UPGRADES = DronePortBlockEntity.UPGRADE_SLOTS; // 3 (13..15)
    private static final int CARGO_END = CARGO;
    private static final int DRONE_END = CARGO_END + DRONES;
    private static final int UPGRADE_END = DRONE_END + UPGRADES;
    private static final int PLAYER_START = UPGRADE_END;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final Container cargo;

    public DronePortMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(CARGO), new SimpleContainer(DRONES),
                new SimpleContainer(UPGRADES));
    }

    public DronePortMenu(int id, Inventory playerInventory, DronePortBlockEntity be) {
        this(id, playerInventory, be.buffer(), be.droneSlots(), be.upgradeSlots());
    }

    private DronePortMenu(int id, Inventory playerInventory, Container cargo, Container drones,
            Container upgrades) {
        super(ModMenuTypes.DRONE_PORT.get(), id);
        this.cargo = cargo;

        for (int col = 0; col < CARGO; col++) {
            this.addSlot(new Slot(cargo, col, 8 + col * 18, 20));
        }
        for (int col = 0; col < DRONES; col++) {
            this.addSlot(new FilteredSlot(drones, col, 8 + col * 18, 46, ModItems.DRONE.get()));
        }
        for (int col = 0; col < UPGRADES; col++) {
            this.addSlot(new FilteredSlot(upgrades, col, 116 + col * 18, 46, ModItems.HYPERSPEED_CARD.get()));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 156));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.cargo.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (index < PLAYER_START) {
            // From a machine slot → player inventory.
            if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From player inventory → the matching machine region.
            boolean moved;
            if (stack.getItem() == ModItems.DRONE.get()) {
                moved = this.moveItemStackTo(stack, CARGO_END, DRONE_END, false);
            } else if (stack.getItem() == ModItems.HYPERSPEED_CARD.get()) {
                moved = this.moveItemStackTo(stack, DRONE_END, UPGRADE_END, false);
            } else {
                moved = this.moveItemStackTo(stack, 0, CARGO_END, false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    /** A slot that only accepts one specific item. */
    private static final class FilteredSlot extends Slot {
        private final net.minecraft.world.item.Item allowed;

        FilteredSlot(Container container, int index, int x, int y, net.minecraft.world.item.Item allowed) {
            super(container, index, x, y);
            this.allowed = allowed;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() == this.allowed;
        }
    }
}
