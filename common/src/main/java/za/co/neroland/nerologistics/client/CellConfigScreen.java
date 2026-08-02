package za.co.neroland.nerologistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerologistics.menu.CellConfigMenu;

/**
 * Storage-cell configuration screen — a procedural dark-hull panel (no texture asset). Left: the
 * 3×3 partition ghost grid (click with a carried stack to stamp, empty cursor to clear; buckets
 * partition fluid cells). Right: the synced priority with −10/−1/+1/+10 buttons, routed through
 * {@code handleInventoryButtonClick} like every other NeroLogistics menu (no custom packets).
 */
public class CellConfigScreen extends AbstractContainerScreen<CellConfigMenu> {

    private static final int PANEL = 0xFF11161D;
    private static final int EDGE = 0xFF05080D;
    private static final int WELL = 0xFF8B8B8B;
    private static final int WELL_EDGE = 0xFF373737;
    private static final int GHOST = 0xFF2A3340;
    private static final int GHOST_EDGE = 0xFF18202A;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int BUTTON_BG = 0xFF223041;
    private static final int BUTTON_EDGE = 0xFF3A506B;
    private static final int BUTTON_TEXT = 0xFFD6ECFF;

    /** Priority button strip: 4 buttons of BW×BH at (BX + i*(BW+2), BY). */
    private static final int BX = 96;
    private static final int BY = 44;
    private static final int BW = 17;
    private static final int BH = 12;
    private static final String[] BUTTON_LABELS = {"-10", "-1", "+1", "+10"};

    public CellConfigScreen(CellConfigMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166);
        this.titleLabelX = 8;
        this.inventoryLabelX = 8;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;
        extractor.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        extractor.fill(x, y, x + w, y + h, PANEL);
        int index = 0;
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            boolean ghost = index < CellConfigMenu.PARTITION;
            extractor.fill(sx - 1, sy - 1, sx + 17, sy + 17, ghost ? GHOST_EDGE : WELL_EDGE);
            extractor.fill(sx, sy, sx + 16, sy + 16, ghost ? GHOST : WELL);
            index++;
        }
        for (int i = 0; i < BUTTON_LABELS.length; i++) {
            int bx = x + BX + i * (BW + 2);
            int by = y + BY;
            extractor.fill(bx - 1, by - 1, bx + BW + 1, by + BH + 1, BUTTON_EDGE);
            extractor.fill(bx, by, bx + BW, by + BH, BUTTON_BG);
            Component label = Component.literal(BUTTON_LABELS[i]);
            int tw = this.font.width(label);
            extractor.text(this.font, label, bx + (BW - tw) / 2, by + 2, BUTTON_TEXT, false);
        }
        super.extractContents(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        extractor.text(this.font,
                Component.translatable("gui.nerologistics.cell_config.priority", this.menu.priority()),
                BX, 28, TITLE, false);
        extractor.text(this.font, Component.translatable("gui.nerologistics.cell_config.hint"),
                this.titleLabelX, 72, SUBTLE, false);
        extractor.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, SUBTLE, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        double mx = mouseButtonEvent.x();
        double my = mouseButtonEvent.y();
        for (int i = 0; i < BUTTON_LABELS.length; i++) {
            int bx = this.leftPos + BX + i * (BW + 2);
            int by = this.topPos + BY;
            if (mx >= bx && mx < bx + BW && my >= by && my < by + BH) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, i);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick);
    }
}
