package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.Objects;

/**
 * Object-side level collision probes from Level/Collision Check.asm at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Only ObjGetFloorDist (:157-179) is ported; the
 * ObjGetFloorDist2 caller-X entry, the wall/ceiling probes and the Player_* chunks are not.
 *
 * <p>The production FindLevelFloor owner is the existing CdPlayerTerrain; nothing here supplies a
 * floor value. GetLevelBlock (Collision Floor.asm:467-515,519-520,643-650) reads only the probing
 * object's own oSprFlags bit 6 on the Palmtree Panic branches and writes no object field, so a
 * probe is pure over the supplied slot image. The primaryAngle global write is reported to the
 * caller; it is never applied to the player.</p>
 */
public final class CdObjectCollisionCheck {
    private CdObjectCollisionCheck() { }

    /** d1 (signed word), the primaryAngle global byte after the call, and the d3.b angle result. */
    public record FloorDistance(int distance, int primaryAngle, int angleResult) {
        public FloorDistance {
            if (distance != (short) distance) {
                throw new IllegalArgumentException("Floor distance must be a signed native word");
            }
            if ((primaryAngle & ~0xFF) != 0 || (angleResult & ~0xFF) != 0) {
                throw new IllegalArgumentException("Angles must be unsigned native bytes");
            }
        }
    }

    /**
     * ObjGetFloorDist: d3 = oX; d2 = oY + EXT.W(oYRadius byte); primaryAngle := 0; FindLevelFloor with
     * a3 = $10, d6 = 0, d5 = $D over the object's own oSprFlags; d3.b = primaryAngle, or 0 when odd.
     * An unavailable native collision word propagates the terrain owner's IllegalStateException.
     */
    public static FloorDistance objGetFloorDist(CdPlayerTerrain terrain, CdObjectState object) {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(object, "object");
        int y = (object.centreY() + object.signedByte(CdObjectState.ByteField.Y_RADIUS)) & 0xFFFF;
        var floor = terrain.floor(object.centreX(), y, 13, 0, 16, object.spriteFlags());
        // MOVE.B #0,(a4) precedes FindLevelFloor; an absent angle write leaves that zero.
        int primaryAngle = floor.angleWrite().orElse(0);
        return new FloorDistance(floor.distance(), primaryAngle, (primaryAngle & 1) == 0 ? primaryAngle : 0);
    }
}
