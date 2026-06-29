package za.co.neroland.nerologistics.network;

import za.co.neroland.nerolandcore.sideconfig.Channel;

/**
 * The three resource media NeroLogistics conduits route. Each maps onto a Core
 * {@link Channel} so conduit faces speak the same {@code SideMode} vocabulary as
 * machine faces (per the Core side-config contract). One {@link ConduitNetwork} only
 * ever connects conduits of a single medium.
 */
public enum NetworkMedium {

    ITEM(Channel.ITEM),
    FLUID(Channel.FLUID),
    ENERGY(Channel.ENERGY);

    private final Channel channel;

    NetworkMedium(Channel channel) {
        this.channel = channel;
    }

    /** The Core side-config channel this medium configures faces through. */
    public Channel channel() {
        return this.channel;
    }

    public String lowerName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
