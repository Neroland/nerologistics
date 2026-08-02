package za.co.neroland.nerologistics.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.registry.ModMenuTypes;
import za.co.neroland.nerologistics.storage.CellSettings;
import za.co.neroland.nerologistics.storage.StorageCellItem;

/**
 * Configuration menu for a held {@link StorageCellItem} (opened by sneak-use): nine <b>ghost</b>
 * partition slots (3×3) plus a synced priority, all persisted straight into the held stack's
 * {@code cell_settings} component — closing the menu never loses state (pattern mirrors the
 * item-duct {@link FilterMenu} ghost handling and rides the vanilla {@code openMenu} path — no
 * loader-specific menu API, no custom packets).
 *
 * <p>Interactions: click a ghost slot with a carried stack to stamp a count-1 copy (the item is
 * not consumed); click with an empty cursor to clear it; shift-click a player-inventory stack to
 * stamp the first free ghost slot. For fluid cells, stamp <b>buckets</b> — the partition matches
 * the bucket's fluid. Priority rides {@link #clickMenuButton} (−10/−1/+1/+10).</p>
 */
public class CellConfigMenu extends AbstractContainerMenu {

    public static final int PARTITION = CellSettings.PARTITION_SLOTS; // 9 (3×3)
    public static final int DATA_COUNT = 1;                            // [0] = priority

    public static final int BUTTON_PRIORITY_DOWN_10 = 0;
    public static final int BUTTON_PRIORITY_DOWN_1 = 1;
    public static final int BUTTON_PRIORITY_UP_1 = 2;
    public static final int BUTTON_PRIORITY_UP_10 = 3;

    /** Ghost-grid top-left (3×3, 18px pitch). */
    public static final int GRID_X = 26;
    public static final int GRID_Y = 17;

    /** Priority is clamped to a sane band so the synced short-friendly value never overflows. */
    public static final int PRIORITY_MIN = -9_999;
    public static final int PRIORITY_MAX = 9_999;

    @Nullable
    private final Player player;
    @Nullable
    private final InteractionHand hand;
    /** The exact stack being edited (object identity guarded by {@link #stillValid}). */
    private final ItemStack cellStack;

    private final SimpleContainer ghosts = new SimpleContainer(PARTITION);
    private int priority;

    private final ContainerData data;

    /** Client constructor (referenced by the {@code MenuType}). */
    public CellConfigMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    /** Server constructor: edits the storage cell currently held in {@code hand}. */
    public CellConfigMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        this(containerId, playerInventory, hand, null);
    }

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    private CellConfigMenu(int containerId, Inventory playerInventory,
            @Nullable InteractionHand hand, @Nullable ContainerData clientData) {
        super(ModMenuTypes.CELL_CONFIG.get(), containerId);
        this.hand = hand;
        if (hand != null) {
            this.player = playerInventory.player;
            this.cellStack = this.player.getItemInHand(hand);
            CellSettings initial = StorageCellItem.settings(this.cellStack);
            this.priority = initial.priority();
            List<ItemStack> partition = initial.partition();
            for (int i = 0; i < PARTITION; i++) {
                this.ghosts.setItem(i, i < partition.size() ? partition.get(i).copy() : ItemStack.EMPTY);
            }
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return CellConfigMenu.this.priority;
                }

                @Override
                public void set(int index, int value) {
                    // read-only from the client
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            this.player = null;
            this.cellStack = ItemStack.EMPTY;
            this.data = clientData != null ? clientData : new SimpleContainerData(DATA_COUNT);
        }
        checkContainerDataCount(this.data, DATA_COUNT);
        for (int i = 0; i < PARTITION; i++) {
            int row = i / 3;
            int col = i % 3;
            this.addSlot(new GhostSlot(this.ghosts, i, GRID_X + col * 18, GRID_Y + row * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
        this.addDataSlots(this.data);
    }

    /** Ghost slots never move real items; all interaction goes through {@link #clicked}. */
    private static final class GhostSlot extends Slot {
        GhostSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player clickingPlayer) {
        if (slotId >= 0 && slotId < PARTITION) {
            if (clickType == ContainerInput.PICKUP || clickType == ContainerInput.PICKUP_ALL
                    || clickType == ContainerInput.QUICK_MOVE) {
                ItemStack carried = getCarried();
                this.ghosts.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
                saveToStack();
            }
            return; // ghost grid never routes to vanilla slot handling
        }
        // Never let the cell being edited move under its own open menu; SWAP (hotbar number keys)
        // is blocked wholesale for the same reason.
        if (clickType == ContainerInput.SWAP) {
            return;
        }
        if (slotId >= 0 && slotId < this.slots.size()
                && this.slots.get(slotId).getItem() == this.cellStack && !this.cellStack.isEmpty()) {
            return;
        }
        super.clicked(slotId, button, clickType, clickingPlayer);
    }

    @Override
    public ItemStack quickMoveStack(Player quickMovePlayer, int index) {
        if (index < PARTITION) {
            this.ghosts.setItem(index, ItemStack.EMPTY);
            saveToStack();
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.slots.get(index).getItem();
        if (!stack.isEmpty() && stack != this.cellStack) {
            for (int i = 0; i < PARTITION; i++) {
                if (this.ghosts.getItem(i).isEmpty()) {
                    this.ghosts.setItem(i, stack.copyWithCount(1));
                    saveToStack();
                    break;
                }
            }
        }
        return ItemStack.EMPTY; // ghost copy only — the real stack never moves
    }

    @Override
    public boolean clickMenuButton(Player buttonPlayer, int id) {
        int delta = switch (id) {
            case BUTTON_PRIORITY_DOWN_10 -> -10;
            case BUTTON_PRIORITY_DOWN_1 -> -1;
            case BUTTON_PRIORITY_UP_1 -> 1;
            case BUTTON_PRIORITY_UP_10 -> 10;
            default -> 0;
        };
        if (delta == 0) {
            return false;
        }
        this.priority = Math.max(PRIORITY_MIN, Math.min(PRIORITY_MAX, this.priority + delta));
        saveToStack();
        return true;
    }

    @Override
    public boolean stillValid(Player validPlayer) {
        if (this.hand == null || this.player == null) {
            return true; // client mirror — the server decides
        }
        return this.player.getItemInHand(this.hand) == this.cellStack
                && this.cellStack.getItem() instanceof StorageCellItem;
    }

    /** Persist the working state into the held stack's component (server only). */
    private void saveToStack() {
        if (this.player == null || this.player.level().isClientSide()) {
            return;
        }
        List<ItemStack> partition = new ArrayList<>(PARTITION);
        for (int i = 0; i < PARTITION; i++) {
            partition.add(this.ghosts.getItem(i).copy());
        }
        StorageCellItem.storeSettings(this.cellStack,
                new CellSettings(List.copyOf(partition), this.priority));
    }

    // --- Screen helpers -----------------------------------------------------

    /** The synced priority of the edited cell. */
    public int priority() {
        return this.data.get(0);
    }
}
