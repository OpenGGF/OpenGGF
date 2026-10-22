package com.openggf.game.sonic2.objects;

import com.openggf.level.metadata.ObjectPropertySchema;
import java.util.List;
import java.util.Map;
import static com.openggf.level.metadata.ObjectPropertySchema.*;

public final class Sonic2ObjectProperties {
    private Sonic2ObjectProperties() { }
    // Obj26_Init (s2.asm:25541-25579). fixBugs=0: keep invalid raw values, never normalize on inspection.
    // Obj26_Init (s2.asm:25541-25579)
    private static final ObjectPropertySchema MONITOR = new ObjectPropertySchema(List.of(
            enumeration("contents", "Contents", 255, "Static", "Sonic life", "Tails life", "Eggman", "10 rings",
                    "Speed shoes", "Shield", "Invincibility", "Swap", "Random", "Broken")),
            "Obj26_Init (s2.asm:25541-25579)");
    // Obj41_Init (s2.asm:33824-33843); Obj41_Init_Common (s2.asm:33878-33898).
    // Obj41_Init (s2.asm:33824-33843); Obj41_Init_Common (s2.asm:33878-33898)
    private static final ObjectPropertySchema SPRING = new ObjectPropertySchema(List.of(
            enumeration("orientation", "Orientation", 0x70, "Up", "Horizontal", "Down", "Diagonal up", "Diagonal down"),
            enumeration("strength", "Strength", 2, "Red (16 px/frame)", "Yellow (10 px/frame)")),
            "Obj41_Init (s2.asm:33824-33843); Obj41_Init_Common (s2.asm:33878-33898)");
    // Obj18_Init (s2.asm:23176-23224); Obj18_Behaviours (s2.asm:23341-23355).
    // Obj18_Init (s2.asm:23176-23224); Obj18_Behaviours (s2.asm:23341-23355)
    private static final ObjectPropertySchema PLATFORM = new ObjectPropertySchema(List.of(
            enumeration("movement", "Movement", 15, "Stationary", "Horizontal", "Vertical", "Falling trigger", "Falling",
                    "Horizontal reversed", "Vertical reversed", "Button trigger", "Rising", "Stationary (9)",
                    "Short vertical reversed", "Short vertical", "Vertical alternate", "Vertical alternate reversed"),
            enumeration("shape", "Shape / width", 0x70, "Frame 0 / 64px", "Frame 1 / 64px", "Frame 2 / 64px", "Frame 3 / 128px", "Frame 4 / 96px"),
            bool("fullSolid", "Full solidity", 128)),
            "Obj18_Init (s2.asm:23176-23224); Obj18_Behaviours (s2.asm:23341-23355)");
    // Obj36_InitData (s2.asm:29349-29388); MoveSpikes_Behaviors (s2.asm:29529-29533)
    private static final ObjectPropertySchema SPIKES = new ObjectPropertySchema(List.of(
            enumeration("shape", "Shape / span", 0xF0, "Upright 32px", "Upright 64px", "Upright 96px", "Upright 128px",
                    "Sideways 32px", "Sideways 64px", "Sideways 96px", "Sideways 128px"),
            enumeration("movement", "Movement", 15, "Stationary", "Vertical", "Horizontal")),
            "Obj36_InitData (s2.asm:29349-29388); MoveSpikes_Behaviors (s2.asm:29529-29533)");
    // Obj19_SubtypeProperties (s2.asm:47948-47990); Obj19_MoveTypes (s2.asm:48030-48183)
    private static final ObjectPropertySchema CHECKPOINT = new ObjectPropertySchema(List.of(
            range("number", "Checkpoint number", 0x7F)), "Obj79_Init (s2.asm:44580-44607)");
    // Obj19_SubtypeProperties (s2.asm:47948-47990); Obj19_MoveTypes (s2.asm:48030-48183)
    private static final ObjectPropertySchema LEDGE = new ObjectPropertySchema(List.of(
            range("frame", "Mapping frame (zone art)", 255)), "Obj1A_Init (s2.asm:23646-23664)");
    // Obj19_SubtypeProperties (s2.asm:47948-47990); Obj19_MoveTypes (s2.asm:48030-48183)
    private static final ObjectPropertySchema COLLAPSING_FLOOR = new ObjectPropertySchema(List.of(
            enumeration("collapse", "Collapse sequence", 1, "Left-to-right", "Shuffled")), "Obj1F (s2.asm:23754-23782)");
    // Obj19_SubtypeProperties (s2.asm:47948-47990); Obj19_MoveTypes (s2.asm:48030-48183)
    private static final ObjectPropertySchema BUTTON = new ObjectPropertySchema(List.of(
            range("switch", "Switch index", 15), bool("alternate", "Set switch bit 7", 0x40)), "Obj47_Main (s2.asm:50889-50907)");
    // Obj19_SubtypeProperties (s2.asm:47948-47990); Obj19_MoveTypes (s2.asm:48030-48183)
    private static final ObjectPropertySchema BRIDGE = new ObjectPropertySchema(List.of(
            range("logs", "Log count", 255)), "Obj11_Init (s2.asm:21943-21964)");
    // Obj19_SubtypeProperties (s2.asm:47948-47990); Obj19_MoveTypes (s2.asm:48030-48183)
    private static final ObjectPropertySchema SWING = new ObjectPropertySchema(List.of(
            range("links", "Chain links", 15), range("style", "Swing configuration index", 0x70),
            bool("detach", "Detach when ridden", 0x80)), "Obj15_Init (s2.asm:22466-22515); Obj15_State4 (s2.asm:22753-22830)");
    // Obj19_SubtypeProperties (s2.asm:47948-47990); Obj19_MoveTypes (s2.asm:48030-48183)
    private static final ObjectPropertySchema BOOSTER = new ObjectPropertySchema(List.of(
            enumeration("strength", "Boost speed", 2, "16 px/frame", "10 px/frame")), "Obj1B_BoosterSpeeds (s2.asm:48206-48224)");
    // Obj19_SubtypeProperties (s2.asm:47948-47990); Obj19_MoveTypes (s2.asm:48030-48183)
    private static final ObjectPropertySchema CPZ_PLATFORM = new ObjectPropertySchema(List.of(
            enumeration("movement", "Movement", 15, "Horizontal 64px", "Horizontal 96px", "Vertical 128px", "Rise when stood on",
                    "Rising", "Stationary", "Vertical acceleration", "Vertical acceleration (raised)",
                    "Circular phase 0", "Circular phase 1", "Circular phase 2", "Circular phase 3",
                    "Circular alternate 0", "Circular alternate 1", "Circular alternate 2", "Circular alternate 3"),
            enumeration("shape", "Shape / width", 0xF0, "Frame 0 / 64px", "Frame 1 / 48px", "Frame 2 / 128px", "Frame 3 / 64px")),
            "Obj19_SubtypeProperties (s2.asm:47948-47990); Obj19_MoveTypes (s2.asm:48030-48183)");
    // Obj6B_Properties (s2.asm:54394-54420); Obj6B_Types (s2.asm:54460-54472)
    private static final ObjectPropertySchema MTZ_PLATFORM = new ObjectPropertySchema(List.of(
            enumeration("movement", "Movement", 15, "Stationary", "Horizontal 64px", "Horizontal 128px", "Vertical 64px", "Vertical 128px",
                    "Sink when stood on", "Sinking", "Descending", "Stair smallest", "Stair small", "Stair large", "Stair largest"),
            enumeration("shape", "Shape / radii", 0x70, "32 x 12", "16 x 16")),
            "Obj6B_Properties (s2.asm:54394-54420); Obj6B_Types (s2.asm:54460-54472)");
    // Obj95_Index (s2.asm:74155-74161); loc_371BA (s2.asm:74217-74224); loc_37224 (s2.asm:74258-74264)
    private static final ObjectPropertySchema SPIKE_BLOCK = new ObjectPropertySchema(List.of(
            range("phase", "Rotating spike phase", 3)), "Obj68_Init (s2.asm:53728-53761)");
    // Obj95_Index (s2.asm:74155-74161); loc_371BA (s2.asm:74217-74224); loc_37224 (s2.asm:74258-74264)
    private static final ObjectPropertySchema FLOOR_SPIKE = new ObjectPropertySchema(List.of(
            range("phase", "128-frame cycle offset", 0x7F)), "Obj6D_Main (s2.asm:53914-53942)");
    // Obj95_Index (s2.asm:74155-74161); loc_371BA (s2.asm:74217-74224); loc_37224 (s2.asm:74258-74264)
    private static final ObjectPropertySchema TRIGGER_BRIDGE = new ObjectPropertySchema(List.of(
            range("switch", "Switch index", 255)), "Obj77_Main (s2.asm:55982-55994)");
    // Obj95_Index (s2.asm:74155-74161); loc_371BA (s2.asm:74217-74224); loc_37224 (s2.asm:74258-74264)
    private static final ObjectPropertySchema DRAWBRIDGE = new ObjectPropertySchema(List.of(
            range("switch", "Switch index", 255)), "Obj81_BridgeUp (s2.asm:57114-57123)");
    // Obj95_Index (s2.asm:74155-74161); loc_371BA (s2.asm:74217-74224); loc_37224 (s2.asm:74258-74264)
    private static final ObjectPropertySchema SOL = new ObjectPropertySchema(List.of(
            new Field("variant", "Variant", 255, Kind.ENUM, Map.of(0, "Wait then throw fireballs", 2, "Orbiting fireballs without throwing"))),
            "Obj95_Index (s2.asm:74155-74161); loc_371BA (s2.asm:74217-74224); loc_37224 (s2.asm:74258-74264)");
    private static final ObjectPropertySchema SIGNPOST = new ObjectPropertySchema(List.of(
            range("unused", "Unused subtype (ignored)", 255)), "Obj0D_Init (s2.asm:34636-34669)");
    private static final ObjectPropertySchema CAPSULE = new ObjectPropertySchema(List.of(
            range("unused", "Unused subtype (ignored)", 255)), "loc_3F212 (s2.asm:84959-84995)");

    public static ObjectPropertySchema schema(int id) {
        return switch (id) {
            case 0x0D -> SIGNPOST;
            case 0x11 -> BRIDGE;
            case 0x15 -> SWING;
            case 0x18 -> PLATFORM;
            case 0x19 -> CPZ_PLATFORM;
            case 0x1A -> LEDGE;
            case 0x1B -> BOOSTER;
            case 0x1F -> COLLAPSING_FLOOR;
            case 0x26 -> MONITOR;
            case 0x36 -> SPIKES;
            case 0x3E -> CAPSULE;
            case 0x41 -> SPRING;
            case 0x47 -> BUTTON;
            case 0x68 -> SPIKE_BLOCK;
            case 0x6B -> MTZ_PLATFORM;
            case 0x6D -> FLOOR_SPIKE;
            case 0x77 -> TRIGGER_BRIDGE;
            case 0x79 -> CHECKPOINT;
            case 0x81 -> DRAWBRIDGE;
            case 0x95 -> SOL;
            default -> FALLBACK;
        };
    }
    private static Field range(String id, String label, int mask) {
        return new Field(id, label, mask, Kind.RANGE, Map.of());
    }
}
