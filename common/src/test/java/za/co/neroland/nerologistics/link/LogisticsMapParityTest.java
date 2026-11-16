package za.co.neroland.nerologistics.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.map.MapViewer;
import za.co.neroland.nerologistics.map.NeroLogisticsMapLayers;

/**
 * Rule G6 for a mod with no owners: the map's reach rule is the link module's reach rule at the map's
 * tighter 64-block radius. Everything the map shows the link module would also show; nothing the map shows
 * is farther than 64 blocks; offline and other-dimension viewers get nothing from either.
 */
class LogisticsMapParityTest {

    static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "overworld"));
    static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "the_nether"));

    @Test
    void mapReachIsTheLinkReachAtSixtyFourBlocks() {
        BlockPos origin = new BlockPos(0, 64, 0);
        NeroLogisticsLinkAccess.Reach link = new NeroLogisticsLinkAccess.Reach(null, null, origin);
        MapViewer viewer = MapViewer.forTest(UUID.randomUUID(), null, OVERWORLD, origin);
        for (int x = -200; x <= 200; x += 7) {
            for (int z = -200; z <= 200; z += 11) {
                BlockPos pos = new BlockPos(x, 64, z);
                boolean map = NeroLogisticsMapLayers.inReach(viewer, OVERWORLD, pos);
                if (map) {
                    assertTrue(link.covers(pos), "the map never shows what the link module would not");
                }
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d < 63.0) {
                    assertTrue(map);
                }
                if (d > 64.0) {
                    assertFalse(map);
                }
                if (d < 63.0) {
                    assertEquals(link.covers(pos), map);
                }
            }
        }
        BlockPos near = new BlockPos(5, 64, 5);
        assertFalse(NeroLogisticsMapLayers.inReach(MapViewer.forTest(UUID.randomUUID(), null, null, null), OVERWORLD,
                near), "offline sees nothing, as the link module's reach is null offline");
        assertFalse(NeroLogisticsMapLayers.inReach(viewer, NETHER, near), "another dimension sees nothing");
    }
}
