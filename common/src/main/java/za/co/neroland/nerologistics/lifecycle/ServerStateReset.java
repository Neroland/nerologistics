package za.co.neroland.nerologistics.lifecycle;

import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.network.DronePortRegistry;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.TrainStationRegistry;
import za.co.neroland.nerologistics.network.WirelessRegistry;
import za.co.neroland.nerologistics.ship.ShipmentManager;
import za.co.neroland.nerologistics.world.SavedDataRecovery;

/**
 * One entry point that clears every common static server-scoped cache when a server stops, invoked
 * from each loader's server-stopped hook (mirrors the NeroAgriculture {@code ServerStateReset}
 * pattern). Without this the JVM-lifetime statics survive world unload — conduit networks, wireless
 * channels, drone-port/train-station directories and shipment ports from the previous world bleed
 * into the next single-player world, and {@code LogisticsMetrics} keeps stale counters plus
 * player-keyed attribution (a POPIA/GDPR data-minimisation problem) pinned in memory.
 *
 * <p>Everything cleared here is a lazily-rebuilt cache: block entities re-register on their first
 * server tick after the next world load, so no durable state is lost. Durable state (in-transit
 * shipments, erased-owner tombstones) lives in SavedData and is untouched.
 */
public final class ServerStateReset {

    private ServerStateReset() {
    }

    /** Clear all server-scoped static state; loaders fire it on the server thread after stop. */
    public static void serverStopped() {
        NetworkManager.clearAll();
        WirelessRegistry.clearAll();
        DronePortRegistry.clearAll();
        TrainStationRegistry.clearAll();
        ShipmentManager.clearAll();
        LogisticsMetrics.clearAll();
        RocketCargoPortBlockEntity.clearAll();
        SavedDataRecovery.clearAll();
    }
}
