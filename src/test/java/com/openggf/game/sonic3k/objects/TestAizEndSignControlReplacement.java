package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Declared loc_68C02 entry in a production SST scan; no timer or slot is seeded. */
@RequiresRom(SonicGame.SONIC_3K)
class TestAizEndSignControlReplacement {
    @AfterEach void clear() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void firstSignpostBirthPassInitializesAtThePhysicalControlThenReplaysTwice(boolean aizHandoff)
            throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var manager = GameServices.level().getObjectManager();
        AbstractObjectInstance owner;
        if (aizHandoff) {
            owner = manager.createDynamicObject(() -> new HandoffEntry(new ObjectSpawn(
                    fixture.sprite().getCentreX(), 0x334, 0x91, 0, 0, false, 0x334)));
        } else {
            var flow = manager.createDynamicObject(() -> new S3kBossDefeatSignpostFlow(
                    fixture.sprite().getCentreX(), 0, S3kBossDefeatSignpostFlow.CleanupAction.NONE));
            flow.beginNativeReplacement(flow.getSlotIndex());
            owner = flow;
        }
        int controlSlot = owner.getSlotIndex();
        GameServices.gameState().setCurrentBossId(0x91);
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        int birthPass = 0;
        S3kSignpostInstance sign = null;
        for (int pass = 1; pass <= 180; pass++) {
            scan(fixture, pass);
            if (pass == 1) {
                assertEquals(0x91, GameServices.gameState().getCurrentBossId(), "replacement preserves boss ownership until DoSign");
                assertTrue(GameServices.gameState().isEndOfLevelActive(), "native control install publishes FAA8");
                if (aizHandoff) {
                    assertTrue(((com.openggf.game.sonic3k.runtime.AizZoneRuntimeState)
                            GameServices.zoneRuntimeState()).isBossFlagActive());
                    assertEquals(1, manager.activeObjectsOfType(SongFadeTransitionInstance.class).size());
                }
            }
            sign = S3kSignpostInstance.activeSignpost(manager);
            if (sign != null) { birthPass = pass; break; }
        }
        assertNotNull(sign, "literal $77 control wait must produce a signpost");
        int signSlot = sign.getSlotIndex();
        int actualY = sign.getY();
        int expectedY = fixture.camera().getY() - 0x20;
        long stubs = manager.activeObjectsOfType(S3kSignpostStubChild.class).size();
        boolean physicalControl = NativeSstSlotAccess.currentOwner(manager, controlSlot)
                instanceof S3kBossDefeatSignpostFlow;
        CompositeSnapshot born = registry.capture();
        for (int pass = birthPass + 1; pass <= birthPass + 45; pass++) scan(fixture, pass);
        CompositeSnapshot after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            same(before, registry.capture());
            for (int pass = 1; pass <= birthPass + 45; pass++) scan(fixture, pass);
            same(after, registry.capture());
            registry.restore(born);
            same(born, registry.capture());
            for (int pass = birthPass + 1; pass <= birthPass + 45; pass++) scan(fixture, pass);
            same(after, registry.capture());
        }
        System.out.printf("AIZ_BIRTH handoff=%s control=%d sign=%d scan=%d Y=%04X expected=%04X stubs=%d physical=%s%n",
                aizHandoff, controlSlot, signSlot, birthPass, actualY & 0xFFFF, expectedY & 0xFFFF,
                stubs, physicalControl);
        assertAll(
                () -> assertTrue(physicalControl, "loc_68C02 rewrites the physical retained SST"),
                () -> assertEquals(expectedY, actualY, "Obj_EndSignInit runs in the creation pass"),
                () -> assertEquals(1, stubs, "native INIT creates its stub in that same pass"));
    }

    @Test
    void fatalTouchPollFollowsMovementAndCarriesLiteralWaitWordsWithoutDisplacedEntries() throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var manager = GameServices.level().getObjectManager();
        fixture.camera().setX((short) 0x10E0);
        var boss = manager.createDynamicObject(() -> new AizMinibossInstance(
                new ObjectSpawn(0x11F0, 0x289, 0x91, 0, 0, false, 0x289)));
        for (int pass = 0; pass < 256 && boss.getState().routine != 6; pass++) scan(fixture, pass);
        assertEquals(6, boss.getState().routine, "natural arena wait reaches MoveSprite2/Obj_Wait");
        int controlSlot = boss.getSlotIndex();
        int yBefore = boss.getState().y;
        int scoreBefore = GameServices.gameState().getScore();
        boss.getState().hitCount = 1; // Declared final-hit component stimulus, not recorded state.
        boss.onPlayerAttack(fixture.sprite(), null);
        assertFalse(boss.getState().defeated, "touch writes HP; the parent's following poll owns defeat");
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        scan(fixture, 1);
        boolean defeatedAtOwnPoll = boss.getState().defeated;
        int yAtPoll = boss.getState().y;
        var wait = AizMinibossInstance.class.getDeclaredField("defeatHandoffTimer");
        wait.setAccessible(true);
        int freshWait = wait.getInt(boss);
        for (int pass = 2; pass <= 65; pass++) scan(fixture, pass);
        var flow = NativeSstSlotAccess.currentOwner(manager, controlSlot);
        int flowWait = -1;
        if (flow instanceof S3kBossDefeatSignpostFlow) {
            var timer = S3kBossDefeatSignpostFlow.class.getDeclaredField("timer");
            timer.setAccessible(true);
            flowWait = timer.getInt(flow);
        }
        int scoreAfter = GameServices.gameState().getScore();
        long musicChildren = manager.activeObjectsOfType(SongFadeTransitionInstance.class).size();
        long explosionTail = manager.activeObjectsOfType(MhzMinibossExplosionController.class).size();
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before); same(before, registry.capture());
            for (int pass = 1; pass <= 65; pass++) scan(fixture, pass);
            same(after, registry.capture());
        }
        int installedFlowWait = flowWait;
        System.out.printf("AIZ_FATAL defeated=%s y=%04X/%04X freshWait=%d flowWait=%d score=%d/%d music=%d%n",
                defeatedAtOwnPoll, yAtPoll, yBefore, freshWait, flowWait, scoreBefore, scoreAfter, musicChildren);
        assertAll(
                () -> assertTrue(defeatedAtOwnPoll, "loc_68F62 polls zero HP after this routine dispatch"),
                () -> assertEquals(yBefore + 1, yAtPoll, "MoveSprite2 precedes the fatal poll"),
                () -> assertEquals(0x3F, freshWait, "BossDefeated_StopTimer falls through the literal writer"),
                () -> assertInstanceOf(S3kBossDefeatSignpostFlow.class, flow),
                () -> assertEquals(0x77, installedFlowWait, "64 later wait passes install fresh native EndSignControl"),
                () -> assertEquals(scoreBefore + 1000, scoreAfter, "fatal side effects occur exactly once"),
                () -> assertEquals(1, musicChildren, "independent native music child survives replacement"),
                () -> assertEquals(1, explosionTail, "independent subtype-$10 controller retains its remaining tail"));
        var tailStart = registry.capture();
        for (int pass = 66; pass <= 110; pass++) scan(fixture, pass);
        var tailEnd = registry.capture();
        assertTrue(manager.activeObjectsOfType(MhzMinibossExplosionController.class).isEmpty());
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(tailStart); same(tailStart, registry.capture());
            for (int pass = 66; pass <= 110; pass++) scan(fixture, pass);
            same(tailEnd, registry.capture());
        }
    }

    @Test void musicUsesGlobalHoleAndStartsOnItsOwnFollowingPassThenReplaysTwice() throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var manager = GameServices.level().getObjectManager();
        var hole = manager.createDynamicObject(() -> new RetiringSlot(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        var boss = manager.createDynamicObject(() -> new HandoffEntry(new ObjectSpawn(0x11F0, 0x334, 0x91, 0, 0, false, 0x334)));
        boss.armed = false;
        int holeSlot = hole.getSlotIndex();
        int controlSlot = boss.getSlotIndex();
        scan(fixture, 0);
        assertNull(NativeSstSlotAccess.currentOwner(manager, holeSlot));
        assertEquals(holeSlot, manager.firstFreeDynamicSlot());
        assertTrue(holeSlot < controlSlot);
        boss.armed = true;
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        scan(fixture, 1);
        var music = manager.activeObjectsOfType(SongFadeTransitionInstance.class).getFirst();
        int actualSlot = music.getSlotIndex();
        var elapsed = SongFadeTransitionInstance.class.getDeclaredField("elapsedUpdates");
        elapsed.setAccessible(true);
        int birthUpdates = elapsed.getInt(music);
        var born = registry.capture();
        for (int pass = 2; pass <= 121; pass++) scan(fixture, pass);
        boolean liveAtOwnUpdate120 = !manager.activeObjectsOfType(SongFadeTransitionInstance.class).isEmpty();
        scan(fixture, 122);
        assertTrue(manager.activeObjectsOfType(SongFadeTransitionInstance.class).isEmpty());
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before); same(before, registry.capture());
            for (int pass = 1; pass <= 122; pass++) scan(fixture, pass);
            same(after, registry.capture());
            registry.restore(born); same(born, registry.capture());
            for (int pass = 2; pass <= 122; pass++) scan(fixture, pass);
            same(after, registry.capture());
        }
        System.out.printf("AIZ_MUSIC control=%d globalHole=%d actual=%d birthUpdates=%d liveAt120=%s%n",
                controlSlot, holeSlot, actualSlot, birthUpdates, liveAtOwnUpdate120);
        assertAll(
                () -> assertEquals(holeSlot, actualSlot, "Wait_FadeToLevelMusic calls global AllocateObject"),
                () -> assertEquals(0, birthUpdates, "the lower SST has already passed in Process_Sprites"),
                () -> assertTrue(liveAtOwnUpdate120, "literal120 expires only on its121st own dispatch"));
    }

    private static final class RetiringSlot extends AbstractObjectInstance implements SpawnRewindRecreatable {
        public RetiringSlot(ObjectSpawn spawn) { super(spawn, "AizMusicHole"); setRomWorldPositioned(false); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { setDestroyed(true); }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }

    private static void scan(HeadlessTestFixture fixture, int pass) {
        GameServices.level().getObjectManager().update(fixture.camera().getX(), fixture.sprite(), List.of(), pass);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private static final class HandoffEntry extends AizMinibossInstance {
        private boolean entered;
        private boolean armed = true;
        public HandoffEntry(ObjectSpawn spawn) { super(spawn); }
        @Override public AizMinibossInstance recreateForRewind(RewindRecreateContext ctx) {
            return new HandoffEntry(ctx.spawn());
        }
        @Override protected void updateBossLogic(int vIntRunCount, PlayableEntity player) {
            if (entered || !armed) return;
            entered = true;
            com.openggf.game.sonic3k.events.S3kAizEventWriteSupport.setBossFlag(services(), true);
            try {
                var entry = AizMinibossInstance.class.getDeclaredMethod("startDefeatHandoff");
                entry.setAccessible(true);
                entry.invoke(this);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("declared native handoff entry", failure);
            }
        }
    }
}
