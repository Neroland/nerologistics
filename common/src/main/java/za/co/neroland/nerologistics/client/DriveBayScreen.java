package za.co.neroland.nerologistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerologistics.menu.DriveBayMenu;
import za.co.neroland.nerologistics.storage.DriveBayBlockEntity;

/**
 * Drive Bay screen — a procedural dark-hull panel (no texture asset), mirroring the other
 * NeroLogistics screens. The six bay wells are tinted like {@link CellConfigScreen} ghost wells to
 * read as digital slots; a hint line reminds the player to configure cells by sneak-using them.
 */
public class DriveBayScreen extends AbstractContainerScreen<DriveBayMenu> {

    private static final int PANEL = 0xFF11161D;
    private static final int EDGE = 0xFF05080D;
    private static final int WELL = 0xFF8B8B8B;
    private static final int WELL_EDGE = 0xFF373737;
    private static final int BAY = 0xFF223041;
    private static final int BAY_EDGE = 0xFF18202A;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;

    public DriveBayScreen(DriveBayMenu menu, Inventory playerInventory, Component title) {
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
            boolean bay = index < DriveBayBlockEntity.BAYS;
            extractor.fill(sx - 1, sy - 1, sx + 17, sy + 17, bay ? BAY_EDGE : WELL_EDGE);
            extractor.fill(sx, sy, sx + 16, sy + 16, bay ? BAY : WELL);
            index++;
        }
        super.extractContents(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        extractor.text(this.font, Component.translatable("gui.nerologistics.drive_bay.hint"),
                this.titleLabelX, 46, SUBTLE, false);
        extractor.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, SUBTLE, false);
    }
}
