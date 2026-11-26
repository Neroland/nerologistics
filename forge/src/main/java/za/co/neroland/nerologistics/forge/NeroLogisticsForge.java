package za.co.neroland.nerologistics.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else {
/*import net.minecraftforge.eventbus.api.IEventBus;
*///?}
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.command.NeroLogisticsCommands;
import za.co.neroland.nerologistics.lifecycle.ServerStateReset;
import za.co.neroland.nerologistics.link.NeroLogisticsLinkModule;
import za.co.neroland.nerologistics.registry.ForgeRegistrationFactory;
import za.co.neroland.nerologistics.ship.ShipmentManager;
import za.co.neroland.nerologistics.telemetry.NeroLogisticsTelemetry;

/** MinecraftForge entry point for NeroLogistics. */
@Mod(NeroLogisticsCommon.MOD_ID)
public final class NeroLogisticsForge {

    //? if >=1.20.5 {
    public NeroLogisticsForge(FMLJavaModLoadingContext context) {
    //?} else {
    /*public NeroLogisticsForge() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
    *///?}
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Forge bootstrap");
        // Forge before 1.21.6 runs EventBus 6: one mod event bus, and game events go through MinecraftForge.EVENT_BUS.
        //? if >=1.21.6 {
        BusGroup modBusGroup = context.getModBusGroup();
        //?} else {
        /*IEventBus modBusGroup = context.getModEventBus();
        *///?}
        // Shared init builds the DeferredRegisters via the RegistrationProvider seam;
        // attach them to NeroLogistics' mod bus group.
        NeroLogisticsCommon.init();
        ForgeRegistrationFactory.registerAll(modBusGroup);
        NeroLogisticsForgeCapabilities.register();
        // NeroLogistics' own payloads (storage-terminal sync); see network.NeroLogisticsNetwork.
        ForgeNetwork.register();
        // Anonymous, NeroLogistics-only crash reporting (opt-out via config; off in dev unless DSN set).
        NeroLogisticsTelemetry.init();
        // Drive cross-dimension shipment arrivals once per server tick, and capture the running server
        // for the NeroLink module (Core's SPI hands a provider nothing but a player UUID).
        //? if >=1.21.9 {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> {
            NeroLogisticsLinkModule.rememberServer(event.server());
            ShipmentManager.tick(event.server());
        });
        //?}
        //? if >=1.21.6 && <1.21.9 {
        /*TickEvent.ServerTickEvent.Post.BUS.addListener(event -> {
            NeroLogisticsLinkModule.rememberServer(event.getServer());
            ShipmentManager.tick(event.getServer());
        });
        *///?}
        //? if >=1.21.6 {
        // Clear the common server-scoped static caches so nothing leaks into the next (single-player) world.
        ServerStoppedEvent.BUS.addListener(event -> ServerStateReset.serverStopped());
        // /nerologistics gallery
        RegisterCommandsEvent.BUS.addListener(event -> NeroLogisticsCommands.register(event.getDispatcher()));
        //?} else {
        /*registerLegacyTick();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
                ServerStateReset.serverStopped());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                NeroLogisticsCommands.register(event.getDispatcher()));
        *///?}
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientSetup.init(modBusGroup);
        }
    }

    //? if <1.21.6 {
    /*private static void registerLegacyTick() {
        //? if >=1.20.6 {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> tick(event.getServer()));
        //?} else {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                tick(event.getServer());
            }
        });
        //?}
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        NeroLogisticsLinkModule.rememberServer(server);
        ShipmentManager.tick(server);
    }
    *///?}
}
