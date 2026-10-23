package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter;
import com.openggf.game.sonic2.specialstage.CompetitiveSpecialStagePlayerAccess;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageObject;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStagePlayer;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Actual two-human runtime checks; no ordinary SS manager or campaign bootstrap. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitiveHalfpipe {
    private CompetitiveHalfpipeRomData data;
    private Sonic2CompetitiveHalfpipe halfpipe;
    private InputHandler input;
    private RecordingEvents events;
    private final AtomicInteger verticalInterrupts = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        // Existing player jump audio still crosses the ordinary engine audio
        // service. Install no-device output explicitly even in a reused JVM.
        GameServices.audio().setBackend(new NullAudioBackend());
        data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
        GraphicsManager graphics = new GraphicsManager();
        graphics.initHeadless();
        halfpipe = new Sonic2CompetitiveHalfpipe(data, graphics,
                SpecialStageViewport.nativeViewport(), AudioManager.createSilent(), verticalInterrupts::get);
        input = new InputHandler();
        events = new RecordingEvents();
    }

    @AfterEach
    void tearDown() {
        if (halfpipe != null) halfpipe.close();
    }

    @Test
    void productionBootKeepsBothLogicalControllersIndependentThroughNativeIntro() {
        assertEquals(Sonic2CompetitiveHalfpipe.Phase.EMPTY, halfpipe.phase());
        assertThrows(IllegalStateException.class, halfpipe::draw);
        for (int direction : new int[]{AbstractPlayableSprite.INPUT_RIGHT, AbstractPlayableSprite.INPUT_LEFT}) {
            load(0);
            assertEquals(Sonic2CompetitiveHalfpipe.Phase.FADE_IN, halfpipe.phase());
            assertEquals(Sonic2SpecialStagePlayer.PlayerType.SONIC, halfpipe.player(0).getPlayerType());
            assertEquals(Sonic2SpecialStagePlayer.PlayerType.TAILS, halfpipe.player(1).getPlayerType());
            assertEquals(0x6E, halfpipe.player(0).getSSZPos());
            assertEquals(0x80, halfpipe.player(1).getSSZPos());
            assertTrue(halfpipe.player(0).isSpawned());
            assertTrue(halfpipe.player(1).isSpawned());
            int other = direction == AbstractPlayableSprite.INPUT_RIGHT
                    ? AbstractPlayableSprite.INPUT_LEFT : AbstractPlayableSprite.INPUT_RIGHT;
            for (int tick = 0; tick < 22; tick++) step(direction(direction), direction(other));
            assertEquals(Sonic2CompetitiveHalfpipe.Phase.INTRO, halfpipe.phase());
            assertEquals(0x40, halfpipe.player(0).getAngle(), "white fade does not execute actor passes");
            assertEquals(0x40, halfpipe.player(1).getAngle());
            for (int tick = 0; tick < 20; tick++) step(direction(direction), direction(other));
            assertEquals(Sonic2CompetitiveHalfpipe.Phase.INTRO, halfpipe.phase(),
                    "logical controls run before the native banner has finished");
            if (direction == AbstractPlayableSprite.INPUT_RIGHT) {
                assertTrue(halfpipe.player(0).getInertia() < 0);
                assertTrue(halfpipe.player(1).getInertia() > 0);
                assertTrue(halfpipe.player(0).getAngle() < 0x40);
                assertTrue(halfpipe.player(1).getAngle() > 0x40);
            } else {
                assertTrue(halfpipe.player(0).getInertia() > 0);
                assertTrue(halfpipe.player(1).getInertia() < 0);
                assertTrue(halfpipe.player(0).getAngle() > 0x40);
                assertTrue(halfpipe.player(1).getAngle() < 0x40);
            }
        }
    }

    @Test
    void preStartLoopLatchesBothControllerSamplesBeforeVintAndRestoresThatLatch() {
        load(0);
        for (int tick = 0; tick < 22; tick++) step(PlayerInputState.neutral(), PlayerInputState.neutral());
        step(direction(AbstractPlayableSprite.INPUT_RIGHT), direction(AbstractPlayableSprite.INPUT_LEFT));
        assertEquals(0, halfpipe.player(0).getInertia());
        assertEquals(0, halfpipe.player(1).getInertia());
        var saved = halfpipe.capture();
        for (int replay = 0; replay < 2; replay++) {
            step(PlayerInputState.neutral(), PlayerInputState.neutral());
            assertEquals(-0x60, halfpipe.player(0).getInertia());
            assertEquals(0x60, halfpipe.player(1).getInertia());
            if (replay == 0) halfpipe.restore(saved);
        }
    }

    @Test
    void eitherHumanJumpTogglesTheSharedNativeDepthOwner() {
        for (int jumpingSlot = 0; jumpingSlot < 2; jumpingSlot++) {
            CompetitiveSpecialStagePlayerAccess.Pair pair = CompetitiveSpecialStagePlayerAccess.create();
            pair.step(0, 0, jumpingSlot == 0 ? 0x40 : 0, 4);
            pair.step(1, 0, jumpingSlot == 1 ? 0x40 : 0, 4);
            assertTrue(pair.player(jumpingSlot).isJumping());
            assertFalse(pair.player(1 - jumpingSlot).isJumping());
            for (int tick = 0; tick < 18; tick++) {
                pair.step(0, 0, 0, 4);
                pair.step(1, 0, 0, 4);
            }
            assertEquals(0x80, pair.player(0).getSSZPos());
            assertEquals(0x6E, pair.player(1).getSSZPos());
            assertEquals(2, pair.player(0).getPriority());
            assertEquals(3, pair.player(1).getPriority());
        }
    }

    @Test
    void everyRetailStageRunsItsActualStreamsAndThreeSectorsToTerminalPublication() {
        int allRingTouches = 0;
        int allBombLosses = 0;
        boolean sawFlippedTrack = false;
        boolean sawCurvedGeometry = false;
        for (int stage = 0; stage < 7; stage++) {
            load(stage);
            events.onComplete = () -> {
                assertTrue(halfpipe.objects().isEmpty(), "native clear precedes external terminal callback");
                assertFalse(halfpipe.player(0).isSpawned());
                assertFalse(halfpipe.player(1).isSpawned());
                assertEquals(Sonic2CompetitiveHalfpipe.Phase.COMPLETE, halfpipe.phase());
            };
            int maximumObjects = 0;
            int maximumSegment = 0;
            while (halfpipe.phase() != Sonic2CompetitiveHalfpipe.Phase.COMPLETE && halfpipe.ticks() < 15_000) {
                step(PlayerInputState.neutral(), PlayerInputState.neutral());
                var snapshot = halfpipe.capture();
                maximumObjects = Math.max(maximumObjects, halfpipe.objects().size());
                maximumSegment = Math.max(maximumSegment, snapshot.track().segment());
                sawFlippedTrack |= snapshot.track().flipped();
                sawCurvedGeometry |= snapshot.track().geometry() != 3;
            }
            assertEquals(Sonic2CompetitiveHalfpipe.Phase.COMPLETE, halfpipe.phase(), "stage " + stage);
            assertTrue(maximumObjects > 0, "ROM objects were actually instantiated for stage " + stage);
            assertTrue(maximumSegment >= finalMarker(stage), "actual final native object group was consumed");
            assertEquals(List.of(0, 1, 2), events.sectors(), "stage " + stage);
            assertEquals(1, events.count(Kind.COMPLETE));
            assertEquals(Kind.COMPLETE, events.entries.getLast().kind());
            assertEquals(CompetitiveHalfpipeCheckpoint.Phase.COMPLETE, halfpipe.capture().checkpoint().phase());
            assertTrue(halfpipe.objects().isEmpty(), "native terminal clearing owns remaining objects");
            allRingTouches += events.count(Kind.RING);
            allBombLosses += events.count(Kind.BOMB);
        }
        assertTrue(allRingTouches > 0);
        assertTrue(allBombLosses > 0);
        assertTrue(sawFlippedTrack);
        assertTrue(sawCurvedGeometry);
    }

    @Test
    void actualP2TouchesOwnTheirRingBankAndDelayedBombLossIncludingBlinkingRejection() {
        load(0);
        fadeIn();
        // Separate the native actors with real P1 input. P2 receives neutral
        // logical samples rather than CPU-delayed copies of P1's directions.
        for (int tick = 0; tick < 20; tick++) step(direction(AbstractPlayableSprite.INPUT_RIGHT), PlayerInputState.neutral());
        assertEquals(0x40, halfpipe.player(1).getAngle());
        assertTrue(circularDistance(halfpipe.player(0).getAngle(), halfpipe.player(1).getAngle()) >= 10);
        inject(false, 10, halfpipe.player(1).getAngle());
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertEquals(0, halfpipe.player(0).getRings());
        assertEquals(10, halfpipe.player(1).getRings());
        assertEquals(10, events.count(Kind.RING));
        assertTrue(events.entries.stream().filter(event -> event.kind() == Kind.RING).allMatch(event -> event.slot() == 1));

        inject(true, 1, halfpipe.player(1).getAngle());
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertEquals(Sonic2SpecialStageObject.State.EXPLODING, halfpipe.objects().getFirst().getState());
        assertFalse(halfpipe.player(1).isHurt(), "bomb touch queues collision after the actor pass");
        assertEquals(10, halfpipe.player(1).getRings());
        assertEquals(0, events.count(Kind.BOMB));
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertTrue(halfpipe.player(1).isHurt());
        assertEquals(0, halfpipe.player(1).getHurtTimer());
        assertEquals(10, halfpipe.player(1).getRings());
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertEquals(8, halfpipe.player(1).getHurtTimer());
        assertEquals(0, halfpipe.player(1).getRings());
        assertEquals(0, halfpipe.player(0).getRings());
        assertEquals(1, events.count(Kind.BOMB));
        assertEquals(1, events.entries.stream().filter(event -> event.kind() == Kind.BOMB).findFirst().orElseThrow().slot());

        for (int tick = 0; halfpipe.player(1).isHurt() && tick < 40; tick++) {
            step(PlayerInputState.neutral(), PlayerInputState.neutral());
        }
        assertFalse(halfpipe.player(1).isHurt());
        assertTrue(halfpipe.player(1).isInvulnerable());
        inject(false, 10, halfpipe.player(1).getAngle());
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertEquals(10, halfpipe.player(1).getRings(), "blink does not prevent native ring collection");
        inject(true, 1, halfpipe.player(1).getAngle());
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertEquals(Sonic2SpecialStageObject.State.EXPLODING, halfpipe.objects().getFirst().getState(),
                "the native collision routine consumes bombs even during the actor's blinking window");
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertFalse(halfpipe.player(1).isHurt(), "actor collision dispatch rejects the queued hurt while invulnerable");
        assertEquals(10, halfpipe.player(1).getRings());
        assertEquals(1, events.count(Kind.BOMB), "rejected hurt never creates Obj5B's ring-loss event");
    }

    @Test
    void actualHumanHurtAttemptsSpillOnlyAtTimerEightAndFullRamPreservesBothBanks() {
        load(0); fadeIn();
        for (int tick = 0; tick < 20; tick++) step(direction(AbstractPlayableSprite.INPUT_RIGHT), PlayerInputState.neutral());
        inject(false, 10, halfpipe.player(1).getAngle());
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertEquals(10, halfpipe.player(1).getRings());
        inject(true, 1, halfpipe.player(1).getAngle());
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        var ram = halfpipe.objectMachine().ram();
        for (int slot = 8; slot <= 126; slot++) ram.replace(slot, new CompetitiveHalfpipeObjectMachine.Node(
                CompetitiveHalfpipeObjectMachine.Kind.HIDDEN));
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertTrue(halfpipe.player(1).isHurt());
        assertEquals(0, halfpipe.player(1).getHurtTimer());
        step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertEquals(8, halfpipe.player(1).getHurtTimer());
        assertEquals(10, halfpipe.player(1).getRings(), "failed Obj5B owner allocation performs no debit");
        ram.clear(63);
        for (int tick = 0; tick < 40; tick++) step(PlayerInputState.neutral(), PlayerInputState.neutral());
        assertFalse(halfpipe.player(1).isHurt());
        assertEquals(10, halfpipe.player(1).getRings(), "a later free slot cannot retry the timer-$08 edge");
        assertEquals(0, halfpipe.player(0).getRings());
        assertEquals(0, events.count(Kind.BOMB));
        assertTrue(halfpipe.objectMachine().capture().ram().slots().stream().noneMatch(slot -> slot.state() != null
                && (slot.state().kind() == CompetitiveHalfpipeObjectMachine.Kind.SPILL
                || slot.state().kind() == CompetitiveHalfpipeObjectMachine.Kind.SPILL_OWNER)));
    }

    @Test
    void productionSnapshotsRecreateActorsItemsAndRepeatOneHundredTwentyForwardPasses() throws Exception {
        load(4);
        while ((halfpipe.ticks() < 300 || halfpipe.objects().isEmpty()) && halfpipe.ticks() < 1500) {
            int tick = halfpipe.ticks();
            step(pattern(tick, 0), pattern(tick, 1));
        }
        assertFalse(halfpipe.objects().isEmpty());
        var saved = halfpipe.capture();
        int savedVerticalInterrupts = verticalInterrupts.get();
        int[] eventBanks = events.banks.clone();
        events.entries.clear();
        List<Sonic2CompetitiveHalfpipe.Snapshot> expected = new ArrayList<>();
        for (int tick = 0; tick < 120; tick++) {
            step(pattern(tick, 0), pattern(tick, 1));
            expected.add(halfpipe.capture());
        }
        List<Event> expectedEvents = List.copyOf(events.entries);
        // The host restores its event/session banks separately from this
        // registered halfpipe snapshot; restore that independent owner here.
        System.arraycopy(eventBanks, 0, events.banks, 0, 2);
        events.entries.clear();
        verticalInterrupts.set(savedVerticalInterrupts);
        halfpipe.restore(saved);
        assertDeep(saved, halfpipe.capture(), "restored snapshot");
        for (int tick = 0; tick < expected.size(); tick++) {
            step(pattern(tick, 0), pattern(tick, 1));
            assertDeep(expected.get(tick), halfpipe.capture(), "forward pass " + tick);
        }
        assertEquals(expectedEvents, events.entries);
    }

    @Test
    void resetAndNewStageLoadsHaveNoOldActorsItemsTrackOrControllerHistory() throws Exception {
        assertFalse(halfpipe.supportsCompetitiveStage(-1));
        assertFalse(halfpipe.supportsCompetitiveStage(7));
        load(4);
        var initial = halfpipe.capture();
        for (int tick = 0; tick < 650; tick++) step(pattern(tick, 0), pattern(tick, 1));
        assertFalse(halfpipe.objects().isEmpty());
        load(2);
        for (int tick = 0; tick < 80; tick++) step(direction(AbstractPlayableSprite.INPUT_LEFT), jump());
        load(4);
        assertDeep(initial, halfpipe.capture(), "fresh native stage load");
        assertThrows(IllegalArgumentException.class, () -> halfpipe.loadCompetitiveStage(7, events));
        assertEquals(4, halfpipe.stage());
        halfpipe.reset();
        assertEquals(Sonic2CompetitiveHalfpipe.Phase.EMPTY, halfpipe.phase());
        assertEquals(-1, halfpipe.stage());
        assertEquals(0, halfpipe.ticks());
        assertTrue(halfpipe.objects().isEmpty());
        assertThrows(IllegalStateException.class, () -> halfpipe.player(0));
        assertThrows(IllegalStateException.class, halfpipe::draw);
        halfpipe.restore(halfpipe.capture());
        load(4);
        assertDeep(initial, halfpipe.capture(), "load after empty-snapshot restoration");
    }

    private void load(int stage) {
        verticalInterrupts.set(0);
        events = new RecordingEvents();
        halfpipe.loadCompetitiveStage(stage, events);
    }

    private void fadeIn() {
        for (int tick = 0; tick < 22; tick++) step(PlayerInputState.neutral(), PlayerInputState.neutral());
    }

    private void step(PlayerInputState first, PlayerInputState second) {
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(first, second));
        events.tick = halfpipe.ticks() + 1;
        verticalInterrupts.incrementAndGet();
        halfpipe.step(input);
    }

    /** Arrange actual native cells; snapshots retain the physical parent/child slot links. */
    private void inject(boolean bomb, int count, int angle) {
        var ram = halfpipe.objectMachine().ram();
        for (int slot = 8; slot <= 126; slot++) {
            var node = ram.at(slot);
            if (node != null && (node.kind() == CompetitiveHalfpipeObjectMachine.Kind.ITEM
                    || node.kind() == CompetitiveHalfpipeObjectMachine.Kind.ITEM_SHADOW)) ram.clear(slot);
        }
        for (int index = 0; index < count; index++) {
            var node = new CompetitiveHalfpipeObjectMachine.Node(CompetitiveHalfpipeObjectMachine.Kind.ITEM);
            node.item = CompetitiveHalfpipeItems.create(data, bomb, 4, angle);
            assertTrue(ram.allocate(node) >= 8);
        }
    }

    private int finalMarker(int stage) {
        var segments = data.segments(stage);
        for (int index = 0; index < segments.size(); index++) if (segments.get(index).marker() == 0xFD) return index;
        throw new AssertionError("Retail stage has no final marker");
    }

    private static int circularDistance(int first, int second) {
        int delta = (first - second) & 0xFF;
        return Math.min(delta, 256 - delta);
    }

    private static PlayerInputState direction(int mask) {
        return PlayerInputState.of(mask, 0, 0, 0, false, false);
    }

    private static PlayerInputState jump() {
        return PlayerInputState.of(0, 0, InputActionMasks.ACTION_A, InputActionMasks.ACTION_A, false, false);
    }

    private static PlayerInputState pattern(int tick, int slot) {
        int direction = ((tick / 19 + slot) & 1) == 0
                ? AbstractPlayableSprite.INPUT_LEFT : AbstractPlayableSprite.INPUT_RIGHT;
        boolean jump = (tick + slot * 17) % 83 == 0;
        return PlayerInputState.of(direction, 0, jump ? InputActionMasks.ACTION_A : 0,
                jump ? InputActionMasks.ACTION_A : 0, false, false);
    }

    /** Opaque player records contain arrays, whose generated record equals is identity-based. */
    private static void assertDeep(Object expected, Object actual, String path) throws Exception {
        if (expected == null || actual == null) { assertEquals(expected, actual, path); return; }
        assertEquals(expected.getClass(), actual.getClass(), path);
        if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual), path);
            for (int index = 0; index < Array.getLength(expected); index++)
                assertDeep(Array.get(expected, index), Array.get(actual, index), path + "[" + index + "]");
        } else if (expected instanceof List<?> first && actual instanceof List<?> second) {
            assertEquals(first.size(), second.size(), path);
            for (int index = 0; index < first.size(); index++) assertDeep(first.get(index), second.get(index), path + "[" + index + "]");
        } else if (expected.getClass().isRecord()) {
            for (RecordComponent component : expected.getClass().getRecordComponents()) {
                var accessor = component.getAccessor();
                assertTrue(accessor.trySetAccessible(), path + "." + component.getName());
                assertDeep(accessor.invoke(expected), accessor.invoke(actual), path + "." + component.getName());
            }
        } else assertEquals(expected, actual, path);
    }

    private enum Kind { RING, BOMB, ARCHIVE, TIE, COMPLETE }
    private record Event(Kind kind, int slot, int sector, int p1Rings, int p2Rings, int tick) { }

    /** Independent adapter consumer with per-human banks and native archive/reset rules. */
    private static final class RecordingEvents implements Sonic2CompetitiveSpecialStageAdapter.Events {
        private final int[] banks = new int[2];
        private final List<Event> entries = new ArrayList<>();
        private int tick;
        private Runnable onComplete = () -> { };

        @Override public void collectedRing(int slot) {
            banks[slot] = Math.min(999, banks[slot] + 1);
            entries.add(new Event(Kind.RING, slot, -1, banks[0], banks[1], tick));
        }
        @Override public void hitBomb(int slot) {
            banks[slot] = Math.max(0, banks[slot] - 10);
            entries.add(new Event(Kind.BOMB, slot, -1, banks[0], banks[1], tick));
        }
        @Override public void checkpoint() { fail("Production halfpipe must publish explicit native sector events"); }
        @Override public void archivedSector(int sector, int p1Rings, int p2Rings) {
            assertArrayEquals(banks, new int[]{p1Rings, p2Rings}, "archive reads each bank after intervening native touches");
            entries.add(new Event(Kind.ARCHIVE, -1, sector, p1Rings, p2Rings, tick));
            banks[0] = banks[1] = 0;
        }
        @Override public void tiedSector(int sector) {
            entries.add(new Event(Kind.TIE, -1, sector, 0, 0, tick));
        }
        @Override public void stageComplete() {
            onComplete.run();
            entries.add(new Event(Kind.COMPLETE, -1, -1, banks[0], banks[1], tick));
        }
        private int count(Kind kind) { return (int) entries.stream().filter(event -> event.kind() == kind).count(); }
        private List<Integer> sectors() {
            return entries.stream().filter(event -> event.kind() == Kind.ARCHIVE || event.kind() == Kind.TIE)
                    .map(Event::sector).toList();
        }
    }
}
