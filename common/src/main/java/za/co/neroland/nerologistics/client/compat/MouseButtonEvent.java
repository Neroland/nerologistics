package za.co.neroland.nerologistics.client.compat;

// Minecraft before 1.21.9 only (empty on every later line). 1.21.9 hands screens a MouseButtonEvent; before
// it mouseClicked takes the raw coordinates and button, which the screens wrap in this look-alike so their
// hit tests read the same. The build points net.minecraft.client.input.MouseButtonEvent here on those lines.

//? if <1.21.9 {
/*import net.minecraft.client.gui.screens.Screen;

public record MouseButtonEvent(double x, double y, int button) {

    public boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }

    public boolean hasControlDown() {
        return Screen.hasControlDown();
    }
}
*///?}
