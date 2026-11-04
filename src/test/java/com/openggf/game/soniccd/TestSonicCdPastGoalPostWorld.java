package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Object-only World tests. Uses the existing cold five-jump
 * programme to the fixed healthy8278 cut and changes one initially empty physical object image only.
 * No actor/global/camera/terrain/art/clock or private invocation is forged; no failed owner is restored.
 * Native N1ecc5f3a Level End/Main.asm568-702, Object Spawner385-446. Component vectors own other-family
 * raw tile/TTZ latch edges; actual production B family cannot reach those malformed metadata reads.
 * Constructed object contact is not natural row490 GoalReached, act completion, parity or pixel evidence.
 */
@Isolated
class TestSonicCdPastGoalPostWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void fixedObjectInitUsesActualPlayerYAndConditionalXWithEveryDirtyNonwriteRetained() throws Exception {
        var p = ordinary(); sameChunk(p); goalPatterns(p);
        var m = p.frame().state().motion();
        var entry = fixed(p).moveByte(ROUTINE, 0).moveWord(X, ((m.centreX16_16() >>> 16) + 64) & 0xFFFF)
                .moveWord(Y, m.centreY16_16() >>> 16).moveWord(X_FRACTION, 0x1357).moveWord(Y_FRACTION, 0x2468)
                .moveByte(FLAGS, 0x40).moveByte(COLLISION_STATUS, 0xA5).moveByte(ANIMATION_FRAME, 0x79)
                .moveByte(ANIMATION_TIME, 0x42).moveWord(ROBOT_GENERATOR_TIMER, 0x9ABC);
        seed(p, entry); replay(p); var actual = p.placement().pool().state(127);
        var actor = p.frame().state().motion();
        int dy = ((actor.centreY16_16() >>> 16) - entry.centreY() + 128) & 0xFFFF;
        assertTrue(dy < 256, "Fixed healthy ingress must actually reach the conditional X read");
        assertTrue((actor.centreX16_16() >>> 16) < entry.centreX(), "One fixed object offset, no actor fitting or search");
        var expected = entry.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, entry.spriteFlags() | 4).moveByte(PRIORITY, 4)
                .moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST).range().nativeAddress())
                .moveByte(X_RADIUS, 16).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 32).moveByte(MAP_FRAME, 5).moveWord(TILE, 0x835A);
        assertEquals(expected.moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
        assertEquals(expected.spriteFlags() & 0x7F, actual.spriteFlags() & 0x7F);
        assertEquals(1, calls(p, SonicCdGameModule.PastLoadCall.GOAL_POST_BODY));
    }

    @Test void fixedActualThresholdPublishesPreparedFrameScrollContactThenAppendsNativeHex12List() throws Exception {
        var p = ordinary(); sameChunk(p); goalPatterns(p);
        var presentation = presentation(p); var beforeScroll = presentation.capturePastScroll();
        var motion = p.frame().state().motion();
        var entry = fixed(p).moveWord(X, ((motion.centreX16_16() >>> 16) - 64) & 0xFFFF)
                .moveWord(Y, motion.centreY16_16() >>> 16).moveWord(X_FRACTION, 0x1357).moveWord(Y_FRACTION, 0x2468);
        seed(p, entry); var oldGlobals = loadingGlobals(p); var oldContact = contact(p); replay(p);
        var after = p.frame().state().motion();
        int dy = ((after.centreY16_16() >>> 16) - entry.centreY() + 128) & 0xFFFF;
        assertTrue(dy < 256 && (after.centreX16_16() >>> 16) >= entry.centreX(), "Actual current actor must pass both native checks");
        var live = p.placement().pool().state(127);
        assertEquals(entry.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, live.spriteFlags()), live);
        int expectedLeft = beforeScroll.state().cameraX(); var environment = p.frame().environment();
        assertEquals(expectedLeft, environment.movement().bounds().left());
        assertEquals(expectedLeft, presentation.pastForegroundImage().destLeftBound());
        assertEquals(0, environment.globals().timeWarpTimer()); assertEquals(0, environment.movement().gates().timeWarpDirection());
        assertEquals(new CdPlayerBody.ContactGlobals(0, oldContact.shield(), oldContact.combineRing()), contact(p));
        assertEquals(actualField(oldGlobals, "retainedDev"), actualField(loadingGlobals(p), "retainedDev"));
        assertEquals(actualField(oldGlobals, "score"), actualField(loadingGlobals(p), "score"));
        assertEquals(1, calls(p, SonicCdGameModule.PastLoadCall.GOAL_POST_BODY));
        var queue = p.harness().module().pastLoadState().section().queue().bytes(); var destinations = new ArrayList<Integer>();
        var sources = new ArrayList<Long>();
        for (int at = 0; at + 6 <= queue.length() && queue.unsignedLong(at) != 0; at += 6) {
            sources.add(queue.unsignedLong(at)); destinations.add(queue.unsignedWord(at + 4));
        }
        assertTrue(destinations.size() >= 3);
        int tail = destinations.size() - 3;
        assertEquals(List.of(0x8780, 0x9100, 0x7DE0), destinations.subList(tail, destinations.size()),
                "Existing LoadPLC native$12 is decimal18; no camera-list12 substitution or service occurs in the callback");
        var signpost = CdR11bAssets.Asset.ART_SIGNPOST.range();
        assertTrue(sources.get(tail) >= signpost.nativeAddress() && sources.get(tail) < signpost.endExclusive(),
                "The ordinary post-object Prepare may advance only the genuine first source header");
        assertEquals((long) CdR11bAssets.Asset.ART_BIG_RING.range().nativeAddress(), sources.get(tail + 1).longValue());
        assertEquals((long) CdR11bAssets.Asset.ART_BIG_RING_FLASH.range().nativeAddress(), sources.get(tail + 2).longValue());
    }

    @Test void doneUsesNoPlayerReadOrGoalWriteAndRetainsEveryFull64DirtyByteExceptNativeScreenBit() throws Exception {
        var p = ordinary(); sameChunk(p); goalPatterns(p);
        var entry = fixed(p).moveByte(ROUTINE, 4).moveWord(X_FRACTION, 0xA135).moveWord(Y_FRACTION, 0xB246)
                .moveByte(SUBTYPE, 255).moveByte(SUBTYPE2, 0x71).moveByte(FLAGS, 0x40)
                .moveByte(ANIMATION, 0xC2).moveByte(PREVIOUS_ANIMATION, 0xD3).moveByte(COLLISION_STATUS, 0xE4);
        seed(p, entry); replay(p); var actual = p.placement().pool().state(127);
        assertEquals(entry.moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
        assertEquals(entry.spriteFlags() & 0x7F, actual.spriteFlags() & 0x7F);
        assertEquals(1, calls(p, SonicCdGameModule.PastLoadCall.GOAL_POST_BODY));
    }

    @Test void reachedInvalidRoutineFailsClosedWithSourceUntouchedAndNoFailedOwnerRestore() throws Exception {
        var p = ordinary(); sameChunk(p); var entry = fixed(p).moveByte(ROUTINE, 1); seed(p, entry);
        var failure = assertThrows(CdGoalPost.UnsupportedGoalPost.class, () -> step(p.harness(), neutral()));
        assertEquals(CdGoalPost.UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary()); assertEquals(127, failure.wholeIndex());
        assertEquals(entry, p.placement().pool().state(127)); assertTrue(p.frame().closed()); retired(p);
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, ((CdObjectDrawQueue) actualField(p.visit(), "drawQueue")).phase());
        var calls = (List<?>) actualField(actualField(p.visit(), "values"), "calls");
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.GOAL_POST_BODY));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
    }

    @Test void preparedPresentationValueIsCallbackFreeAndBecomesStaleAfterActualGoalPublication() throws Exception {
        var p = ordinary(); sameChunk(p); var presentation = presentation(p); var before = captureNative(p.harness());
        var scroll = presentation.capturePastScroll();
        var prepared = presentation.preparePastGoalPostDestLeftBound(p.frame(), scroll, scroll.state().cameraX());
        assertNativeCapture(before, p.harness());
        assertEquals(scroll.state().cameraX(), prepared.result().state().destLeftBound());
        assertSame(scroll.background(), prepared.result().background()); assertSame(scroll.plane(), prepared.result().plane());
        assertSame(scroll.copies(), prepared.result().copies());
        var actor = p.frame().state().motion(); seed(p, fixed(p).moveWord(X, ((actor.centreX16_16() >>> 16) - 64) & 0xFFFF)
                .moveWord(Y, actor.centreY16_16() >>> 16));
        replay(p); assertThrows(IllegalArgumentException.class, () -> presentation.validatePreparedPastGoalPostDestLeftBound(prepared));
    }

    private static Prepared ordinary() throws Exception {
        var p = ready();
        for (int call = p.returned() + 1; call <= 8278; call++) { step(p.harness(), route(call)); healthy(p); }
        return p;
    }
    private static void healthy(Prepared p) throws Exception {
        assertSame(p.frame(), p.harness().module().requirePlayerFrame()); assertFalse(p.frame().closed());
        assertInstanceOf(CdGoalPost.class, actualField(p.visit(), "goalPost"));
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN)); retired(p);
    }
    private static void retired(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "activeGoal")); assertFalse((boolean) actualField(p.visit(), "goalPort"));
        assertFalse((boolean) actualField(p.visit(), "drawingGoal"));
    }
    private static void replay(Prepared p) throws Exception {
        var h = p.harness(); var registry = h.mode().getRewindRegistry(); var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var source = p.frame().stageSource(); var body = actualField(p.visit(), "goalPost");
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        step(h, neutral()); healthy(p); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h); healthy(p); step(h, neutral()); healthy(p); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h); healthy(p); step(h, neutral()); healthy(p); assertNativeCapture(after, h);
        assertSame(world, h.mode().getWorldSession()); assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(source, p.frame().stageSource()); assertSame(body, actualField(p.visit(), "goalPost"));
    }
    private static CdObjectState fixed(Prepared p) {
        var pool = p.placement().pool(); assertEquals(0, pool.state(127).id()); assertEquals(0, pool.state(127).routine());
        var m = p.frame().state().motion();
        return pool.state(127).moveByte(ID, 0x16).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4)
                .moveWord(X, m.centreX16_16() >>> 16).moveWord(Y, m.centreY16_16() >>> 16)
                .moveByte(PRIORITY, 4).moveByte(WIDTH, 16).moveByte(X_RADIUS, 16).moveByte(Y_RADIUS, 32)
                .moveByte(MAP_FRAME, 5).moveWord(TILE, 0x835A)
                .moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST).range().nativeAddress());
    }
    private static void seed(Prepared p, CdObjectState state) {
        var before = p.placement().capture(); p.placement().pool().write(state);
        assertEquals(before.status(), p.placement().status()); assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var old : before.pool().slots()) if (old.identity().wholeIndex() != state.identity().wholeIndex())
            assertEquals(old, p.placement().pool().state(old.identity().wholeIndex()));
    }
    private static void sameChunk(Prepared p) throws Exception {
        assertEquals(presentation(p).pastForegroundImage().cameraX() & 0xFF80, p.placement().status().previousChunkWord(),
                "One fixed healthy scene; no coordinate search or prespawn change is performed");
    }
    private static void goalPatterns(Prepared p) {
        for (int tile = 0; tile < 24; tile++) CdR11bCameraArtResidency.readTile(p.harness().module().pastLoadState().section().residency(), 0x35A + tile);
    }
    private static SonicCdPlayerPresentation presentation(Prepared p) throws Exception {
        return (SonicCdPlayerPresentation) actualField(p.harness().module(), "playerPresentation");
    }
    private static Object loadingGlobals(Prepared p) throws Exception { return actualField(actualField(p.visit(), "values"), "globals"); }
    private static CdPlayerBody.ContactGlobals contact(Prepared p) throws Exception { return (CdPlayerBody.ContactGlobals) actualField(loadingGlobals(p), "contact"); }
    private static long calls(Prepared p, SonicCdGameModule.PastLoadCall call) {
        return p.harness().module().pastLoadState().calls().stream().filter(c -> c == call).count();
    }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }
}
