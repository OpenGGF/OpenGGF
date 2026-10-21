package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.difficulty.SkySanctuaryMetropolisRecipe;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossOrbChild;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicHeadChild;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectCallbackAbortException;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native recipe intake and managed orb initialization; no cold-route or native-emulator claim. */
class TestSszMetropolisRecipeBirth {
    @Test void acceptedBirthSamplesBeforeFlagsAndOnlyOnce() throws Exception {
        World world = new World();
        world.available = SkySanctuaryMetropolisRecipe.FOURTH_PAIR;
        world.events.dynamicResize(world.state);
        SszMtzBossObjectInstance boss = world.boss();
        assertEquals(1, world.samples);
        assertEquals(SkySanctuaryMetropolisRecipe.FOURTH_PAIR, field(boss, "encounterRecipe"));
        assertEquals(true, field(boss, "encounterRecipeCommitted"));
        assertEquals(false, field(boss, "ordinaryUpdateStarted"));
        assertPublished(world);
        world.available = null; // Already unavailable after birth: the native lifetime is committed.
        world.events.dynamicResize(world.state);
        assertEquals(1, world.samples);
        assertEquals(1, world.manager.getActiveObjects().size());
        assertThrows(IllegalStateException.class,
                () -> boss.commitEncounterRecipe(SkySanctuaryMetropolisRecipe.STOCK));
    }

    @Test void firstProviderAbortPropagatesBeforeCommitmentAndFightingPublication() throws Exception {
        World world = new World();
        world.failure = new DeclinedRecipe();
        assertSame(world.failure, assertThrows(ObjectCallbackAbortException.class,
                () -> world.events.dynamicResize(world.state)));
        assertEquals(SkySanctuaryMetropolisRecipe.STOCK, field(world.boss(), "encounterRecipe"));
        assertEquals(false, field(world.boss(), "encounterRecipeCommitted"));
        assertEquals(false, field(world.boss(), "ordinaryUpdateStarted"));
        assertEquals(0x00FF, world.state.eventsBgWord(2), "native lock precedes optional lookup");
        assertEquals(0, world.state.eventsBgByte(5), "new fault cannot publish fighting ownership");
        assertEquals(1, world.samples);
        assertEquals(1, world.manager.getActiveObjects().size());
        // The accepted partial root belongs to Engine abort teardown; a native
        // event must not swallow the fault, retry, or invent local cleanup.
    }

    @Test void absentOrAlreadyUnavailableBeforeBirthUsesStock() throws Exception {
        World world = new World(); // A fresh lookup returning no value models both native absence and withdrawal.
        world.events.dynamicResize(world.state);
        assertEquals(SkySanctuaryMetropolisRecipe.STOCK, field(world.boss(), "encounterRecipe"));
        assertPublished(world);
        world.available = SkySanctuaryMetropolisRecipe.FOURTH_PAIR;
        world.events.dynamicResize(world.state);
        assertEquals(1, world.samples);
        assertEquals(SkySanctuaryMetropolisRecipe.STOCK, field(world.boss(), "encounterRecipe"));
    }

    @Test void failedActualSlotAllocationNeverSamplesAndKeepsOriginalRetryGate() {
        World world = new World();
        world.manager.reserveAllButNFreeSlots(0);
        world.events.dynamicResize(world.state);
        assertEquals(0, world.samples);
        assertTrue(world.manager.getActiveObjects().isEmpty());
        assertEquals(0x00FF, world.state.eventsBgWord(2), "pre-existing native lock remains until fighting word publication");
        assertEquals(0, world.state.eventsBgByte(5));
        // Native failed allocation retries, unlike FBZ's one-attempt controller.
        world.manager.releaseDynamicSlot(4);
        world.events.dynamicResize(world.state);
        assertEquals(1, world.samples);
        assertPublished(world);
    }

