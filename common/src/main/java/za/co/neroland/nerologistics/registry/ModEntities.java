package za.co.neroland.nerologistics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.entity.DeliveryDroneEntity;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;

/** Entity types for NeroLogistics, registered cross-loader via {@link RegistrationProvider}. */
public final class ModEntities {

    public static final RegistrationProvider<EntityType<?>> ENTITY_TYPES =
            RegistrationProvider.get(Registries.ENTITY_TYPE, NeroLogisticsCommon.MOD_ID);

    public static final RegistryEntry<EntityType<DeliveryDroneEntity>> DELIVERY_DRONE = ENTITY_TYPES.register(
            "delivery_drone",
            key -> EntityType.Builder.<DeliveryDroneEntity>of(DeliveryDroneEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .build(key));

    private ModEntities() {
    }

    public static void init() {
    }
}
