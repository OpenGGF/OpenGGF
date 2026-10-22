package com.openggf.game.sonic1.objects;

import com.openggf.editor.properties.ObjectPropertySchema;
import java.util.List;
import java.util.Map;
import static com.openggf.editor.properties.ObjectPropertySchema.*;

/** Shipped-ROM subtype meanings; unsupported values remain lossless and visible as raw. */
public final class Sonic1ObjectProperties {
    private Sonic1ObjectProperties() { }
    // Mon_Main (_incObj/26, 2E Monitors and Power-Ups.asm:19-65).
    // FixBugs=0: no new validity clamp; the fixed branch replaces invalid monitors with barriers.
    private static final ObjectPropertySchema MONITOR = new ObjectPropertySchema(List.of(
            enumeration("contents", "Contents", 255, "Static", "Eggman", "Sonic life", "Speed shoes", "Shield",
                    "Invincibility", "10 rings", "S monitor", "Goggles", "Broken")),
            "Mon_Main (_incObj/26, 2E Monitors and Power-Ups.asm:19-65)");
    // Spring_Main (_incObj/41 Springs.asm:40-79). Down overrides horizontal when both bits are set.
    private static final ObjectPropertySchema SPRING = new ObjectPropertySchema(List.of(
            enumeration("orientation", "Orientation", 0x30, "Up", "Horizontal", "Down", "Down (both bits)"),
            new Field("strength", "Strength", 0x0F, Kind.ENUM, Map.of(0, "Red (16 px/frame)", 2, "Yellow (10 px/frame)"))),
            "Spring_Main (_incObj/41 Springs.asm:40-79)");
    public static ObjectPropertySchema schema(int id) {
        return switch (id) { case 0x26 -> MONITOR; case 0x41 -> SPRING; default -> FALLBACK; };
    }
}
