package za.co.neroland.nerologistics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroLogistics' own dedicated creative tab, registered cross-loader via {@link RegistrationProvider}
 * (mirroring Nerotech/Nerospace — each mod gets its own tab rather than pouring into Core's shared one).
 */
public final class ModCreativeTab {

    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, NeroLogisticsCommon.MOD_ID);

    public static final RegistryEntry<CreativeModeTab> NEROLOGISTICS = TABS.register("nerologistics",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.nerologistics"))
                    .icon(() -> new ItemStack(ModItems.ITEM_DUCT_ITEM.get()))
                    .displayItems((params, output) -> ModItems.creativeContents().forEach(output::accept))
                    .build());

    private ModCreativeTab() {
    }

    public static void init() {
    }
}
