package za.co.neroland.nerologistics.menu;

import net.minecraft.world.Container;
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

import za.co.neroland.nerologistics.conduit.LogisticsProcessorBlockEntity;
import za.co.neroland.nerologistics.conduit.LogisticsProcessorBlockEntity.RuleAction;
import za.co.neroland.nerologistics.conduit.LogisticsProcessorBlockEntity.RuleStatus;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

/**
 * Logistics-processor menu: eight rule rows. Slots {@code 0–7} are per-rule <b>ghost</b> item slots
 * (click with a carried stack to stamp a count-1 copy, empty cursor to clear — the ghost-slot
 * pattern of {@link CellConfigMenu}); then the player inventory. Everything else about a rule
 * (comparator, threshold, action, enabled flag, status) syncs down through {@link ContainerData}
 * and mutates up through {@link #clickMenuButton} — no custom packets, same as the cell-config
 * priority buttons. Thresholds reach {@code 1_000_000}, beyond a synced short, so each is split
 * into a low-15-bit and high-bit pair of data slots.
 *
 * <p>Button id encoding: {@code id = rule * 100 + op} with ops {@link #OP_TOGGLE_ENABLED},
 * {@link #OP_TOGGLE_COMPARATOR}, {@link #OP_CYCLE_ACTION}, and {@code OP_THRESHOLD_DOWN/UP + k}
 * where {@code k = 0..3} selects a ±10^k step (the screen picks k from shift/ctrl).</p>
 */
public class LogisticsProcessorMenu extends AbstractContainerMenu {

    public static final int RULES = LogisticsProcessorBlockEntity.RULE_COUNT;

    /** Data slots per rule: comparator, action, enabled, status, thrLow, thrHigh. */
    public static final int DATA_PER_RULE = 6;
    public static final int DATA_COUNT = RULES * DATA_PER_RULE;

    public static final int OP_TOGGLE_ENABLED = 0;
    public static final int OP_TOGGLE_COMPARATOR = 1;
    public static final int OP_CYCLE_ACTION = 2;
    /** {@code OP_THRESHOLD_DOWN + k} subtracts 10^k (k = 0..3). */
    public static final int OP_THRESHOLD_DOWN = 10;
    /** {@code OP_THRESHOLD_UP + k} adds 10^k (k = 0..3). */
    public static final int OP_THRESHOLD_UP = 20;

    /** Rule-row layout shared with the screen: ghost slot column and first-row Y, 18px pitch. */
    public static final int ROW_X = 8;
    public static final int ROW_Y = 18;
    public static final int ROW_H = 18;

    private static final int PLAYER_START = RULES;
    private static final int PLAYER_END = PLAYER_START + 36;

    @Nullable
    private final LogisticsProcessorBlockEntity blockEntity;
    private final Container ghosts;
    private final ContainerData data;

