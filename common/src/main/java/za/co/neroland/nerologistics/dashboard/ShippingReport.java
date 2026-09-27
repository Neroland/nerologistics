package za.co.neroland.nerologistics.dashboard;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;
import za.co.neroland.nerologistics.ship.FlightView;
import za.co.neroland.nerologistics.ship.RouteProvider;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShipDenial;

/**
 * The shipping section shared by the Logistics Dashboard and {@code /nerologistics shipping}: in-transit
 * shipments with state and ETA, rocket cargo ports with their last refusal, and recently crated cargo.
 *
 * <p><b>Proximity-scoped</b> ({@value #RADIUS} blocks in the viewer's dimension), like the link module —
 * never a server-wide roster. Aggregate world data only: pad names and dimension paths, no player names or
 * UUIDs, and a crate position only for a pad the viewer may route to anyway. Fully useful with per-player
 * attribution OFF.</p>
 */
public final class ShippingReport {

    /** Report radius in blocks (matches the link module's proximity scope). */
    public static final int RADIUS = 128;
    /** Lines per section, so a busy hub does not flood chat. */
    private static final int MAX_LINES = 8;

    private ShippingReport() {
    }

    public static void send(ServerLevel level, BlockPos centre, ServerPlayer viewer) {
        MinecraftServer server = level.getServer();
        RouteProvider provider = RouteProviders.get();

        List<FlightView> flights = provider.flightsNear(server, level.dimension(), centre, RADIUS);
        viewer.sendSystemMessage(Component.translatable("nerologistics.ship.report.flights", flights.size(), RADIUS));
        for (int i = 0; i < Math.min(MAX_LINES, flights.size()); i++) {
            viewer.sendSystemMessage(Component.literal("  ").append(flights.get(i).describe()));
        }
        more(viewer, flights.size());

        List<RocketCargoPortBlockEntity> ports = RocketCargoPortBlockEntity.loadedNear(level, centre, RADIUS);
        int shown = 0;
        int stalled = 0;
        for (RocketCargoPortBlockEntity port : ports) {
            ShipDenial denial = port.lastDenial();
            if (denial == null) {
                continue;
            }
            stalled++;
            if (shown < MAX_LINES) {
                BlockPos p = port.getBlockPos();
                viewer.sendSystemMessage(Component.translatable("nerologistics.ship.report.port_denied",
                        p.getX(), p.getY(), p.getZ(), Component.translatable(denial.translationKey())));
                shown++;
            }
        }
        viewer.sendSystemMessage(Component.translatable("nerologistics.ship.report.ports", ports.size(), stalled));

        List<RouteProvider.DropHint> drops = provider.recentDropsNear(server, level.dimension(), centre, RADIUS,
                viewer.getUUID());
        for (int i = Math.max(0, drops.size() - MAX_LINES); i < drops.size(); i++) {
            RouteProvider.DropHint drop = drops.get(i);
            viewer.sendSystemMessage(Component.translatable("nerologistics.ship.report.dropped", drop.flightId(),
                    drop.destination(), drop.ageTicks() / 20L));
        }
    }

    private static void more(ServerPlayer viewer, int total) {
        if (total > MAX_LINES) {
            viewer.sendSystemMessage(Component.translatable("nerologistics.ship.report.more", total - MAX_LINES));
        }
    }
}
