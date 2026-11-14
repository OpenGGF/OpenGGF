package com.openggf.game.soniccd.objects.projector;

import com.openggf.game.soniccd.CdZone;

/**
 * What differs between the six zones' copies of {@code ObjProjector}: Palmtree Panic and Wacky Workbench act 1 Present
 * assemble {@code Level/<Zone>/Objects/Projector/Main.asm}, the other acts and zones {@code objects/r4..r7/projector.asm},
 * and Collision Chaos links the Palmtree Panic body with {@code CC_LEGACY_PROJECTOR_ABI} (parent $38, animal $39, tile $33E,
 * {@code AddGfxQueue}). The routine is the same in all of them (the hologram, the shutdown schedule, the sounds and the
 * solid); the immediates below are read from each source and checked against the live code in the stage files.
 *
 * @param parentId   {@code PROJECTOR_PARENT_ID}: the projector's object ID, tested by its children
 * @param animalId   the zone's animal object ({@code PROJECTOR_ANIMAL_ID})
 * @param tileAct1   {@code oTile} in act 1 ({@code tst.b act; beq})
 * @param tileOther  {@code oTile} in the other acts
 * @param plc        the pattern load the init requests ({@code LoadPLC}/{@code AddGfxQueue}), 0 for none
 * @param metalDx    the Metal Sonic hologram's X offset (its Y offset is -4 in every zone)
 * @param animal1    X and Y offset of the first hologram animal (subtype $80)
 * @param animal2    X and Y offset of the second hologram animal (subtype $81)
 */
public record ProjectorVariant(int parentId, int animalId, int tileAct1, int tileOther, int plc, int metalDx,
                               int[] animal1, int[] animal2) {
    /** Palmtree Panic/Objects/Projector/Main.asm:25-26, 76, 98-104, 128-152; the live copy at R11B $EAAA. */
    private static final ProjectorVariant PALMTREE_PANIC = new ProjectorVariant(0x2E, 0x24, 0x403, 0x3AF, 5, -0x58,
            new int[] {-0x58, -0x18}, new int[] {-0x64, 4});
    /** CC_LEGACY_PROJECTOR_ABI (Palmtree Panic/Objects/Projector/Main.asm:15-21, 85-87, 140-143). */
    private static final ProjectorVariant COLLISION_CHAOS = new ProjectorVariant(0x38, 0x39, 0x33E, 0x33E, 5, -0x58,
            new int[] {-0x58, -0x18}, new int[] {-0x62, 0});
    /** objects/r4/projector.asm:46, 69-83, 157: no pattern load, the art is in the level's own list. */
    private static final ProjectorVariant TIDAL_TEMPEST = new ProjectorVariant(0x52, 0x2F, 0x3B0, 0x3B0, 0, -0x58,
            new int[] {-0x4C, -0x1C}, new int[] {-0x54, -0x11});
    /** objects/r5/projector.asm:16-17, 43-44, 52, 60-89. */
    private static final ProjectorVariant QUARTZ_QUADRANT = new ProjectorVariant(0x2C, 0x2D, 0x3B6, 0x3B6, 8, -0x58,
            new int[] {-0x58, -0x18}, new int[] {-0x64, 4});
    /** objects/r6/projector.asm:44-58, 67-96; Wacky Workbench/Objects/Projector/Main.asm:38-52, 71-107. */
    private static final ProjectorVariant WACKY_WORKBENCH = new ProjectorVariant(0x2F, 0x29, 0x4E8, 0x300, 5, -0x48,
            new int[] {-0x48, -0x18}, new int[] {-0x54, 7});
    /** objects/r7/projector.asm:16-17, 43-44, 52, 60-89, 165. */
    private static final ProjectorVariant STARDUST_SPEEDWAY = new ProjectorVariant(0x27, 0x28, 0x4AF, 0x4AF, 5, -0x58,
            new int[] {-0x58, -0x18}, new int[] {-0x62, 0});

    /** The zone's copy, or {@code null} for Metallic Madness, which assembles no projector. */
    public static ProjectorVariant of(CdZone zone) {
        return switch (zone) {
            case PALMTREE_PANIC -> PALMTREE_PANIC;
            case COLLISION_CHAOS -> COLLISION_CHAOS;
            case TIDAL_TEMPEST -> TIDAL_TEMPEST;
            case QUARTZ_QUADRANT -> QUARTZ_QUADRANT;
            case WACKY_WORKBENCH -> WACKY_WORKBENCH;
            case STARDUST_SPEEDWAY -> STARDUST_SPEEDWAY;
            case METALLIC_MADNESS -> null;
        };
    }

    /** {@code tst.b act}: act 1 (0) takes the first tile. */
    public int tile(int actIndex) {
        return actIndex == 0 ? tileAct1 : tileOther;
    }
}