    /** Client constructor (referenced by the {@code MenuType}). */
    public LogisticsProcessorMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(RULES), null, new SimpleContainerData(DATA_COUNT));
    }

    /** Server constructor: edits the given processor's live rules. */
    public LogisticsProcessorMenu(int id, Inventory playerInventory, LogisticsProcessorBlockEntity be) {
        this(id, playerInventory, be.ghosts(), be, null);
    }

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    private LogisticsProcessorMenu(int id, Inventory playerInventory, Container ghosts,
            @Nullable LogisticsProcessorBlockEntity be, @Nullable ContainerData clientData) {
        super(ModMenuTypes.LOGISTICS_PROCESSOR.get(), id);
        this.blockEntity = be;
        this.ghosts = ghosts;
        if (be != null) {
            // Read-only live view over the BE's rule state; writes go through clickMenuButton.
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    int rule = index / DATA_PER_RULE;
                    return switch (index % DATA_PER_RULE) {
                        case 0 -> be.ruleAbove(rule) ? 1 : 0;
                        case 1 -> be.ruleAction(rule).ordinal();
                        case 2 -> be.ruleEnabled(rule) ? 1 : 0;
                        case 3 -> be.ruleStatus(rule).ordinal();
                        case 4 -> be.ruleThreshold(rule) & 0x7FFF;
                        default -> be.ruleThreshold(rule) >>> 15;
                    };
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
            this.data = clientData != null ? clientData : new SimpleContainerData(DATA_COUNT);
        }
        checkContainerDataCount(this.data, DATA_COUNT);

        for (int i = 0; i < RULES; i++) {
            this.addSlot(new GhostSlot(ghosts, i, ROW_X, ROW_Y + i * ROW_H));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 178 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 236));
        }
        this.addDataSlots(this.data);
    }

    /** Ghost slots never move real items; all interaction goes through {@link #clicked}. */
    private static final class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) {
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
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < RULES) {
            if (clickType == ContainerInput.PICKUP || clickType == ContainerInput.PICKUP_ALL
                    || clickType == ContainerInput.QUICK_MOVE) {
                ItemStack carried = getCarried();
                this.ghosts.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
                if (this.blockEntity != null) {
                    this.blockEntity.setChanged();
                }
            }
            return; // the ghost column never routes to vanilla slot handling
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < RULES) {
            this.ghosts.setItem(index, ItemStack.EMPTY);
            if (this.blockEntity != null) {
                this.blockEntity.setChanged();
            }
            return ItemStack.EMPTY;
        }
        // Shift-click a player stack: stamp the first empty rule slot (ghost copy — nothing moves).
        ItemStack stack = this.slots.get(index).getItem();
        if (!stack.isEmpty()) {
            for (int i = 0; i < RULES; i++) {
                if (this.ghosts.getItem(i).isEmpty()) {
                    this.ghosts.setItem(i, stack.copyWithCount(1));
                    if (this.blockEntity != null) {
                        this.blockEntity.setChanged();
                    }
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.blockEntity == null || id < 0) {
            return false;
        }
        int rule = id / 100;
        int op = id % 100;
        if (rule >= RULES) {
            return false;
        }
        if (op == OP_TOGGLE_ENABLED) {
            this.blockEntity.toggleRuleEnabled(rule);
            return true;
        }
        if (op == OP_TOGGLE_COMPARATOR) {
            this.blockEntity.toggleRuleAbove(rule);
            return true;
        }
        if (op == OP_CYCLE_ACTION) {
            this.blockEntity.cycleRuleAction(rule);
            return true;
        }
        if (op >= OP_THRESHOLD_DOWN && op < OP_THRESHOLD_DOWN + 4) {
            this.blockEntity.adjustRuleThreshold(rule, -pow10(op - OP_THRESHOLD_DOWN));
            return true;
        }
        if (op >= OP_THRESHOLD_UP && op < OP_THRESHOLD_UP + 4) {
            this.blockEntity.adjustRuleThreshold(rule, pow10(op - OP_THRESHOLD_UP));
            return true;
        }
        return false;
    }

    private static int pow10(int k) {
        int result = 1;
        for (int i = 0; i < k; i++) {
            result *= 10;
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.blockEntity == null || this.blockEntity.stillValid(player);
    }

    // --- Screen helpers (client reads of the synced rule state) --------------

    public boolean ruleAbove(int rule) {
        return this.data.get(rule * DATA_PER_RULE) != 0;
    }

    public RuleAction ruleAction(int rule) {
        RuleAction[] values = RuleAction.values();
        int ordinal = this.data.get(rule * DATA_PER_RULE + 1);
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RuleAction.KEEP_STOCKED_ADJACENT;
    }

    public boolean ruleEnabled(int rule) {
        return this.data.get(rule * DATA_PER_RULE + 2) != 0;
    }

    public RuleStatus ruleStatus(int rule) {
        RuleStatus[] values = RuleStatus.values();
        int ordinal = this.data.get(rule * DATA_PER_RULE + 3);
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RuleStatus.DISABLED;
    }

    public int ruleThreshold(int rule) {
        return (this.data.get(rule * DATA_PER_RULE + 5) << 15) | this.data.get(rule * DATA_PER_RULE + 4);
    }
}
