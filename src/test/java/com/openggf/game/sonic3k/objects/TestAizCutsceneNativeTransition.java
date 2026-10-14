package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Literal AIZ1BGE_Finish retained SST and Go_Delete_Sprite_2 contract; declared scenes, not cold input. */
class TestAizCutsceneNativeTransition {
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
        var config=com.openggf.configuration.SonicConfigurationService.getInstance();
        config.clearSessionOverrides(); config.resolveDisplayAspect();
    }

    @Test void emptyNativeOffsetScanIsARealZeroSetAndRejectsInvalidRanges() {
        var request = assertDoesNotThrow(() -> SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .romWorldObjectOffsetRange(0,0).build());
        for (int slot=0;slot<110;slot++)
            assertFalse(request.shouldApplyRomWorldOffset(slot,true,true));
        assertThrows(IllegalArgumentException.class, () -> SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .romWorldObjectOffsetRange(-1,0));
        assertThrows(IllegalArgumentException.class, () -> SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .romWorldObjectOffsetRange(6,5));
    }

    @Test void escapeUnderflowRetainsNativeBitFourGraphUntilTheNextCodeOwnerDispatch() throws Exception {
        var h = new TestAizMinibossFlareNativeGraph.Harness(320);
        h.services.withRomReader(TestAizMinibossFallingShotNativeGraph.rawReader())
                .withGameState(new com.openggf.game.GameStateManager());
        org.mockito.Mockito.when(h.camera.getX()).thenReturn((short)0x2F10);
        org.mockito.Mockito.when(h.camera.getXCopy()).thenReturn((short)0x2F10);
        for (var old:List.copyOf(h.manager.getActiveObjects())) h.manager.removeDynamicObject(old);
        var boss = scene(h.manager);
        call(boss,"onPreExitComplete");
        set(boss,"waitTimer",0); // literal word $0000 -> $FFFF edge, not a fitted delay
        for(var debris:h.manager.activeObjectsOfType(AizMinibossDebrisChild.class)) set(debris,"moving",true);
        h.step(1);
        assertFalse(boss.isDestroyed(),"Go_Delete_Sprite_2 changes code, not physical slot ownership");
        assertEquals(0x10,boss.getCustomFlag(0x38)&0x10);
        for(int slot=5;slot<=16;slot++) {
            var owner=NativeSstSlotAccess.currentOwner(h.manager,slot);
            assertNotNull(owner,"retained marker slot "+slot);
            assertFalse(owner.isDestroyed());
        }
        org.mockito.Mockito.clearInvocations(h.renderer);
        for(var owner:h.manager.getActiveObjects()) owner.appendRenderCommands(new java.util.ArrayList<>());
        org.mockito.Mockito.verifyNoInteractions(h.renderer);
        var rewind=h.manager.rewindSnapshottable();
        var marked=rewind.capture();
        h.step(2);
        for(int slot=5;slot<=16;slot++) assertNull(NativeSstSlotAccess.currentOwner(h.manager,slot));
        var cleared=rewind.capture();
        for(int cycle=0;cycle<2;cycle++) {
            rewind.restore(marked); sameObjects(marked,rewind.capture());
            h.step(2); sameObjects(cleared,rewind.capture());
            rewind.restore(cleared); sameObjects(cleared,rewind.capture());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={320,352,400,528,800})
    @RequiresRom(SonicGame.SONIC_3K)
    void realLoadedAizHandoffRetainsAllNativeSceneSlotsWithoutShiftingTheirWords(int width) throws Exception {
        var config=com.openggf.configuration.SonicConfigurationService.getInstance();
        var preset=java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                .filter(value -> value.pixelWidth()==width).findFirst().orElseThrow();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT,preset.name());
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.TEST_MODE_ENABLED,false);
        config.resolveDisplayAspect();
        assertEquals(width,config.getInt(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS));
        GameServices.camera().refreshViewportDimensions(config);
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(0,0).withSkippedZoneIntro().build();
        assertEquals(width,fixture.camera().getWidth());
        fixture.stepIdleFrames(6);
        var level=GameServices.level();
        var oldManager=level.getObjectManager();
        var events=((com.openggf.game.sonic3k.Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getAizEvents();
        Method prepare=Sonic3kAIZEvents.class.getDeclaredMethod("ensureFireOverlayTilesLoaded");
        prepare.setAccessible(true);
        boolean ready=false;
        for(int n=0;n<5000&&!ready;n++){
            prepare.invoke(events);
            var loaded=Sonic3kAIZEvents.class.getDeclaredField("fireOverlayTilesLoaded");
            loaded.setAccessible(true); ready=loaded.getBoolean(events);
            if(!ready)fixture.stepIdleFrames(1);
        }
        assertTrue(ready,"actual overlay submission must complete before declaring the native load boundary");
        // Declared scene just before AIZ1BGE_Finish, using the actual ROM-loaded level/services.
        for(var old:List.copyOf(oldManager.getActiveObjects())) oldManager.removeDynamicObject(old);
        fixture.camera().setX((short)0x2F10); fixture.camera().setY((short)0x2E0);
        var boss=scene(oldManager); call(boss,"onPreExitComplete");
        var floor=oldManager.createDynamicObjectAtSlot(AizTransitionFloorObjectInstance::new,17);
        var player=fixture.sprite();
        // Literal scene support: the retained helper's own $A0/$10 SolidObjectTop
        // receives an ordinary stopped rider before the native coordinate scan.
        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player,0x2F9C);
        com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player,0x3A0-0x10-player.getYRadius());
        player.setGSpeed((short)0);player.setDirection(com.openggf.physics.Direction.RIGHT);
        oldManager.forceRidingObjectForBootstrap(player,floor);
        int[] x=new int[12],y=new int[12];
        for(int slot=5;slot<=16;slot++) {
            var owner=NativeSstSlotAccess.currentOwner(oldManager,slot);
            x[slot-5]=owner.getX(); y[slot-5]=owner.getY();
        }
        // Declare the already-published fire terrain prerequisite; this case owns the load boundary only.
        set(events,"fireTransitionMutationRequested",true);
        call(events,"requestAct2Transition");
        assertEquals(1,level.getCurrentAct());
        assertEquals(width,fixture.camera().getWidth());
        assertTrue(level.consumeActTransitionRewindBoundaryDuringFrame(),
                "the actual reload retires outgoing rewind history; raw cross-act restore is not an allowed window");
        var manager=level.getObjectManager();
        assertNotSame(oldManager,manager);
        assertSame(boss,NativeSstSlotAccess.currentOwner(manager,5));
        for(int slot=5;slot<=16;slot++) {
            var owner=NativeSstSlotAccess.currentOwner(manager,slot);
            assertNotNull(owner,"Load_Level retains native SST "+slot);
            assertEquals(x[slot-5],owner.getX(),"Offset_SomeObjects skips ordinary scene slot "+slot);
            assertEquals(y[slot-5],owner.getY());
        }
        var registry=fixture.gameplayMode().getRewindRegistry();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        assertSame(floor,NativeSstSlotAccess.currentOwner(manager,17));
        var before=registry.capture();
        fixture.stepIdleFrames(1);
        assertEquals(0x7FFF,floor.getX());assertFalse(floor.isDestroyed());
        assertSame(floor,NativeSstSlotAccess.currentOwner(manager,17));
        assertEquals(0,player.getBalanceState(),"native status7 suppresses stale support edge balance");
        assertEquals(com.openggf.physics.Direction.RIGHT,player.getDirection());
        fixture.stepIdleFrames(44);
        assertNull(NativeSstSlotAccess.currentOwner(manager,17));
        var expected=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before); sameWorld(before,registry.capture());
            fixture.stepIdleFrames(45); sameWorld(expected,registry.capture());
            registry.restore(expected); sameWorld(expected,registry.capture());
        }
        // Independent literal remaining-word boundary, followed by two full-world
        // forward/reverse windows on the actual receiving act's registry.
        var liveBoss=assertInstanceOf(AizMinibossCutsceneInstance.class,NativeSstSlotAccess.currentOwner(manager,5));
        set(liveBoss,"waitTimer",44);
        for(var debris:manager.activeObjectsOfType(AizMinibossDebrisChild.class)) set(debris,"moving",true);
        var beforeRetirement=registry.capture();
        fixture.stepIdleFrames(45);var marked=registry.capture();
        assertFalse(liveBoss.isDestroyed());assertEquals(0x10,liveBoss.getCustomFlag(0x38)&0x10);
        fixture.stepIdleFrames(45);var cleared=registry.capture();
        assertTrue(manager.activeObjectsOfType(AizMinibossCutsceneInstance.class).isEmpty());
        assertTrue(manager.activeObjectsOfType(AizMinibossDebrisChild.class).isEmpty());
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(beforeRetirement);sameWorld(beforeRetirement,registry.capture());
            fixture.stepIdleFrames(45);sameWorld(marked,registry.capture());
            registry.restore(marked);sameWorld(marked,registry.capture());
            fixture.stepIdleFrames(45);sameWorld(cleared,registry.capture());
            registry.restore(cleared);sameWorld(cleared,registry.capture());
        }
    }

    @Test void debrisWaitUnderflowOnlyReplacesCodeAndDoesNotPublishADraw() throws Exception {
        var h=new TestAizMinibossFlareNativeGraph.Harness(320);
        h.services.withGameState(new com.openggf.game.GameStateManager());
        org.mockito.Mockito.when(h.camera.getX()).thenReturn((short)0x2F10);
        org.mockito.Mockito.when(h.services.renderManager().getRenderer(
                com.openggf.game.sonic3k.Sonic3kObjectArtKeys.AIZ_MINIBOSS_SMALL)).thenReturn(h.renderer);
        for(var old:List.copyOf(h.manager.getActiveObjects())) h.manager.removeDynamicObject(old);
        var boss=scene(h.manager);boss.getState().routine=4;set(boss,"waitTimer",1000);
        var debris=assertInstanceOf(AizMinibossDebrisChild.class,NativeSstSlotAccess.currentOwner(h.manager,6));
        set(debris,"waitTimer",0);int x=debris.getX();
        h.step(1);assertEquals(x,debris.getX());
        org.mockito.Mockito.clearInvocations(h.renderer);
        debris.appendRenderCommands(new java.util.ArrayList<>());
        org.mockito.Mockito.verifyNoInteractions(h.renderer);
        h.step(2);assertEquals(x+2,debris.getX());
        debris.appendRenderCommands(new java.util.ArrayList<>());
        org.mockito.Mockito.verify(h.renderer).drawFrameIndex(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(debris.getX()),org.mockito.ArgumentMatchers.eq(debris.getY()),
                org.mockito.ArgumentMatchers.eq(false),org.mockito.ArgumentMatchers.eq(false));
    }

    private static AizMinibossCutsceneInstance scene(ObjectManager manager) throws Exception {
        var boss=manager.createDynamicObjectAtSlot(() -> new AizMinibossCutsceneInstance(
                new ObjectSpawn(0x3020,0x289,0x90,0,0,false,0)),5);
        call(boss,"updateWaitTrigger");
        call(boss,"onInitialDelayComplete");
        assertEquals(6,manager.activeObjectsOfType(AizMinibossDebrisChild.class).size());
        assertEquals(5,boss.getChildComponents().size());
        return boss;
    }
    private static void call(Object target,String name) throws Exception {
        Method method=target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    private static void set(Object target,String name,Object value) throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private static void sameObjects(Object expected,Object actual) {
        var diff=RewindSnapshotDiff.diffKey("object-manager",expected,actual);
        assertTrue(diff.isEmpty(),() -> diff.toString());
    }
    private static void sameWorld(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(var key:expected.entries().keySet()) {
            var left=expected.get(key);var right=actual.get(key);
            if(left instanceof byte[] bytes) assertArrayEquals(bytes,(byte[])right,key);
            else assertTrue(RewindSnapshotDiff.keyEquals(key,left,right),
                    () -> key+RewindSnapshotDiff.diffKey(key,left,right));
        }
    }
}
