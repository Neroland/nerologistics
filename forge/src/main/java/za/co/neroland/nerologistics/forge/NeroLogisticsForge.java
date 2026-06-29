package za.co.neroland.nerologistics.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.registry.ForgeRegistrationFactory;
import za.co.neroland.nerologistics.ship.ShipmentManager;
import za.co.neroland.nerologistics.telemetry.NeroLogisticsTelemetry;

/** MinecraftForge entry point for NeroLogistics. */
@Mod(NeroLogisticsCommon.MOD_ID)
public final class NeroLogisticsForge {

    public NeroLogisticsForge(FMLJavaModLoadingContext context) {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Forge bootstrap");
        BusGroup modBusGroup = context.getModBusGroup();
        // Shared init builds the DeferredRegisters via the RegistrationProvider seam;
        // attach them to NeroLogistics' mod bus group.
        NeroLogisticsCommon.init();
        ForgeRegistrationFactory.registerAll(modBusGroup);
        NeroLogisticsForgeCapabilities.register();
        // Anonymous, NeroLogistics-only crash reporting (opt-out via config; off in dev unless DSN set).
        NeroLogisticsTelemetry.init();
        // Drive cross-dimension shipment arrivals once per server tick.
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ShipmentManager.tick(event.server()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientSetup.init(modBusGroup);
        }
    }
}
