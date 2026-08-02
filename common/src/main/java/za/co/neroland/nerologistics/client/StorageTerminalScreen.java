package za.co.neroland.nerologistics.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import za.co.neroland.nerologistics.menu.StorageTerminalMenu;
import za.co.neroland.nerologistics.network.ClientStorageTerminal;
import za.co.neroland.nerologistics.network.NeroLogisticsNetwork;
import za.co.neroland.nerologistics.network.StorageTerminalActionPayload;
import za.co.neroland.nerologistics.network.StorageTerminalContentsPayload;

/**
 * Storage Terminal screen — the AE2-<i>similar</i> browser on the shared procedural dark-hull
 * panel. A 9×4 scrollable grid shows the payload-synced network contents with abbreviated counts
 * (exact count in the tooltip); the search box filters by item name or mod id and the sort button
 * cycles count↓ / name / mod — all client-side over the last synced list, no round-trips. Grid
 * clicks send {@link StorageTerminalActionPayload} intents referencing lines by exact prototype
 * (see the payload's trust model); the Fluids tab lists mB totals and supports bucket fill/drain
 * from the cursor. The mailbox ({@link ClientStorageTerminal}) is polled each container tick.
 */
public class StorageTerminalScreen extends AbstractContainerScreen<StorageTerminalMenu> {

    private static final int PANEL = 0xFF11161D;
    private static final int EDGE = 0xFF05080D;
    private static final int WELL = 0xFF8B8B8B;
    private static final int WELL_EDGE = 0xFF373737;
    private static final int GRID = 0xFF1B2634;
    private static final int GRID_EDGE = 0xFF18202A;
    private static final int GRID_HOVER = 0x66FFFFFF;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int WARN = 0xFFE0A64B;
    private static final int BUTTON_BG = 0xFF223041;
    private static final int BUTTON_EDGE = 0xFF3A506B;
    private static final int BUTTON_TEXT = 0xFFD6ECFF;
    private static final int SCROLL_TRACK = 0xFF0B1017;
    private static final int SCROLL_THUMB = 0xFF3A506B;

    private static final int W = 194;
    private static final int H = 194;
    private static final int COLS = StorageTerminalMenu.GRID_COLS;
    private static final int ROWS = StorageTerminalMenu.GRID_ROWS;
    private static final int GRID_X = StorageTerminalMenu.GRID_X;
    private static final int GRID_Y = StorageTerminalMenu.GRID_Y;
    /** Scrollbar geometry, right of the grid. */
    private static final int SCROLL_X = GRID_X + COLS * 18 + 4;
    private static final int SCROLL_W = 6;
    /** Sort/tab button strip below the grid. */
    private static final int BUTTON_Y = GRID_Y + ROWS * 18 + 4;
    private static final int BUTTON_H = 12;
    private static final int SORT_X = GRID_X;
    private static final int SORT_W = 78;
    private static final int TAB_X = GRID_X + 84;
    private static final int TAB_W = 60;

    private enum SortMode {
        COUNT("gui.nerologistics.storage_terminal.sort.count"),
        NAME("gui.nerologistics.storage_terminal.sort.name"),
        MOD("gui.nerologistics.storage_terminal.sort.mod");

        final String key;

        SortMode(String key) {
            this.key = key;
        }
    }

    private EditBox searchBox;
    private String searchText = "";
    private SortMode sortMode = SortMode.COUNT;
    private boolean fluidTab;
    private int scrollRow;
    private int lastAppliedRevision = -1;

    /** The filtered+sorted view the grid renders (rebuilt on sync/search/sort changes). */
    private final List<StorageTerminalContentsPayload.ItemLine> itemView = new ArrayList<>();
    private final List<StorageTerminalContentsPayload.FluidLine> fluidView = new ArrayList<>();

    public StorageTerminalScreen(StorageTerminalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = StorageTerminalMenu.INV_X;
        this.inventoryLabelY = StorageTerminalMenu.INV_Y - 12;
    }

