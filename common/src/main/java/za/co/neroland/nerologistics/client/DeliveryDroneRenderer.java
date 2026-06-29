package za.co.neroland.nerologistics.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import za.co.neroland.nerologistics.entity.DeliveryDroneEntity;

/**
 * Placeholder renderer for the delivery drone: a valid renderer (so the entity registers and spawns
 * without crashing) that submits no model — the drone is functional but currently invisible. A proper
 * model + texture is a follow-up; the 26.x submit pipeline ({@code submitModel}) is where it goes.
 */
public class DeliveryDroneRenderer extends EntityRenderer<DeliveryDroneEntity, DeliveryDroneRenderState> {

    public DeliveryDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public DeliveryDroneRenderState createRenderState() {
        return new DeliveryDroneRenderState();
    }

    @Override
    public void submit(DeliveryDroneRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        super.submit(state, poseStack, collector, cameraState);
    }
}
