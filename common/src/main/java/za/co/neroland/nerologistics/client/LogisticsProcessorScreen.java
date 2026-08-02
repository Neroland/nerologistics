package za.co.neroland.nerologistics.client;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerologistics.conduit.LogisticsProcessorBlockEntity.RuleStatus;
import za.co.neroland.nerologistics.menu.LogisticsProcessorMenu;

/**
 * Logistics-processor screen — the shared procedural dark-hull panel. Eight rule rows, each: the
 * ghost item well, a status dot (hover for the reason), a BELOW/ABOVE comparator toggle, −/+
 * threshold buttons (shift ×10, ctrl ×100, shift+ctrl ×1000 — the multiplier is resolved here and
 * sent as a distinct button op), the threshold value, an action cycle button and an on/off toggle.
 * All clicks ride {@code handleInventoryButtonClick} like every other NeroLogistics menu.
 */
public class LogisticsProcessorScreen extends AbstractContainerScreen<LogisticsProcessorMenu> {

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
    private static final int BUTTON_TEXT_DIM = 0xFF5E7189;

    private static final int DOT_OK = 0xFF3FBF6F;
    private static final int DOT_IDLE = 0xFF4A709C;
    private static final int DOT_WARN = 0xFFE0A33A;
    private static final int DOT_OFF = 0xFF444C57;

    private static final int RULES = LogisticsProcessorMenu.RULES;
    private static final int ROW_Y = LogisticsProcessorMenu.ROW_Y;
    private static final int ROW_H = LogisticsProcessorMenu.ROW_H;

    /** Row widget geometry (x, width; y is row-relative +3, height 12). */
    private static final int DOT_X = 27;
    private static final int CMP_X = 36;
    private static final int CMP_W = 16;
    private static final int MINUS_X = 55;
    private static final int STEP_W = 10;
    private static final int VALUE_X = 67;
    private static final int VALUE_W = 41;
    private static final int PLUS_X = 110;
    private static final int ACT_X = 123;
    private static final int ACT_W = 33;
    private static final int ON_X = 159;
    private static final int ON_W = 9;
    private static final int BTN_Y = 3;
    private static final int BTN_H = 12;

