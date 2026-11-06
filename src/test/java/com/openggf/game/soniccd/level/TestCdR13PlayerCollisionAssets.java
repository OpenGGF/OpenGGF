package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests-first; actual configured USA C/D source bytes. No primary/Body/Hurt/score World admission.
 * N1ecc5f3af PP/Player Object Collision.asm:44-47,166,200-204,398-461. Java UNRUN. */
class TestCdR13PlayerCollisionAssets {
    @Test void independentCompleteCandDManifestsRetainOriginalDomainsAndAll126Plus8Bytes()throws Exception{
        for(var a:TestCdR13FutureAssets.retail().all()){var o=CdR13PlayerCollisionAssets.fromAssets(a);var file=a.identity().file();
            assertSame(a.identity(),o.identity());assertTrue(o.ownsAssets(a));int sizes=file==CdNativeFile.R13C?0x6E14:0x6E2C,points=file==CdNativeFile.R13C?0x6C2A:0x6C42;
            var r=CdR13PlayerCollisionAssets.View.TOUCH_DIMENSIONS.range(file);assertEquals(sizes,r.fileOffset());assertEquals(126,r.length());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,r.domain());
            var p=CdR13PlayerCollisionAssets.View.ENEMY_POINTS.range(file);assertEquals(points,p.fileOffset());assertEquals(8,p.length());
            assertEquals("658a42cd58f3c61fc034da9e81c25656b9dc40d923ceed515e14b56672b1581d",hash(o.view(CdR13PlayerCollisionAssets.View.TOUCH_DIMENSIONS)));
            assertEquals("75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5",hash(o.enemyPoints()));
            var image=TestCdR13FutureAssets.retail().image(file);assertArrayEquals(image.readFile(sizes,126),o.view(CdR13PlayerCollisionAssets.View.TOUCH_DIMENSIONS).copy());assertArrayEquals(image.readFile(points,8),o.enemyPoints().copy());
            assertThrows(IndexOutOfBoundsException.class,()->o.view(CdR13PlayerCollisionAssets.View.TOUCH_DIMENSIONS).unsignedByte(126));assertThrows(IndexOutOfBoundsException.class,()->o.enemyPoints().unsignedWord(8));}
    }
    @Test void actualTouchAndResponseConsumersPinIndependentLiteralAndPcRelativeOperands()throws Exception{
        var f=TestCdR13FutureAssets.retail();for(var a:f.all()){var b=f.image(a.identity().file());int touch=a.identity().file()==CdNativeFile.R13C?0x6AB4:0x6ACC,points=a.identity().file()==CdNativeFile.R13C?0x6BCE:0x6BE6;
            assertEquals(a.identity().file()==CdNativeFile.R13C?"45f900206e1445f200fe7200121a30290008":"45f900206e2c45f200fe7200121a30290008",HexFormat.of().formatHex(b.readFile(touch,18)));
            assertEquals("303b005a0c780020f7d0650a",HexFormat.of().formatHex(b.readFile(points,12)));
            assertEquals(CdR13PlayerCollisionAssets.View.ENEMY_POINTS.range(a.identity().file()).fileOffset(),points+2+(byte)b.readFile(points+3,1)[0]);}
    }
    @Test void everyDecodedUnsignedHalfSizeAndAllFourPointWordsComeFromTheirActualRawSource()throws Exception{
        for(var a:TestCdR13FutureAssets.retail().all()){var o=CdR13PlayerCollisionAssets.fromAssets(a);var raw=o.view(CdR13PlayerCollisionAssets.View.TOUCH_DIMENSIONS);var sizes=o.touchDimensions();assertEquals(63,sizes.size());assertSame(sizes,o.touchDimensions());
            for(int n=0;n<63;n++){assertEquals(raw.unsignedByte(n*2),sizes.get(n).halfWidth());assertEquals(raw.unsignedByte(n*2+1),sizes.get(n).halfHeight());}
            for(int n=0;n<4;n++)assertEquals(new int[]{10,20,50,100}[n],o.enemyPoints().unsignedWord(n*2));
            assertDoesNotThrow(()->new CdPlayerObjectTouch(sizes));assertDoesNotThrow(()->new CdPlayerTouchResponse(o.enemyPoints()));}
    }
    @Test void foreignPairImageHashAndDomainCannotBeRelabelledAsAPlayerCollisionOwner()throws Exception{
        var f=TestCdR13FutureAssets.retail();var o=CdR13PlayerCollisionAssets.fromAssets(f.good());assertFalse(o.ownsAssets(f.bad()));assertFalse(o.ownsAssets(null));
        assertThrows(NullPointerException.class,()->CdR13PlayerCollisionAssets.fromAssets(null));
        for(var image:List.of(f.d(),f.legacyImage(),CdMmdImage.parse(f.c().readFile(0,262143)))){var wrong=spy(f.good());doReturn(image).when(wrong).originalImage();assertThrows(IllegalArgumentException.class,()->CdR13PlayerCollisionAssets.fromAssets(wrong));}
        for(var v:CdR13PlayerCollisionAssets.View.values()){byte[] bytes=f.c().readFile(0,262144);bytes[v.range(CdNativeFile.R13C).fileOffset()]^=1;var wrong=spy(f.good());doReturn(CdMmdImage.parse(bytes)).when(wrong).originalImage();assertThrows(IllegalArgumentException.class,()->CdR13PlayerCollisionAssets.fromAssets(wrong));}
        assertThrows(IllegalArgumentException.class,()->CdR13PlayerCollisionAssets.View.ENEMY_POINTS.range(CdNativeFile.R12B));assertThrows(IllegalArgumentException.class,()->new CdR13FutureAssets.FileRange(CdNativeFile.R13C,CdMmdImage.Domain.RELOCATED_COPY,0x6E14,126));}
    @Test void cachedViewsAndDimensionsAreImmutableWithoutChangingAnyExistingInventory()throws Exception{
        for(var a:TestCdR13FutureAssets.retail().all()){var o=CdR13PlayerCollisionAssets.fromAssets(a);for(var v:CdR13PlayerCollisionAssets.View.values()){var b=o.view(v);assertSame(b,o.view(v));byte[] before=b.copy(),copy=b.copy();Arrays.fill(copy,(byte)0x55);assertArrayEquals(before,o.view(v).copy());assertThrows(ReadOnlyBufferException.class,()->b.buffer().put(0,(byte)1));}
            assertSame(o.view(CdR13PlayerCollisionAssets.View.ENEMY_POINTS),o.enemyPoints());assertThrows(UnsupportedOperationException.class,()->o.touchDimensions().clear());assertThrows(NullPointerException.class,()->o.view(null));}
        assertEquals(29,CdR13FutureAssets.Asset.values().length);assertEquals(25,CdR13ObjectAssets.View.values().length);}
    @Test void sourceOnlySurfaceHasNoRawImageFactoryOrPlayerStateConstructor(){
        assertEquals(0,CdR13PlayerCollisionAssets.class.getConstructors().length);assertEquals(1,CdR13PlayerCollisionAssets.class.getDeclaredConstructors().length);assertEquals(2,CdR13PlayerCollisionAssets.View.values().length);
        assertEquals(List.of("enemyPoints/0","fromAssets/1","identity/0","ownsAssets/1","touchDimensions/0","view/1"),Arrays.stream(CdR13PlayerCollisionAssets.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());}
    private static String hash(CdAssetBytes b){return CdR11aProfile.sha256(b.copy());}
}