    @Override
    protected void init() {
        super.init();
        this.searchBox = new EditBox(this.font, this.leftPos + 96, this.topPos + 4, 90, 12,
                Component.translatable("gui.nerologistics.storage_terminal.search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(true);
        this.searchBox.setHint(Component.translatable("gui.nerologistics.storage_terminal.search_hint"));
        this.searchBox.setValue(this.searchText);
        this.searchBox.setResponder(text -> {
            this.searchText = text;
            rebuildView();
        });
        this.addRenderableWidget(this.searchBox);
        rebuildView();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        StorageTerminalContentsPayload payload = ClientStorageTerminal.poll(this.menu.containerId);
        if (payload != null) {
            this.menu.applyContents(payload);
        }
        if (this.lastAppliedRevision != viewRevision()) {
            rebuildView();
        }
        if (this.fluidTab && !this.menu.hasFluidNetwork()) {
            this.fluidTab = false;
            rebuildView();
        }
    }

    /** Cheap change detector: list identity moves with each applied payload. */
    private int viewRevision() {
        return System.identityHashCode(this.menu.clientItems())
                ^ System.identityHashCode(this.menu.clientFluids());
    }

    // --- View building (pure client) ----------------------------------------

    private void rebuildView() {
        this.lastAppliedRevision = viewRevision();
        String query = this.searchText.trim().toLowerCase(Locale.ROOT);
        this.itemView.clear();
        for (StorageTerminalContentsPayload.ItemLine line : this.menu.clientItems()) {
            if (matches(query, line.prototype())) {
                this.itemView.add(line);
            }
        }
        switch (this.sortMode) {
            case COUNT -> this.itemView.sort(
                    Comparator.comparingLong(StorageTerminalContentsPayload.ItemLine::count).reversed()
                            .thenComparing(line -> line.prototype().getHoverName().getString(),
                                    String.CASE_INSENSITIVE_ORDER));
            case NAME -> this.itemView.sort(
                    Comparator.comparing((StorageTerminalContentsPayload.ItemLine line) ->
                            line.prototype().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
            case MOD -> this.itemView.sort(
                    Comparator.comparing((StorageTerminalContentsPayload.ItemLine line) -> modId(line))
                            .thenComparing(line -> line.prototype().getHoverName().getString(),
                                    String.CASE_INSENSITIVE_ORDER));
        }
        this.fluidView.clear();
        for (StorageTerminalContentsPayload.FluidLine line : this.menu.clientFluids()) {
            if (query.isEmpty() || line.fluidId().toString().toLowerCase(Locale.ROOT).contains(query)) {
                this.fluidView.add(line);
            }
        }
        this.fluidView.sort(Comparator
                .comparingLong(StorageTerminalContentsPayload.FluidLine::amount).reversed()
                .thenComparing(line -> line.fluidId().toString()));
        int maxRow = Math.max(0, (activeSize() + COLS - 1) / COLS - ROWS);
        this.scrollRow = Math.min(this.scrollRow, maxRow);
    }

    private static boolean matches(String query, ItemStack stack) {
        if (query.isEmpty()) {
            return true;
        }
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return modId(stack).contains(query);
    }

    private static String modId(StorageTerminalContentsPayload.ItemLine line) {
        return modId(line.prototype());
    }

    private static String modId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    private int activeSize() {
        return this.fluidTab ? this.fluidView.size() : this.itemView.size();
    }

    /** 1234 → "1234"; larger counts abbreviate to 1.2k / 34k / 1.2M / 3.4G (tooltip stays exact). */
    static String abbreviate(long count) {
        if (count < 10_000L) {
            return Long.toString(count);
        }
        double value = count;
        String[] units = {"k", "M", "G", "T"};
        int unit = -1;
        while (value >= 1_000D && unit < units.length - 1) {
            value /= 1_000D;
            unit++;
        }
        return value < 10D
                ? String.format(Locale.ROOT, "%.1f%s", value, units[unit])
                : String.format(Locale.ROOT, "%.0f%s", value, units[unit]);
    }

    // --- Rendering -----------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        extractor.fill(x - 1, y - 1, x + W + 1, y + H + 1, EDGE);
        extractor.fill(x, y, x + W, y + H, PANEL);
        // Player slots (procedural wells, like every other NeroLogistics screen).
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            extractor.fill(sx - 1, sy - 1, sx + 17, sy + 17, WELL_EDGE);
            extractor.fill(sx, sy, sx + 16, sy + 16, WELL);
        }
        // Network grid wells.
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int gx = x + GRID_X + col * 18;
                int gy = y + GRID_Y + row * 18;
                extractor.fill(gx - 1, gy - 1, gx + 17, gy + 17, GRID_EDGE);
                extractor.fill(gx, gy, gx + 16, gy + 16, GRID);
            }
        }
        extractGridEntries(extractor, mouseX, mouseY);
        extractScrollbar(extractor);
        extractButtons(extractor, mouseX, mouseY);
        super.extractContents(extractor, mouseX, mouseY, partialTick);
    }

