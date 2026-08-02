package za.co.neroland.nerologistics.registry;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.nerologistics.storage.CellSettings;
import za.co.neroland.nerologistics.storage.FluidCellContents;
import za.co.neroland.nerologistics.storage.ItemCellContents;

/**
 * NeroLogistics' item data components, registered cross-loader through the
 * {@link RegistrationProvider} seam over the vanilla data-component registry — 26.x's replacement
 * for ad-hoc stack NBT (pattern mirrors Nerotech's {@code ModDataComponents}). The network stream
 * codec is derived from the persistent codec by the builder, so components sync with the stack
 * automatically (tooltips read them client-side).
 *
 * <p>All three components are storage-cell state: contents + partition/priority. Block/network-
 * scoped resource data only — no player data (POPIA/GDPR).</p>
 */
public final class ModDataComponents {

    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroLogisticsCommon.MOD_ID);

    /** Item-cell inventory: exact item+components lines with counts. */
    public static final RegistryEntry<DataComponentType<ItemCellContents>> ITEM_CELL_CONTENTS =
            COMPONENTS.register("item_cell_contents", key -> DataComponentType.<ItemCellContents>builder()
                    .persistent(ItemCellContents.CODEC)
                    .build());

    /** Fluid-cell inventory: fluid-id lines with mB amounts. */
    public static final RegistryEntry<DataComponentType<FluidCellContents>> FLUID_CELL_CONTENTS =
            COMPONENTS.register("fluid_cell_contents", key -> DataComponentType.<FluidCellContents>builder()
                    .persistent(FluidCellContents.CODEC)
                    .build());

    /** Cell partition (9 ghost stacks) + signed priority. */
    public static final RegistryEntry<DataComponentType<CellSettings>> CELL_SETTINGS =
            COMPONENTS.register("cell_settings", key -> DataComponentType.<CellSettings>builder()
                    .persistent(CellSettings.CODEC)
                    .build());

    /** Wireless terminal binding: the bound network controller's dimension + position. */
    public static final RegistryEntry<DataComponentType<GlobalPos>> WIRELESS_TARGET =
            COMPONENTS.register("wireless_target", key -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .build());

    private ModDataComponents() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}
