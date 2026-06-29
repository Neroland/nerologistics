package za.co.neroland.nerologistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import za.co.neroland.nerolandcore.sideconfig.SideMode;

/**
 * A resolved interaction point on a network: a conduit at {@code conduitPos} whose
 * face {@code face} touches a non-conduit block, configured to the given
 * {@link SideMode}. The external block sits at {@code conduitPos.relative(face)} and is
 * queried on its {@code face.getOpposite()} side. Endpoints are cached on the
 * {@link ConduitNetwork} and recomputed only when topology or a face mode changes.
 */
public record ConduitEndpoint(BlockPos conduitPos, Direction face, SideMode mode) {

    /** Position of the external (non-conduit) block this endpoint interacts with. */
    public BlockPos neighborPos() {
        return this.conduitPos.relative(this.face);
    }

    /** The side of the external block facing the conduit. */
    public Direction neighborSide() {
        return this.face.getOpposite();
    }

    /** A source pulls a resource FROM the neighbor into the network. */
    public boolean isSource() {
        return this.mode == SideMode.INPUT || this.mode == SideMode.IO;
    }

    /** A sink pushes a resource from the network INTO the neighbor. */
    public boolean isSink() {
        return this.mode == SideMode.OUTPUT || this.mode == SideMode.IO || this.mode == SideMode.PUSH;
    }
}
