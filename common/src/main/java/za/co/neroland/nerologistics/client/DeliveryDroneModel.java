package za.co.neroland.nerologistics.client;

//? if >=1.21.2 {
import net.minecraft.client.model.EntityModel;
//?}
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?}
import net.minecraft.resources.Identifier;

import za.co.neroland.nerologistics.NeroLogisticsCommon;

/**
 * A small quadcopter-style delivery drone: a flat body, a slung cargo box, and a cross of rotor arms.
 * Built with the 26.x {@code LayerDefinition} mesh API and (per the cross-loader convention) baked
 * directly from {@code createBodyLayer().bakeRoot()} by the renderer, so no model-layer registry is
 * required.
 */
//? if >=1.21.2 {
public class DeliveryDroneModel extends EntityModel<EntityRenderState> {
//?} else {
/*// 1.21.1 entity models are typed by entity and keep their own root.
public class DeliveryDroneModel extends net.minecraft.client.model.HierarchicalModel<za.co.neroland.nerologistics.entity.DeliveryDroneEntity> {
*///?}

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "delivery_drone"), "main");

    //? if >=1.21.2 {
    public DeliveryDroneModel(ModelPart root) {
        super(root);
    }
    //?} else {
    /*private final ModelPart root;

    public DeliveryDroneModel(ModelPart root) {
        this.root = root;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(za.co.neroland.nerologistics.entity.DeliveryDroneEntity drone, float limbSwing,
            float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }
    *///?}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -2F, -4F, 8F, 3F, 8F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("cargo",
                CubeListBuilder.create().texOffs(0, 11).addBox(-3F, 1F, -3F, 6F, 4F, 6F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("rotors",
                CubeListBuilder.create().texOffs(28, 0).addBox(-7F, -3F, -7F, 14F, 1F, 14F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
