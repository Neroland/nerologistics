package za.co.neroland.nerologistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.entity.DeliveryDroneEntity;

/**
 * Renders the delivery drone via the 26.x submit pipeline: a {@link DeliveryDroneModel} baked directly
 * from its layer (no model-layer registry), with the rotor cross spun on the entity's age.
 */
public class DeliveryDroneRenderer extends EntityRenderer<DeliveryDroneEntity, DeliveryDroneRenderState> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "textures/entity/delivery_drone.png");
    private static final int FULL_BRIGHT = 0x00F000F0;

    private final DeliveryDroneModel model;

    public DeliveryDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DeliveryDroneModel(DeliveryDroneModel.createBodyLayer().bakeRoot());
    }

    @Override
    public DeliveryDroneRenderState createRenderState() {
        return new DeliveryDroneRenderState();
    }

    @Override
    public void extractRenderState(DeliveryDroneEntity drone, DeliveryDroneRenderState state, float partialTick) {
        super.extractRenderState(drone, state, partialTick);
        state.ticks = drone.tickCount + partialTick;
    }

    @Override
    public void submit(DeliveryDroneRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        poseStack.pushPose();
        // Into model space, drop to ground level, and spin the rotor cross on age.
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.ticks * 12.0F));

        RenderType renderType = this.model.renderType(TEXTURE);
        collector.order(0).submitModel(this.model, state, poseStack, renderType,
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, null, 0, null);

        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
    }
}
