package starpost.core;

import java.util.Map;

/**
 * A part of the game that saves its own state (villagers, animals, the Ruins...). Each section
 * writes keys under its own prefix; {@link SaveCodec} collects them into the save file and hands
 * the same keys back on load. Loading must validate everything, like the codec does.
 */
public interface SaveSection {
    /** The key prefix, e.g. {@code "people"}; keys are written as {@code prefix.key}. */
    String prefix();

    void save(Map<String, String> out);

    /** Restores from the section's keys (prefix removed); missing keys mean a fresh start. */
    void load(Map<String, String> in, Catalog catalog);

    /** Overnight work after the farm grows (friendship decay, animal produce...). */
    default void nextDay(Game game) {
    }
}
