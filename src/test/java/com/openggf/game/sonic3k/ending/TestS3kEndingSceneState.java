package com.openggf.game.sonic3k.ending;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kEndingSceneState {
    @Test void preendingSnapshotsEncodeNativeZeroPlanesSparselyAndClearLaterCellsOnRestore(){
        var ending=new S3kEndingState();assertEquals(131,ending.snapshotBytes());
        var saved=ByteBuffer.allocate(ending.snapshotBytes());ending.captureTo(saved);
        ending.scene().foreground().fill(0,0,(x,y)->0xABCD);
        assertEquals(131+4096,ending.snapshotBytes());
        ending.restoreFrom(saved.flip());assertEquals(131,ending.snapshotBytes());
        assertEquals(0,ending.scene().foreground().descriptor(0,0));
    }
    @Test void aBlankDelayedRedrawRetainsItsHeaderAcrossSparseRestore(){
        var scene=new S3kEndingSceneState();var plane=scene.background();plane.begin(0x230,0x210);
        assertEquals(15,plane.remaining());
        var saved=ByteBuffer.allocate(scene.snapshotBytes());scene.captureTo(saved);
        plane.advance((x,y)->0xFFFF);
        scene.restoreFrom(saved.flip());
        assertEquals(0,plane.revision());assertEquals(15,plane.remaining());
        assertEquals(0,plane.descriptor(0x230,0x2F0));
        plane.advance((x,y)->(x+y)&0xFFFF);
        assertEquals(13,plane.remaining());
        assertEquals(0x520,plane.descriptor(0x230,0x2F0),"source position and delayed Y survive without cell payload");
    }
    @Test void bottomUpPublishesTwoRowsAndRetainsUndrawnNameTableCellsAcrossRestore(){
        var scene=new S3kEndingSceneState();var plane=scene.foreground();
        plane.fill(0,0,(x,y)->0x1111);plane.begin(0x200,0x200);
        assertFalse(plane.advance((x,y)->0x2222));assertEquals(13,plane.remaining());
        assertEquals(0x2222,plane.descriptor(0x200,0x2E0));
        assertEquals(0x1111,plane.descriptor(0x200,0x200));
        ByteBuffer saved=ByteBuffer.allocate(S3kEndingSceneState.SNAPSHOT_BYTES);scene.captureTo(saved);
        for(int i=0;i<6;i++)assertFalse(plane.advance((x,y)->0x2222));
        assertTrue(plane.advance((x,y)->0x2222));assertEquals(-1,plane.remaining());
        scene.restoreFrom(saved.flip());assertEquals(13,plane.remaining());
        assertEquals(0x1111,plane.descriptor(0x200,0x200));
        for(int i=0;i<6;i++)assertFalse(plane.advance((x,y)->0x2222));
        assertTrue(plane.advance((x,y)->0x2222));
    }
    @Test void screenFadeConsumesNativeComponentsInOrderInsteadOfObjectFade(){
        assertEquals(0xEEC,S3kEndingPalette.toBlackWord(0xEEE));
        assertEquals(0xEC0,S3kEndingPalette.toBlackWord(0xEE0));
        assertEquals(0xC00,S3kEndingPalette.toBlackWord(0xE00));
        assertEquals(0x200,S3kEndingPalette.fromBlackWord(0,0xEEE));
        assertEquals(0xE20,S3kEndingPalette.fromBlackWord(0xE00,0xEEE));
        assertEquals(0xEE2,S3kEndingPalette.fromBlackWord(0xEE0,0xEEE));
        int color=0xEEE;for(int i=0;i<21;i++)color=S3kEndingPalette.toBlackWord(color);assertEquals(0,color);
        for(int i=0;i<21;i++)color=S3kEndingPalette.fromBlackWord(color,0xEEE);assertEquals(0xEEE,color);
    }
    @Test void complexBackgroundSelectsTheNativeColumnForEachAuthoredBand(){
        var plane=new S3kEndingSceneState().background();plane.begin(0,0x200);
        plane.advanceComplex(y->y<0x220?0:0x5DC,(x,y)->x&0xFFFF);
        assertEquals(0x5D0,plane.descriptor(0x5D0,0x2E0),"Setup_TileRowDraw aligns source X to 16 pixels");
    }
}
