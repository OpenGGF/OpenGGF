package com.openggf.game.sonic3k.objects;

import com.openggf.editor.properties.ObjectPropertySchema;
import java.util.List;
import static com.openggf.editor.properties.ObjectPropertySchema.*;

/** Common IDs shared by S3KL and SKL; family-specific IDs deliberately use raw fallback. */
public final class Sonic3kObjectProperties {
    private Sonic3kObjectProperties() { }
    // Obj_MonitorInit (sonic3k.asm:40502-40532). The entire byte selects native animation.
    private static final ObjectPropertySchema MONITOR = new ObjectPropertySchema(List.of(
            enumeration("contents", "Contents", 255, "Eggman", "Life", "Eggman (2)", "10 rings", "Speed shoes",
                    "Fire shield", "Lightning shield", "Bubble shield", "Invincibility", "Super")),
            "Obj_MonitorInit (sonic3k.asm:40502-40532)");
    // Obj_Spring (sonic3k.asm:47545-47568); Spring_Common (sonic3k.asm:47682-47702).
    private static final ObjectPropertySchema SPRING = new ObjectPropertySchema(List.of(
            enumeration("orientation", "Orientation", 0x70, "Up", "Horizontal", "Down", "Diagonal up", "Diagonal down"),
            enumeration("strength", "Strength", 2, "Red (16 px/frame)", "Yellow (10 px/frame)")),
            "Obj_Spring (sonic3k.asm:47545-47568); Spring_Common (sonic3k.asm:47682-47702)");
    public static ObjectPropertySchema schema(int id) {
        return switch (id) { case 1 -> MONITOR; case 7 -> SPRING; default -> FALLBACK; };
    }
}
