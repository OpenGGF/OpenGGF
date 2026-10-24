package com.openggf.physics;

import com.openggf.game.GroundMode;
import com.openggf.level.*;
import com.openggf.sprites.playable.PlayablePlacementProfile;
import com.openggf.sprites.playable.PlayablePlacementProfile.Offset;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TestNativePlayerTerrainPlacement {
    private PlayablePlacementProfile profile(int top, int lrb) {
        return new PlayablePlacementProfile(new Offset((short)-9,(short)19),new Offset((short)9,(short)19),
                new Offset((short)-9,(short)-19),new Offset((short)9,(short)-19),9,19,top,lrb,2047);
    }
    private LevelManager level() { return level(0); }
    private LevelManager level(int angle) {
        var level = mock(LevelManager.class);
        var desc = new ChunkDesc(7 | 0x3000);
        byte[] full = new byte[16]; Arrays.fill(full,(byte)16);
        var tile = new SolidTile(7,full,full.clone(),(byte)angle);
        when(level.getChunkDescAt(eq((byte)0),anyInt(),anyInt(),eq(false))).thenAnswer(call -> {
            int y = call.getArgument(2); return y >= 64 && y < 80 ? desc : null;
        });
        when(level.getSolidTileForChunkDesc(eq(desc),anyInt())).thenReturn(tile);
        return level;
    }
    @Test void pairedNativeSupportIsZeroDistanceAndPrimaryBitsDoNotMasqueradeAsPlayerAlternateBits() {
        var placement = new NativePlayerTerrainPlacement(level(),() -> {});
        var near = placement.near(profile(12,13),100,40).proposal();
        assertNotNull(near); assertEquals(44,near.y()); assertEquals(63,near.surfaceY());
        assertEquals(NativePlayerTerrainPlacement.Foot.LEFT,near.foot(),"Native floor tie chooses left");
        var exact = placement.exact(profile(12,13),100,44).proposal();
        assertNotNull(exact); assertEquals(0,exact.distance());
        assertNull(placement.near(profile(14,15),100,40).proposal());
    }
    @Test void emptyAndAngleOnlyDefaultsCannotProposeStandingAndClearanceIsCancellable() {
        var empty = new NativePlayerTerrainPlacement(mock(LevelManager.class),() -> {});
        for (int y=0;y<64;y++) assertNull(empty.near(profile(12,13),100,y).proposal());
        var angleOnly = mock(LevelManager.class);
        var desc = new ChunkDesc(7 | 0x3000);
        var zero = new SolidTile(7,new byte[16],new byte[16],(byte)1);
        when(angleOnly.getChunkDescAt(eq((byte)0),anyInt(),anyInt(),eq(false))).thenReturn(desc);
        when(angleOnly.getSolidTileForChunkDesc(eq(desc),anyInt())).thenReturn(zero);
        var kernel = new NativePlayerTerrainScan(); var raw = new SensorResult();
        kernel.scan(angleOnly,(short)100,(short)60,12,Direction.DOWN,false,(byte)0,false,raw,2047,() -> {});
        assertEquals(7,raw.tileId());
        assertEquals(NativePlayerTerrainScan.SupportKind.ANGLE_ONLY_DEFAULT,kernel.kind());
        assertNull(new NativePlayerTerrainPlacement(angleOnly,() -> {}).near(profile(12,13),100,40).proposal());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var bounded = new NativePlayerTerrainPlacement(level(),() -> { if(calls.incrementAndGet()>12) throw new IllegalStateException("cancelled"); });
        assertThrows(IllegalStateException.class,() -> bounded.exact(profile(12,13),100,44));
    }
    @Test void orientationUsesNativeAnglePosBoundariesAndOddAngleFlags() {
        for (int angle : new int[]{0,1,255,0x20,0xE0}) {
            assertNotNull(new NativePlayerTerrainPlacement(level(angle),() -> {}).near(profile(12,13),100,40).proposal(),"Native floor angle "+angle);
        }
        for (int angle : new int[]{0x22,0x40,0x80,0xA0,0xC0,0xDE}) {
            assertEquals(NativePlayerTerrainPlacement.Reason.UNSUITABLE_ORIENTATION,
                    new NativePlayerTerrainPlacement(level(angle),() -> {}).near(profile(12,13),100,40).refusal());
        }
    }
    @Test void collisionSingleSensorPathPreservesItsOriginalGroundModeReadBoundary() throws Exception {
        var sprite = mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        when(sprite.getGroundMode()).thenThrow(new AssertionError("Single-sensor path must not read ground mode"));
        var collision = new CollisionSystem(new TerrainCollisionManager());
        var select = CollisionSystem.class.getDeclaredMethod("selectSensorWithAngle",com.openggf.sprites.playable.AbstractPlayableSprite.class,SensorResult.class,SensorResult.class);
        select.setAccessible(true);
        var sensor = new SensorResult((byte)0,(byte)0,1,Direction.DOWN);
        assertNull(select.invoke(collision,sprite,null,null));
        assertSame(sensor,select.invoke(collision,sprite,sensor,null));
        assertSame(sensor,select.invoke(collision,sprite,null,sensor));
        verify(sprite,never()).getGroundMode();
    }
    @Test void pureSelectionRetainsNullHandlingAndNativeTieRulesForEveryMode() {
        for (var mode : GroundMode.values()) {
            var right = new SensorResult((byte)0,(byte)0,1,Direction.DOWN);
            var left = new SensorResult((byte)2,(byte)0,2,Direction.DOWN);
            assertNull(NativeGroundSelection.select(mode,null,null));
            assertSame(left,NativeGroundSelection.select(mode,null,left));
            assertSame(right,NativeGroundSelection.select(mode,right,null));
            assertSame(mode == GroundMode.RIGHTWALL || mode == GroundMode.LEFTWALL ? right : left,NativeGroundSelection.select(mode,right,left));
        }
    }
}
