package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kResultsKosQueueAndChildren {

    @Test
    void existingQueueDelaysPublicationUntilThreeActualResultsArchivesAndTwelveSstsExist()
            throws Exception {
        HeadlessTestFixture fixture = fixture();
        KosinskiModuleQueue queue = fixture.gameplayMode().getKosinskiModuleQueue();
        assertTrue(queue.enqueue(GameServices.rom().getRom(),
                Sonic3kConstants.ART_KOSM_RESULTS_GENERAL_ADDR, 0x2000));

        S3kResultsScreenObjectInstance root = createResults();
        GameServices.level().getObjectManager().addDynamicObject(root);

        assertEquals(0, com.openggf.game.GameServices.hardwareTiming()
                        .incompleteCount(com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE),
                "allocation alone must not execute Obj_LevelResultsInit");
        root.update(0, fixture.sprite());

        // The results screen's three KosM loads are scheduled through the
        // hardware-timing service, so that is where they are observable; the
        // pre-enqueued archive above stays on the gameplay queue and is what makes
        // this a "queue already busy" case.
        assertEquals(3, com.openggf.game.GameServices.hardwareTiming()
                        .incompleteCount(com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE),
                "Obj_LevelResultsInit queues three Kosinski module loads");
        assertEquals(Sonic3kConstants.ART_KOSM_RESULTS_GENERAL_ADDR + 2,
                queue.activeSourceAddress(),
                "Process_Kos_Module_Queue_Init consumes the active archive header immediately");
        assertEquals(List.of(0x2000),
                queue.queuedArchives().stream()
                        .map(KosinskiModuleQueue.ArchiveState::destinationVramBytes).toList());

        // Observe publication itself: the same full frame may create the
        // children and reload Act2, submitting a different terrain KosM batch.
        // A post-frame global count cannot identify still-pending results art.
        int guard = 0;
        while (resultChildren().isEmpty()) {
            assertEquals(0, GameServices.level().getCurrentAct(),
                    "Obj_LevelResultsCreate may not publish while its own KosM loads are pending");
            assertTrue(resultChildren().isEmpty());
            assertEquals(0, root.activeResultsFrames(),
                    "Obj_LevelResultsCreate must return without consuming the 360-frame wait");
            fixture.stepFrame(false, false, false, false, false);
            assertTrue(++guard < 64, "results KosM work must complete");
        }
        assertTrue(queue.isIdle());
        assertTrue(root.hasLoadedResultsArt(),
                "publication requires claiming all three completed results archives");

        assertEquals(1, GameServices.level().getCurrentAct(),
                "Events_fg_5 publication must occur only after the real child allocation pass");
        List<S3kResultsElementObjectInstance> children = resultChildren();
        assertEquals(12, children.size());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
                children.stream().map(S3kResultsElementObjectInstance::entryIndex).toList());
        assertTrue(children.stream().allMatch(child -> child.parentResults() == root));
        assertTrue(children.stream().allMatch(child -> child.getSlotIndex() > root.getSlotIndex()));
        assertEquals(children.stream().map(AbstractObjectInstance::getSlotIndex).sorted().toList(),
                children.stream().map(AbstractObjectInstance::getSlotIndex).toList(),
                "CreateNewSprite4 must preserve native ObjArray_LevResults order in ascending SST slots");
        assertEquals(0, root.activeResultsFrames(),
                "the successful creation dispatch still returns before Obj_LevelResultsWait");

        for (int i = 0; i < 70; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertFalse(root.hasPlayedResultsMusic());
        fixture.stepFrame(false, false, false, false, false);
        assertTrue(root.hasPlayedResultsMusic(),
                "act-clear music begins on Wait dispatch 71, excluding every Kos/allocation wait");
    }

    @Test
    void firstAllocateObjectAfterCurrentFailureRetriesWithoutEarlyPublication() throws Exception {
        HeadlessTestFixture fixture = fixture();
        S3kResultsScreenObjectInstance root = createResults();
        ObjectManager manager = GameServices.level().getObjectManager();
        manager.addDynamicObject(root);

        awaitResultsArt(fixture, root);
        assertTrue(fixture.gameplayMode().getKosinskiModuleQueue().isIdle());

        // Exercise this single owner's allocation boundary. A whole level
        // frame could retire other SSTs or consume slots before Create runs.
        List<SlotFiller> fillers = new ArrayList<>();
        while (true) {
            SlotFiller filler = ObjectConstructionContext.construct(TestEnvironment.objectServices(),
                    SlotFiller::new);
            manager.addDynamicObject(filler);
            if (filler.isDestroyed()) {
                break;
            }
            fillers.add(filler);
        }
        root.update(GameServices.level().getObjectManager().getVblaCounter(), fixture.sprite());
        assertEquals(0, GameServices.level().getCurrentAct());
        assertTrue(resultChildren().isEmpty());
        assertFalse(((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .getFbzEvents().isEventsFg5());

        fillers.stream()
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex).reversed())
                .limit(12)
                .forEach(manager::removeDynamicObject);
        root.update(GameServices.level().getObjectManager().getVblaCounter(), fixture.sprite());

        assertTrue(actTransitionPublished());
        assertEquals(0, GameServices.level().getCurrentAct(),
                "the isolated Create dispatch precedes ScreenEvents' reload");
        assertEquals(12, resultChildren().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11})
    void laterCreateNewSprite4FailurePublishesItsPrefixButLeavesNativeResidualCount(
            int availablePrefixSlots) throws Exception {
        HeadlessTestFixture fixture = fixture();
        S3kResultsScreenObjectInstance root = createResults();
        ObjectManager manager = GameServices.level().getObjectManager();
        manager.addDynamicObject(root);
        awaitResultsArt(fixture, root);

        List<SlotFiller> fillers = fillEveryDynamicSlot(manager);
        fillers.stream()
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex).reversed())
                .limit(availablePrefixSlots)
                .forEach(manager::removeDynamicObject);
        root.update(GameServices.level().getObjectManager().getVblaCounter(), fixture.sprite());

        assertTrue(actTransitionPublished(),
                "a failure after the initial allocation still publishes Events_fg_5");
        assertEquals(0, GameServices.level().getCurrentAct());
        List<S3kResultsElementObjectInstance> prefix = resultChildren();
        assertEquals(availablePrefixSlots, prefix.size());
        assertEquals(12, root.nativeChildrenRemaining(),
                "ROM $30 is not reduced to the successfully allocated prefix");
        prefix.forEach(root::childExited);
        assertEquals(12 - availablePrefixSlots, root.nativeChildrenRemaining(),
                "the unallocated suffix leaves Obj_LevelResultsWait2 permanently residual");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rewindRestoresQueuePhaseExactChildSlotsAndParentLinksWithoutDuplication(
            boolean inPlace) throws Exception {
        HeadlessTestFixture fixture = fixture();
        ObjectManager manager = GameServices.level().getObjectManager();
        S3kResultsScreenObjectInstance root = ObjectConstructionContext.construct(
                TestEnvironment.objectServices(),
                () -> new S3kResultsScreenObjectInstance(PlayerCharacter.TAILS_ALONE, 1));
        manager.addDynamicObject(root);
        awaitResultsArt(fixture, root);
        root.update(1, fixture.sprite());
        List<S3kResultsElementObjectInstance> capturedChildren = resultChildren();
        assertEquals(12, capturedChildren.size());
        List<Integer> capturedSlots = capturedChildren.stream()
                .map(AbstractObjectInstance::getSlotIndex).toList();

        KosinskiModuleQueue queue = fixture.gameplayMode().getKosinskiModuleQueue();
        assertTrue(queue.enqueue(GameServices.rom().getRom(),
                Sonic3kConstants.ART_KOSM_SS_RESULTS_ADDR, 0x4000));
        queue.processNativeFrame();
        assertEquals(KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS, queue.phase());
        KosinskiModuleQueue.Snapshot capturedQueue = queue.capture();
        CompositeSnapshot snapshot = fixture.gameplayMode().getRewindRegistry().capture();

        if (!inPlace) {
            manager.setRewindInPlaceRestoreEnabledForTest(false);
        }
        queue.processNativeFrame();
        fixture.gameplayMode().getRewindRegistry().restore(snapshot);

        S3kResultsScreenObjectInstance restoredRoot = manager.getActiveObjects().stream()
                .filter(S3kResultsScreenObjectInstance.class::isInstance)
                .map(S3kResultsScreenObjectInstance.class::cast)
                .findFirst().orElseThrow();
        List<S3kResultsElementObjectInstance> restoredChildren = resultChildren();
        assertEquals(12, restoredChildren.size(), "restore must not duplicate the SST family");
        assertEquals(capturedSlots, restoredChildren.stream()
                .map(AbstractObjectInstance::getSlotIndex).toList());
        assertTrue(restoredChildren.stream().allMatch(child -> child.parentResults() == restoredRoot));
        assertEquals(capturedQueue, queue.capture(),
                "object reconstruction must not enqueue results art after queue restore");
        assertEquals(PlayerCharacter.TAILS_ALONE, restoredRoot.resultsCharacter());
        assertEquals(1, restoredRoot.resultsAct());
        assertTrue(restoredRoot.hasLoadedResultsArt(),
                "derived renderer art must rebuild from restored character/act scalars");
        if (!inPlace) {
            assertNotSame(root, restoredRoot);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completedResultsArtStillWaitsForAnUnrelatedPhysicalModule(boolean retryAfterClaim)
            throws Exception {
        HeadlessTestFixture fixture = fixture();
        S3kResultsScreenObjectInstance root = createResults();
        GameServices.level().getObjectManager().addDynamicObject(root);
        awaitResultsArt(fixture, root);

        if (retryAfterClaim) {
            ObjectManager manager = GameServices.level().getObjectManager();
            List<SlotFiller> fillers = fillEveryDynamicSlot(manager);
            root.update(1, fixture.sprite());
            assertTrue(root.hasLoadedResultsArt());
            assertTrue(resultChildren().isEmpty(), "full SST keeps Create active after its art claim");
            fillers.stream().sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex).reversed())
                    .limit(12).forEach(manager::removeDynamicObject);
        }
        var moduleQueue = S3kRuntimeArtCoordinator.current().moduleQueue();
        var other = moduleQueue.queue(GameServices.rom().getRom(),
                Sonic3kConstants.ART_KOSM_SS_RESULTS_ADDR, 0x200);
        assertTrue(moduleQueue.hasPendingPhysicalModules());
        assertFalse(moduleQueue.isReady(other));
        root.update(1, fixture.sprite());
        assertTrue(resultChildren().isEmpty(),
                "Obj_LevelResultsCreate polls global Kos_modules_left, not only its own handles");
        assertEquals(retryAfterClaim, root.hasLoadedResultsArt(),
                "the global gate preserves an earlier claim while blocking every Create retry");
        assertEquals(0, GameServices.level().getCurrentAct());

        int guard = 0;
        while (!moduleQueue.isReady(other)) {
            serviceArtBoundary(fixture);
            assertTrue(++guard < 256, "the unrelated physical archive must retire");
        }
        assertFalse(moduleQueue.hasPendingPhysicalModules());
        root.update(2, fixture.sprite());
        assertTrue(root.hasLoadedResultsArt());
        assertEquals(12, resultChildren().size());
        assertTrue(actTransitionPublished());
        assertEquals(0, GameServices.level().getCurrentAct());
    }

    private static boolean actTransitionPublished() {
        return ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .getFbzEvents().isEventsFg5();
    }

    /** Stops at completed art without dispatching Create or a later level reload. */
    private static void awaitResultsArt(HeadlessTestFixture fixture,
                                        S3kResultsScreenObjectInstance root) {
        root.update(GameServices.level().getObjectManager().getVblaCounter(), fixture.sprite());
        var timing = GameServices.hardwareTiming();
        var submitted = timing.pendingHandles().stream()
                .filter(handle -> handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE).toList();
        assertEquals(3, submitted.size(), "the fixture submits the three actual results archives");
        int guard = 0;
        while (submitted.stream().anyMatch(handle -> !timing.isReady(handle))) {
            assertTrue(resultChildren().isEmpty(), "service-only work cannot dispatch Create");
            assertEquals(0, root.activeResultsFrames());
            serviceArtBoundary(fixture);
            assertTrue(++guard < 256, "results KosM work must complete");
        }
        assertFalse(S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules(),
                "native Kos_modules_left must be empty at the pre-Create checkpoint");
        assertTrue(resultChildren().isEmpty(), "child allocation belongs to the next dispatch");
    }

    private static void serviceArtBoundary(HeadlessTestFixture fixture) {
        // Same physical services as LevelLoop, deliberately without the object
        // dispatch. This is the pre-Create fixture boundary, not a gameplay tick.
        HardwareBoundaryPump.service(HardwareServiceBoundary.VINT_SERVICE);
        HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
        HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        fixture.gameplayMode().getKosinskiModuleQueue().processNativeFrame();
    }

    private static HeadlessTestFixture fixture() {
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 0)
                .startPosition((short) 0x2EE1, (short) 0x0540)
                .startPositionIsCentre()
                .build();
    }

    private static S3kResultsScreenObjectInstance createResults() {
        return ObjectConstructionContext.construct(TestEnvironment.objectServices(),
                () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_ALONE, 0));
    }

    private static List<S3kResultsElementObjectInstance> resultChildren() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(S3kResultsElementObjectInstance.class::isInstance)
                .map(S3kResultsElementObjectInstance.class::cast)
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                .toList();
    }

    private static List<SlotFiller> fillEveryDynamicSlot(ObjectManager manager) {
        List<SlotFiller> fillers = new ArrayList<>();
        while (true) {
            SlotFiller filler = ObjectConstructionContext.construct(TestEnvironment.objectServices(),
                    SlotFiller::new);
            manager.addDynamicObject(filler);
            if (filler.isDestroyed()) {
                return fillers;
            }
            fillers.add(filler);
        }
    }

    private static final class SlotFiller extends AbstractObjectInstance {
        private SlotFiller() {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "ResultsSlotFiller");
            setRomWorldPositioned(false);
        }

        @Override
        public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) {
        }
    }
}
