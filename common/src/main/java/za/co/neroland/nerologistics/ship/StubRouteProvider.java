package za.co.neroland.nerologistics.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;

/**
 * The standalone {@link RouteProvider}: every loaded dimension is a destination and NeroLogistics
 * simulates the flight itself — the manifest waits in {@link ShipmentState} for the transit time and is
 * delivered into a same-channel Rocket Cargo Port in the destination dimension. The port pays with
 * {@code nerologistics:rocket_fuel}-tagged items. Used whenever Nerospace is absent, too old, or disabled
 * with {@code nerospaceRouting=false}; its behaviour is unchanged from the pre-0.4 stub.
 */
public final class StubRouteProvider implements RouteProvider {

    @Override
    public String id() {
        return "stub";
    }

    @Override
    public boolean chargesOwnFuel() {
        return true;
    }

    @Override
    public boolean needsDispatcher() {
        return false;
    }

    @Override
    public List<RouteDestination> destinations(MinecraftServer server, ShipOrigin origin) {
        List<RouteDestination> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.add(RouteDestination.ofDimension(level.dimension()));
        }
        return out;
    }

    @Override
    public Optional<RouteDestination> resolve(MinecraftServer server, ShipOrigin origin, DestinationKey key) {
        if (key.kind() != DestinationKey.Kind.DIMENSION) {
            return Optional.empty();
        }
        Identifier id = Identifier.tryParse(key.value());
        if (id == null) {
            return Optional.empty();
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(dim) == null ? Optional.empty() : Optional.of(RouteDestination.ofDimension(dim));
    }

    @Override
    public int fuelItems(MinecraftServer server, ShipOrigin origin, RouteDestination destination) {
        return NeroLogisticsConfig.shipFuelPerLaunch();
    }

    @Override
    public Optional<Integer> estimateTicks(MinecraftServer server, ShipOrigin origin, RouteDestination destination,
            List<ItemStack> payload, boolean returnEmpty) {
        return Optional.of(NeroLogisticsConfig.shipTransitTicks());
    }

    @Override
    public LaunchResult launch(MinecraftServer server, ShipOrigin origin, RouteDestination destination,
            List<ItemStack> payload, ShippingClass qos, boolean returnEmpty) {
        if (server.getLevel(destination.dimension()) == null) {
            return LaunchResult.denied(ShipDenial.ROUTE_CLOSED);
        }
        BlockPos exclude = destination.dimension().equals(origin.level().dimension()) ? origin.pos() : null;
        BlockPos target = ShipmentManager.findPort(destination.dimension(), origin.channel(), exclude);
        if (target == null) {
            return LaunchResult.denied(ShipDenial.NO_DESTINATION_PORT);
        }
        ShipmentManager.ship(server, payload, origin.level().dimension(), origin.pos(), destination.dimension(),
                target, qos.applyTransit(NeroLogisticsConfig.shipTransitTicks()));
        LogisticsMetrics.recordShipmentLaunched(origin.level());
        return LaunchResult.accepted(-1);
    }

    @Override
    public int inTransitCount(MinecraftServer server) {
        return ShipmentManager.pendingCount(server);
    }

    @Override
    public List<FlightView> flightsNear(MinecraftServer server, ResourceKey<Level> dim, BlockPos centre, int radius) {
        long now = server.overworld().getGameTime();
        long r2 = (long) radius * radius;
        List<FlightView> out = new ArrayList<>();
        int index = 0;
        for (CargoManifest manifest : ShipmentState.get(server).snapshot()) {
            index++;
            if (!manifest.fromDim().equals(dim) || manifest.fromPos().distSqr(centre) > r2) {
                continue;
            }
            out.add(new FlightView(index, manifest.fromDim().identifier().getPath(),
                    manifest.destDim().identifier().getPath(), FlightView.State.IN_FLIGHT,
                    manifest.arrivalTick() - now, -1L));
        }
        return out;
    }
}
