package openggf.timeattack;

import com.openggf.net.identity.PlayerAppearance;

import java.util.List;

/**
 * The named looks offered before a custom one: the stock "Classic" look and a set of base
 * colours spread around the wheel, each a Mega Drive colour word ({@code 0x0BGR}, even nibbles)
 * bright enough for a body ramp to step one level lighter and two darker.
 */
public final class LookPresets {
    /** One preset; {@code baseColour} is {@link PlayerAppearance#NO_BASE_COLOUR} for Classic. */
    public record Preset(String name, int baseColour) { }

    private LookPresets() { }

    public static List<Preset> all() {
        return List.of(
                new Preset("Classic", PlayerAppearance.NO_BASE_COLOUR),
                new Preset("Crimson", 0x022E),
                new Preset("Ember", 0x006E),
                new Preset("Gold", 0x00CE),
                new Preset("Emerald", 0x04C2),
                new Preset("Teal", 0x0AA0),
                new Preset("Azure", 0x0E62),
                new Preset("Violet", 0x0E46),
                new Preset("Rose", 0x0A6E),
                new Preset("Silver", 0x0AAA));
    }

    /** The preset with {@code baseColour}, or null when the colour is the player's own. */
    public static Preset forBase(int baseColour) {
        return all().stream().filter(preset -> preset.baseColour() == baseColour).findFirst().orElse(null);
    }

    /** What a settings row shows for a look: the preset name, "Custom colour" or "Custom palette". */
    public static String label(TimeAttackSettings settings, String gameId, String character) {
        if (settings.lookBase() == PlayerAppearance.NO_BASE_COLOUR) return "Classic";
        if (gameId != null && character != null && settings.customPalette(gameId, character) != null) {
            return "Custom palette";
        }
        Preset preset = forBase(settings.lookBase());
        return preset != null ? preset.name() : "Custom colour";
    }
}
