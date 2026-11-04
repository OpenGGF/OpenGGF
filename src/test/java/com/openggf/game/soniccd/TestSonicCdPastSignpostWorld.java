package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.CdLevelHud;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual-World object-only scenes. The cold five-jump recipe
 * reaches fixed healthy8278, then only initially empty object images are changed. No actor/global/
 * camera/art/clock/invocation is manufactured; current aliased pixels remain actual and are not certified
 * as Signpost art. Native N1ecc5f3a Level End708-907, Main CPU Variables67-68. These fixed scenes are
 * not naturally spawned end-of-act contact, completed Results, parity or pixels. Failed owners are never restored.
 */
@Isolated
class TestSonicCdPastSignpostWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void fixedInitUsesActualZoneAndStrictPlayerThresholdWhilePreservingDirtyNonwrites() throws Exception {
        var p = ordinary(); sameChunk(p); var m = p.frame().state().motion();
        var entry = fixed(p, 127).moveByte(ROUTINE, 0).moveWord(X, ((m.centreX16_16() >>> 16) + 64) & 0xFFFF)
                .moveWord(Y, m.centreY16_16() >>> 16).moveWord(X_FRACTION, 0x1357).moveWord(Y_FRACTION, 0x2468)
                .moveByte(FLAGS, 0x40).moveByte(COLLISION_STATUS, 0x51).moveByte(ANIMATION_FRAME, 0x62).moveByte(ANIMATION_TIME, 0x73)
                .moveByte(LEVEL_END_TIMER, 0x84);
        seed(p, entry); replay(p); var actual = p.placement().pool().state(127); var actor = p.frame().state().motion();
        assertTrue(((actor.centreY16_16() >>> 16) - entry.centreY() + 128 & 0xFFFF) < 256);
        assertTrue((actor.centreX16_16() >>> 16) < entry.centreX(), "One fixed object offset; no equality fitting or actor override");
        var expected = entry.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, entry.spriteFlags() | 4)
                .moveByte(X_RADIUS, 24).moveByte(WIDTH, 24).moveByte(Y_RADIUS, 32).moveByte(PRIORITY, 4)
                .moveWord(TILE, 0x843C).moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST).range().nativeAddress());
        assertEquals(expected.moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
        assertEquals(expected.spriteFlags() & 0x7F, actual.spriteFlags() & 0x7F);
        assertEquals(1, calls(p, SonicCdGameModule.PastLoadCall.SIGNPOST_BODY)); assertTrue(signpostSounds(p).isEmpty());
    }

    @Test void actualStrictPassLocksBoundsStopsHudClearsOnlyPowerupFlagsAndRequestsFm9d() throws Exception {
        var p = ordinary(); sameChunk(p); var presentation = presentation(p); int camera = presentation.pastForegroundImage().cameraX();
        var m = p.frame().state().motion();
        var entry = fixed(p, 127).moveWord(X, ((m.centreX16_16() >>> 16) - 64) & 0xFFFF).moveWord(Y, m.centreY16_16() >>> 16)
                .moveWord(X_FRACTION, 0x1357).moveWord(Y_FRACTION, 0x2468).moveByte(LEVEL_END_TIMER, 0xA5);
        seed(p, entry); replay(p); var actor = p.frame().state().motion(); var actual = p.placement().pool().state(127);
        assertTrue(((actor.centreY16_16() >>> 16) - entry.centreY() + 128 & 0xFFFF) < 256);
        assertTrue((actor.centreX16_16() >>> 16) > entry.centreX(), "Actual strict X branch, not GoalPost equality semantics");
        assertEquals(entry.moveByte(LEVEL_END_TIMER, 120).moveByte(MAP_FRAME, 0).addByte(ROUTINE, 2)
                .moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
        var e = p.frame().environment();
        assertEquals(camera, e.movement().bounds().left()); assertEquals(camera, presentation.pastForegroundImage().destLeftBound());
        assertEquals(0, p.harness().module().pastLoadState().level().updateHudTime());
        assertEquals(0, e.movement().gates().speedShoes()); assertEquals(0, e.globals().invincible());
        assertEquals(List.of(CdSignpost.Sound.FM_SIGNPOST), signpostSounds(p));
        assertEquals(0x9D, signpostSounds(p).getFirst().nativeId());
    }

    @Test void realSpinAnimationAndBothByteCountdownsReplayWithoutStoresOrSounds() throws Exception {
        var p = ordinary(); sameChunk(p);
        var entry = fixed(p, 127).moveByte(ROUTINE, 4).moveByte(LEVEL_END_TIMER, 2)
                .moveByte(ANIMATION, 0).moveByte(PREVIOUS_ANIMATION, 0).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
        seed(p, entry); replay(p); var one = p.placement().pool().state(127);
        assertEquals(entry.moveByte(ANIMATION_FRAME, 1).moveByte(ANIMATION_TIME, 1).moveByte(MAP_FRAME, 0)
                .moveByte(LEVEL_END_TIMER, 1).moveByte(SPRITE_FLAGS, one.spriteFlags()), one);
        replay(p); var two = p.placement().pool().state(127);
        assertEquals(one.moveByte(ANIMATION_TIME, 0).moveByte(LEVEL_END_TIMER, 60).moveByte(ROUTINE, 6)
                .moveByte(MAP_FRAME, 3).moveByte(SPRITE_FLAGS, two.spriteFlags()), two);
        replay(p); var three = p.placement().pool().state(127);
        assertEquals(two.moveByte(LEVEL_END_TIMER, 59).moveByte(SPRITE_FLAGS, three.spriteFlags()), three);
        assertTrue(signpostSounds(p).isEmpty());
    }

    @Test void earlierRealResultsChildKeepsDirtyBytesAndActualClockBonusesThenExecutesOnTheFollowingPass() throws Exception {
        var p = ordinary(); sameChunk(p); proveResultsOwners(p);
        int parent = 127; var entry = fixed(p, parent).moveByte(ROUTINE, 6).moveByte(LEVEL_END_TIMER, 1).moveByte(MAP_FRAME, 3);
        seed(p, entry); int child = p.placement().pool().findObjSlot().orElseThrow(); assertTrue(child < parent);
        var beforeChild = p.placement().pool().state(child).moveWord(X_FRACTION, 0x1357).moveByte(FLAGS, 0x40);
        seed(p, beforeChild); replay(p);
        var liveChild = p.placement().pool().state(child);
        assertEquals(beforeChild.moveByte(ID, 0x3A).moveByte(RESULTS_TIMER, 16), liveChild);
        var liveParent = p.placement().pool().state(parent);
        assertEquals(entry.moveByte(LEVEL_END_TIMER, 180).moveByte(ROUTINE, 8).moveByte(SPRITE_FLAGS, liveParent.spriteFlags()), liveParent);
        assertEquals(List.of(CdSignpost.Sound.SCMD_FADEPCM, CdSignpost.Sound.SCMD_RESULTMUS), signpostSounds(p));
        var controls = p.frame().environment().controls();
        assertEquals(1, controls.controlLocked() & 1); assertEquals(8, controls.logicalHeld()); assertEquals(8, controls.logicalTap());
        assertEquals(0, controls.rawHeld()); assertEquals(0, controls.rawTap());
        assertBonuses(p);
        replay(p);
        assertEquals(liveChild.moveByte(RESULTS_TIMER, 15), p.placement().pool().state(child));
        assertEquals(1, calls(p, SonicCdGameModule.PastLoadCall.RESULTS_BODY));
        assertFalse(p.frame().closed()); retired(p);
    }

    @Test void laterRealResultsChildExecutesTheSameIncreasingPassAfterEveryOwnedSignpostPrefix() throws Exception {
        var p = ordinary(); sameChunk(p); proveResultsOwners(p); var pool = p.placement().pool();
        int parent = pool.findObjSlot().orElseThrow(); var entry = fixed(p, parent).moveByte(ROUTINE, 6)
                .moveByte(LEVEL_END_TIMER, 1).moveByte(MAP_FRAME, 3); seed(p, entry);
        int child = pool.findObjSlot().orElseThrow(); assertTrue(child > parent, "Actual first-free order, not filler allocations");
        var beforeChild = pool.state(child);
        replay(p);
        assertEquals(beforeChild.moveByte(ID, 0x3A).moveByte(RESULTS_TIMER, 15), pool.state(child));
        var actual = pool.state(parent);
        assertEquals(entry.moveByte(LEVEL_END_TIMER, 180).moveByte(ROUTINE, 8).moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
        assertFalse(p.frame().closed()); retired(p);
        var values = actualField(p.visit(), "values"); var calls = (List<?>) actualField(values, "calls");
        assertTrue(calls.contains(SonicCdGameModule.PastLoadCall.SIGNPOST_BODY));
        assertTrue(calls.contains(SonicCdGameModule.PastLoadCall.RESULTS_BODY));
        assertTrue(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertEquals(List.of(CdSignpost.Sound.SCMD_FADEPCM, CdSignpost.Sound.SCMD_RESULTMUS), rawSignpostSounds(p));
        assertBonuses(p);
        assertNull(actualField(p.visit(), "drawQueue"), "Successful ordinary return retires its queue");
    }

    @Test void genuineMalformedAnimationReadRetainsOnlyItsNativeHeadAndFailsClosedWithoutRestore() throws Exception {
        var p = ordinary(); sameChunk(p); var entry = fixed(p, 127).moveByte(ROUTINE, 4).moveByte(LEVEL_END_TIMER, 7)
                .moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 255).moveByte(ANIMATION_TIME, 0);
        seed(p, entry); var failure = assertThrows(CdSignpost.UnsupportedSignpost.class, () -> step(p.harness(), neutral()));
        assertEquals(CdSignpost.UnsupportedBoundary.ANIMATION_DIRECTORY_READ, failure.boundary()); assertEquals(127, failure.wholeIndex());
        assertEquals(entry.moveByte(ANIMATION_TIME, 255), p.placement().pool().state(127));
        assertTrue(p.frame().closed()); retired(p);
        var calls = (List<?>) actualField(actualField(p.visit(), "values"), "calls");
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.SIGNPOST_BODY)); assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, ((CdObjectDrawQueue) actualField(p.visit(), "drawQueue")).phase());
    }

    private static Prepared ordinary() throws Exception {
        var p = ready(); for (int call = p.returned() + 1; call <= 8278; call++) { step(p.harness(), route(call)); healthy(p); } return p;
    }
    private static void healthy(Prepared p) throws Exception {
        assertSame(p.frame(), p.harness().module().requirePlayerFrame()); assertFalse(p.frame().closed());
        assertInstanceOf(CdSignpost.class, actualField(p.visit(), "signpost"));
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN)); retired(p);
    }
    private static void retired(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "activeSignpost")); assertFalse((boolean) actualField(p.visit(), "signpostPort"));
        assertFalse((boolean) actualField(p.visit(), "drawingSignpost"));
    }
    private static void replay(Prepared p) throws Exception {
        var h = p.harness(); var registry = h.mode().getRewindRegistry(); var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var source = p.frame().stageSource(); var body = actualField(p.visit(), "signpost");
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        step(h, neutral()); healthy(p); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h); healthy(p); step(h, neutral()); healthy(p); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h); healthy(p); step(h, neutral()); healthy(p); assertNativeCapture(after, h);
        assertSame(world, h.mode().getWorldSession()); assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(source, p.frame().stageSource()); assertSame(body, actualField(p.visit(), "signpost"));
    }
    private static CdObjectState fixed(Prepared p, int whole) {
        var pool = p.placement().pool(); assertEquals(0, pool.state(whole).id()); assertEquals(0, pool.state(whole).routine());
        var m = p.frame().state().motion();
        return pool.state(whole).moveByte(ID, 0x17).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4)
                .moveWord(X, m.centreX16_16() >>> 16).moveWord(Y, m.centreY16_16() >>> 16)
                .moveByte(PRIORITY, 4).moveByte(WIDTH, 24).moveByte(X_RADIUS, 24).moveByte(Y_RADIUS, 32)
                .moveByte(MAP_FRAME, 0).moveWord(TILE, 0x843C)
                .moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST).range().nativeAddress());
    }
    private static void seed(Prepared p, CdObjectState state) {
        var before = p.placement().capture(); p.placement().pool().write(state);
        assertEquals(before.status(), p.placement().status()); assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var old : before.pool().slots()) if (old.identity().wholeIndex() != state.identity().wholeIndex()) assertEquals(old, p.placement().pool().state(old.identity().wholeIndex()));
    }
    private static void sameChunk(Prepared p) throws Exception {
        assertEquals(presentation(p).pastForegroundImage().cameraX() & 0xFF80, p.placement().status().previousChunkWord(), "One fixed healthy cut; no prespawn or coordinate search");
    }
    private static SonicCdPlayerPresentation presentation(Prepared p) throws Exception { return (SonicCdPlayerPresentation) actualField(p.harness().module(), "playerPresentation"); }
    private static Object globals(Prepared p) throws Exception { return actualField(actualField(p.visit(), "values"), "globals"); }
    private static CdCheckpointLiveAux.State live(Prepared p) throws Exception { return (CdCheckpointLiveAux.State) actualField(globals(p), "live"); }
    private static void proveResultsOwners(Prepared p) throws Exception {
        assertTrue(live(p).knownByte(CdCheckpointLiveAux.TimeByte.MINUTES.nativeAddress()).isPresent(), "Actual minute owner must be known; no default/seed");
        assertTrue(live(p).knownByte(CdCheckpointLiveAux.TimeByte.SECONDS.nativeAddress()).isPresent(), "Actual second owner must be known; no default/seed");
        assertTrue(((Optional<?>) actualField(globals(p), "bonus")).isPresent(), "Actual loading-owned bonus preimage, never a fabricated zero");
    }
    private static void assertBonuses(Prepared p) throws Exception {
        int minute = live(p).knownByte(CdCheckpointLiveAux.TimeByte.MINUTES.nativeAddress()).orElseThrow();
        int second = live(p).knownByte(CdCheckpointLiveAux.TimeByte.SECONDS.nativeAddress()).orElseThrow();
        int[] nativeBonuses = {50000,50000,10000,5000,4000,4000,3000,3000,2000,2000,2000,2000,1000,1000,1000,1000,500,500,500,500,0};
        int index = Math.min(((minute * 60 + second) & 0xFFFF) / 15, 20);
        var bonus = (CdLevelHud.BonusValues) ((Optional<?>) actualField(globals(p), "bonus")).orElseThrow();
        var ring = (CdRingGlobals) actualField(actualField(p.visit(), "values"), "ring");
        assertEquals(new CdLevelHud.BonusValues(nativeBonuses[index], (ring.rings() * 100) & 0xFFFF, 1), bonus);
    }
    private static List<CdSignpost.Sound> signpostSounds(Prepared p) { return p.harness().module().pastLoadState().sounds().stream()
            .filter(SonicCdGameModule.PastSignpostSound.class::isInstance).map(SonicCdGameModule.PastSignpostSound.class::cast)
            .map(SonicCdGameModule.PastSignpostSound::sound).toList(); }
    private static List<CdSignpost.Sound> rawSignpostSounds(Prepared p) throws Exception { return ((List<?>) actualField(p.visit(), "sounds")).stream()
            .filter(SonicCdGameModule.PastSignpostSound.class::isInstance).map(SonicCdGameModule.PastSignpostSound.class::cast)
            .map(SonicCdGameModule.PastSignpostSound::sound).toList(); }
    private static long calls(Prepared p, SonicCdGameModule.PastLoadCall call) { return p.harness().module().pastLoadState().calls().stream().filter(c -> c == call).count(); }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }
}
