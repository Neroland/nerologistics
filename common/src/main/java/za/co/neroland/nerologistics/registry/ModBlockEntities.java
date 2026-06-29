package za.co.neroland.nerologistics.registry;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.EnergyCableBlockEntity;
import za.co.neroland.nerologistics.conduit.FluidDuctBlockEntity;
import za.co.neroland.nerologistics.conduit.ItemDuctBlockEntity;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;

/** Block-entity types for NeroLogistics conduits, registered cross-loader via {@link RegistrationProvider}. */
public final class ModBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroLogisticsCommon.MOD_ID);

    public static final RegistryEntry<BlockEntityType<ItemDuctBlockEntity>> ITEM_DUCT =
            BLOCK_ENTITIES.register("item_duct",
                    key -> new BlockEntityType<>(ItemDuctBlockEntity::new, Set.of(ModBlocks.ITEM_DUCT.get())));

    public static final RegistryEntry<BlockEntityType<FluidDuctBlockEntity>> FLUID_DUCT =
            BLOCK_ENTITIES.register("fluid_duct",
                    key -> new BlockEntityType<>(FluidDuctBlockEntity::new, Set.of(ModBlocks.FLUID_DUCT.get())));

    public static final RegistryEntry<BlockEntityType<EnergyCableBlockEntity>> ENERGY_CABLE =
            BLOCK_ENTITIES.register("energy_cable",
                    key -> new BlockEntityType<>(EnergyCableBlockEntity::new, Set.of(ModBlocks.ENERGY_CABLE.get())));

    private ModBlockEntities() {
    }

    public static void init() {
    }
}
