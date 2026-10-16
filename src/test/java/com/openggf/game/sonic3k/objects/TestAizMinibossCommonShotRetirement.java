package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native common MoveWait terminal callback; declared local graph/world scenarios, not cold routes. */
class TestAizMinibossCommonShotRetirement {
    @BeforeEach void init(){GraphicsManager.getInstance().initHeadless();}
    @AfterEach void reset(){AbstractObjectInstance.resetCameraBoundsForTests();GraphicsManager.getInstance().resetState();}

    @Test void word60HoldsZeroThenMovesAndDrawsOnGoDeleteBeforeNextPhysicalClear() throws Exception {
        var h=harness(320); var shot=addShot(h);
        for(int entry=0;entry<=202;entry++)h.step(entry);
        assertEquals(0,field(shot,"timer"));
        assertEquals("TOP_DROP_SIMPLE",field(shot,"state").toString());
        int beforeY=shot.getY();
        h.step(203);
        assertSame(shot,owner(h),"Obj_Wait callback retains the physical code/status marker through Draw_Sprite");
        assertFalse(shot.isDestroyed());
        assertEquals(beforeY+4,shot.getY());
        clearInvocations(h.renderer);
        shot.appendRenderCommands(List.of());
        verify(h.renderer).drawFrameIndex(0x0D,shot.getX(),shot.getY(),false,true,0);
        h.step(204);
        assertNull(owner(h),"next Delete_Current_Sprite dispatch owns clearing, without more movement");
        assertEquals(beforeY+4,shot.getY());
    }

    @Test void verticalCameraDepartureDoesNotOverrideTheNativeWaitOrCreateAnEarlyDelete() {
        var h=harness(320); var shot=addShot(h);
        for(int entry=0;entry<=106;entry++)h.step(entry);
        // Native MoveWait has no range/delete-touch tail. Keep X visible while
        // changing the presentation camera vertically after the camera-relative Launch.
        when(h.camera.getY()).thenReturn((short)0x1000);
        when(h.camera.getYCopy()).thenReturn((short)0x1000);
        h.manager.refreshPostCameraRenderState();
        int y=shot.getY();
        h.step(107);
        assertSame(shot,owner(h),"a hidden Draw_Sprite is not a native retirement operation");
        assertEquals(y+4,shot.getY());
        for(int entry=108;entry<=203;entry++)h.step(entry);
        assertSame(shot,owner(h));
        h.step(204);assertNull(owner(h));
    }

    @ParameterizedTest @ValueSource(ints={320,352,400,528,800})
    void terminalMarkerRecreatesAndReplaysBothDirectionsForTwo45PassCycles(int width) {
        var h=harness(width);addShot(h);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        for(int entry=0;entry<=202;entry++)h.step(entry);
        var rewind=h.manager.rewindSnapshottable();var before=rewind.capture();
        h.step(203);assertNotNull(owner(h));var marker=rewind.capture();
        for(int entry=204;entry<=248;entry++)h.step(entry);
        var after=rewind.capture();assertNull(owner(h));
        for(int cycle=0;cycle<2;cycle++){
            rewind.restore(before);same("object-manager",before,rewind.capture());
            h.step(203);same("object-manager",marker,rewind.capture());
            for(int entry=204;entry<=248;entry++)h.step(entry);
            same("object-manager",after,rewind.capture());
            rewind.restore(marker);same("object-manager",marker,rewind.capture());
            for(int entry=204;entry<=248;entry++)h.step(entry);
            same("object-manager",after,rewind.capture());
            rewind.restore(before);same("object-manager",before,rewind.capture());
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void loadedAizRegistryRestoresTheLiveMarkerAndReplaysItsPhysicalReleaseTwice() throws Exception {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(0,0).withSkippedZoneIntro().build();
        fixture.stepIdleFrames(6);
        var manager=GameServices.level().getObjectManager();manager.setRewindInPlaceRestoreEnabledForTest(false);
        int x=fixture.camera().getX()+100,y=fixture.camera().getY()+100;
        var boss=manager.createDynamicObject(()->new TestAizMinibossFlareNativeGraph.FrozenBoss(
                new ObjectSpawn(x,y,0x90,0,0,false,0)));
        var barrel=manager.createDynamicObject(()->new AizMinibossFlameBarrelChild(boss,0,true));
        boss.getChildComponents().add(barrel);
        var shot=manager.createDynamicObject(()->new AizMinibossBarrelShotChild(boss,barrel,x,y+4,
                AizMinibossBarrelShotChild.Mode.SIMPLE));
        int slot=shot.getSlotIndex();
        fixture.stepIdleFrames(203);
        assertEquals(0,field(NativeSstSlotAccess.currentOwner(manager,slot),"timer"));
        var registry=fixture.gameplayMode().getRewindRegistry();var before=registry.capture();
        fixture.stepIdleFrames(1);assertNotNull(NativeSstSlotAccess.currentOwner(manager,slot));
        var marker=registry.capture();fixture.stepIdleFrames(45);var after=registry.capture();
        assertNull(NativeSstSlotAccess.currentOwner(manager,slot));
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);sameWorld(before,registry.capture());
            fixture.stepIdleFrames(1);sameWorld(marker,registry.capture());
            fixture.stepIdleFrames(45);sameWorld(after,registry.capture());
            registry.restore(marker);sameWorld(marker,registry.capture());
            fixture.stepIdleFrames(45);sameWorld(after,registry.capture());
            registry.restore(before);sameWorld(before,registry.capture());
        }
    }

    private static TestAizMinibossFlareNativeGraph.Harness harness(int width){
        var h=new TestAizMinibossFlareNativeGraph.Harness(width);
        h.services.withRomReader(TestAizMinibossFallingShotNativeGraph.rawReader());return h;
    }
    private static AizMinibossBarrelShotChild addShot(TestAizMinibossFlareNativeGraph.Harness h){
        return h.manager.createDynamicObjectAtSlot(()->new AizMinibossBarrelShotChild(h.boss(),h.barrel(7),
                h.barrel(7).getX(),h.barrel(7).getY()+4,AizMinibossBarrelShotChild.Mode.SIMPLE),9);
    }
    private static ObjectInstance owner(TestAizMinibossFlareNativeGraph.Harness h){return NativeSstSlotAccess.currentOwner(h.manager,9);}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static void same(String k,Object e,Object a){var d=RewindSnapshotDiff.diffKey(k,e,a);assertTrue(d.isEmpty(),()->d.toString());}
    private static void sameWorld(CompositeSnapshot e,CompositeSnapshot a){assertEquals(e.entries().keySet(),a.entries().keySet());for(String k:e.entries().keySet())same(k,e.get(k),a.get(k));}
}
