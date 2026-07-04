package za.co.neroland.nerologistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerologistics.menu.DronePortMenu;

/**
 * Drone-port screen — procedural dark-hull panel. Drone and upgrade slots are tinted to read as
 * configuration wells; the cargo row uses plain wells. A hint reminds the player that sneak-clicking the
 * block toggles import/export.
 */
public class DronePortScreen extends AbstractContainerScreen<DronePortMenu> {

    private static final int PANEL = 0xFF11161D;
    private static final int EDGE = 0xFF05080D;
    private static final int WELL = 0xFF8B8B8B;
    private static final int WELL_EDGE = 0xFF373737;
    private static final int CONFIG = 0xFF2A3340;
    private static final int CONFIG_EDGE = 0xFF18202A;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;

    /** Slots 9–15 (after the 9 cargo slots) are the drone + upgrade configuration wells. */
    private static final int CONFIG_START = 9;
    private static final int CONFIG_END = 16;

    public DronePortScreen(DronePortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 180);
        this.titleLabelX = 8;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
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
            boolean config = index >= CONFIG_START && index < CONFIG_END;
            extractor.fill(sx - 1, sy - 1, sx + 17, sy + 17, config ? CONFIG_EDGE : WELL_EDGE);
            extractor.fill(sx, sy, sx + 16, sy + 16, config ? CONFIG : WELL);
            index++;
        }
        super.extractContents(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        extractor.text(this.font, Component.translatable("block.nerologistics.drone_port.hint"),
                8, 36, SUBTLE, false);
        extractor.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, SUBTLE, false);
    }
}