    @Test void unrelatedRuntimeFailureIsNotSwallowed() {
        World world = new World();
        var failure = new IllegalArgumentException("native witness");
        world.failure = failure;
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> world.events.dynamicResize(world.state)));
        assertEquals(1, world.manager.getActiveObjects().size());
        assertEquals(0x00FF, world.state.eventsBgWord(2), "pre-existing native lock remains until fighting word publication");
    }

    @Test void destructivePreUpdateRecreationPreservesCommitmentWithoutProviderExecution() throws Exception {
        World world = new World();
        world.available = SkySanctuaryMetropolisRecipe.FOURTH_PAIR;
        world.events.dynamicResize(world.state);
        SszMtzBossObjectInstance original = world.boss();
        int originalSlot = original.getSlotIndex();
        var registry = new RewindRegistry();
        registry.register(world.manager.rewindSnapshottable());
        var before = registry.capture();
        world.failure = new DeclinedRecipe();
        world.manager.setRewindInPlaceRestoreEnabledForTest(false);
        world.manager.getActiveObjects().stream().toList().forEach(world.manager::removeDynamicObject);
        registry.restore(before);
        var restored = world.boss();
        assertNotSame(original, restored);
        assertEquals(originalSlot, restored.getSlotIndex());
        assertEquals(SkySanctuaryMetropolisRecipe.FOURTH_PAIR, field(restored, "encounterRecipe"));
        assertEquals(true, field(restored, "encounterRecipeCommitted"));
        assertEquals(false, field(restored, "ordinaryUpdateStarted"));
        assertThrows(IllegalStateException.class,
                () -> restored.commitEncounterRecipe(SkySanctuaryMetropolisRecipe.STOCK));
        assertEquals(1, world.samples);
    }

    /** Declared allocator component: this is not an ordinary cold SSZ route. */
    @Test void metropolisFirstOrbAboveParentReturnsFromItsOwnSetupWithoutOrbitOrPublication() {
        var w = new OrbWorld(4, 5, 6, 7, 8, 9, 10, 11, 12);
        w.setup();
        assertEquals(5, w.head().getSlotIndex(), "CreateChild1_Normal scans strictly after the ship");
        assertEquals(List.of(6, 7, 8, 9, 10, 11, 12), w.orbSlots());
        var first = w.orb(6);
        assertEquals(2, first.routineForTest());
        assertEquals(0x24, first.horizontalAngleForTest(), "locret_7AE12 returns before sub_7AEB0");
        assertEquals(0x180, first.priorityWordForTest());
        assertEquals(0x87, first.getCollisionFlags(), "setup writes flags but does not publish");
        assertFalse(first.publishesTouchResponseListEntryThisFrame());
        clearInvocations(w.renderer);
        first.appendRenderCommands(List.of());
        verifyNoInteractions(w.renderer);
        assertDoesNotThrow(() -> assertEquals(0x24, field(first, "verticalAngle")));
        assertEquals(0, first.getX(), "parent allocation writes only code pointer and parent3");
        assertEquals(List.of(7, 8, 9, 10, 11, 12), w.publishedOrbSlots());
        for (int slot = 7; slot <= 12; slot++) {
            assertEquals(new int[]{0x70, 0xB8, 0, 0x4C, 0x94, 0xDC}[slot - 7],
                    w.orb(slot).horizontalAngleForTest(), "higher siblings orbit in this same sweep");
        }
        w.assertGraph();
        w.step();
        assertEquals(0x28, first.horizontalAngleForTest());
        assertTrue(first.publishesTouchResponseListEntryThisFrame());
        first.appendRenderCommands(List.of());
        verify(w.renderer).drawFrameIndex(first.mappingFrameForTest(), first.getX(), first.getY(), false, false, 0);
        assertEquals(List.of(6, 7, 8, 9, 10, 11, 12), w.publishedOrbSlots());
    }

    @Test void metropolisFirstOrbBelowParentWaitsForOwnDispatchAndCreatesGlobalSiblings() {
        var w = new OrbWorld(8, 4, 9, 10, 11, 12, 13, 14, 15);
        w.setup();
        assertEquals(9, w.head().getSlotIndex());
        assertEquals(List.of(4), w.orbSlots(), "the parent allocates just one global slot");
        assertEquals(0, w.orb(4).routineForTest());
        assertEquals(0, w.orb(4).horizontalAngleForTest(), "no constructor-side angle setup");
        assertEquals(0, w.orb(4).getCollisionFlags());
        assertFalse(w.orb(4).publishesTouchResponseListEntryThisFrame());
        assertDoesNotThrow(() -> assertEquals(0, field(w.orb(4), "verticalAngle")));
        clearInvocations(w.renderer);
        w.orb(4).appendRenderCommands(List.of());
        verifyNoInteractions(w.renderer);
        assertTrue(w.publishedOrbSlots().isEmpty());
        w.assertGraph();
        w.step();
        assertEquals(List.of(4, 10, 11, 12, 13, 14, 15), w.orbSlots());
        assertEquals(0x24, w.orb(4).horizontalAngleForTest());
        assertEquals(0x180, w.orb(4).priorityWordForTest());
        assertFalse(w.orb(4).publishesTouchResponseListEntryThisFrame());
        assertEquals(List.of(10, 11, 12, 13, 14, 15), w.publishedOrbSlots());
        w.assertGraph();
    }

    @Test void metropolisSiblingAllocatedIntoAnAlreadyPassedSlotKeepsItsSeedUntilNextSweep() {
        var w = new OrbWorld(8, 6, 9, 10, 11, 12, 13, 14);
        w.setup();
        assertEquals(List.of(6), w.orbSlots());
        w.manager.removeDynamicObject(NativeSstSlotAccess.currentOwner(w.manager, 4));
        w.step();
        assertEquals(List.of(6, 4, 10, 11, 12, 13, 14), w.orbSlots());
        assertEquals(0x6C, w.orb(4).horizontalAngleForTest());
        assertEquals(0x180, w.orb(4).priorityWordForTest());
        assertFalse(w.orb(4).publishesTouchResponseListEntryThisFrame());
        assertEquals(List.of(10, 11, 12, 13, 14), w.publishedOrbSlots());
        w.step();
        assertEquals(0x70, w.orb(4).horizontalAngleForTest());
        w.assertGraph();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void metropolisAllocationFailureRetainsOnlyTheSuccessfulPrefixAndNeverRetries(int siblingCapacity) {
        int[] holes = java.util.stream.IntStream.range(10, 10 + siblingCapacity).toArray();
        var w = new OrbWorld(8, java.util.stream.IntStream.concat(
                java.util.stream.IntStream.of(4, 9), java.util.Arrays.stream(holes)).toArray());
        w.setup();
        assertEquals(List.of(4), w.orbSlots());
        w.step();
        assertEquals(1 + siblingCapacity, w.boss().orbsForTest().size());
        w.assertGraph();
        var before = w.manager.rewindSnapshottable().capture();
        w.manager.removeDynamicObject(NativeSstSlotAccess.currentOwner(w.manager, 20));
        w.step();
        assertEquals(1 + siblingCapacity, w.boss().orbsForTest().size(), "failed suffix never heals");
        w.assertGraph();
        var after = w.manager.rewindSnapshottable().capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            w.manager.getActiveObjects().stream().toList().forEach(w.manager::removeDynamicObject);
            w.manager.rewindSnapshottable().restore(before);
            sameManager(before, w.manager.rewindSnapshottable().capture());
            w.assertGraph();
            w.manager.removeDynamicObject(NativeSstSlotAccess.currentOwner(w.manager, 20));
            w.stepAt(35);
            sameManager(after, w.manager.rewindSnapshottable().capture());
            w.assertGraph();
        }
    }

    @Test void metropolisHeadConsumingTheLastSlotLeavesNoLogicalOrCapturedOrbs() {
        var w = new OrbWorld(8, 9);
        w.setup();
        assertEquals(9, w.head().getSlotIndex());
        assertTrue(w.boss().orbsForTest().isEmpty(), "destroyed allocation shell is not a child");
        w.assertGraph();
        var saved = w.manager.rewindSnapshottable().capture();
        w.manager.getActiveObjects().stream().toList().forEach(w.manager::removeDynamicObject);
        w.manager.rewindSnapshottable().restore(saved);
        sameManager(saved, w.manager.rewindSnapshottable().capture());
        assertTrue(w.boss().orbsForTest().isEmpty());
    }

    @Test void metropolisExhaustedHeadAndOrbAllocationsLeaveNoUnregisteredReferences() {
        var w = new OrbWorld(8);
        w.setup();
        assertNull(w.boss().headForTest());
        assertTrue(w.boss().orbsForTest().isEmpty());
        var saved = w.manager.rewindSnapshottable().capture();
        w.manager.getActiveObjects().stream().toList().forEach(w.manager::removeDynamicObject);
        w.manager.rewindSnapshottable().restore(saved);
        sameManager(saved, w.manager.rewindSnapshottable().capture());
    }

    @Test void metropolisPendingFirstInitAndOrbitRecreateRawGraphAndReplayTwice() {
        var w = new OrbWorld(8, 4, 9, 10, 11, 12, 13, 14, 15);
        // Entry + Obj_Wait $1F through loc_7A712, before parent setup.
        for (int frame = 0; frame < 33; frame++) w.stepAt(frame);
        for (int boundary = 33; boundary <= 36; boundary++) {
            var before = w.manager.rewindSnapshottable().capture();
            w.stepAt(boundary);
            var after = w.manager.rewindSnapshottable().capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                w.manager.getActiveObjects().stream().toList().forEach(w.manager::removeDynamicObject);
                w.manager.rewindSnapshottable().restore(before);
                sameManager(before, w.manager.rewindSnapshottable().capture());
                w.assertGraph();
                w.stepAt(boundary);
                sameManager(after, w.manager.rewindSnapshottable().capture());
                w.assertGraph();
            }
        }
    }

    private static void sameManager(Object expected, Object actual) {
        var diff = RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diff.isEmpty(), () -> diff.toString());
    }

    /** Real SST occupants: reservations alone are deliberately normalized away by rewind. */
    public static final class OrbPoolBlocker extends AbstractObjectInstance
            implements SpawnRewindRecreatable {
        public OrbPoolBlocker(ObjectSpawn spawn) { super(spawn, "DeclaredMtzOrbPressure"); }
        @Override public void update(int count, PlayableEntity player) { }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class OrbWorld {
        final int parentSlot;
        final Camera camera = new Camera(SonicConfigurationService.createStandalone());
        final TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1700, (short) 0x420);
        final TestObjectServices services;
        final com.openggf.level.render.PatternSpriteRenderer renderer = mock(com.openggf.level.render.PatternSpriteRenderer.class);
        final ObjectManager manager;
        int frame;
        OrbWorld(int parentSlot, int... holes) {
            this.parentSlot = parentSlot;
            camera.setX((short) 0x1660); camera.setY((short) 0x280);
            player.setCentreX((short) 0x1700); player.setCentreY((short) 0x420);
            var sprites = mock(SpriteManager.class);
            when(sprites.getMainPlayable()).thenReturn(player);
            var render = mock(com.openggf.level.objects.ObjectRenderManager.class);
            when(render.getRenderer(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SSZ_MTZ_ORBS)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            services = new TestObjectServices() {
                @Override public com.openggf.level.objects.ObjectRenderManager renderManager() { return render; }
            }.withCamera(camera).withSpriteManager(sprites).withGameModule(new Sonic3kGameModule());
            manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null, null, camera, services);
            services.withDirectObjectManager(manager);
            manager.reset(0x1660);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.createDynamicObjectAtSlot(() -> new SszMtzBossObjectInstance(
                    new ObjectSpawn(0, 0, 0, 0, 0, false, 0)), parentSlot);
            for (int slot = 4; slot <= 93; slot++) {
                int allocated = slot;
                if (slot != parentSlot && java.util.Arrays.stream(holes).noneMatch(hole -> hole == allocated)) {
                    manager.createDynamicObjectAtSlot(() -> new OrbPoolBlocker(
                            new ObjectSpawn(0, 0, 0, 0, 0, false, 0)), slot);
                }
            }
        }
        SszMtzBossObjectInstance boss() { return manager.activeObjectsOfType(SszMtzBossObjectInstance.class).getFirst(); }
        SszMechaSonicHeadChild head() {
            return manager.activeObjectsOfType(SszMechaSonicHeadChild.class).getFirst();
        }
        SszMtzBossOrbChild orb(int slot) {
            return (SszMtzBossOrbChild)
                    NativeSstSlotAccess.currentOwner(manager, slot);
        }
        List<Integer> orbSlots() { return boss().orbsForTest().stream().map(o -> o.getSlotIndex()).toList(); }
        List<Integer> publishedOrbSlots() {
            var snapshot = (ObjectManagerSnapshot) manager.rewindSnapshottable().capture();
            var ids = snapshot.collisionResponseState().previousCollisionObjectIds();
            return snapshot.dynamicObjects().stream()
                    .filter(entry -> ids.contains(entry.objectId()))
                    .filter(entry -> entry.className().endsWith("SszMtzBossOrbChild"))
                    .map(entry -> entry.slotIndex()).sorted().toList();
        }
        void assertGraph() {
            if (boss().headForTest() != null) {
                assertSame(head(), boss().headForTest());
                assertDoesNotThrow(() -> assertSame(boss(), field(head(), "parent")));
            }
            var actual = manager.activeObjectsOfType(SszMtzBossOrbChild.class);
            assertEquals(actual.size(), boss().orbsForTest().size());
            for (var orb : boss().orbsForTest()) {
                assertFalse(orb.isDestroyed());
                assertDoesNotThrow(() -> assertSame(boss(), field(orb, "parent")));
                assertSame(orb, NativeSstSlotAccess.currentOwner(manager, orb.getSlotIndex()));
                assertEquals(1, actual.stream().filter(live -> live == orb).count(), "raw identity, not equal spawn aliases");
            }
        }
        void stepAt(int count) { manager.update(0x1660, player, List.of(), count); }
        void step() { stepAt(frame++); }
        void setup() { for (frame = 0; frame <= 33; frame++) stepAt(frame); }
    }

    private static void assertPublished(World world) {
        assertEquals(0x7F00, world.state.eventsBgWord(2));
        assertEquals(-1, world.state.eventsBgByte(5));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class DeclinedRecipe extends ObjectCallbackAbortException {
        DeclinedRecipe() { super("controlled optional provider abort", null); }
    }

    private static final class World {
        final Camera camera = new Camera(SonicConfigurationService.createStandalone());
        final TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1700, (short) 0x420);
        final SpriteManager sprites = mock(SpriteManager.class);
        final LevelManager level = mock(LevelManager.class);
        final GameModule module = mock(GameModule.class);
        final SszZoneRuntimeState state = new SszZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);
        final ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null, null, camera,
                new StubObjectServices());
        SkySanctuaryMetropolisRecipe available;
        RuntimeException failure;
        int samples;
        final Sonic3kSSZEvents events = new Sonic3kSSZEvents() {
            @Override protected Camera camera() { return camera; }
            @Override protected SpriteManager spriteManager() { return sprites; }
            @Override protected LevelManager levelManager() { return level; }
            @Override protected GameModule module() { return module; }
        };
        World() {
            manager.reset(0);
            camera.setX((short) 0x1660); camera.setY((short) 0x380);
            player.setCentreX((short) 0x1700); player.setCentreY((short) 0x420); player.setAir(false);
            state.setEventsBgByte(3, 0xFF);
            when(sprites.getMainPlayable()).thenReturn(player);
            when(level.getObjectManager()).thenReturn(manager);
            when(module.getGameService(SkySanctuaryMetropolisRecipe.class)).thenAnswer(invocation -> {
                samples++;
                assertEquals(1, manager.activeObjectsOfType(SszMtzBossObjectInstance.class).size());
                assertTrue(boss().getSlotIndex() >= 0, "lookup occurs after actual accepted allocation");
                assertEquals(0x00FF, state.eventsBgWord(2), "pre-existing lock; fighting word publication follows optional intake");
                assertEquals(0, state.eventsBgByte(5));
                if (failure != null) throw failure;
                return available;
            });
        }
        SszMtzBossObjectInstance boss() { return manager.activeObjectsOfType(SszMtzBossObjectInstance.class).getFirst(); }
    }
}
