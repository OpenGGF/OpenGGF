package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter.Events;
import com.openggf.game.sonic2.competition.specialstage.CompetitiveHalfpipeObjectMachine.Kind;
import com.openggf.game.sonic2.specialstage.CompetitiveSpecialStagePlayerAccess;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Production slot consumers, allocation failures and recreation without an ordinary SS owner. */
@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeObjectMachine {
    @Test
    void bootCreatesExactNativeOccupantsThroughSlotThirtyTwoAndKeepsHiddenMessages() throws Exception {
        var fixture = fixture();
        assertEquals("11fc0009b0004a78ff70660611fc0010b04011fc005eb08011fc005fb0c011fc0087b100",
                HexFormat.of().formatHex(fixture.data.rom().slice(0x5140, 36)));
        assertEquals("03040500ff1108000802ff06ff00",
                HexFormat.of().formatHex(fixture.data.rom().slice(0x35604, 14)));
        fixture.machine.initializeHiddenMessages();
        fixture.pass();
        Kind[] fixed = {Kind.PLAYER, Kind.PLAYER, Kind.HUD, Kind.BANNER, Kind.COUNTERS,
                Kind.PLAYER_SHADOW, Kind.PLAYER_SHADOW, Kind.TAIL};
        for (int slot = 0; slot < fixed.length; slot++) assertEquals(fixed[slot], fixture.machine.ram().at(slot).kind());
        assertEquals(0, fixture.machine.ram().at(5).owner());
        assertEquals(1, fixture.machine.ram().at(6).owner());
        assertEquals(1, fixture.machine.ram().at(7).owner());
        List<CompetitiveHalfpipeObjectMachine.Node> hidden = new ArrayList<>();
        for (int slot = 8; slot <= 18; slot++) {
            hidden.add(fixture.machine.ram().at(slot));
            assertEquals(Kind.HIDDEN, fixture.machine.ram().at(slot).kind());
        }
        int[] routines = {14, 16, 16, 16, 16, 18, 18, 18, 18, 18, 20};
        for (int index = 0; index < routines.length; index++) assertEquals(routines[index], hidden.get(index).routine());
        for (int slot = 19; slot <= 31; slot++) {
            assertEquals(Kind.LETTER, fixture.machine.ram().at(slot).kind());
            assertEquals(69, fixture.machine.ram().at(slot).timer, "later native slots execute on their creation pass");
        }
        assertEquals(Kind.WIN_COUNTERS, fixture.machine.ram().at(32).kind());
        for (int slot = 33; slot < 128; slot++) assertNull(fixture.machine.ram().at(slot));
        for (int tick = 0; tick < 240; tick++) {
            fixture.track.tick();
            fixture.pass();
            for (int index = 0; index < hidden.size(); index++) assertSame(hidden.get(index), fixture.machine.ram().at(8 + index));
            assertTrue(fixture.machine.capture().display().stream().noneMatch(draw -> draw.slot() >= 8 && draw.slot() <= 18));
        }
        assertTrue(fixture.machine.started());
        assertTrue(fixture.events.log.isEmpty());
    }

    @Test
    void zeroRingsAndFullRamCannotCreateAnOwnerOrDebitEitherHuman() throws Exception {
        var fixture = fixture();
        fixture.clearAndFillDynamic();
        fixture.rings(0, 13);
        fixture.rings(1, 27);
        var full = fixture.machine.capture();
        assertEquals(-1, fixture.machine.requestSpill(0));
        assertEquals(-1, fixture.machine.requestSpill(1));
        assertEquals(full, fixture.machine.capture());
        assertEquals(13, fixture.players.player(0).getRings());
        assertEquals(27, fixture.players.player(1).getRings());
        fixture.players.resetRings();
        fixture.rings(0, 13);
        fixture.machine.ram().clear(63);
        assertEquals(-1, fixture.machine.requestSpill(1));
        assertNull(fixture.machine.ram().at(63), "zero-ring owner does not consume the available SST");
        assertEquals(13, fixture.players.player(0).getRings());
        assertTrue(fixture.events.log.isEmpty());
    }

    @Test
    void aSingleFreeOwnerDebitsTenButItsOwnParticleDoesNotRunUntilTheFollowingPass() throws Exception {
        var fixture = fixture();
        fixture.clearAndFillDynamic();
        fixture.machine.ram().clear(63);
        fixture.rings(0, 13);
        fixture.rings(1, 27);
        assertEquals(63, fixture.machine.requestSpill(1));
        assertEquals(27, fixture.players.player(1).getRings(), "SSHurt allocation precedes Obj5B_Init's debit");
        fixture.pass();
        assertEquals(13, fixture.players.player(0).getRings());
        assertEquals(17, fixture.players.player(1).getRings());
        assertEquals(List.of("spill:1"), fixture.events.log);
        var node = fixture.machine.ram().at(63);
        assertEquals(Kind.SPILL, node.kind());
        assertEquals(1, count(fixture.machine, Kind.SPILL));
        assertEquals(0, node.spill.mappingFrame());
        assertEquals(fixture.players.player(1).getXPos() << 16, node.spill.capture().xFixed());
        assertEquals(fixture.players.player(1).getYPos() << 16, node.spill.capture().yFixed());
        assertTrue(fixture.machine.capture().display().isEmpty());
        fixture.pass();
        assertEquals(8, node.spill.mappingFrame());
        assertTrue(fixture.machine.capture().display().stream().anyMatch(draw -> draw.slot() == 63));
        assertEquals(List.of("spill:1"), fixture.events.log, "failed extra allocations do not refund or repeat the debit");
    }

    @Test
    void extraParticlesRespectLiveHigherSlotsAndEarlierSlotsFreedDuringThePass() throws Exception {
        var data = data();
        var higher = fixture(data);
        higher.clearAndFillDynamic();
        higher.machine.ram().clear(40);
        higher.machine.ram().clear(80);
        higher.rings(1, 20);
        assertEquals(40, higher.machine.requestSpill(1));
        higher.pass();
        assertEquals(0, higher.machine.ram().at(40).spill.mappingFrame());
        assertEquals(8, higher.machine.ram().at(80).spill.mappingFrame());
        assertEquals(List.of(new CompetitiveHalfpipeObjectMachine.Display(80, 5)), higher.machine.capture().display());
        assertEquals(10, higher.players.player(1).getRings());

        var lower = fixture(data);
        lower.clearAndFillDynamic();
        lower.machine.ram().clear(100);
        lower.machine.ram().replace(20, item(data, 0, 0x80));
        lower.rings(1, 20);
        assertEquals(100, lower.machine.requestSpill(1));
        lower.pass();
        assertEquals(Kind.SPILL, lower.machine.ram().at(20).kind(), "Obj60 deletes first, creating a lower hole");
        assertEquals(0, lower.machine.ram().at(20).spill.mappingFrame(), "the lower slot was already visited");
        assertEquals(0, lower.machine.ram().at(100).spill.mappingFrame(), "the owner does not fall through to Main");
        assertTrue(lower.machine.capture().display().isEmpty());
        lower.pass();
        assertEquals(8, lower.machine.ram().at(20).spill.mappingFrame());
        assertEquals(8, lower.machine.ram().at(100).spill.mappingFrame());
        assertEquals(List.of(new CompetitiveHalfpipeObjectMachine.Display(20, 5),
                new CompetitiveHalfpipeObjectMachine.Display(100, 5)), lower.machine.capture().display());
    }

    @Test
    void afterCurrentShadowFailureIsOneShotAndNeverWrapsToAnEarlierHole() throws Exception {
        var fixture = fixture();
        fixture.machine.ram().clear();
        var item = item(fixture.data, 20, 0x40);
        fixture.machine.ram().replace(125, item);
        fixture.machine.ram().replace(126, hidden());
        fixture.pass();
        assertEquals(2, item.routine());
        assertEquals(-1, item.child());
        assertNull(fixture.machine.ram().at(8), "AfterCurrent cannot use an earlier dynamic hole");
        assertNull(fixture.machine.ram().at(127), "the physical guard is excluded");
        fixture.machine.ram().clear(126);
        fixture.pass();
        assertEquals(-1, item.child());
        assertNull(fixture.machine.ram().at(126), "routine two never retries its failed shadow allocation");
    }

    @Test
    void linkedShadowSurvivesInvisiblePositiveFractionThenDiesWithItsParentInTheSamePass() throws Exception {
        var fixture = fixture();
        fixture.machine.ram().clear();
        var parent = item(fixture.data, 2, 0x40);
        fixture.machine.ram().replace(30, parent);
        fixture.pass();
        assertEquals(31, parent.child());
        var shadow = fixture.machine.ram().at(31);
        assertEquals(Kind.ITEM_SHADOW, shadow.kind());
        assertEquals(30, shadow.owner());
        assertTrue(fixture.machine.capture().display().contains(new CompetitiveHalfpipeObjectMachine.Display(31, 5)));
        fixture.pass();
        assertEquals(0, parent.item.getDepth());
        assertFalse(parent.item.shouldRemove(), "positive fixed fraction is still allocated at integer depth zero");
        assertSame(shadow, fixture.machine.ram().at(31));
        assertFalse(fixture.machine.capture().display().stream().anyMatch(draw -> draw.slot() == 31));
        fixture.pass();
        assertNull(fixture.machine.ram().at(30));
        assertNull(fixture.machine.ram().at(31), "the later shadow observes its parent's same-pass kill flag");
    }

    @Test
    void fullRamCheckpointCannotInventItsMissingRainbowOrAdvanceAResultTimer() throws Exception {
        var fixture = fixture();
        fixture.clearAndFillDynamic();
        fixture.machine.ram().replace(100, new CompetitiveHalfpipeObjectMachine.Node(Kind.MARKER));
        fixture.inStraightFourWindow();
        fixture.pass();
        assertTrue(fixture.machine.rainbowActive());
        assertEquals(0, count(fixture.machine, Kind.RAINBOW));
        assertNull(fixture.machine.ram().at(100), "the init object is deleted after all failed child allocations");
        for (int tick = 0; tick < 180; tick++) { fixture.track.tick(); fixture.pass(); }
        assertEquals(0, fixture.machine.checkpoint().sector());
        assertFalse(fixture.machine.complete());
        assertTrue(fixture.events.log.isEmpty());
    }

    @Test
    void restoredPartialRainbowWithoutTheE8OwnerCannotFabricateComparisonOrCompletion() throws Exception {
        var fixture = fixture();
        assertEquals("0c6800e8000866000cf6", HexFormat.of().formatHex(fixture.data.rom().slice(0x358C4, 10)));
        fixture.clearAndFillDynamic();
        fixture.machine.ram().clear(8);
        fixture.machine.ram().clear(9);
        fixture.machine.ram().replace(100, new CompetitiveHalfpipeObjectMachine.Node(Kind.MARKER));
        fixture.inStraightFourWindow();
        fixture.pass();
        assertEquals(6, fixture.machine.ram().at(8).base, "native allocation always attempts the E8 owner first");
        assertEquals(5, fixture.machine.ram().at(9).base);
        var snapshot = fixture.machine.capture();
        // Deliberately restore an incomplete physical image. A naturally
        // truncated nonempty allocation retains base six, so this test does
        // not pretend its absence is a different native allocation order.
        var slots = new ArrayList<>(snapshot.ram().slots());
        slots.set(8, new CompetitiveHalfpipeObjectRam.Slot<>(8, null));
        fixture.machine.restore(new CompetitiveHalfpipeObjectMachine.State(snapshot.stage(),
                new CompetitiveHalfpipeObjectRam.State<>(slots), snapshot.stream(), snapshot.checkpoint(),
                snapshot.noCheckpoint(), snapshot.rainbow(), snapshot.started(), snapshot.complete(), snapshot.display(),
                snapshot.addressRegister2(), snapshot.inactiveArtWords()));
        for (int tick = 0; tick < 180; tick++) { fixture.track.tick(); fixture.pass(); }
        assertEquals(0, count(fixture.machine, Kind.RAINBOW));
        assertTrue(fixture.machine.rainbowActive(), "only the actual E8 fragment clears the native flag");
        assertEquals(0, fixture.machine.checkpoint().sector());
        assertFalse(fixture.machine.complete());
        assertTrue(fixture.events.log.isEmpty());
    }

    @Test
    void fullRamWinnerPaletteWritesRecolorEarlierSonicDisplayAndSurviveRecreation() throws Exception {
        var data = data();
        assertEquals("45f8b00047f8b040", HexFormat.of().formatHex(data.rom().slice(0x350AE, 8)),
                "the size-eight collision test loads a2 before angle/routine rejection");
        assertEquals("dd690002dd6a0002", HexFormat.of().formatHex(data.rom().slice(0x35932, 8)),
                "both art_tile additions execute after the allocation helper returns");
        var fixture = bootFilledFixture(data, 5);
        assertEquals(0x22E5, fixture.machine.nativeArtWord(0));
        finishedRainbow(fixture, 100);
        fixture.pass();
        assertEquals(8, fixture.machine.ram().at(30).item.getAnimIndex());
        assertEquals(10, fixture.players.player(0).getRings(), "angle $30 misses neutral angle $40");
        assertEquals(20, fixture.players.player(1).getRings());
        assertEquals(0x42E5, fixture.machine.nativeArtWord(0));
        assertNull(fixture.machine.ram().at(127), "CFC0 retains ID zero after the stray art write");
        assertEquals(0x2000, fixture.machine.nativeArtWord(127));
        assertEquals(0x2000, fixture.machine.capture().inactiveArtWords().get(127));
        assertEquals(0, count(fixture.machine, Kind.WINGS));
        assertEquals(0, count(fixture.machine, Kind.HAND));
        assertTrue(fixture.events.log.isEmpty(), "the missing hand cannot archive the winner's rings");
        var sonicDisplay = playerDraw(fixture.machine, 0);
        assertEquals(0x42E5, sonicDisplay.nativeArt());
        assertEquals(2, sonicDisplay.palette(), "BuildSprites dereferences the art word after Sonic queued its display");
        assertEquals(0x35E19, fixture.machine.addressRegister2(), "PrintPhrase subsequently leaves its ROM terminator end");

        var savedMachine = fixture.machine.capture();
        var savedPlayers = fixture.players.capture();
        var savedTrack = fixture.track.capture();
        var replay = fixture(data);
        replay.players.restore(savedPlayers);
        replay.machine.restore(savedMachine);
        replay.track.restore(savedTrack);
        assertNotSame(fixture.machine.ram().at(0), replay.machine.ram().at(0));
        assertEquals(0x2000, replay.machine.nativeArtWord(127));
        assertEquals(savedMachine.addressRegister2(), replay.machine.addressRegister2());
        for (int tick = 0; tick < 24; tick++) {
            fixture.track.tick(); replay.track.tick();
            fixture.pass(); replay.pass();
            assertEquals(fixture.machine.capture(), replay.machine.capture());
            assertEquals(0x42E5, replay.machine.nativeArtWord(0));
            assertEquals(0x2000, replay.machine.nativeArtWord(127));
            assertEquals(2, playerDraw(replay.machine, 0).palette());
            assertTrue(replay.events.log.isEmpty());
        }
        assertEquals(10, replay.players.player(0).getRings());
        assertEquals(20, replay.players.player(1).getRings());
    }

    @Test
    void failedWingsWithoutAnEarlierSizeEightTestLeaveSonicPaletteUnchanged() throws Exception {
        var fixture = bootFilledFixture(data(), 20);
        finishedRainbow(fixture, 100);
        fixture.pass();
        assertEquals(1, fixture.machine.ram().at(30).item.getAnimIndex());
        assertEquals(0x22E5, fixture.machine.nativeArtWord(0),
                "the preceding player-shadow pass leaves a2 in ROM, so its stray write has no RAM target");
        assertEquals(1, playerDraw(fixture.machine, 0).palette());
        assertNull(fixture.machine.ram().at(127));
        assertEquals(0x2000, fixture.machine.nativeArtWord(127), "failed a1 still names the allocation guard");
        assertEquals(0, count(fixture.machine, Kind.WINGS));
        assertEquals(0, count(fixture.machine, Kind.HAND));
        assertTrue(fixture.events.log.isEmpty());
    }

    @Test
    void successfulWingsThenFailedHandBiasOnlyTheWingsAndGuardArtWords() throws Exception {
        var fixture = bootFilledFixture(data(), 5);
        // Initialize under full RAM first: the item's one-shot AfterCurrent
        // shadow allocation fails. Routine two cannot retry when slot 120 is
        // freed later, so that new hole belongs to the checkpoint's wing.
        fixture.pass();
        assertEquals(2, fixture.machine.ram().at(30).routine());
        assertEquals(-1, fixture.machine.ram().at(30).child());
        fixture.machine.ram().clear(120);
        finishedRainbow(fixture, 100);
        fixture.pass();
        assertEquals(8, fixture.machine.ram().at(30).item.getAnimIndex());
        assertEquals(Kind.WINGS, fixture.machine.ram().at(120).kind());
        assertEquals(69, fixture.machine.ram().at(120).timer, "the later wing executes on its creation pass");
        assertEquals(0x41A2, fixture.machine.nativeArtWord(120),
                "successful first allocation replaces a2 with the wing SST before the second allocation fails");
        assertEquals(0x22E5, fixture.machine.nativeArtWord(0));
        assertEquals(1, playerDraw(fixture.machine, 0).palette());
        assertNull(fixture.machine.ram().at(127));
        assertEquals(0x2000, fixture.machine.nativeArtWord(127));
        assertEquals(1, count(fixture.machine, Kind.WINGS));
        assertEquals(0, count(fixture.machine, Kind.HAND));
        assertTrue(fixture.machine.renderFrame().draws().stream()
                .anyMatch(draw -> draw.type() == CompetitiveHalfpipeRenderFrame.Type.WINGS && draw.slot() == 120 && draw.palette() == 2));
        assertTrue(fixture.events.log.isEmpty());
    }

    @Test
    void pendingOwnerRecreationRepeatsDebitsAllocationOrderAndForwardParticleStates() throws Exception {
        var data = data();
        var first = fixture(data);
        first.clearAndFillDynamic();
        first.machine.ram().clear(40);
        first.machine.ram().clear(80);
        first.rings(0, 13);
        first.rings(1, 27);
        assertEquals(40, first.machine.requestSpill(1));
        // This parent creates its later linked shadow before the spill owner
        // runs. Angle $30 stays outside either neutral actor's touch interval.
        first.machine.ram().replace(30, item(data, 50, 0x30));
        first.machine.ram().clear(31);
        var savedMachine = first.machine.capture();
        var savedPlayers = first.players.capture();
        var savedTrack = first.track.capture();
        var originalOwner = first.machine.ram().at(40);
        var replay = fixture(data);
        replay.players.restore(savedPlayers);
        replay.machine.restore(savedMachine);
        replay.track.restore(savedTrack);
        assertNotSame(first.players.player(1), replay.players.player(1));
        assertNotSame(originalOwner, replay.machine.ram().at(40));
        assertNotSame(first.machine.ram().at(30).item, replay.machine.ram().at(30).item);
        for (int tick = 0; tick < 80; tick++) {
            first.track.tick(); replay.track.tick();
            first.pass(); replay.pass();
            assertEquals(first.machine.capture(), replay.machine.capture());
            assertEquals(first.players.player(0).getRings(), replay.players.player(0).getRings());
            assertEquals(first.players.player(1).getRings(), replay.players.player(1).getRings());
            assertEquals(first.events.log, replay.events.log);
            if (tick == 0) {
                assertEquals(31, replay.machine.ram().at(30).child());
                assertEquals(30, replay.machine.ram().at(31).owner());
            }
        }
        assertEquals(List.of("spill:1"), replay.events.log);
        assertEquals(13, replay.players.player(0).getRings());
        assertEquals(17, replay.players.player(1).getRings());
        assertEquals(0, count(replay.machine, Kind.SPILL));
        assertEquals(Kind.SPILL_OWNER, savedMachine.ram().slots().get(40).state().kind());
    }

    private static CompetitiveHalfpipeObjectMachine.Node item(CompetitiveHalfpipeRomData data, int depth, int angle) {
        var node = new CompetitiveHalfpipeObjectMachine.Node(Kind.ITEM);
        node.item = CompetitiveHalfpipeItems.create(data, false, depth, angle);
        return node;
    }
    private static CompetitiveHalfpipeObjectMachine.Node hidden() {
        return new CompetitiveHalfpipeObjectMachine.Node(Kind.HIDDEN);
    }
    private static long count(CompetitiveHalfpipeObjectMachine machine, Kind kind) {
        return machine.capture().ram().slots().stream().filter(slot -> slot.state() != null && slot.state().kind() == kind).count();
    }
    private static Fixture fixture() throws Exception { return fixture(data()); }
    private static Fixture fixture(CompetitiveHalfpipeRomData data) { return new Fixture(data); }
    private static Fixture bootFilledFixture(CompetitiveHalfpipeRomData data, int depth) {
        var fixture = fixture(data);
        fixture.machine.initializeHiddenMessages();
        fixture.pass();
        for (int slot = 0; slot < 8; slot++) assertNotNull(fixture.machine.ram().at(slot));
        for (int slot = 8; slot <= 126; slot++) fixture.machine.ram().replace(slot, hidden());
        fixture.machine.ram().replace(30, item(data, depth, 0x30));
        fixture.rings(0, 10);
        fixture.rings(1, 20);
        fixture.inStraightFourWindow();
        return fixture;
    }
    private static void finishedRainbow(Fixture fixture, int slot) {
        var rainbow = new CompetitiveHalfpipeObjectMachine.Node(Kind.RAINBOW);
        rainbow.base = 6;
        rainbow.frame = 9;
        rainbow.x = 0xE8;
        rainbow.mapping = 9;
        fixture.machine.ram().replace(slot, rainbow);
    }
    private static CompetitiveHalfpipeRenderFrame.Draw playerDraw(CompetitiveHalfpipeObjectMachine machine, int owner) {
        return machine.renderFrame().draws().stream()
                .filter(draw -> draw.type() == CompetitiveHalfpipeRenderFrame.Type.PLAYER && draw.owner() == owner)
                .findFirst().orElseThrow();
    }
    private static CompetitiveHalfpipeRomData data() throws Exception {
        return new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
    }
    private static final class Fixture {
        final CompetitiveHalfpipeRomData data;
        final CompetitiveSpecialStagePlayerAccess.Pair players = CompetitiveSpecialStagePlayerAccess.create();
        final RecordingEvents events = new RecordingEvents();
        final CompetitiveHalfpipeObjectMachine machine;
        final CompetitiveHalfpipeTrack track;
        Fixture(CompetitiveHalfpipeRomData data) {
            this.data = data;
            machine = new CompetitiveHalfpipeObjectMachine(data, players, AudioManager.createSilent(), events);
            machine.load(0);
            track = new CompetitiveHalfpipeTrack(data);
        }
        void rings(int owner, int count) { for (int i = 0; i < count; i++) players.collectRing(owner); }
        void pass() { machine.runPass(track, 0x14, ignored -> { }); }
        void clearAndFillDynamic() {
            machine.ram().clear();
            for (int slot = 8; slot <= 126; slot++) machine.ram().replace(slot, hidden());
        }
        void inStraightFourWindow() {
            for (int tick = 0; tick < 100; tick++) {
                track.tick();
                if (track.previousFrame() == 0x14 && track.drawingIndex() == 4) return;
            }
            fail("retail straight animation must publish the previous-Straight4 gate");
        }
    }
    private static final class RecordingEvents implements Events {
        final List<String> log = new ArrayList<>();
        @Override public void collectedRing(int slot) { log.add("ring:" + slot); }
        @Override public void hitBomb(int slot) { log.add("spill:" + slot); }
        @Override public void checkpoint() { fail("native machine cannot call the legacy immediate checkpoint ingress"); }
        @Override public void archivedSector(int sector, int one, int two) { log.add("archive:" + sector + ":" + one + ":" + two); }
        @Override public void tiedSector(int sector) { log.add("tie:" + sector); }
        @Override public void stageComplete() { log.add("complete"); }
    }
}
