package za.co.neroland.nerologistics.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.network.ClientStorageTerminal;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NeroLogisticsNetwork;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.network.StorageTerminalActionPayload;
import za.co.neroland.nerologistics.network.StorageTerminalContentsPayload;
import za.co.neroland.nerologistics.registry.ModMenuTypes;
import za.co.neroland.nerologistics.storage.ItemKey;
import za.co.neroland.nerologistics.storage.NetworkStorageIndex;

/**
 * Storage Terminal menu — the AE2-<i>similar</i> network browser, shared by the terminal block and
 * the wireless portable terminal (they differ only in the {@link TerminalTarget} behind it).
 *
 * <p><b>Layout.</b> Only the 36 player-inventory slots are real vanilla slots; the scrollable
 * network grid is screen-drawn from the payload-synced content list, and every grid interaction
 * rides the serverbound {@link StorageTerminalActionPayload} referencing lines by exact item
 * prototype — never by index (see that class for the trust model). Shift-clicking a player slot
 * quick-moves the stack <i>into the network</i> via {@link #quickMoveStack}.
 *
 * <p><b>Sync.</b> The server piggybacks on {@link #broadcastChanges()} (runs every tick for open
 * menus): at most every {@code terminalResyncTicks} (default 10t) it snapshots the item/fluid
 * indexes and sends a {@link StorageTerminalContentsPayload} <b>only when the snapshot changed</b>
 * since the last send (grid mutations force an immediate resync). Status/fluid-presence ride a
 * plain {@link ContainerData}. This mirrors Nerotech's menu-scoped payload seam — Core's channel
 * is sealed before downstream mods init, so NeroLogistics ships its own (see
 * {@link NeroLogisticsNetwork}).
 *
 * <p><b>Consumers.</b> While open, the menu holds one {@code openConsumer()} on each index it
 * browses, released exactly once in {@link #removed} (disconnects included — vanilla always calls
 * {@code removed}); if the network object is rebuilt mid-session the refs migrate on the next
 * sync pass. Block/network-scoped data only — no player data (POPIA/GDPR).</p>
 */
public class StorageTerminalMenu extends AbstractContainerMenu {

    /** Grid geometry (shared with the screen): 9 columns × 4 visible rows. */
    public static final int GRID_COLS = 9;
    public static final int GRID_ROWS = 4;
    public static final int GRID_X = 17;
    public static final int GRID_Y = 20;

    /** Player-inventory geometry. */
    public static final int INV_X = 17;
    public static final int INV_Y = 112;
    public static final int HOTBAR_Y = 170;

    public static final int DATA_COUNT = 2;
    /** data[0]: terminal status. */
    public static final int STATUS_NO_NETWORK = 0;
    public static final int STATUS_OK = 1;
    public static final int STATUS_DISABLED = 2;
    /** data[1]: 1 when a fluid network is reachable too (enables the fluid tab). */

    /**
     * What the menu talks to: the terminal block entity, or a wireless binding against a network
     * controller. Resolution is live — every sync pass re-resolves, so broken ducts/controllers
     * degrade to {@link #STATUS_NO_NETWORK} instead of dangling.
     */
    public interface TerminalTarget {
        Level level();

        /** Same contract as {@link AbstractContainerMenu#stillValid(Player)}. */
        boolean stillValid(Player player);

        /** The reachable network carrying {@code medium}, or {@code null}. */
        @Nullable
        ConduitNetwork network(NetworkMedium medium);
    }

    @Nullable
    private final TerminalTarget target;
    @Nullable
    private final ServerPlayer serverPlayer;
    private final ContainerData data;

    // --- Server sync state ---------------------------------------------------
    private long lastSyncGameTime = Long.MIN_VALUE;
    private boolean forceSync = true;
    private int revision;
    private final Map<ItemKey, Long> lastSentItems = new HashMap<>();
    private final Map<Fluid, Long> lastSentFluids = new HashMap<>();
    /** The indexes this menu holds a consumer ref on (closed/migrated refcount-safely). */
    @Nullable
    private NetworkStorageIndex openedItemIndex;
    @Nullable
    private NetworkStorageIndex openedFluidIndex;
    private boolean closed;

    // --- Client view state ---------------------------------------------------
    private List<StorageTerminalContentsPayload.ItemLine> clientItems = List.of();
    private List<StorageTerminalContentsPayload.FluidLine> clientFluids = List.of();
    private int clientRevision = -1;

    /** Client constructor (referenced by the {@code MenuType}). */
    public StorageTerminalMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    /** Server constructor: browse the networks behind {@code target}. */
    public StorageTerminalMenu(int containerId, Inventory playerInventory, TerminalTarget target) {
        this(containerId, playerInventory, target, null);
    }

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    private StorageTerminalMenu(int containerId, Inventory playerInventory,
            @Nullable TerminalTarget target, @Nullable ContainerData clientData) {
        super(ModMenuTypes.STORAGE_TERMINAL.get(), containerId);
        this.target = target;
        this.serverPlayer = target != null && playerInventory.player instanceof ServerPlayer sp ? sp : null;
        if (target != null) {
            this.data = new SimpleContainerData(DATA_COUNT);
        } else {
            this.data = clientData != null ? clientData : new SimpleContainerData(DATA_COUNT);
        }
        checkContainerDataCount(this.data, DATA_COUNT);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
        this.addDataSlots(this.data);
    }

