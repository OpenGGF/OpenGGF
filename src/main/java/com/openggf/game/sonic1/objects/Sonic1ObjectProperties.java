package com.openggf.game.sonic1.objects;

import com.openggf.level.metadata.ObjectPropertySchema;
import java.util.List;
import java.util.Map;
import static com.openggf.level.metadata.ObjectPropertySchema.*;

/** Shipped-ROM subtype meanings; unsupported values remain lossless and visible as raw. */
public final class Sonic1ObjectProperties {
    private Sonic1ObjectProperties() { }
    // Mon_Main (_incObj/26, 2E Monitors and Power-Ups.asm:19-65).
    // FixBugs=0: no new validity clamp; the fixed branch replaces invalid monitors with barriers.
    // Mon_Main (_incObj/26, 2E Monitors and Power-Ups.asm:19-65)
    private static final ObjectPropertySchema MONITOR = new ObjectPropertySchema(List.of(
            enumeration("contents", "Contents", 255, "Static", "Eggman", "Sonic life", "Speed shoes", "Shield",
                    "Invincibility", "10 rings", "S monitor", "Goggles", "Broken")),
            "Mon_Main (_incObj/26, 2E Monitors and Power-Ups.asm:19-65)");
    // Spring_Main (_incObj/41 Springs.asm:40-79). Down overrides horizontal when both bits are set.
    // Spring_Main (_incObj/41 Springs.asm:40-79)
    private static final ObjectPropertySchema SPRING = new ObjectPropertySchema(List.of(
            enumeration("orientation", "Orientation", 0x30, "Up", "Horizontal", "Down", "Down (both bits)"),
            new Field("strength", "Strength", 0x0F, Kind.ENUM, Map.of(0, "Red (16 px/frame)", 2, "Yellow (10 px/frame)"))),
            "Spring_Main (_incObj/41 Springs.asm:40-79)");
    // Spikes_Config (_incObj/36 Spikes.asm:22-29); Spik_TypeIndex (_incObj/36 Spikes.asm:187-191)
    private static final ObjectPropertySchema SPIKES = new ObjectPropertySchema(List.of(
            enumeration("shape", "Shape", 0xF0, "3 upright", "3 sideways", "1 upright", "3 upright (wide)", "6 upright (wide)", "1 sideways"),
            enumeration("movement", "Movement", 0x0F, "Stationary", "Vertical", "Horizontal")),
            "Spikes_Config (_incObj/36 Spikes.asm:22-29); Spik_TypeIndex (_incObj/36 Spikes.asm:187-191)");
    // Lamp_Main (_incObj/79 Lamppost.asm:31-43)
    private static final ObjectPropertySchema CHECKPOINT = new ObjectPropertySchema(List.of(
            range("number", "Checkpoint number", 0x7F)),
            "Lamp_Main (_incObj/79 Lamppost.asm:31-43)");
    // Plat_Move (_incObj/18 Platforms.asm:130-151)
    private static final ObjectPropertySchema PLATFORM = new ObjectPropertySchema(List.of(
            enumeration("movement", "Movement", 0x0F, "Stationary", "Horizontal", "Vertical", "Fall when stood on", "Falling",
                    "Horizontal reversed", "Vertical reversed", "Rise on switch", "Rising", "Stationary (9)",
                    "Large GHZ2 vertical", "Slow vertical", "Slow vertical reversed")),
            "Plat_Move (_incObj/18 Platforms.asm:130-151)");
    // Ledge_Main (_incObj/1A, 53 Collapsing Ledges and Floors.asm:29-51)
    private static final ObjectPropertySchema LEDGE = new ObjectPropertySchema(List.of(
            enumeration("frame", "Mapping frame", 255, "Frame 0", "Frame 1")),
            "Ledge_Main (_incObj/1A, 53 Collapsing Ledges and Floors.asm:29-51)");
    // CFlo_ChkTouch (_incObj/1A, 53 Collapsing Ledges and Floors.asm:177-198); Fragmentate_8x2Floor_NoReset (_incObj/1A, 53 Collapsing Ledges and Floors.asm:280-292)
    private static final ObjectPropertySchema COLLAPSING_FLOOR = new ObjectPropertySchema(List.of(
            enumeration("collapse", "Collapse sequence", 1, "Left-to-right", "Shuffled"),
            bool("playerSide", "Reverse collapse from player side", 0x80)),
            "CFlo_ChkTouch (_incObj/1A, 53 Collapsing Ledges and Floors.asm:177-198); Fragmentate_8x2Floor_NoReset (_incObj/1A, 53 Collapsing Ledges and Floors.asm:280-292)");
    // Pri_Var (_incObj/3E Prison Capsule.asm:32-54)
    private static final ObjectPropertySchema CAPSULE = new ObjectPropertySchema(List.of(
            enumeration("part", "Capsule part", 255, "Body", "Switch", "Unused deleted part 2", "Unused deleted part 3")),
            "Pri_Var (_incObj/3E Prison Capsule.asm:32-54)");
    // Harp_Main (_incObj/16 LZ Harpoon.asm:17-27); Ani_Harp (_anim/Harpoon.asm:5-8)
    private static final ObjectPropertySchema HARPOON = new ObjectPropertySchema(List.of(
            enumeration("animation", "Initial animation", 255, "Sideways extending", "Sideways retracting", "Upright extending", "Upright retracting")),
            "Harp_Main (_incObj/16 LZ Harpoon.asm:17-27); Ani_Harp (_anim/Harpoon.asm:5-8)");
    // Newt_Action_ChkDistance (_incObj/42 Badnik - Newtron.asm:44-67)
    private static final ObjectPropertySchema NEWTRON = new ObjectPropertySchema(List.of(
            enumeration("variant", "Variant", 255, "Blue running", "Green missile-firing")),
            "Newt_Action_ChkDistance (_incObj/42 Badnik - Newtron.asm:44-67)");
    // Jaws_Main (_incObj/2C Badnik - Jaws.asm:20-42)
    private static final ObjectPropertySchema JAWS = new ObjectPropertySchema(List.of(
            range("turnInterval", "Turn interval (64-frame units)", 255)),
            "Jaws_Main (_incObj/2C Badnik - Jaws.asm:20-42)");
    // Hog_Action (_incObj/1E, 20 Badnik - Ball Hog and Cannonball.asm:46-85); CBal_Main (_incObj/1E, 20 Badnik - Ball Hog and Cannonball.asm:106-119)
    private static final ObjectPropertySchema BALL_HOG = new ObjectPropertySchema(List.of(
            range("fuse", "Cannonball fuse (seconds)", 255)),
            "Hog_Action (_incObj/1E, 20 Badnik - Ball Hog and Cannonball.asm:46-85); CBal_Main (_incObj/1E, 20 Badnik - Ball Hog and Cannonball.asm:106-119)");
    // FixBugs=0: the MZ-block check remains ungated by zone; flashing is shipped but unused.
    // But_Pressed (_incObj/32 Button.asm:33-77); But_Pressed (_incObj/32 Button.asm:33-108)
    private static final ObjectPropertySchema BUTTON = new ObjectPropertySchema(List.of(
            range("switch", "Switch index", 0x0F), bool("flash", "Flashing (unused)", 0x20),
            bool("alternate", "Set switch bit 7", 0x40), bool("pushBlock", "Check MZ push block", 0x80)),
            "But_Pressed (_incObj/32 Button.asm:33-77); But_Pressed (_incObj/32 Button.asm:33-108)");
    // Bri_Main (_incObj/11 GHZ Bridge.asm:26-54)
    private static final ObjectPropertySchema BRIDGE = new ObjectPropertySchema(List.of(
            range("logs", "Log count", 255)),
            "Bri_Main (_incObj/11 GHZ Bridge.asm:26-54)");
    // Swing_CreateLinks (_incObj/15 Swinging Platforms.asm:56-68)
    private static final ObjectPropertySchema SWING = new ObjectPropertySchema(List.of(
            range("links", "Chain links (16px units)", 0x0F)),
            "Swing_CreateLinks (_incObj/15 Swinging Platforms.asm:56-68)");
    // PushB_Main (_incObj/33 MZ, LZ Pushable Blocks.asm:27-53)
    private static final ObjectPropertySchema PUSH_BLOCK = new ObjectPropertySchema(List.of(
            enumeration("shape", "Shape", 7, "Single block", "Four-block row")),
            "PushB_Main (_incObj/33 MZ, LZ Pushable Blocks.asm:27-53)");
    // FixBugs=0 retains the unsmashed four-piece mapping selected by subtype 1.
    // Smab_Main (_incObj/51 MZ Smashable Green Block.asm:22-33)
    private static final ObjectPropertySchema SMASH_BLOCK = new ObjectPropertySchema(List.of(
            enumeration("frame", "Mapping frame", 255, "Two pieces", "Four pieces (unused)")),
            "Smab_Main (_incObj/51 MZ Smashable Green Block.asm:22-33)");
    // Flap_Main (_incObj/0C LZ Flapping Door.asm:20-29)
    private static final ObjectPropertySchema FLAP_DOOR = new ObjectPropertySchema(List.of(
            range("interval", "Open / close interval (seconds)", 255)),
            "Flap_Main (_incObj/0C LZ Flapping Door.asm:20-29)");
    // Sign_Main (_incObj/0D Signpost.asm:35-41)
    private static final ObjectPropertySchema SIGNPOST = new ObjectPropertySchema(List.of(
            range("unused", "Unused subtype (ignored)", 255)),
            "Sign_Main (_incObj/0D Signpost.asm:35-41)");

    public static ObjectPropertySchema schema(int id) {
        return switch (id) {
            case 0x0C -> FLAP_DOOR;
            case 0x0D -> SIGNPOST;
            case 0x11 -> BRIDGE;
            case 0x15 -> SWING;
            case 0x16 -> HARPOON;
            case 0x18 -> PLATFORM;
            case 0x1A -> LEDGE;
            case 0x1E -> BALL_HOG;
            case 0x26 -> MONITOR;
            case 0x2C -> JAWS;
            case 0x32 -> BUTTON;
            case 0x33 -> PUSH_BLOCK;
            case 0x36 -> SPIKES;
            case 0x3E -> CAPSULE;
            case 0x41 -> SPRING;
            case 0x42 -> NEWTRON;
            case 0x51 -> SMASH_BLOCK;
            case 0x53 -> COLLAPSING_FLOOR;
            case 0x79 -> CHECKPOINT;
            default -> FALLBACK;
        };
    }
    private static Field range(String id, String label, int mask) {
        return new Field(id, label, mask, Kind.RANGE, Map.of());
    }
}
