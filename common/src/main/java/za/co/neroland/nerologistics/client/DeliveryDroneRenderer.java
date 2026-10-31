package za.co.neroland.nerologistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=1.21.9 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?}
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.entity.DeliveryDroneEntity;

/**
 * Renders the delivery drone via the 26.x submit pipeline: a {@link DeliveryDroneModel} baked directly
 * from its layer (no model-layer registry), with the rotor cross spun on the entity's age.
 */
//? if >=1.21.2 {
public class DeliveryDroneRenderer extends EntityRenderer<DeliveryDroneEntity, DeliveryDroneRenderState> {
//?} else {
/*public class DeliveryDroneRenderer extends EntityRenderer<DeliveryDroneEntity> {
*///?}

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "textures/entity/delivery_drone.png");
    private static final int FULL_BRIGHT = 0x00F000F0;

    private final DeliveryDroneModel model;

    public DeliveryDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DeliveryDroneModel(DeliveryDroneModel.createBodyLayer().bakeRoot());
    }

    //? if >=1.21.2 {
    @Override
    public DeliveryDroneRenderState createRenderState() {
        return new DeliveryDroneRenderState();
    }

    @Override
    public void extractRenderState(DeliveryDroneEntity drone, DeliveryDroneRenderState state, float partialTick) {
        super.extractRenderState(drone, state, partialTick);
        state.ticks = drone.tickCount + partialTick;
    }
    //?}

    //? if >=26.3 {
    /*@Override
    public void submit(DeliveryDroneRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        poseStack.pushPose();
        // Into model space, drop to ground level, and spin the rotor cross on age.
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);
        poseStack.rotate(Axis.YP.rotationDegrees(state.ticks * 12.0F));

        RenderType renderType = this.model.renderType(TEXTURE);
        collector.order(0).submitModel(this.model, state, poseStack, renderType, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);

        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
    }
    *///?}
    //? if >=1.21.9 && <26.3 {
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
    //?}
    //? if >=1.21.2 && <1.21.9 {
    /*// Before 1.21.9 there is no submit pipeline: the renderer draws straight into the buffer source.
    @Override
    public void render(DeliveryDroneRenderState state, PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.ticks * 12.0F));
        this.model.renderToBuffer(poseStack, buffers.getBuffer(this.model.renderType(TEXTURE)),
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(state, poseStack, buffers, light);
    }
    *///?}
    //? if <1.21.2 {
    /*// 1.21.1 renders from the entity itself (no render states yet).
    @Override
    public void render(DeliveryDroneEntity drone, float entityYaw, float partialTick, PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees((drone.tickCount + partialTick) * 12.0F));
        this.model.renderToBuffer(poseStack, buffers.getBuffer(this.model.renderType(TEXTURE)),
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        poseStack.popPose();
        super.render(drone, entityYaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public Identifier getTextureLocation(DeliveryDroneEntity drone) {
        return TEXTURE;
    }
    *///?}
}