    public LogisticsProcessorScreen(LogisticsProcessorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 260);
        this.titleLabelX = 8;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 166;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        extractor.fill(x - 1, y - 1, x + this.imageWidth + 1, y + this.imageHeight + 1, EDGE);
        extractor.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        // Slot wells: rule ghosts tinted, player inventory plain.
        int index = 0;
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            boolean ghost = index < RULES;
            extractor.fill(sx - 1, sy - 1, sx + 17, sy + 17, ghost ? GHOST_EDGE : WELL_EDGE);
            extractor.fill(sx, sy, sx + 16, sy + 16, ghost ? GHOST : WELL);
            index++;
        }
        for (int rule = 0; rule < RULES; rule++) {
            extractRuleRow(extractor, x, y + ROW_Y + rule * ROW_H, rule);
        }
        super.extractContents(extractor, mouseX, mouseY, partialTick);
        extractStatusTooltip(extractor, mouseX, mouseY);
    }

    private void extractRuleRow(GuiGraphicsExtractor extractor, int x, int rowY, int rule) {
        boolean on = this.menu.ruleEnabled(rule);
        int textColor = on ? BUTTON_TEXT : BUTTON_TEXT_DIM;
        // Status dot.
        extractor.fill(x + DOT_X, rowY + 5, x + DOT_X + 6, rowY + 11, dotColor(this.menu.ruleStatus(rule)));
        // Comparator toggle.
        button(extractor, x + CMP_X, rowY + BTN_Y, CMP_W,
                Component.literal(this.menu.ruleAbove(rule) ? ">" : "<"), textColor);
        // Threshold − / value / +.
        button(extractor, x + MINUS_X, rowY + BTN_Y, STEP_W, Component.literal("-"), textColor);
        String value = String.format(Locale.ROOT, "%,d", this.menu.ruleThreshold(rule));
        int vw = this.font.width(value);
        extractor.text(this.font, Component.literal(value),
                x + VALUE_X + Math.max(0, (VALUE_W - vw) / 2), rowY + 5, textColor, false);
        button(extractor, x + PLUS_X, rowY + BTN_Y, STEP_W, Component.literal("+"), textColor);
        // Action cycle button (short label).
        button(extractor, x + ACT_X, rowY + BTN_Y, ACT_W, Component.translatable(
                "gui.nerologistics.logistics_processor.action_short."
                        + this.menu.ruleAction(rule).name().toLowerCase(Locale.ROOT)), textColor);
        // Enabled toggle.
        button(extractor, x + ON_X, rowY + BTN_Y, ON_W, Component.literal(on ? "I" : "O"),
                on ? BUTTON_TEXT : BUTTON_TEXT_DIM);
    }

    private void button(GuiGraphicsExtractor extractor, int bx, int by, int bw, Component label, int color) {
        extractor.fill(bx - 1, by - 1, bx + bw + 1, by + BTN_H + 1, BUTTON_EDGE);
        extractor.fill(bx, by, bx + bw, by + BTN_H, BUTTON_BG);
        int tw = this.font.width(label);
        extractor.text(this.font, label, bx + Math.max(0, (bw - tw) / 2), by + 2, color, false);
    }

    private static int dotColor(RuleStatus status) {
        return switch (status) {
            case ACTED -> DOT_OK;
            case IDLE -> DOT_IDLE;
            case DISABLED -> DOT_OFF;
            default -> DOT_WARN;
        };
    }

    private void extractStatusTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int rule = hoveredStatusRule(mouseX, mouseY);
        if (rule < 0) {
            return;
        }
        RuleStatus status = this.menu.ruleStatus(rule);
        extractor.setComponentTooltipForNextFrame(this.font,
                java.util.List.of(
                        Component.translatable("gui.nerologistics.logistics_processor.status."
                                + status.name().toLowerCase(Locale.ROOT)),
                        Component.translatable("gui.nerologistics.logistics_processor.action."
                                + this.menu.ruleAction(rule).name().toLowerCase(Locale.ROOT))
                                .withStyle(ChatFormatting.GRAY)),
                mouseX, mouseY);
    }

    /** The rule whose status dot is hovered, or −1. */
    private int hoveredStatusRule(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        if (localX < DOT_X || localX >= DOT_X + 6) {
            return -1;
        }
        for (int rule = 0; rule < RULES; rule++) {
            int rowY = ROW_Y + rule * ROW_H;
            if (localY >= rowY + 5 && localY < rowY + 11) {
                return rule;
            }
        }
        return -1;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE, false);
        extractor.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, SUBTLE, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        double mx = mouseButtonEvent.x();
        double my = mouseButtonEvent.y();
        for (int rule = 0; rule < RULES; rule++) {
            int rowY = this.topPos + ROW_Y + rule * ROW_H + BTN_Y;
            if (my < rowY || my >= rowY + BTN_H) {
                continue;
            }
            int op = -1;
            if (hit(mx, CMP_X, CMP_W)) {
                op = LogisticsProcessorMenu.OP_TOGGLE_COMPARATOR;
            } else if (hit(mx, MINUS_X, STEP_W)) {
                op = LogisticsProcessorMenu.OP_THRESHOLD_DOWN + stepExponent(mouseButtonEvent);
            } else if (hit(mx, PLUS_X, STEP_W)) {
                op = LogisticsProcessorMenu.OP_THRESHOLD_UP + stepExponent(mouseButtonEvent);
            } else if (hit(mx, ACT_X, ACT_W)) {
                op = LogisticsProcessorMenu.OP_CYCLE_ACTION;
            } else if (hit(mx, ON_X, ON_W)) {
                op = LogisticsProcessorMenu.OP_TOGGLE_ENABLED;
            }
            if (op >= 0) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, rule * 100 + op);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick);
    }

    private boolean hit(double mx, int bx, int bw) {
        return mx >= this.leftPos + bx && mx < this.leftPos + bx + bw;
    }

    /** ±1 by default; shift ×10, ctrl ×100, shift+ctrl ×1000. */
    private static int stepExponent(MouseButtonEvent event) {
        boolean shift = event.hasShiftDown();
        boolean ctrl = event.hasControlDown();
        if (shift && ctrl) {
            return 3;
        }
        if (ctrl) {
            return 2;
        }
        return shift ? 1 : 0;
    }
}
