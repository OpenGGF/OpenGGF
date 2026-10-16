package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Obj_SSZCollapsingColumn/loc_44BCC both use make_art_tile(...,3,1).
 * Native current-poll SAT words E310/E320 independently corroborate bit15.
 */
class TestS3kSszColumnSpritePriority {
    private static ObjectSpawn spawn(int id) { return new ObjectSpawn(0x100, 0x200, id, 0, 0, false, 0); }

    @Test void intactAndCollapsedColumnOwnerKeepsNativeHighSpritePriority() {
        var column = new SszCollapsingColumnObjectInstance(spawn(0x7E));
        assertEquals(RenderPriority.fromS3kWord(0x180), column.getPriorityBucket());
        assertTrue(column.isHighPriority(), "native art_tile bit15 survives the object presentation owner");
    }

    @Test void independentlyRecreatedDebrisKeepsItsNativeHighSpritePriority() {
        var piece = new SszCollapsingColumnDebrisObjectInstance(spawn(0x7E));
        assertEquals(RenderPriority.fromS3kWord(0x200), piece.getPriorityBucket());
        assertTrue(piece.isHighPriority(), "loc_44BCC uses the same high-priority art word");
    }

    @Test void ordinaryFloatingPlatformKeepsNativeLowSpritePriority() {
        assertFalse(new SszFloatingPlatformObjectInstance(spawn(0x7D)).isHighPriority());
    }
    @Test void roamingCloudUsesNativeHighPriorityWithoutChangingItsFrontBucket() {
        var cloud = new SszRoamingCloudObjectInstance(spawn(0));
        assertEquals(RenderPriority.fromS3kWord(0), cloud.getPriorityBucket());
        assertTrue(cloud.isHighPriority(), "loc_57BB2 ArtTile_SSZMisc+$3C is native tile310 with bit15");
    }

    @Test void bouncyCloudKeepsNativeHighPriority() {
        assertTrue(new SszBouncyCloudObjectInstance(spawn(0)).isHighPriority());
    }

    @Test void independentlyRecreatedBouncyPuffKeepsNativeHighPriority() {
        assertTrue(new SszBouncyCloudPuffObjectInstance(spawn(0)).isHighPriority());
    }

}
