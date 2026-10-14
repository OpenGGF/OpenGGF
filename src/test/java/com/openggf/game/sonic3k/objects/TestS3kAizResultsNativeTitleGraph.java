package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.AbstractResultsScreen;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Declared native results-exit boundary, independent of a cold boss/controller completion claim. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAizResultsNativeTitleGraph {
    @AfterEach void clear() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void retainedSstUsesHigherSlotMovementAndRetirementLatchesWithWholeWorldReplay(WidescreenAspect aspect)
            throws Exception {
        var fixture=fixture(aspect);
        var manager=GameServices.level().getObjectManager();
        // Independent component setup: give this native owner a known free after-current prefix.
        for (int slot=40;slot<=44;slot++) {
            var old=NativeSstSlotAccess.currentOwner(manager,slot);
            if (old!=null) manager.removeDynamicObject(old);
        }
        var root=manager.createDynamicObjectAtSlot(() -> new S3kResultsScreenObjectInstance(true),40);
        assertNotNull(root);
        Field state=AbstractResultsScreen.class.getDeclaredField("state");
        state.setAccessible(true); state.setInt(root,4); // Obj_LevelResultsWait2's children already retired.
        Method exit=S3kResultsScreenObjectInstance.class.getDeclaredMethod("onExitReady");
        exit.setAccessible(true); exit.invoke(root); // loc_2DD06 replaces the pointer and returns.
        GameServices.level().getLevelGamestate().setRings(17);
        fixture.sprite().setRingCount(17);
        assertFalse(root.isDestroyed());
        assertEquals(S3kResultsScreenObjectInstance.CarriedTitlePhase.TITLE_CARD_INIT,root.carriedTitlePhase());
        assertTrue(children().isEmpty());
        fixture.stepIdleFrames(1); // Obj_TitleCardInit; no child can allocate on this pass.
        assertFalse(root.isDestroyed());
        assertTrue(children().isEmpty());
        int queuePolls=0;
        while (children().isEmpty() && queuePolls++<256) fixture.stepIdleFrames(1);
        assertEquals(4,children().size(),"Create publishes the complete literal ROM table");
        assertTrue(((com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager)GameServices.module().getTitleCardProvider()).capture().artLoaded(),
                "literal child admission must also have successfully loaded its ROM presentation bytes");
        assertEquals(List.of(41,42,43,44),children().stream().map(AbstractObjectInstance::getSlotIndex).toList());
        assertEquals(List.of(0x250,0x2EC,0x334,0xE0),children().stream().map(AbstractObjectInstance::getX).toList(),
                "higher-slot children perform their first $10 movement on the creation scan");
        assertEquals(List.of(0xE0,0x100,0x120,0x20),children().stream().map(AbstractObjectInstance::getY).toList());
        var registry=fixture.gameplayMode().getRewindRegistry();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        CompositeSnapshot birth=registry.capture();
        fixture.stepIdleFrames(27);
        assertEquals(0x184,children().get(2).getX(),"28th admitted act-element dispatch reaches $46");
        assertEquals(17,GameServices.level().getLevelGamestate().getRings());
        fixture.stepIdleFrames(1);
        assertEquals(17,GameServices.level().getLevelGamestate().getRings(),"lower owner clears the final $34 write and returns");
        fixture.stepIdleFrames(1);
        assertEquals(0,GameServices.level().getLevelGamestate().getRings(),"only the next stationary parent poll executes loc_2D810");
        fixture.stepIdleFrames(16);
        CompositeSnapshot after45=registry.capture();
        for (int cycle=0;cycle<2;cycle++) {
            registry.restore(birth); assertWorld(birth,registry.capture());
            fixture.stepIdleFrames(45); assertWorld(after45,registry.capture());
        }
        var current=(S3kResultsScreenObjectInstance)NativeSstSlotAccess.currentOwner(manager,40);
        while (current.carriedTitleWaitTimer()>1) fixture.stepIdleFrames(1);
        CompositeSnapshot beforeExit=registry.capture();
        fixture.stepIdleFrames(45);
        assertNull(NativeSstSlotAccess.currentOwner(manager,40),"owner clears only after every higher-slot child retires");
        assertTrue(children().isEmpty());
        CompositeSnapshot afterExit=registry.capture();
        for (int cycle=0;cycle<2;cycle++) {
            registry.restore(beforeExit); assertWorld(beforeExit,registry.capture());
            fixture.stepIdleFrames(45); assertWorld(afterExit,registry.capture());
        }
    }

    @org.junit.jupiter.api.Test
    void firstCheckedFailureRetriesThenPartialPrefixUsesItsOwnMovementLatch() throws Exception {
        var fixture=fixture(WidescreenAspect.NATIVE_4_3);
        var manager=GameServices.level().getObjectManager();
        var previous=NativeSstSlotAccess.currentOwner(manager,40);
        if (previous!=null) manager.removeDynamicObject(previous);
        var root=manager.createDynamicObjectAtSlot(() -> new S3kResultsScreenObjectInstance(true),40);
        Field state=AbstractResultsScreen.class.getDeclaredField("state");
        state.setAccessible(true); state.setInt(root,4);
        Method exit=S3kResultsScreenObjectInstance.class.getDeclaredMethod("onExitReady");
        exit.setAccessible(true); exit.invoke(root);
        // Literal legal scene-table pressure; these no-op occupants cannot free themselves.
        for (int count=0;count<256;count++) {
            var filler=ObjectConstructionContext.construct(TestEnvironment.objectServices(),
                    () -> new Filler(new com.openggf.level.objects.ObjectSpawn(0,0,0,0,0,false,0)));
            manager.addDynamicObject(filler);
            if (filler.isDestroyed()) break;
        }
        int queueTicks=0;
        fixture.stepIdleFrames(1);
        while (com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules()
                && queueTicks++<256) fixture.stepIdleFrames(1);
        fixture.stepIdleFrames(1);
        assertTrue(children().isEmpty(),"failed first AllocateObjectAfterCurrent leaves Create active");
        assertEquals(S3kResultsScreenObjectInstance.CarriedTitlePhase.TITLE_CARD_CREATE,root.carriedTitlePhase());
        manager.removeDynamicObject(NativeSstSlotAccess.currentOwner(manager,41));
        GameServices.level().getLevelGamestate().setRings(17);
        fixture.sprite().setRingCount(17);
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry=fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before=registry.capture();
        fixture.stepIdleFrames(1);
        assertEquals(1,children().size(),"later failure preserves the admitted prefix and proceeds to Wait");
        assertEquals(41,children().getFirst().getSlotIndex());
        fixture.stepIdleFrames(19);
        assertEquals(0x120,children().getFirst().getX(),"only the name child exists and needs20 moves");
        fixture.stepIdleFrames(1);
        assertEquals(17,GameServices.level().getLevelGamestate().getRings());
        fixture.stepIdleFrames(1);
        assertEquals(0,GameServices.level().getLevelGamestate().getRings(),"partial prefix may not wait for absent28-move act child");
        fixture.stepIdleFrames(23);
        CompositeSnapshot after=registry.capture();
        for (int cycle=0;cycle<2;cycle++) {
            registry.restore(before); assertWorld(before,registry.capture());
            fixture.stepIdleFrames(45); assertWorld(after,registry.capture());
        }
    }

    @org.junit.jupiter.api.Test
    void pureContextSpawnRecreationKeepsExactParent2WithAnotherResultsOwner() throws Exception {
        var fixture=fixture(WidescreenAspect.NATIVE_4_3);
        var manager=GameServices.level().getObjectManager();
        for (int slot:new int[]{69,70,71}) {
            var old=NativeSstSlotAccess.currentOwner(manager,slot);
            if (old!=null) manager.removeDynamicObject(old);
        }
        var other=manager.createDynamicObjectAtSlot(() -> new S3kResultsScreenObjectInstance(true),69);
        var parent=manager.createDynamicObjectAtSlot(() -> new S3kResultsScreenObjectInstance(true),70);
        var child=manager.createDynamicObjectAtSlot(() -> S3kInLevelTitleCardElementObjectInstance.create(parent,0),71);
        assertFalse(other.isDestroyed(),"independent parent occupies its own genuinely free SST");
        assertFalse(parent.isDestroyed());
        assertFalse(child.isDestroyed());
        var spawn=child.getSpawn();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry=fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before=registry.capture();
        child.update(manager.getVblaCounter(),fixture.sprite());
        CompositeSnapshot after=registry.capture();
        Field link=S3kInLevelTitleCardElementObjectInstance.class.getDeclaredField("parent");
        link.setAccessible(true);
        Field latch=S3kResultsScreenObjectInstance.class.getDeclaredField("nativeTitleMovementLatch");
        latch.setAccessible(true);
        for (int cycle=0;cycle<2;cycle++) {
            registry.restore(before); assertWorld(before,registry.capture());
            var restoredChild=(S3kInLevelTitleCardElementObjectInstance)NativeSstSlotAccess.currentOwner(manager,71);
            var restoredParent=NativeSstSlotAccess.currentOwner(manager,70);
            var restoredOther=NativeSstSlotAccess.currentOwner(manager,69);
            assertNotSame(child,restoredChild);
            assertSame(spawn,restoredChild.getSpawn(),"factory retains exact immutable ctx.spawn resource");
            assertSame(restoredParent,link.get(restoredChild),"parent2 never selects another results owner");
            restoredChild.update(manager.getVblaCounter(),fixture.sprite());
            assertTrue(latch.getBoolean(restoredParent));
            assertFalse(latch.getBoolean(restoredOther));
            assertWorld(after,registry.capture());
        }
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void hiddenRevealRetainsExactlyOnePhysicalOwnerThroughCleanupAndWholeWorldReplay(WidescreenAspect aspect)
            throws Exception {
        var fixture=fixture(aspect);
        var manager=GameServices.level().getObjectManager();
        // Declared local loc_83760 admission; actual cold BK2 separately proves the real placed instance.
        for (int slot:new int[]{70,80}) {
            var old=NativeSstSlotAccess.currentOwner(manager,slot);
            if (old!=null) manager.removeDynamicObject(old);
        }
        var hidden=manager.createDynamicObjectAtSlot(() -> new S3kHiddenMonitorInstance(
                new com.openggf.level.objects.ObjectSpawn(0x80,0x3C0,0x80,3,0,false,0)),70);
        var signpost=manager.createDynamicObjectAtSlot(() -> new S3kSignpostInstance(0x80,0),80);
        Field y=S3kSignpostInstance.class.getDeclaredField("worldY");
        y.setAccessible(true); y.setInt(signpost,0x3C0);
        Field state=S3kSignpostInstance.class.getDeclaredField("state"); state.setAccessible(true);
        @SuppressWarnings({"rawtypes","unchecked"})
        Object after=Enum.valueOf((Class)state.getType(),"AFTER");
        state.set(signpost,after); signpost.setLanded(true);
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry=fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before=registry.capture();
        fixture.stepIdleFrames(1);
        assertTrue(hidden.isDestroyed());
        assertEquals(-1,hidden.getSlotIndex(),"the obsolete handle cannot free the transformed SST");
        assertInstanceOf(Sonic3kMonitorObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,70));
        int next=manager.allocateSlotAfter(69);
        assertEquals(71,next,"ordinary allocation must skip the revealed live monitor");
        manager.releaseDynamicSlot(next);
        fixture.stepIdleFrames(44);
        assertInstanceOf(Sonic3kMonitorObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,70));
        CompositeSnapshot after45=registry.capture();
        for (int cycle=0;cycle<2;cycle++) {
            registry.restore(before); assertWorld(before,registry.capture());
            fixture.stepIdleFrames(1);
            assertInstanceOf(Sonic3kMonitorObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,70));
            int replayNext=manager.allocateSlotAfter(69);
            assertEquals(71,replayNext); manager.releaseDynamicSlot(replayNext);
            fixture.stepIdleFrames(44); assertWorld(after45,registry.capture());
            registry.restore(after45); assertWorld(after45,registry.capture());
        }
    }

    private static final class Filler extends AbstractObjectInstance
            implements com.openggf.level.objects.SpawnRewindRecreatable {
        public Filler(com.openggf.level.objects.ObjectSpawn spawn) {
            super(spawn,"NativeTitlePressureFiller"); setRomWorldPositioned(false);
        }
        @Override public boolean isPersistent() { return true; }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
        @Override public void update(int clock,com.openggf.game.PlayableEntity leader) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }

    private static HeadlessTestFixture fixture(WidescreenAspect aspect) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED,false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,aspect.name());
        config.resolveDisplayAspect();
        GameServices.camera().refreshViewportDimensions(config);
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(0,1).build();
        assertEquals(aspect.pixelWidth(),fixture.camera().getWidth(),"actual production preset before dispatch");
        // The direct fixture load also owns an omitted initial title. Drain its actual
        // lease/FIFO before starting another fresh boundary, rather than stacking archives.
        var art=(com.openggf.game.sonic3k.Sonic3kObjectArtProvider)GameServices.module().getObjectArtProvider();
        int initialTicks=0;
        while ((!art.capture().runtimeArtAdmissionConsumed()
                || com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules())
                && initialTicks++<256) fixture.stepIdleFrames(1);
        assertTrue(art.capture().runtimeArtAdmissionConsumed());
        assertFalse(com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules());
        GameServices.level().loadZoneAndActAtFreshTitleCardBoundary(0,1);
        assertTrue(GameServices.level().skipPendingInitialTitleCardPresentation());
        var manager=GameServices.level().getObjectManager();
        // This is an in-level owner: finish the actual fresh entry owner's lease before declaring results exit.
        int entryTicks=0;
        while (!manager.activeObjectsOfType(S3kFreshTitleCardOwnerObjectInstance.class).isEmpty()
                && entryTicks++<256) fixture.stepIdleFrames(1);
        assertTrue(manager.activeObjectsOfType(S3kFreshTitleCardOwnerObjectInstance.class).isEmpty());
        int enemyTicks=0;
        while (com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules()
                && enemyTicks++<256) fixture.stepIdleFrames(1);
        assertFalse(com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules(),
                "declared results exit follows the initial title's admitted enemy art, as an actual later boss exit does");
        return fixture;
    }

    private static List<S3kInLevelTitleCardElementObjectInstance> children() {
        return GameServices.level().getObjectManager().activeObjectsOfType(S3kInLevelTitleCardElementObjectInstance.class)
                .stream().filter(c -> !c.isDestroyed()).sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }
    private static void assertWorld(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for (String key:expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key,expected.get(key),actual.get(key)),
                    () -> key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
        }
    }
}
