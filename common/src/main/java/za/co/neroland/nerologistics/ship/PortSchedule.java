package za.co.neroland.nerologistics.ship;

import java.util.Locale;

/**
 * When a rocket cargo port launches. Mirrors Nerospace's pad {@code ScheduleMode} by name (so the two
 * read the same to a player), but is NeroLogistics' own type — the port never touches Nerospace classes.
 *
 * <ul>
 *   <li>{@link #EVERY_INTERVAL} — every {@code shipIntervalTicks} when there is cargo (the classic
 *       behaviour, and the default for ports saved before scheduling existed).</li>
 *   <li>{@link #WHEN_FULL} — only once every buffer slot holds a stack.</li>
 *   <li>{@link #MANUAL} — only on a redstone pulse (rising edge) into the port; no interval work at all.</li>
 * </ul>
 */
public enum PortSchedule {

    EVERY_INTERVAL,
    WHEN_FULL,
    MANUAL;

    public PortSchedule next() {
        PortSchedule[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Safe lookup by name; unknown or missing reads as {@link #EVERY_INTERVAL}. */
    public static PortSchedule byName(String name) {
        if (name != null) {
            for (PortSchedule value : values()) {
                if (value.name().equalsIgnoreCase(name)) {
                    return value;
                }
            }
        }
        return EVERY_INTERVAL;
    }

    public String translationKey() {
        return "nerologistics.ship.schedule." + name().toLowerCase(Locale.ROOT);
    }
}
