package com.openggf.game.sonic2.objects;

import com.openggf.editor.properties.ObjectPropertySchema;
import java.util.List;
import static com.openggf.editor.properties.ObjectPropertySchema.*;

public final class Sonic2ObjectProperties {
    private Sonic2ObjectProperties() { }
    // Obj26_Init (s2.asm:25541-25579). fixBugs=0: keep invalid raw values, never normalize on inspection.
    private static final ObjectPropertySchema MONITOR = new ObjectPropertySchema(List.of(
            enumeration("contents", "Contents", 255, "Static", "Sonic life", "Tails life", "Eggman", "10 rings",
                    "Speed shoes", "Shield", "Invincibility", "Swap", "Random", "Broken")),
            "Obj26_Init (s2.asm:25541-25579)");
    // Obj41_Init (s2.asm:33824-33843); Obj41_Init_Common (s2.asm:33878-33898).
    private static final ObjectPropertySchema SPRING = new ObjectPropertySchema(List.of(
            enumeration("orientation", "Orientation", 0x70, "Up", "Horizontal", "Down", "Diagonal up", "Diagonal down"),
            enumeration("strength", "Strength", 2, "Red (16 px/frame)", "Yellow (10 px/frame)")),
            "Obj41_Init (s2.asm:33824-33843); Obj41_Init_Common (s2.asm:33878-33898)");
    // Obj18_Init (s2.asm:23176-23224); Obj18_Behaviours (s2.asm:23341-23355).
    private static final ObjectPropertySchema PLATFORM = new ObjectPropertySchema(List.of(
            enumeration("movement", "Movement", 15, "Stationary", "Horizontal", "Vertical", "Falling trigger", "Falling",
                    "Horizontal reversed", "Vertical reversed", "Button trigger", "Rising", "Stationary (9)",
                    "Short vertical reversed", "Short vertical", "Vertical alternate", "Vertical alternate reversed"),
            enumeration("shape", "Shape / width", 0x70, "Frame 0 / 64px", "Frame 1 / 64px", "Frame 2 / 64px", "Frame 3 / 128px", "Frame 4 / 96px"),
            bool("fullSolid", "Full solidity", 128)),
            "Obj18_Init (s2.asm:23176-23224); Obj18_Behaviours (s2.asm:23341-23355)");
    public static ObjectPropertySchema schema(int id) {
        return switch (id) { case 0x26 -> MONITOR; case 0x41 -> SPRING; case 0x18 -> PLATFORM; default -> FALLBACK; };
    }
}