    private void extractGridEntries(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int hovered = gridIndexAt(mouseX, mouseY);
        for (int cell = 0; cell < ROWS * COLS; cell++) {
            int index = this.scrollRow * COLS + cell;
            if (index >= activeSize()) {
                break;
            }
            int gx = this.leftPos + GRID_X + (cell % COLS) * 18;
            int gy = this.topPos + GRID_Y + (cell / COLS) * 18;
            if (this.fluidTab) {
                StorageTerminalContentsPayload.FluidLine line = this.fluidView.get(index);
                ItemStack icon = fluidIcon(line.fluidId());
                extractor.item(icon, gx, gy);
                extractor.itemDecorations(this.font, icon, gx, gy, abbreviate(line.amount() / 1000L) + "B");
            } else {
                StorageTerminalContentsPayload.ItemLine line = this.itemView.get(index);
                extractor.item(line.prototype(), gx, gy);
                extractor.itemDecorations(this.font, line.prototype(), gx, gy, abbreviate(line.count()));
            }
            if (index == hovered) {
                extractor.fill(gx, gy, gx + 16, gy + 16, GRID_HOVER);
            }
        }
        if (hovered >= 0 && hovered < activeSize()) {
            extractTooltipFor(extractor, hovered, mouseX, mouseY);
        }
    }