    // --- Vanilla contract ----------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        if (this.target == null) {
            return true; // client mirror — the server-side menu is authoritative
        }
        return this.target.stillValid(player);
    }

    /** Shift-click a player slot: push the stack into the item network (server-side only). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (this.target == null) {
            return ItemStack.EMPTY; // no client prediction — the server moves the items
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        NetworkStorageIndex index0 = itemIndex();
        if (index0 == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        long moved = index0.insertItem(this.target.level(), stack, stack.getCount(), false);
        if (moved > 0) {
            stack.shrink((int) moved);
            slot.setChanged();
            this.forceSync = true;
        }
        return ItemStack.EMPTY; // never loop — partial inserts stay where they are
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.target != null && !this.closed) {
            this.closed = true;
            if (this.openedItemIndex != null) {
                this.openedItemIndex.closeConsumer();
                this.openedItemIndex = null;
            }
            if (this.openedFluidIndex != null) {
                this.openedFluidIndex.closeConsumer();
                this.openedFluidIndex = null;
            }
        }
    }

    // --- Server: content sync ------------------------------------------------

    @Override
    public void broadcastChanges() {
        if (this.target != null && this.serverPlayer != null && !this.closed) {
            syncContents();
        }
        super.broadcastChanges();
    }

    private void syncContents() {
        Level level = this.target.level();
        long now = level.getGameTime();
        if (!this.forceSync && this.lastSyncGameTime != Long.MIN_VALUE
                && now - this.lastSyncGameTime < NeroLogisticsConfig.terminalResyncTicks()) {
            return;
        }
        this.lastSyncGameTime = now;
        boolean enabled = NeroLogisticsConfig.enableStorageTerminal()
                && NeroLogisticsConfig.enableStorageNetwork();
        NetworkStorageIndex items = enabled ? itemIndex() : null;
        NetworkStorageIndex fluids = enabled ? fluidIndex() : null;
        this.data.set(0, !enabled ? STATUS_DISABLED : items == null && fluids == null
                ? STATUS_NO_NETWORK : STATUS_OK);
        this.data.set(1, fluids != null ? 1 : 0);
        Map<ItemKey, Long> itemSnapshot = items != null ? items.itemSnapshot(level) : Map.of();
        Map<Fluid, Long> fluidSnapshot = fluids != null ? fluids.fluidSnapshot(level) : Map.of();
        if (!this.forceSync && itemSnapshot.equals(this.lastSentItems)
                && fluidSnapshot.equals(this.lastSentFluids)) {
            return; // unchanged — nothing to send
        }
        this.forceSync = false;
        // The snapshots are live views into the index cache — copy before retaining.
        this.lastSentItems.clear();
        this.lastSentItems.putAll(itemSnapshot);
        this.lastSentFluids.clear();
        this.lastSentFluids.putAll(fluidSnapshot);
        this.revision++;
        NeroLogisticsNetwork.sendToPlayer(this.serverPlayer,
                StorageTerminalContentsPayload.of(this.containerId, this.revision,
                        this.lastSentItems, this.lastSentFluids));
    }

    /** Live item-network index (consumer ref migrated refcount-safely), or {@code null}. */
    @Nullable
    private NetworkStorageIndex itemIndex() {
        ConduitNetwork network = this.target.network(NetworkMedium.ITEM);
        NetworkStorageIndex index = network != null ? network.storageIndex() : null;
        if (index != this.openedItemIndex) {
            if (this.openedItemIndex != null) {
                this.openedItemIndex.closeConsumer();
            }
            if (index != null) {
                index.openConsumer();
            }
            this.openedItemIndex = index;
        }
        return index;
    }

    /** Live fluid-network index (consumer ref migrated refcount-safely), or {@code null}. */
    @Nullable
    private NetworkStorageIndex fluidIndex() {
        ConduitNetwork network = this.target.network(NetworkMedium.FLUID);
        NetworkStorageIndex index = network != null ? network.storageIndex() : null;
        if (index != this.openedFluidIndex) {
            if (this.openedFluidIndex != null) {
                this.openedFluidIndex.closeConsumer();
            }
            if (index != null) {
                index.openConsumer();
            }
            this.openedFluidIndex = index;
        }
        return index;
    }

    // --- Server: grid actions (already menu/session-validated by the payload) ---

    /** Apply a validated grid intent. All amounts are recomputed here from live network state. */
    public void handleAction(ServerPlayer player, StorageTerminalActionPayload payload) {
        if (this.target == null || this.closed
                || !NeroLogisticsConfig.enableStorageTerminal()
                || !NeroLogisticsConfig.enableStorageNetwork()) {
            return;
        }
        Level level = this.target.level();
        switch (payload.action()) {
            case EXTRACT_STACK -> extractToCursor(level, payload.item(), false);
            case EXTRACT_HALF -> extractToCursor(level, payload.item(), true);
            case EXTRACT_TO_INVENTORY -> extractToInventory(level, player, payload.item());
            case INSERT_CARRIED -> insertCarried(level, Integer.MAX_VALUE);
            case INSERT_CARRIED_ONE -> insertCarried(level, 1);
            case FILL_BUCKET -> fillBucket(level, player, payload.fluidId());
            case DRAIN_BUCKET -> drainBucket(level);
        }
    }

    private void extractToCursor(Level level, ItemStack prototype, boolean half) {
        if (prototype.isEmpty() || !getCarried().isEmpty()) {
            return;
        }
        NetworkStorageIndex index = itemIndex();
        if (index == null) {
            return;
        }
        int max = prototype.getMaxStackSize();
        long want = max;
        if (half) {
            long available = index.extractItem(level, prototype, max, true);
            want = Math.max(1, Math.min(available, max) / 2);
        }
        long moved = index.extractItem(level, prototype, want, false);
        if (moved > 0) {
            setCarried(prototype.copyWithCount((int) Math.min(moved, max)));
            this.forceSync = true;
        }
    }

    private void extractToInventory(Level level, ServerPlayer player, ItemStack prototype) {
        if (prototype.isEmpty()) {
            return;
        }
        NetworkStorageIndex index = itemIndex();
        if (index == null) {
            return;
        }
        int max = prototype.getMaxStackSize();
        long moved = index.extractItem(level, prototype, max, false);
        if (moved <= 0) {
            return;
        }
        this.forceSync = true;
        ItemStack stack = prototype.copyWithCount((int) Math.min(moved, max));
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            // Inventory full — put the remainder straight back (it just came out, so there is room;
            // if a racing consumer stole the space, drop it rather than void it).
            long returned = index.insertItem(level, stack, stack.getCount(), false);
            stack.shrink((int) returned);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    private void insertCarried(Level level, int limit) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }
        NetworkStorageIndex index = itemIndex();
        if (index == null) {
            return;
        }
        long moved = index.insertItem(level, carried, Math.min(limit, carried.getCount()), false);
        if (moved > 0) {
            carried.shrink((int) moved);
            setCarried(carried); // re-set so the empty case normalises to ItemStack.EMPTY
            this.forceSync = true;
        }
    }

    private void fillBucket(Level level, ServerPlayer player, @Nullable net.minecraft.resources.Identifier fluidId) {
        ItemStack carried = getCarried();
        if (fluidId == null || !carried.is(Items.BUCKET)) {
            return;
        }
        NetworkStorageIndex index = fluidIndex();
        if (index == null) {
            return;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getValue(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return;
        }
        Item bucket = fluid.getBucket();
        if (bucket == Items.AIR) {
            return;
        }
        // All-or-nothing: only fill when a whole bucket's worth is actually extractable.
        if (index.extractFluid(level, fluid, 1000, true) < 1000) {
            return;
        }
        index.extractFluid(level, fluid, 1000, false);
        this.forceSync = true;
        ItemStack filled = new ItemStack(bucket);
        if (carried.getCount() == 1) {
            setCarried(filled);
        } else {
            carried.shrink(1);
            player.getInventory().add(filled);
            if (!filled.isEmpty()) {
                player.drop(filled, false);
            }
        }
    }

    private void drainBucket(Level level) {
        ItemStack carried = getCarried();
        if (!(carried.getItem() instanceof BucketItem bucketItem)) {
            return;
        }
        Fluid fluid = bucketItem.getContent();
        if (fluid == Fluids.EMPTY) {
            return;
        }
        NetworkStorageIndex index = fluidIndex();
        if (index == null) {
            return;
        }
        // All-or-nothing: a bucket cannot half-empty.
        if (index.insertFluid(level, fluid, 1000, true) < 1000) {
            return;
        }
        index.insertFluid(level, fluid, 1000, false);
        setCarried(new ItemStack(Items.BUCKET));
        this.forceSync = true;
    }

    // --- Client: synced view -------------------------------------------------

    /** Apply a mailbox payload (client side); stale revisions for this menu are dropped. */
    public void applyContents(StorageTerminalContentsPayload payload) {
        if (payload.revision() > this.clientRevision) {
            this.clientRevision = payload.revision();
            this.clientItems = payload.items();
            this.clientFluids = payload.fluids();
        }
    }

    public List<StorageTerminalContentsPayload.ItemLine> clientItems() {
        return this.clientItems;
    }

    public List<StorageTerminalContentsPayload.FluidLine> clientFluids() {
        return this.clientFluids;
    }

    /** Synced terminal status ({@link #STATUS_NO_NETWORK}/{@link #STATUS_OK}/{@link #STATUS_DISABLED}). */
    public int status() {
        return this.data.get(0);
    }

    /** Whether a fluid network is reachable (enables the fluid tab client-side). */
    public boolean hasFluidNetwork() {
        return this.data.get(1) != 0;
    }
}
