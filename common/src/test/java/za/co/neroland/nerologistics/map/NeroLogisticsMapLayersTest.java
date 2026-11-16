package za.co.neroland.nerologistics.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLayerConformance;
import za.co.neroland.nerolandcore.map.MapViewer;

/** Core's map-layer conformance fixture over NeroLogistics' two proximity layers. */
class NeroLogisticsMapLayersTest {

    static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "overworld"));
    static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "the_nether"));
    static final UUID VIEWER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-0000000000c3");

    /** Seeded registries; handed over unfiltered so the provider has to do the filtering. */
    static final class Seeded implements NeroLogisticsMapSource {
        final List<BlockPos> ports = new ArrayList<>();
        final List<BlockPos> stations = new ArrayList<>();

        @Override
        public Collection<BlockPos> dronePorts(@Nullable MinecraftServer server, ResourceKey<Level> dimension) {
            return OVERWORLD.equals(dimension) ? ports : List.of();
        }

        @Override
        public Collection<BlockPos> trainStations(@Nullable MinecraftServer server, ResourceKey<Level> dimension) {
            return OVERWORLD.equals(dimension) ? stations : List.of();
        }
    }

    static Seeded seeded() {
        Seeded s = new Seeded();
        s.ports.add(new BlockPos(20, 70, 10));
        s.ports.add(new BlockPos(-40, 64, 30));
        s.ports.add(new BlockPos(70, 64, 0));     // beyond 64 blocks
        s.stations.add(new BlockPos(0, 64, -50));
        s.stations.add(new BlockPos(3_000, 64, 0));
        return s;
    }

    static MapViewer at(UUID who, int x, int z) {
        return MapViewer.forTest(who, null, OVERWORLD, new BlockPos(x, 64, z));
    }

    @Test
    void conforms() {
        MapLayerConformance.create(new NeroLogisticsMapLayers(seeded()))
                .viewers(at(VIEWER, 0, 0), at(STRANGER, 4, 0))
                .dimensions(OVERWORLD, NETHER)
                .bounds(MapBounds.around(0, 0, 512))
                .farBounds(MapBounds.around(20_000, 20_000, 256))
                .expectEntitledSees(NeroLogisticsMapLayers.DRONE_PORTS, 2)
                .expectEntitledSees(NeroLogisticsMapLayers.TRAIN_STATIONS, 1)
                .verify();
    }

    @Test
    void reachIsMeasuredFromTheViewerNotTheBounds() {
        MapViewer viewer = at(VIEWER, 0, 0);
        assertEquals(true, NeroLogisticsMapLayers.inReach(viewer, OVERWORLD, new BlockPos(0, 64, -50)));
        assertEquals(false, NeroLogisticsMapLayers.inReach(viewer, OVERWORLD, new BlockPos(3_000, 64, 0)),
                "a station 3 000 blocks away is out of reach however the map is panned");
        assertEquals(false, NeroLogisticsMapLayers.inReach(viewer, OVERWORLD, new BlockPos(70, 64, 0)));
    }
}
