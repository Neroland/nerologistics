package za.co.neroland.nerologistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerologistics.menu.BufferMenu;

/**
 * Buffer screen — procedural dark-hull panel. The single ghost target well sits top-left; the buffer row
 * below it. A hint line reminds the player that sneak-clicking the block toggles keep-stocked vs passive.
 */
public class BufferScreen extends AbstractContainerScreen<BufferMenu> {

    private static final int PANEL = 0xFF11161D;
    private static final int EDGE = 0xFF05080D;
    private static final int WELL = 0xFF8B8B8B;
    private static final int WELL_EDGE = 0xFF373737;
    private static final int GHOST = 0xFF2A3340;
    private static final int GHOST_EDGE = 0xFF18202A;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;

    public BufferScreen(BufferMenu menu, Inventory playerInventory, Component title) {
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
            boolean ghost = index == 0;
            extractor.fill(sx - 1, sy - 1, sx + 17, sy + 17, ghost ? GHOST_EDGE : WELL_EDGE);
            extractor.fill(sx, sy, sx + 16, sy + 16, ghost ? GHOST : WELL);
            index++;
        }
        super.extractContents(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        extractor.text(this.font, Component.translatable("block.nerologistics.buffer.hint"),
                30, 24, SUBTLE, false);
        extractor.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, SUBTLE, false);
    }
}
