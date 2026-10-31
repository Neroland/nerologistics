package za.co.neroland.nerologistics.client;

//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?}

/** Render state for the delivery drone. Carries the age for rotor spin animation. */
//? if >=1.21.2 {
public class DeliveryDroneRenderState extends EntityRenderState {
//?} else {
/*// 1.21.1 has no entity render states; the renderer reads the entity directly there.
public class DeliveryDroneRenderState {
*///?}
    public float ticks;
}
