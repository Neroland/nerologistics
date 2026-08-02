package za.co.neroland.nerologistics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.menu.AutoCrafterMenu;
import za.co.neroland.nerologistics.menu.BufferMenu;
import za.co.neroland.nerologistics.menu.CellConfigMenu;
import za.co.neroland.nerologistics.menu.DriveBayMenu;
import za.co.neroland.nerologistics.menu.DronePortMenu;
import za.co.neroland.nerologistics.menu.FilterMenu;
import za.co.neroland.nerologistics.menu.LogisticsProcessorMenu;
import za.co.neroland.nerologistics.menu.StorageRequestMenu;
import za.co.neroland.nerologistics.menu.StorageTerminalMenu;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;

/** Container menu types for NeroLogistics terminals, registered cross-loader via {@link RegistrationProvider}. */
public final class ModMenuTypes {

    public static final RegistrationProvider<MenuType<?>> MENUS =
            RegistrationProvider.get(Registries.MENU, NeroLogisticsCommon.MOD_ID);

    public static final RegistryEntry<MenuType<StorageRequestMenu>> STORAGE_REQUEST =
            MENUS.register("storage_request",
                    key -> new MenuType<>(StorageRequestMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<MenuType<FilterMenu>> FILTER =
            MENUS.register("filter", key -> new MenuType<>(FilterMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<MenuType<AutoCrafterMenu>> AUTO_CRAFTER =
            MENUS.register("auto_crafter", key -> new MenuType<>(AutoCrafterMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<MenuType<BufferMenu>> BUFFER =
            MENUS.register("buffer", key -> new MenuType<>(BufferMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<MenuType<DronePortMenu>> DRONE_PORT =
            MENUS.register("drone_port", key -> new MenuType<>(DronePortMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<MenuType<DriveBayMenu>> DRIVE_BAY =
            MENUS.register("drive_bay", key -> new MenuType<>(DriveBayMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<MenuType<CellConfigMenu>> CELL_CONFIG =
            MENUS.register("cell_config", key -> new MenuType<>(CellConfigMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<MenuType<StorageTerminalMenu>> STORAGE_TERMINAL =
            MENUS.register("storage_terminal",
                    key -> new MenuType<>(StorageTerminalMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<MenuType<LogisticsProcessorMenu>> LOGISTICS_PROCESSOR =
            MENUS.register("logistics_processor",
                    key -> new MenuType<>(LogisticsProcessorMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenuTypes() {
    }

    public static void init() {
    }
}
