package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestSozSpringVine {
    private SozSpringVineObjectInstance vine(int flip) {
        var result = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 8; }
        }.create(new ObjectSpawn(0x200, 0x300, 0x3F, 0, flip, false, 0));
        var vine = assertInstanceOf(SozSpringVineObjectInstance.class, result);
        vine.setServices(new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> null, List::of)));
        return vine;
    }
    private TestablePlayableSprite player(int localX) {
        var p = new TestablePlayableSprite("sonic", (short)0, (short)0);
        p.setCentreX((short)(0x200 - 0x30 + localX));
        p.setCentreY((short)0x300);
        p.setAirForTest(false); p.setOnObject(true);
        return p;
    }
    private void stand(SozSpringVineObjectInstance v, TestablePlayableSprite p) {
        v.onSolidContact(p, new SolidContact(true,false,false,true,false), 0);
    }
    @Test void registryRetainsHczConveyorSpikesInS3kl() {
        var o = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 1; }
        }.create(new ObjectSpawn(0,0,0x3F,0,0,false,0));
        assertInstanceOf(HCZConveyorSpikeObjectInstance.class,o);
    }
    @Test void initialSurfaceUsesEveryPixelAndParentAnchorMovesDownForty() {
        var v=vine(0); v.update(0,null);
        assertEquals(0x328,v.getY()); assertEquals(0,v.getSlopeSampleShift());
        byte[] data=v.getSlopeData(); assertEquals(96,data.length);
        for(int i=0;i<96;i++) assertEquals(i,data[i]);
    }
    @Test void crossingFromLeftLaunchesOnSixtyWithoutButtonInput() {
        var v=vine(0); var p=player(59); stand(v,p); v.update(0,p);
        assertFalse(p.getAir()); p.setCentreX((short)(0x200+12)); v.update(1,p);
        assertEquals(-0xEF0,p.getXSpeed()); assertEquals(-0xEF0,p.getYSpeed());
        assertTrue(p.getAir()); assertFalse(p.isOnObject()); assertFalse(p.isJumping());
        assertEquals(0x10,p.getAnimationId());
    }
    @Test void reverseCrossingAppliesGroundCorrectionBeforeLaunch() {
        var v=vine(0); var p=player(61); p.setGSpeed((short)0x100);
        stand(v,p); v.update(0,p); v.update(1,p);
        assertEquals(0xE8,p.getGSpeed()); assertFalse(p.getAir());
        p.setCentreX((short)(0x200+11)); v.update(2,p);
        assertEquals(0xD0,p.getGSpeed()); assertEquals(-0xEF0,p.getYSpeed());
    }
    @Test void flippedCorrectionUsesNotWordRatherThanNegation() {
        var v=vine(1); var p=player(34); stand(v,p); v.update(0,p); v.update(1,p);
        assertEquals(25,p.getGSpeed());
        p.setCentreX((short)(0x200-0x30+36)); v.update(2,p);
        assertEquals(0xEF0,p.getXSpeed()); assertEquals(-0xEF0,p.getYSpeed());
    }
    @Test void rightEdgeIsExclusiveAndStaleMarkerClearsAfterContactExit() {
        var v=vine(0); var p=player(59); stand(v,p); v.update(0,p);
        p.setCentreX((short)(0x200+0x30)); v.update(1,p); assertFalse(p.getAir());
        v.onSolidContactCleared(p,1); v.update(2,p);
        p.setCentreX((short)(0x200+12)); stand(v,p); v.update(3,p);
        assertFalse(p.getAir(),"new ride establishes a side rather than carrying the old crossing marker");
    }
    @Test void pivotZeroPreservesPreviousGeneratedSurface() {
        var v=vine(0); var p=player(24); stand(v,p); v.update(0,p);
        byte[] previous=v.getSlopeData(); p.setCentreX((short)(0x200-0x30)); v.update(1,p);
        assertArrayEquals(previous,v.getSlopeData());
    }
    @Test void playerOneOwnsSharedPivotAfterPlayerTwoAndExtraFollower() {
        var v=vine(0); var p=player(24); var p2=player(48); var p3=player(80);
        v.setServices(new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> p, () -> List.of(p2,p3))));
        stand(v,p); stand(v,p2); stand(v,p3); v.update(0,p);
        var solo=vine(0); var q=player(24); stand(solo,q); solo.update(0,q);
        assertArrayEquals(solo.getSlopeData(),v.getSlopeData());
    }
    @Test void rewindPreservesCrossingMarkerAndReboundPhase() {
        var v=vine(0); var p=player(59); stand(v,p); v.update(0,p);
        var state=v.captureRewindState(); var restored=vine(0); restored.restoreRewindState(state);
        p.setCentreX((short)(0x200+12)); restored.update(1,p);
        assertEquals(-0xEF0,p.getYSpeed());
        restored.onSolidContactCleared(p,1);
        var oscillating=restored.captureRewindState(); var replay=vine(0); replay.restoreRewindState(oscillating);
        for(int i=0;i<55;i++) {
            restored.update(i,p); replay.update(i,p);
            assertArrayEquals(restored.getSlopeData(),replay.getSlopeData());
        }
        byte[] data=replay.getSlopeData();
        for(int i=0;i<data.length;i++) assertEquals(i,data[i]);
    }
    @Test void oneLaterSlotChildDrawsEightPiecesAndRetainsItsSurfaceAcrossRecreation() {
        var renderer=org.mockito.Mockito.mock(com.openggf.level.render.PatternSpriteRenderer.class);
        org.mockito.Mockito.when(renderer.isReady()).thenReturn(true);
        var renderManager=org.mockito.Mockito.mock(ObjectRenderManager.class);
        org.mockito.Mockito.when(renderManager.getRenderer(
                com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SOZ_SPRING_VINE)).thenReturn(renderer);
        var v=vine(0); var p=player(24);
        var services=new StubObjectServices() {
            @Override public ObjectRenderManager renderManager() { return renderManager; }
        }.withPlayerQuery(new ObjectPlayerQuery(() -> p,List::of)).withIsolatedObjectManager();
        v.setServices(services); services.objectManager().addDynamicObject(v);
        stand(v,p); v.update(0,p); v.update(1,p);
        var children=services.objectManager().getActiveObjects().stream()
                .filter(SozSpringVineObjectInstance.Display.class::isInstance).toList();
        assertEquals(1,children.size());
        var child=(SozSpringVineObjectInstance.Display)children.getFirst();
        assertTrue(child.getSlotIndex()>v.getSlotIndex());
        v.appendRenderCommands(new java.util.ArrayList<>());
        org.mockito.Mockito.verifyNoInteractions(renderer);
        child.appendRenderCommands(new java.util.ArrayList<>());
        byte[] heights=v.getSlopeData();
        int fixedX=(0x200-40)<<16;
        for(int i=0;i<8;i++,fixedX+=0xB504F) org.mockito.Mockito.verify(renderer).drawFrameIndex(
                0,fixedX>>16,0x300+40+6-heights[6+i*12],false,false);
        org.mockito.Mockito.verify(renderer,org.mockito.Mockito.times(8)).drawFrameIndex(
                org.mockito.ArgumentMatchers.eq(0),org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(false));
        var restored=new SozSpringVineObjectInstance.Display(child.getSpawn());
        restored.setServices(services); restored.restoreRewindState(child.captureRewindState());
        org.mockito.Mockito.clearInvocations(renderer);
        restored.appendRenderCommands(new java.util.ArrayList<>());
        fixedX=(0x200-40)<<16;
        for(int i=0;i<8;i++,fixedX+=0xB504F) org.mockito.Mockito.verify(renderer).drawFrameIndex(
                0,fixedX>>16,0x300+40+6-heights[6+i*12],false,false);
        assertTrue(v.checksOutOfRangeAfterRoutine()); assertTrue(child.checksOutOfRangeAfterRoutine());
        assertEquals(v.isCustomOutOfRange(0x2000),child.isCustomOutOfRange(0x2000));
        assertTrue(child.isCustomOutOfRange(0x2000));
    }
    @Test void unchangedSurfaceSkipsGenerationAndDisplayWritesWithoutSuppressingCrossing() throws Exception {
        var v=vine(0); var p=player(24);
        var services=new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> p,List::of))
                .withIsolatedObjectManager();
        v.setServices(services); services.objectManager().addDynamicObject(v); stand(v,p);
        for(int i=0;i<4;i++) v.update(i,p);
        var child=display(services);
        byte[] settled=v.getSlopeData();
        for(int i=4;i<260;i++) {
            v.update(i,p);
            assertFalse(rebuild(v), "unchanged pivot/tension must bypass the 96-byte generator");
            assertFalse(distribute(child,settled,24,-0x100), "unchanged child inputs must bypass eight Y writes");
            assertArrayEquals(settled,v.getSlopeData());
        }
        assertDisplaySurface(child,settled);
        assertSame(v.getSolidParams(),v.getSolidParams());
        byte[] external=v.getSlopeData(); external[12]^=0x7F;
        assertArrayEquals(settled,v.getSlopeData(),"slope queries remain defensive");
        p.setCentreX((short)(0x200+12)); v.update(260,p);
        assertEquals(-0xEF0,p.getYSpeed()); assertTrue(p.getAir());
    }

    @Test void replacementDisplayInSavedSlotReceivesUnchangedSurface() throws Exception {
        var v=vine(0); var p=player(24);
        var services=new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> p,List::of))
                .withIsolatedObjectManager();
        v.setServices(services); services.objectManager().addDynamicObject(v); stand(v,p);
        for(int i=0;i<4;i++) v.update(i,p);
        var previous=display(services); int slot=previous.getSlotIndex();
        services.objectManager().removeDynamicObject(previous);
        var replacement=new SozSpringVineObjectInstance.Display(previous.getSpawn());
        replacement.setServices(services); services.objectManager().addDynamicObject(replacement);
        assertEquals(slot,replacement.getSlotIndex());
        v.update(4,p);
        assertDisplaySurface(replacement,v.getSlopeData());
        assertFalse(distribute(replacement,v.getSlopeData(),24,-0x100));
    }

    @Test void rewindInvalidatesDerivedCachesButPivotZeroKeepsCapturedSurfaceAndChildYs() throws Exception {
        var v=vine(0); var p=player(24);
        var services=new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> p,List::of))
                .withIsolatedObjectManager();
        v.setServices(services); services.objectManager().addDynamicObject(v); stand(v,p);
        for(int i=0;i<4;i++) v.update(i,p);
        var child=display(services); byte[] surface=v.getSlopeData();
        var standing=v.captureRewindState(); var childState=child.captureRewindState();
        p.setCentreX((short)(0x200-0x30)); v.update(4,p);
        var zero=v.captureRewindState(); int[] childYs=displayYs(child).clone();
        p.setCentreX((short)(0x200-0x30+48)); v.update(5,p);
        assertFalse(java.util.Arrays.equals(surface,v.getSlopeData()));
        v.restoreRewindState(zero); child.restoreRewindState(childState);
        p.setCentreX((short)(0x200-0x30)); v.update(6,p);
        assertFalse(rebuild(v));
        assertArrayEquals(surface,v.getSlopeData()); assertArrayEquals(childYs,displayYs(child));
        v.restoreRewindState(standing);
        assertArrayEquals(surface,v.getSlopeData());
        assertTrue(rebuild(v),"restore invalidates generation even when cached future inputs happened to match");
        assertArrayEquals(surface,v.getSlopeData()); assertFalse(rebuild(v));
        assertTrue(distribute(child,surface,24,-0x100),"child restore invalidates its derived input cache");
        assertFalse(distribute(child,surface,24,-0x100));
        assertDisplaySurface(child,surface);
    }

    private static SozSpringVineObjectInstance.Display display(StubObjectServices services) {
        return services.objectManager().getActiveObjects().stream()
                .filter(SozSpringVineObjectInstance.Display.class::isInstance)
                .map(SozSpringVineObjectInstance.Display.class::cast).findFirst().orElseThrow();
    }
    // Invoke the actual work methods to observe deterministic suppression, without timing or counters in gameplay.
    private static boolean rebuild(SozSpringVineObjectInstance vine) throws Exception {
        var method=SozSpringVineObjectInstance.class.getDeclaredMethod("rebuildSurface");
        method.setAccessible(true); return (boolean)method.invoke(vine);
    }
    private static boolean distribute(SozSpringVineObjectInstance.Display child,byte[] surface,
                                      int pivot,int tension) throws Exception {
        var method=SozSpringVineObjectInstance.Display.class.getDeclaredMethod(
                "updateSurface",byte[].class,boolean.class,int.class,int.class);
        method.setAccessible(true); return (boolean)method.invoke(child,surface,false,pivot,tension);
    }
    private static int[] displayYs(SozSpringVineObjectInstance.Display child) throws Exception {
        var field=SozSpringVineObjectInstance.Display.class.getDeclaredField("ys");
        field.setAccessible(true); return (int[])field.get(child);
    }
    private static void assertDisplaySurface(SozSpringVineObjectInstance.Display child,byte[] heights) throws Exception {
        for(int i=0;i<8;i++) assertEquals((0x300+40+6-heights[6+i*12])&0xFFFF,displayYs(child)[i]);
    }

}
