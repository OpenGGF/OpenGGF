package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kIncLevelEndXGradualInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Native StartPostDefeat / WaitPostDefeatGeyser ownership, not an eight-hit route oracle.
 * The declared component inlets are final FLEE timer 0, capsule post-open results
 * creation, and results STATE_EXIT after real art/child publication. The boss
 * placement is replaced by one production-constructed component owner with its
 * resolved native arena bounds; the rest of the stock HCZ2 world remains live. Full-world
 * checks use the actual HCZ2 gameplay registry, genuine child retirement and real
 * results flag publication; they do not assign that flag to simulate results exit.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestHczEndBossTerminalOwnership {
    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void finalFleeRetainsTheHiddenRootAndAllocatesCapsuleBeforeLowerFreeHoleIsLost() throws Exception {
        World world = new World("sonic");
        world.moveAfter(world.boss, world.boss.getSlotIndex() + 8);
        int firstFree = world.manager.firstFreeDynamicSlot();
        assertTrue(firstFree < world.boss.getSlotIndex());
        List<Integer> music = world.recordMusic();
        world.boss.getState().renderFlags |= 0x80;
        world.boss.update(0, world.main);

        assertEquals(18, world.boss.getState().routine);
        assertFalse(world.boss.isDestroyed());
        assertTrue(world.boss.isPersistent());
        assertEquals(0, world.boss.getState().renderFlags & 0x80);
        assertTrue(GameServices.gameState().isEndOfLevelActive());
        var capsule = world.only(HczEndBossEggCapsuleInstance.class);
        assertEquals(firstFree, capsule.getSlotIndex(), "AllocateObject uses the lower hole");
        assertEquals(0x4250, capsule.getX());
        assertEquals(0x7E0, capsule.getY());
        var helper = world.only(S3kIncLevelEndXGradualInstance.class);
        assertTrue(helper.getSlotIndex() > world.boss.getSlotIndex(), "Child6 helper remains after-current");
        assertTrue(music.isEmpty(), "StartPostDefeat does not restore music");
        assertTrue(world.live(HczEndBossGeyserCutscene.class).isEmpty());
        clearInvocations(world.renderObserver);
        world.boss.appendRenderCommands(new ArrayList<>());
        verifyNoInteractions(world.renderObserver);

        // The unopened capsule is irrelevant to the retained root's native wait.
        world.fixture.camera().setMinX((short) 0);
        world.boss.update(1, world.main);
        assertEquals(world.fixture.camera().getX(), world.fixture.camera().getMinX());
        assertEquals(18, world.boss.getState().routine);
        assertFalse(world.boss.isDestroyed());
        assertTrue(music.isEmpty());
    }

    @Test
    void capsuleBranchReadsActualKnucklesP1Identity() throws Exception {
        World world = new World("knuckles");
        assertEquals(CharacterKey.KNUCKLES, world.main.characterKey());
        world.boss.update(0, world.main);
        var capsule = world.only(HczEndBossEggCapsuleInstance.class);
        assertEquals(0x4760, capsule.getX());
        assertEquals(0x360, capsule.getY());
        assertEquals(18, world.boss.getState().routine);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void flagClearRestoresCurrentNativePlayerStateAndMusicThenDeletesOnlyNextDispatch(boolean invincible)
            throws Exception {
        World world = new World("sonic");
        List<Integer> music = world.recordMusic();
        world.boss.update(0, world.main);
        AbstractPlayableSprite p2 = world.nativeP2();
        assertNotNull(p2, "this component explicitly covers a real native P2 participant");
        assertNotNull(p2.getCpuController());
        world.main.setInvincibleFrames(invincible ? 20 : 0);
        seedRestoreBoundary(world.main, 0x31);
        seedRestoreBoundary(p2, 0x42);
        int mainX = world.main.getCentreX();
        int mainY = world.main.getCentreY();
        int p2X = p2.getCentreX();
        int p2Y = p2.getCentreY();
        if (!invincible) {
            TestEnvironment.objectServices().setApparentAct(0);
            assertNotEquals(GameServices.level().getCurrentLevelMusicId(),
                    GameServices.level().getApparentLevelMusicId());
        }
        GameServices.gameState().setEndOfLevelActive(false); // Declared component publication inlet.
        int firstFree = world.manager.firstFreeDynamicSlot();
        world.boss.update(1, world.main);

        assertRestored(world.main, 0x31);
        assertRestored(p2, 0x42);
        assertEquals(mainX, world.main.getCentreX());
        assertEquals(mainY, world.main.getCentreY());
        assertEquals(p2X, p2.getCentreX());
        assertEquals(p2Y, p2.getCentreY());
        assertTrue(world.main.isControlLocked());
        assertTrue(p2.getCpuController().isController2SignedLocked());
        assertEquals(List.of(invincible ? GameServices.module().getAudioProfile().getInvincibilityMusicId()
                : GameServices.level().getApparentLevelMusicId()), music);
        assertEquals(firstFree, world.only(HczEndBossGeyserCutscene.class).getSlotIndex());
        assertEquals(0, world.fixture.camera().getMinY());
        assertEquals(20, world.boss.getState().routine);
        assertTrue(world.boss.isPersistent());
        assertFalse(world.boss.isDestroyed());
        assertTrue(world.manager.getActiveObjects().contains(world.boss));
        world.boss.update(2, world.main);
        assertTrue(world.boss.isDestroyed());
        assertEquals(1, music.size(), "the pending-delete dispatch does not repeat restoration or allocation");
        assertEquals(1, world.live(HczEndBossGeyserCutscene.class).size());
    }

    @Test
    void visualReleaseUsesTheRetainedRootsActualAcquisitionsAndRestoresTheirIdentities() throws Exception {
        World world = new World("sonic");
        List<Integer> music = world.recordMusic();
        var p2 = world.nativeP2();
        assertNotNull(p2);
        assertNotNull(p2.getCpuController());
        world.main.setControlLocked(true);
        p2.getCpuController().setController2SignedLocked(true);
        world.boss.releasePlayersForVisualDirection();
        assertTrue(world.main.isControlLocked(), "The root has not acquired this lock yet");
        assertTrue(p2.getCpuController().isController2SignedLocked());
        assertTrue(music.isEmpty());
        world.main.setControlLocked(false);
        p2.getCpuController().setController2SignedLocked(false);

        world.boss.update(0, world.main);
        GameServices.gameState().setEndOfLevelActive(false); // Existing declared publication inlet.
        world.boss.update(1, world.main);
        assertTrue(world.main.isControlLocked());
        assertTrue(p2.getCpuController().isController2SignedLocked());
        var context = world.manager.captureIdentityContext();
        var snapshot = world.boss.captureRewindState(context);
        var graph = List.copyOf(world.manager.getActiveObjects());
        int routine = world.boss.getState().routine;
        int x = world.main.getCentreX(), y = world.main.getCentreY();
        int mapping = world.main.getMappingFrame();
        int logical = world.main.getLogicalInputState();
        int musicCount = music.size();
        world.boss.releasePlayersForVisualDirection();
        assertFalse(world.main.isControlLocked());
        assertFalse(p2.getCpuController().isController2SignedLocked());
        assertEquals(routine, world.boss.getState().routine);
        assertFalse(world.boss.isDestroyed());
        assertEquals(graph, List.copyOf(world.manager.getActiveObjects()));
        assertEquals(x, world.main.getCentreX());
        assertEquals(y, world.main.getCentreY());
        assertEquals(mapping, world.main.getMappingFrame());
        assertEquals(logical, world.main.getLogicalInputState());
        assertEquals(musicCount, music.size());

        world.boss.restoreRewindState(snapshot, context);
        world.main.setControlLocked(true);
        p2.getCpuController().setController2SignedLocked(true);
        world.boss.releasePlayersForVisualDirection();
        assertFalse(world.main.isControlLocked(), "Restored owner metadata must still name P1");
        assertFalse(p2.getCpuController().isController2SignedLocked(), "Restored CPU ownership must still name P2");
        assertEquals(routine, world.boss.getState().routine);
        assertEquals(graph, List.copyOf(world.manager.getActiveObjects()));
        assertEquals(musicCount, music.size());
    }

    @Test
    void fullNativePoolKeepsFailedCapsuleWaitAndStillSelectsDeleteWhenGeyserCannotAllocate() throws Exception {
        World world = new World("sonic");
        List<Integer> music = world.recordMusic();
        while (world.manager.hasFreeDynamicSlot()) {
            assertNotNull(world.manager.createDynamicObject(SlotFiller::new));
        }
        world.boss.update(0, world.main);
        assertEquals(18, world.boss.getState().routine);
        assertTrue(GameServices.gameState().isEndOfLevelActive());
        assertTrue(world.live(HczEndBossEggCapsuleInstance.class).isEmpty());
        assertTrue(world.live(S3kIncLevelEndXGradualInstance.class).isEmpty());
        world.boss.update(1, world.main);
        assertEquals(18, world.boss.getState().routine);
        assertTrue(world.boss.isPersistent());
        assertTrue(music.isEmpty());
        GameServices.gameState().setEndOfLevelActive(false); // Separate full-pool component branch.
        int slot = world.boss.getSlotIndex();
        world.boss.update(2, world.main);
        assertTrue(world.live(HczEndBossGeyserCutscene.class).isEmpty());
        assertEquals(20, world.boss.getState().routine);
        assertEquals(slot, world.boss.getSlotIndex());
        assertFalse(world.boss.isDestroyed(), "the current root slot cannot be reused for this failed AllocateObject");
        assertEquals(0, world.fixture.camera().getMinY());
        world.boss.update(3, world.main);
        assertTrue(world.boss.isDestroyed());
        assertEquals(1, music.size());
    }

    @Test
    void secondaryGeyserUsesFirstFreeHoleAtTheDeclaredEndOfShakeBoundary() throws Exception {
        World world = new World("sonic");
        SlotFiller lower = world.manager.createDynamicObject(SlotFiller::new);
        var geyser = world.manager.createDynamicObject(() -> new HczEndBossGeyserCutscene(0, 0));
        int lowerSlot = lower.getSlotIndex();
        world.manager.removeDynamicObject(lower);
        // Component inlet is the already-loaded SHAKE tail, not an art/timing oracle.
        set(geyser, "artLoaded", true);
        set(geyser, "initialRoutineDispatchesRemaining", 0);
        set(geyser, "timer", 1);
        assertNotNull(world.nativeP2());
        geyser.update(0, world.main);
        var secondary = world.live(HczEndBossGeyserCutscene.class).stream()
                .filter(candidate -> candidate != geyser).findFirst().orElseThrow();
        assertEquals(lowerSlot, secondary.getSlotIndex());
        assertTrue((boolean) get(secondary, "targetsNativeP2"));
        assertEquals(4, get(secondary, "timer"));
    }

    @Test
    void completeHcz2WorldRestoresAndForwardsFinalFleeAndHiddenWaitTwice() throws Exception {
        World world = new World("sonic");
        ObjectRefId rootId = world.id(world.boss);
        CompositeSnapshot before = world.rewind.capture();
        world.frame();
        var retained = world.resolve(rootId, HczEndBossInstance.class);
        assertEquals(18, retained.getState().routine);
        assertTrue(retained.isPersistent());
        assertFalse(retained.isDestroyed());
        assertTrue(GameServices.gameState().isEndOfLevelActive());
        ObjectRefId capsuleId = world.id(world.only(HczEndBossEggCapsuleInstance.class));
        ObjectRefId helperId = world.id(world.only(S3kIncLevelEndXGradualInstance.class));
        CompositeSnapshot afterFlee = world.rewind.capture();
        world.frame();
        assertEquals(world.fixture.camera().getX(), world.fixture.camera().getMinX());
        CompositeSnapshot waiting = world.rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            world.manager.removeDynamicObject(world.resolve(rootId, HczEndBossInstance.class));
            world.rewind.restore(before);
            world.same(before);
            assertNotSame(world.boss, world.resolve(rootId, HczEndBossInstance.class));
            world.frame();
            world.same(afterFlee);
            assertNotNull(world.resolve(capsuleId, HczEndBossEggCapsuleInstance.class));
            assertNotNull(world.resolve(helperId, S3kIncLevelEndXGradualInstance.class));
            world.frame();
            world.same(waiting);
            world.rewind.restore(afterFlee);
            world.frame();
            world.same(waiting);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void genuineResultsPublicationAndPendingDeleteReplayInFullWorldWithEitherSlotOrder(boolean rootFirst)
            throws Exception {
        World world = new World("sonic");
        world.frame();
        var capsule = world.only(HczEndBossEggCapsuleInstance.class);
        // Declared post-open component inlet: production startResults allocates
        // the actual HCZ subclass. Art service, children and flag-clear are real.
        set(capsule, "opened", true);
        invoke(capsule, "startResults", new Class<?>[]{AbstractPlayableSprite.class}, world.main);
        var results = world.only(S3kResultsScreenObjectInstance.class);
        if (rootFirst && world.boss.getSlotIndex() > results.getSlotIndex()) {
            world.moveAfter(results, world.boss.getSlotIndex());
        } else if (!rootFirst && world.boss.getSlotIndex() < results.getSlotIndex()) {
            world.moveAfter(world.boss, results.getSlotIndex());
        }
        // This is an explicit SST-layout inlet before capture/first results
        // dispatch, not a claim that a natural fight produces both layouts.
        assertEquals(rootFirst, world.boss.getSlotIndex() < results.getSlotIndex());
        ObjectRefId rootId = world.id(world.boss);
        for (int frame = 0; frame < 600 && !(boolean) get(results, "resultsChildrenCreated"); frame++) world.frame();
        assertTrue((boolean) get(results, "resultsChildrenCreated"), "actual results art/child gate must complete");
        assertEquals(12, get(results, "childrenRemaining"));
        set(results, "state", 4); // Declared STATE_EXIT inlet; retire all twelve actual children normally.
        for (int frame = 0; frame < 400 && (int) get(results, "childrenRemaining") != 0; frame++) world.frame();
        assertEquals(0, get(results, "childrenRemaining"));
        assertTrue(GameServices.gameState().isEndOfLevelActive());
        assertEquals(18, world.resolve(rootId, HczEndBossInstance.class).getState().routine);
        CompositeSnapshot beforePublication = world.rewind.capture();
        world.frame();
        assertFalse(GameServices.gameState().isEndOfLevelActive(), "actual results owner publishes the clear");
        var root = world.resolve(rootId, HczEndBossInstance.class);
        assertEquals(rootFirst ? 18 : 20, root.getState().routine,
                "the actual object-loop cursor decides whether the root has observed the write");
        if (rootFirst) {
            assertTrue(world.main.isObjectControlled(), "results must not restore before the retained root dispatch");
        }
        CompositeSnapshot publication = world.rewind.capture();
        if (rootFirst) world.frame();
        root = world.resolve(rootId, HczEndBossInstance.class);
        assertEquals(20, root.getState().routine);
        assertFalse(root.isDestroyed());
        assertEquals(Sonic3kAnimationIds.WAIT.id(), world.main.getAnimationId());
        assertTrue(world.main.isControlLocked());
        assertEquals(0, world.fixture.camera().getMinY());
        ObjectRefId geyserId = world.id(world.only(HczEndBossGeyserCutscene.class));
        CompositeSnapshot pendingDelete = world.rewind.capture();
        world.frame();
        assertTrue(world.live(HczEndBossInstance.class).isEmpty());
        CompositeSnapshot deleted = world.rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            world.rewind.restore(beforePublication);
            world.same(beforePublication);
            world.frame();
            world.same(publication);
            if (rootFirst) world.frame();
            world.same(pendingDelete);
            assertNotNull(world.resolve(geyserId, HczEndBossGeyserCutscene.class));
            world.frame();
            world.same(deleted);
            world.rewind.restore(pendingDelete);
            world.frame();
            world.same(deleted);
        }
    }

    private static void seedRestoreBoundary(AbstractPlayableSprite player, int mapping) {
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setControlLocked(false);
        player.setAir(true);
        player.setOnObject(true);
        player.setXSpeed((short) 0x123);
        player.setYSpeed((short) -0x234);
        player.setGSpeed((short) 0x345);
        player.setAnimationFrameIndex(3);
        player.setAnimationTick(7);
        player.setMappingFrame(mapping);
        player.setLogicalInputState(true, true, true, true, true, true);
    }

    private static void assertRestored(AbstractPlayableSprite player, int mapping) {
        assertFalse(player.isObjectControlled());
        assertFalse(player.getAir());
        assertTrue(player.isOnObject(), "direct native air clear preserves standing ownership");
        assertEquals(0x123, player.getXSpeed());
        assertEquals(-0x234, player.getYSpeed());
        assertEquals(0x345, player.getGSpeed());
        assertEquals(mapping, player.getMappingFrame(), "Restore_PlayerControl never writes mapping_frame");
        assertEquals(Sonic3kAnimationIds.WAIT.id(), player.getAnimationId());
        assertEquals(0, player.getAnimationFrameIndex());
        assertEquals(0, player.getAnimationTick());
        assertEquals(0, player.getLogicalInputState());
        assertFalse(player.isLogicalJumpPressActive());
    }

    private static final class World {
        final HeadlessTestFixture fixture;
        final ObjectManager manager;
        final RewindRegistry rewind;
        final AbstractPlayableSprite main;
        final HczEndBossInstance boss;
        final ObjectRenderManager renderObserver = mock(ObjectRenderManager.class);

        World(String character) throws Exception {
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    "sonic".equals(character) ? "tails" : "");
            boolean knuckles = "knuckles".equals(character);
            fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1)
                    .withSkippedZoneIntro().startPosition((short) (knuckles ? 0x4590 : 0x4050),
                            (short) (knuckles ? 0x33C : 0x7BC)).startPositionIsCentre().build();
            manager = GameServices.level().getObjectManager();
            rewind = fixture.gameplayMode().getRewindRegistry();
            assertNotNull(rewind, "use the complete gameplay registry, not a private object-only substitute");
            main = fixture.sprite();
            // Existing test/editor surface establishes one component owner,
            // without a second layout boss appearing on a later stock load pass.
            manager.resyncSpawnList(manager.getAllSpawns().stream()
                    .filter(spawn -> spawn.objectId() != Sonic3kObjectIds.HCZ_END_BOSS).toList());
            boss = manager.createDynamicObject(() -> new HczEndBossInstance(
                    new ObjectSpawn(0x4400, 0x400, Sonic3kObjectIds.HCZ_END_BOSS, 0, 0, false, 0x400)));
            assertNotNull(boss);
            var camera = fixture.camera();
            int left = (int) get(boss, "targetLockXLeft");
            int right = (int) get(boss, "targetLockXRight");
            int top = (int) get(boss, "targetLockYTop");
            int bottom = (int) get(boss, "targetLockYBottom");
            camera.setMinX((short) left);
            camera.setMaxX((short) right);
            camera.setMinY((short) top);
            camera.setMaxY((short) bottom);
            camera.setX((short) left);
            camera.setY((short) top);
            invoke(boss, "setBossFlag", new Class<?>[]{boolean.class}, true);
            // Declared final-FLEE component inlet. No eight-hit fight or route is claimed.
            boss.getState().routine = 16;
            boss.getState().defeated = true;
            boss.getState().x = (right + 0x400) & 0xFFFF; // Deliberate offscreen lifetime boundary.
            boss.getState().xVel = 0x200;
            boss.getState().yVel = -0x200;
            set(boss, "defeatSignal", true);
            set(boss, "fleeTimer", 0);
        }

        List<Integer> recordMusic() {
            List<Integer> music = new ArrayList<>();
            ObjectServices recording = mock(ObjectServices.class, delegatesTo(TestEnvironment.objectServices()));
            doAnswer(call -> { music.add(call.getArgument(0)); return null; }).when(recording).playMusic(anyInt());
            doReturn(renderObserver).when(recording).renderManager();
            boss.setServices(recording);
            return music;
        }

        AbstractPlayableSprite nativeP2() {
            var participant = TestEnvironment.objectServices().playerQuery().nativeP2OrNull();
            return participant instanceof AbstractPlayableSprite sprite ? sprite : null;
        }

        void moveAfter(AbstractObjectInstance object, int afterSlot) {
            int reserved = manager.allocateSlotAfter(afterSlot);
            assertTrue(reserved >= 0, "declared slot-order inlet requires a real free upper SST");
            manager.removeDynamicObject(object);
            manager.addDynamicObjectAtSlot(object, reserved);
        }

        void frame() { fixture.stepFrame(false, false, false, false, false); }

        <T extends ObjectInstance> T only(Class<T> type) {
            var values = live(type);
            assertEquals(1, values.size(), type.getSimpleName());
            return values.getFirst();
        }

        <T extends ObjectInstance> List<T> live(Class<T> type) {
            return manager.activeObjectsOfType(type).stream().filter(object -> !object.isDestroyed()).toList();
        }

        ObjectRefId id(ObjectInstance object) {
            return manager.captureIdentityContext().requireIdentityTable().idFor(object);
        }

        <T extends ObjectInstance> T resolve(ObjectRefId identity, Class<T> type) {
            return type.cast(manager.captureIdentityContext().requireIdentityTable().resolveObject(identity, true));
        }

        void same(CompositeSnapshot expected) {
            var actual = rewind.capture();
            assertEquals(expected.entries().keySet(), actual.entries().keySet());
            for (String key : expected.entries().keySet()) {
                assertTrue(RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)).isEmpty(),
                        () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key))
                                + retainedDynamicSlotDetails(expected.get(key), actual.get(key)));
            }
        }

        /** Failure-only detail from the two retained values, never the live manager. */
        private static String retainedDynamicSlotDetails(Object expected, Object actual) {
            if (!(expected instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot before)
                    || !(actual instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot after)) {
                return "";
            }
            // Equality of captured IDs owns the pairing. Null/duplicate IDs remain
            // explicitly unpaired or ambiguous, rather than falling back to slots.
            var identities = new ArrayList<ObjectRefId>();
            for (var entry : before.dynamicObjects()) {
                if (!identities.contains(entry.objectId())) identities.add(entry.objectId());
            }
            for (var entry : after.dynamicObjects()) {
                if (!identities.contains(entry.objectId())) identities.add(entry.objectId());
            }
            StringBuilder detail = new StringBuilder();
            for (ObjectRefId identity : identities) {
                var left = before.dynamicObjects().stream()
                        .filter(entry -> java.util.Objects.equals(identity, entry.objectId())).toList();
                var right = after.dynamicObjects().stream()
                        .filter(entry -> java.util.Objects.equals(identity, entry.objectId())).toList();
                if (identity != null && left.size() == 1 && right.size() == 1
                        && RewindSnapshotDiff.diffKey("retained-dynamic", left.getFirst(), right.getFirst()).isEmpty()) {
                    continue;
                }
                // At most the retained before+after rows are formatted; no full
                // state, render stream, or gameplay query is expanded here.
                detail.append("\nretained dynamic id=").append(identity);
                if (identity == null) detail.append(" (unpaired null IDs)");
                else if (left.size() > 1 || right.size() > 1) detail.append(" (ambiguous duplicate ID)");
                detail.append(" expected=").append(left.stream().map(World::retainedDynamicRow).toList())
                        .append(" actual=").append(right.stream().map(World::retainedDynamicRow).toList());
            }
            return detail.toString();
        }

        private static String retainedDynamicRow(
                com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry entry) {
            return "{className=" + entry.className() + ", slotIndex=" + entry.slotIndex()
                    + ", state.slotIndex=" + (entry.state() == null ? "<null>" : entry.state().slotIndex()) + "}";
        }
    }

    private static final class SlotFiller extends AbstractObjectInstance implements RewindRecreatable {
        SlotFiller() { this(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)); }
        private SlotFiller(ObjectSpawn spawn) { super(spawn, "HczTerminalSlotFiller"); }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public SlotFiller recreateForRewind(RewindRecreateContext context) {
            return new SlotFiller(context.spawn());
        }
    }

    private static Object get(Object target, String name) throws Exception {
        return field(target.getClass(), name).get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void invoke(Object target, String name, Class<?>[] parameters, Object... arguments)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        method.invoke(target, arguments);
    }
}
