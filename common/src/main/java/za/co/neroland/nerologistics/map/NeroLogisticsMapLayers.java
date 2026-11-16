package za.co.neroland.nerologistics.map;

import java.util.Collection;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapLayerSink;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapViewer;
import za.co.neroland.nerologistics.NeroLogisticsCommon;

/**
 * NeroLogistics' layers for Neroland Core's map API (NeroMaps and the companion app draw them; NeroLogistics
 * draws nothing). NeroLogistics records no owner for anything, so both layers are PROXIMITY: drone ports and
 * train stations within 64 blocks of the online viewer's own position, in the viewer's dimension. No name,
 * mode or drone count is attached (a port's name is free text a player typed). Networks, drive bays and rocket
 * flights are deliberately not layers: a drive bay is where a base keeps its storage, and a flight points at
 * a distant destination pad.
 */
public final class NeroLogisticsMapLayers implements MapLayerProvider {

    public static final Identifier DRONE_PORTS = id("drone_ports");
    public static final Identifier TRAIN_STATIONS = id("train_stations");

    /** Proximity radius for player-built things (rule G4). */
    static final int NEAR = MapLayerInfo.BUILT_PROXIMITY;

    private static final List<MapLayerInfo> LAYERS = List.of(
            MapLayerInfo.proximityBuilt(DRONE_PORTS, "map.layer.nerologistics.drone_ports", id("drone_port")),
            MapLayerInfo.proximityBuilt(TRAIN_STATIONS, "map.layer.nerologistics.train_stations",
                    id("train_station")));

    private final NeroLogisticsMapSource source;

    public NeroLogisticsMapLayers() {
        this(new ServerMapSource());
    }

    /** For tests: the same rules over a seeded source. */
    public NeroLogisticsMapLayers(NeroLogisticsMapSource source) {
        this.source = source;
    }

    /** Registers the provider with Core. A failure is logged and never takes the mod down. */
    public static void register() {
        try {
            MapLayerRegistry.register(new NeroLogisticsMapLayers());
        } catch (RuntimeException e) {
            NeroLogisticsCommon.LOGGER.warn("[NeroLogistics] map layers not registered: {}",
                    e.getClass().getSimpleName());
        }
    }

    @Override
    public String modId() {
        return NeroLogisticsCommon.MOD_ID;
    }

    @Override
    public List<MapLayerInfo> layers() {
        return LAYERS;
    }

    @Override
    public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
        if (!viewer.online() || !viewer.dimension().map(dimension::equals).orElse(false)) {
            return; // proximity only: nothing offline or for another dimension
        }
        emit(viewer, dimension, this.source.dronePorts(viewer.server(), dimension), DRONE_PORTS, "port.",
                "map.marker.nerologistics.drone_port", sink);
        emit(viewer, dimension, this.source.trainStations(viewer.server(), dimension), TRAIN_STATIONS,
                "station.", "map.marker.nerologistics.train_station", sink);
    }

    private static void emit(MapViewer viewer, ResourceKey<Level> dimension, Collection<BlockPos> positions,
            Identifier layer, String prefix, String labelKey, MapLayerSink sink) {
        for (BlockPos pos : positions) {
            if (sink.isFull(layer)) {
                return;
            }
            if (inReach(viewer, dimension, pos)) {
                sink.marker(MapMarker.builder(prefix + pos.getX() + "." + pos.getY() + "." + pos.getZ(), layer,
                        dimension, pos).label(MapLabel.translatable(labelKey)).build());
            }
        }
    }

    /**
     * The map's form of the link module's reach rule (kept in step by a parity test): the viewer is online, in
     * {@code dimension}, and {@code pos} is within {@link #NEAR} blocks of the viewer's server position.
     */
    public static boolean inReach(MapViewer viewer, ResourceKey<Level> dimension, BlockPos pos) {
        return viewer.within(dimension, pos.getX(), pos.getZ(), NEAR);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, path);
    }
}
