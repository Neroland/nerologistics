package za.co.neroland.nerologistics.client.compat;

// Minecraft before 1.21.9 only (empty on every later line). Key-press twin of compat.MouseButtonEvent: the
// raw key code, scan code and modifiers before 1.21.9 wrapped in the shape of 1.21.9's KeyEvent.

//? if <1.21.9 {
/*import org.lwjgl.glfw.GLFW;

public record KeyEvent(int key, int scancode, int modifiers) {

    public boolean isEscape() {
        return key == GLFW.GLFW_KEY_ESCAPE;
    }

    public boolean hasShiftDown() {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    public boolean hasControlDown() {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }
}
*///?}
