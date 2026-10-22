package com.openggf.game.sonic3k.objects;

import com.openggf.level.metadata.ObjectPropertySchema;
import java.util.List;
import java.util.Map;
import static com.openggf.level.metadata.ObjectPropertySchema.*;

/** Common IDs shared by S3KL and SKL; family-specific IDs deliberately use raw fallback. */
public final class Sonic3kObjectProperties {
    private Sonic3kObjectProperties() { }
    // Obj_MonitorInit (sonic3k.asm:40502-40532). The entire byte selects native animation.
    // Obj_MonitorInit (sonic3k.asm:40502-40532)
    private static final ObjectPropertySchema MONITOR = new ObjectPropertySchema(List.of(
            enumeration("contents", "Contents", 255, "Eggman", "Life", "Eggman (2)", "10 rings", "Speed shoes",
                    "Fire shield", "Lightning shield", "Bubble shield", "Invincibility", "Super")),
            "Obj_MonitorInit (sonic3k.asm:40502-40532)");
    // Obj_Spring (sonic3k.asm:47545-47568); Spring_Common (sonic3k.asm:47682-47702).
    // Obj_Spring (sonic3k.asm:47545-47568); Spring_Common (sonic3k.asm:47682-47702)
    private static final ObjectPropertySchema SPRING = new ObjectPropertySchema(List.of(
            enumeration("orientation", "Orientation", 0x70, "Up", "Horizontal", "Down", "Diagonal up", "Diagonal down"),
            enumeration("strength", "Strength", 2, "Red (16 px/frame)", "Yellow (10 px/frame)")),
            "Obj_Spring (sonic3k.asm:47545-47568); Spring_Common (sonic3k.asm:47682-47702)");
    // Spikes_Dimensions (sonic3k.asm:48958-49022); off_242C4 (sonic3k.asm:49279-49300); loc_24356 (sonic3k.asm:49339-49359)
    private static final ObjectPropertySchema SPIKES = new ObjectPropertySchema(List.of(
            enumeration("shape", "Shape / span", 0xF0, "Upright 32px", "Upright 64px", "Upright 96px", "Upright 128px",
                    "Sideways 32px", "Sideways 64px", "Sideways 96px", "Sideways 128px"),
            enumeration("movement", "Movement", 15, "Stationary", "Vertical", "Horizontal", "Push-triggered sideways")),
            "Spikes_Dimensions (sonic3k.asm:48958-49022); off_242C4 (sonic3k.asm:49279-49300); loc_24356 (sonic3k.asm:49339-49359)");
    // Obj_AIZLRZEMZRock (sonic3k.asm:43883-43895); AIZLRZEMZRock_SelectBehavior (sonic3k.asm:43924-43950); AIZLRZEMZRock_MainSolid (sonic3k.asm:43964-44024)
    private static final ObjectPropertySchema CHECKPOINT = new ObjectPropertySchema(List.of(
            range("number", "Checkpoint number", 0x7F)), "loc_2CFC0 (sonic3k.asm:61613-61636)");
    // Obj_AIZLRZEMZRock (sonic3k.asm:43883-43895); AIZLRZEMZRock_SelectBehavior (sonic3k.asm:43924-43950); AIZLRZEMZRock_MainSolid (sonic3k.asm:43964-44024)
    private static final ObjectPropertySchema LEDGE = new ObjectPropertySchema(List.of(
            range("frame", "Mapping frame (zone art)", 255)), "loc_20570 (sonic3k.asm:44851-44857)");
    // Obj_AIZLRZEMZRock (sonic3k.asm:43883-43895); AIZLRZEMZRock_SelectBehavior (sonic3k.asm:43924-43950); AIZLRZEMZRock_MainSolid (sonic3k.asm:43964-44024)
    private static final ObjectPropertySchema ROCK = new ObjectPropertySchema(List.of(
            new Field("behavior", "Break behavior", 15, Kind.ENUM, Map.of(0, "Solid", 1, "Rolling stand break", 2, "Pushable",
                    3, "Pushable / rolling stand break", 4, "Push break", 8, "Vertical break", 15, "Knuckles standing break")),
            range("shape", "Shape index (zone art)", 0x70)),
            "Obj_AIZLRZEMZRock (sonic3k.asm:43883-43895); AIZLRZEMZRock_SelectBehavior (sonic3k.asm:43924-43950); AIZLRZEMZRock_MainSolid (sonic3k.asm:43964-44024)");
    // Obj_PathSwap (sonic3k.asm:39744-39795); Obj_PathSwap (sonic3k.asm:39744-39861)
    private static final ObjectPropertySchema PATH_SWAP = new ObjectPropertySchema(List.of(
            enumeration("range", "Crossing half-span", 3, "32px", "64px", "128px", "256px"),
            enumeration("axis", "Crossing axis", 4, "Left / right", "Up / down"), bool("grounded", "Grounded players only", 0x80)),
            "Obj_PathSwap (sonic3k.asm:39744-39795); Obj_PathSwap (sonic3k.asm:39744-39861)");
    // Obj_InvisibleBlock (sonic3k.asm:42701-42717)
    private static final ObjectPropertySchema INVISIBLE_BLOCK = new ObjectPropertySchema(List.of(
            range("width", "Half-width: (value + 1) x 8px", 0xF0), range("height", "Half-height: (value + 1) x 8px", 15)),
            "Obj_InvisibleBlock (sonic3k.asm:42701-42717)");
    // loc_2C58A (sonic3k.asm:60796-60805); loc_2C5BE (sonic3k.asm:60819-60835); loc_2C62C (sonic3k.asm:60853-60859)
    private static final ObjectPropertySchema BUTTON = new ObjectPropertySchema(List.of(
            range("switch", "Switch index", 15), bool("latch", "Keep switch set after release", 0x10),
            bool("topOnly", "Top-only solidity", 0x20), bool("alternate", "Set switch bit 7", 0x40)),
            "loc_2C58A (sonic3k.asm:60796-60805); loc_2C5BE (sonic3k.asm:60819-60835); loc_2C62C (sonic3k.asm:60853-60859)");
    // byte_254FA (sonic3k.asm:50792-50801); Obj_FloatingPlatform (sonic3k.asm:50803-50868); FloatingPlatformIndex (sonic3k.asm:50219-50232)
    private static final ObjectPropertySchema FLOATING_PLATFORM = new ObjectPropertySchema(List.of(
            enumeration("movement", "Movement", 15, "Stationary", "Horizontal 64px", "Horizontal 128px", "Vertical 64px", "Vertical 128px",
                    "Diagonal up", "Diagonal down", "Rising", "Square 32px", "Square 96px", "Square 160px", "Square 224px", "Horizontal 256px"),
            enumeration("shape", "Shape / radii", 0x70, "32 x 32", "24 x 12", "32 x 20")),
            "byte_254FA (sonic3k.asm:50792-50801); Obj_FloatingPlatform (sonic3k.asm:50803-50868); FloatingPlatformIndex (sonic3k.asm:50219-50232)");
    // Obj_BreakableWall (sonic3k.asm:45564-45580)
    private static final ObjectPropertySchema BREAKABLE_WALL = new ObjectPropertySchema(List.of(
            range("frame", "Mapping frame (zone art)", 15), bool("removeOnTrigger", "Remove after trigger 0", 0x80)),
            "Obj_BreakableWall (sonic3k.asm:45564-45580)");
    // Obj_AutoSpin (sonic3k.asm:42340-42382); AutoSpin_CheckHorizontalPlayer (sonic3k.asm:42417-42436)
    private static final ObjectPropertySchema AUTO_SPIN = new ObjectPropertySchema(List.of(
            enumeration("range", "Crossing half-span", 3, "32px", "64px", "128px", "256px"),
            enumeration("axis", "Crossing axis", 4, "Left / right", "Up / down"), bool("grounded", "Grounded players only", 0x20)),
            "Obj_AutoSpin (sonic3k.asm:42340-42382); AutoSpin_CheckHorizontalPlayer (sonic3k.asm:42417-42436)");
    // Obj_RetractingSpring (sonic3k.asm:48455-48470); Spring_Common (sonic3k.asm:47682-47702)
    private static final ObjectPropertySchema RETRACTING_SPRING = new ObjectPropertySchema(List.of(
            enumeration("orientation", "Orientation", 0x70, "Up", "Horizontal", "Down", "Diagonal up", "Diagonal down"),
            enumeration("strength", "Strength", 2, "Red (16 px/frame)", "Yellow (10 px/frame)")),
            "Obj_RetractingSpring (sonic3k.asm:48455-48470); Spring_Common (sonic3k.asm:47682-47702)");

    public static ObjectPropertySchema schema(int id) {
        return switch (id) {
            case 1 -> MONITOR;
            case 0x02 -> PATH_SWAP;
            case 0x04 -> LEDGE;
            case 0x05 -> ROCK;
            case 7 -> SPRING;
            case 0x08 -> SPIKES;
            case 0x0D -> BREAKABLE_WALL;
            case 0x26 -> AUTO_SPIN;
            case 0x28 -> INVISIBLE_BLOCK;
            case 0x33 -> BUTTON;
            case 0x34 -> CHECKPOINT;
            case 0x3D -> RETRACTING_SPRING;
            case 0x51 -> FLOATING_PLATFORM;
            default -> FALLBACK;
        };
    }
    private static Field range(String id, String label, int mask) {
        return new Field(id, label, mask, Kind.RANGE, Map.of());
    }
}
