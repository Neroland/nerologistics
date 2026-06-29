package za.co.neroland.nerologistics.registry;

import java.util.function.Function;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.EnergyCableBlock;
import za.co.neroland.nerologistics.conduit.FluidDuctBlock;
import za.co.neroland.nerologistics.conduit.ItemDuctBlock;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;

/** NeroLogistics conduit blocks, registered cross-loader via {@link RegistrationProvider}. */
public final class ModBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroLogisticsCommon.MOD_ID);

    public static final RegistryEntry<ItemDuctBlock> ITEM_DUCT =
            register("item_duct", ItemDuctBlock::new);
    public static final RegistryEntry<FluidDuctBlock> FLUID_DUCT =
            register("fluid_duct", FluidDuctBlock::new);
    public static final RegistryEntry<EnergyCableBlock> ENERGY_CABLE =
            register("energy_cable", EnergyCableBlock::new);

    private static <B extends Block> RegistryEntry<B> register(String name,
            Function<BlockBehaviour.Properties, B> factory) {
        return BLOCKS.register(name, key -> factory.apply(conduitProperties().setId(key)));
    }

    private static BlockBehaviour.Properties conduitProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(1.5F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    private ModBlocks() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}
