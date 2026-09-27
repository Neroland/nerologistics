package za.co.neroland.nerologistics.ship;

import java.util.Locale;
import java.util.Optional;

/**
 * The stable, persisted identity of a rocket cargo port's destination. Replaces the old {@code DestIndex}
 * (an index into a live list, which shifted whenever the list changed and means nothing for pad ids):
 *
 * <ul>
 *   <li>{@code pad:<id>} — a Nerospace Cargo Pad or station (stations carry negative ids);</li>
 *   <li>{@code dim:<namespace:path>} — a whole dimension, the stub provider's destination.</li>
 * </ul>
 *
 * <p>Pure Java (no Minecraft types) so it round-trips in plain unit tests. Carries no player data.</p>
 *
 * @param kind  which provider family the key belongs to
 * @param value the pad id as a decimal string, or the dimension identifier
 */
public record DestinationKey(Kind kind, String value) {

    /** What a key points at. */
    public enum Kind {
        PAD("pad"),
        DIMENSION("dim");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return this.prefix;
        }
    }

    public DestinationKey {
        if (kind == null || value == null || value.isEmpty()) {
            throw new IllegalArgumentException("DestinationKey needs a kind and a non-empty value");
        }
    }

    public static DestinationKey pad(int padId) {
        return new DestinationKey(Kind.PAD, Integer.toString(padId));
    }

    /** A dimension key from its identifier string, e.g. {@code minecraft:the_nether}. */
    public static DestinationKey dimension(String identifier) {
        return new DestinationKey(Kind.DIMENSION, identifier.toLowerCase(Locale.ROOT));
    }

    /** The pad id, when this is a {@link Kind#PAD} key with a well-formed value. */
    public Optional<Integer> padId() {
        if (this.kind != Kind.PAD) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(this.value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The persisted form: {@code <prefix>:<value>}. */
    public String serialize() {
        return this.kind.prefix() + ":" + this.value;
    }

    /** Parse {@link #serialize()} output; anything malformed or unknown reads as empty (never throws). */
    public static Optional<DestinationKey> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        int colon = raw.indexOf(':');
        if (colon <= 0 || colon == raw.length() - 1) {
            return Optional.empty();
        }
        String prefix = raw.substring(0, colon);
        String rest = raw.substring(colon + 1);
        for (Kind kind : Kind.values()) {
            if (kind.prefix().equals(prefix)) {
                DestinationKey key = new DestinationKey(kind, rest);
                if (kind == Kind.PAD && key.padId().isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /**
     * Pre-0.4 migration: a saved {@code DestIndex} pointed into the stub's dimension list; resolve it the
     * way the old code did ({@code floorMod} into the list) against today's list. Empty when the list is
     * empty or the index was never set (negative).
     */
    public static Optional<DestinationKey> fromLegacyIndex(int legacyIndex, java.util.List<DestinationKey> stubKeys) {
        if (legacyIndex < 0 || stubKeys.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(stubKeys.get(Math.floorMod(legacyIndex, stubKeys.size())));
    }

    @Override
    public String toString() {
        return serialize();
    }
}