    private void extractTooltipFor(GuiGraphicsExtractor extractor, int index, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (this.fluidTab) {
            StorageTerminalContentsPayload.FluidLine line = this.fluidView.get(index);
            ItemStack icon = fluidIcon(line.fluidId());
            lines.add(icon.is(Items.BARRIER)
                    ? Component.literal(line.fluidId().toString())
                    : icon.getHoverName());
            lines.add(Component.translatable("gui.nerologistics.storage_terminal.tooltip.mb",
                    String.format(Locale.ROOT, "%,d", line.amount())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(line.fluidId().toString()).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.nerologistics.storage_terminal.tooltip.fluid_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            StorageTerminalContentsPayload.ItemLine line = this.itemView.get(index);
            if (this.minecraft != null) {
                lines.addAll(Screen.getTooltipFromItem(this.minecraft, line.prototype()));
            }
            lines.add(Component.translatable("gui.nerologistics.storage_terminal.tooltip.count",
                    String.format(Locale.ROOT, "%,d", line.count())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(modId(line)).withStyle(ChatFormatting.BLUE));
        }
        extractor.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    /** A renderable stand-in for a fluid: its bucket, or a barrier when it has no bucket item. */
    private static ItemStack fluidIcon(Identifier fluidId) {
        Fluid fluid = BuiltInRegistries.FLUID.getValue(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return new ItemStack(Items.BARRIER);
        }
        ItemStack bucket = new ItemStack(fluid.getBucket());
        return bucket.isEmpty() ? new ItemStack(Items.BARRIER) : bucket;
    }

    private void extractScrollbar(GuiGraphicsExtractor extractor) {
        int x = this.leftPos + SCROLL_X;
        int top = this.topPos + GRID_Y;
        int height = ROWS * 18;
        extractor.fill(x, top, x + SCROLL_W, top + height, SCROLL_TRACK);
        int totalRows = Math.max(1, (activeSize() + COLS - 1) / COLS);
        int thumbH = Math.max(8, Math.min(height, height * ROWS / totalRows));
        int maxRow = Math.max(0, totalRows - ROWS);
        int thumbY = maxRow == 0 ? top : top + (height - thumbH) * this.scrollRow / maxRow;
        extractor.fill(x + 1, thumbY, x + SCROLL_W - 1, thumbY + thumbH, SCROLL_THUMB);
    }

    private void extractButtons(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        drawButton(extractor, SORT_X, SORT_W,
                Component.translatable(this.sortMode.key), !this.fluidTab);
        if (this.menu.hasFluidNetwork()) {
            drawButton(extractor, TAB_X, TAB_W, Component.translatable(this.fluidTab
                    ? "gui.nerologistics.storage_terminal.tab.fluids"
                    : "gui.nerologistics.storage_terminal.tab.items"), true);
        }
    }

    private void drawButton(GuiGraphicsExtractor extractor, int bx, int bw, Component label, boolean lit) {
        int x = this.leftPos + bx;
        int y = this.topPos + BUTTON_Y;
        extractor.fill(x - 1, y - 1, x + bw + 1, y + BUTTON_H + 1, BUTTON_EDGE);
        extractor.fill(x, y, x + bw, y + BUTTON_H, BUTTON_BG);
        int tw = this.font.width(label);
        extractor.text(this.font, label, x + (bw - tw) / 2, y + 2, lit ? BUTTON_TEXT : SUBTLE, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        int status = this.menu.status();
        if (status != StorageTerminalMenu.STATUS_OK) {
            extractor.text(this.font, Component.translatable(
                    status == StorageTerminalMenu.STATUS_DISABLED
                            ? "gui.nerologistics.storage_terminal.disabled"
                            : "gui.nerologistics.storage_terminal.no_network"),
                    GRID_X, GRID_Y + 2, WARN, false);
        } else if (activeSize() == 0) {
            extractor.text(this.font, Component.translatable("gui.nerologistics.storage_terminal.empty"),
                    GRID_X, GRID_Y + 2, SUBTLE, false);
        }
        extractor.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                SUBTLE, false);
    }

    // --- Input ---------------------------------------------------------------

    /** The view index under the mouse, or -1 (screen coordinates). */
    private int gridIndexAt(double mouseX, double mouseY) {
        int gx = (int) (mouseX - this.leftPos - GRID_X);
        int gy = (int) (mouseY - this.topPos - GRID_Y);
        if (gx < 0 || gy < 0 || gx >= COLS * 18 || gy >= ROWS * 18) {
            return -1;
        }
        int col = gx / 18;
        int row = gy / 18;
        return (this.scrollRow + row) * COLS + col;
    }

    private boolean inGrid(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + GRID_X && mouseX < this.leftPos + GRID_X + COLS * 18
                && mouseY >= this.topPos + GRID_Y && mouseY < this.topPos + GRID_Y + ROWS * 18;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        // Sort button.
        if (hitButton(mx, my, SORT_X, SORT_W)) {
            this.sortMode = SortMode.values()[(this.sortMode.ordinal() + 1) % SortMode.values().length];
            rebuildView();
            return true;
        }
        // Items/Fluids tab.
        if (this.menu.hasFluidNetwork() && hitButton(mx, my, TAB_X, TAB_W)) {
            this.fluidTab = !this.fluidTab;
            this.scrollRow = 0;
            rebuildView();
            return true;
        }
        if (inGrid(mx, my) && this.menu.status() == StorageTerminalMenu.STATUS_OK) {
            handleGridClick(event, gridIndexAt(mx, my));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean hitButton(double mx, double my, int bx, int bw) {
        int x = this.leftPos + bx;
        int y = this.topPos + BUTTON_Y;
        return mx >= x && mx < x + bw && my >= y && my < y + BUTTON_H;
    }

    private void handleGridClick(MouseButtonEvent event, int index) {
        boolean rightClick = event.button() == 1;
        ItemStack carried = this.menu.getCarried();
        if (this.fluidTab) {
            if (carried.is(Items.BUCKET) && index >= 0 && index < this.fluidView.size()) {
                send(StorageTerminalActionPayload.Action.FILL_BUCKET, ItemStack.EMPTY,
                        this.fluidView.get(index).fluidId());
            } else if (carried.getItem() instanceof BucketItem bucket
                    && bucket.getContent() != Fluids.EMPTY) {
                send(StorageTerminalActionPayload.Action.DRAIN_BUCKET, ItemStack.EMPTY, null);
            }
            return;
        }
        if (!carried.isEmpty()) {
            send(rightClick ? StorageTerminalActionPayload.Action.INSERT_CARRIED_ONE
                    : StorageTerminalActionPayload.Action.INSERT_CARRIED, ItemStack.EMPTY, null);
            return;
        }
        if (index < 0 || index >= this.itemView.size()) {
            return;
        }
        ItemStack prototype = this.itemView.get(index).prototype();
        StorageTerminalActionPayload.Action action;
        if (rightClick) {
            action = StorageTerminalActionPayload.Action.EXTRACT_HALF;
        } else if (event.hasShiftDown()) {
            action = StorageTerminalActionPayload.Action.EXTRACT_TO_INVENTORY;
        } else {
            action = StorageTerminalActionPayload.Action.EXTRACT_STACK;
        }
        send(action, prototype, null);
    }

    private void send(StorageTerminalActionPayload.Action action, ItemStack prototype,
            Identifier fluidId) {
        NeroLogisticsNetwork.sendToServer(new StorageTerminalActionPayload(
                this.menu.containerId, action, prototype, fluidId));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inGrid(mouseX, mouseY) || (mouseX >= this.leftPos + SCROLL_X
                && mouseX < this.leftPos + SCROLL_X + SCROLL_W
                && mouseY >= this.topPos + GRID_Y && mouseY < this.topPos + GRID_Y + ROWS * 18)) {
            int maxRow = Math.max(0, (activeSize() + COLS - 1) / COLS - ROWS);
            this.scrollRow = Math.max(0, Math.min(maxRow, this.scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Let the focused search box swallow typing (incl. the inventory key) without closing. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // Escape
            this.onClose();
            return true;
        }
        if (this.searchBox != null
                && (this.searchBox.keyPressed(event) || this.searchBox.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(event);
    }
}
