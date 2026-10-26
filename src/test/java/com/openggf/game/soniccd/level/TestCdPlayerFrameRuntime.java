package com.openggf.game.soniccd.level;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdSolidObject;
import com.openggf.game.soniccd.objects.CdSpinningDisc;
import com.openggf.game.soniccd.objects.CdSpring;
import com.openggf.game.soniccd.objects.CdSpinTunnelTag;
import com.openggf.game.soniccd.objects.CdTunnelPath;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.player.CdPlayerCharge;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real component and load-plan decoders consume constructed bounded format inputs; this package
 * uses the existing aggregate constructor, never a production test factory,
 * reflection, mock Body/art implementation, supplied-media fallback or skip.
 * Constructed bytes are not an authenticated retail MMD/native execution.
 */
class TestCdPlayerFrameRuntime {

    // Direct object Hurt ports: source-derived values at closed cuts, never object/phase authority. JAVA UNRUN.
    @Test void objectPreHurtYRewindUsesSignedWordShiftFullLongWrapAndEveryFractionBit() throws IOException {
        int[][] vectors = {{0, 1, 0xFFFFFF00}, {0, -1, 0x100}, {0x1234, 0x7FFF, 0xFF801334},
                {0xFFFFFFFF, -0x8000, 0x007FFFFF}, {0x80000000, -0x8000, 0x80800000},
                {0x00010001, 0x101, 0xFFFFFF01}, {0xFFFDBEEF, -0x234, 0xFFFFF2EF}, {0x12345678, 0, 0x12345678}};
        for (var v : vectors) {
            var fixture = new Fixture(); var f = spinTunnelDirtyFrame(fixture); var old = f.captureActor(); var s = old.state();
            var motion = hurtMotion(s.motion(), v[0], v[1], s.motion().flags());
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(old.owner(), new CdPlayerBody.State(motion, s.animation(),
                    s.header(), s.timers(), s.history(), s.rotationDistance(), s.objectLinks(), s.rotationAngle()), old.display()));
            var before = f.captureActor(); var shared = f.captureShared(); var a = hurtActor(f); int uploads = fixture.dmas.size();
            f.bindArtPublication((request, state) -> fail("Y prefix has no art publication authority"));
            f.validateObjectPreHurtYRewind(a, v[2]); assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
            f.applyObjectPreHurtYRewind(a, v[2]);
            var expected = new CdPlayerBody.State(hurtMotion(motion, v[2], v[1], motion.flags()), s.animation(),
                    s.header(), s.timers(), s.history(), s.rotationDistance(), s.objectLinks(), s.rotationAngle());
            assertEquals(new CdPlayerFrameRuntime.ActorState(before.owner(), expected, before.display()), f.captureActor());
            assertSame(s.animation(), f.state().animation()); assertSame(s.header(), f.state().header());
            assertSame(s.timers(), f.state().timers()); assertSame(s.history(), f.state().history());
            assertSame(s.objectLinks(), f.state().objectLinks()); assertSame(before.display(), f.display());
            assertSame(shared.environment(), f.environment()); assertSame(shared.artState(), f.artState());
            assertSame(shared.vIntClock(), f.vIntClock()); assertEquals(shared, f.captureShared());
            assertEquals(uploads, fixture.dmas.size());
            var after = f.captureActor(); f.restoreActor(before); f.applyObjectPreHurtYRewind(a, v[2]);
            assertEquals(after, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    @Test void objectPreHurtYRewindRejectsIntegerOnlyUnsignedOrOtherwiseWrongArithmeticWithoutAPrefix() throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var a = hurtActor(f); var m = a.motion();
        int exact = 0xFFFFF2EF;
        assertEquals(exact, m.centreY16_16() - (m.yVelocity() << 8));
        for (int wrong : new int[]{exact ^ 1, m.centreY16_16(), m.centreY16_16() - m.yVelocity(),
                m.centreY16_16() - ((m.yVelocity() & 0xFFFF) << 8), (exact & 0xFFFF0000) | (m.centreY16_16() & 0xFFFF)}) {
            var actor = f.captureActor(); var shared = f.captureShared();
            assertThrows(IllegalArgumentException.class, () -> f.validateObjectPreHurtYRewind(a, wrong));
            assertThrows(IllegalArgumentException.class, () -> f.applyObjectPreHurtYRewind(a, wrong));
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
            assertSame(actor.state(), f.state()); assertSame(shared.environment(), f.environment());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"CENTRE_X_WORD", "X_FRACTION", "CENTRE_Y_WORD", "Y_FRACTION",
            "X_VELOCITY", "Y_VELOCITY", "GROUND_VELOCITY", "ROUTINE", "ANGLE", "FLAGS", "SPRITE_FLAGS",
            "X_RADIUS", "Y_RADIUS", "ANIMATION", "PREVIOUS_ANIMATION", "PLAYER_JUMP", "CHARGE", "MOVE_LOCK",
            "STICK", "PLAYER_CONTROL", "PREVIOUS_PRIMARY_ANGLE", "PREVIOUS_SECONDARY_ANGLE", "TILE", "PRIORITY",
            "HURT", "ROTATION_DISTANCE"})
    void objectHurtPortsCasEveryExpectedActorFieldIncludingFieldsTheyCannotWrite(String field) throws IOException {
        var f = hurtFrame(new Fixture(), 0xB9, 0, 0, 0); var a = hurtActor(f); var stale = hurtActorChanged(a, field);
        var g = hurtGlobals(f); var pool = hurtPool(); var r = directFrameHurt(f, g, pool, 5);
        var actor = f.captureActor(); var shared = f.captureShared(); int y = a.motion().centreY16_16() - (a.motion().yVelocity() << 8);
        assertThrows(IllegalArgumentException.class, () -> f.validateObjectPreHurtYRewind(stale, y));
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectPreHurtYRewind(stale, y));
        hurtAssertRejected(f, stale, g, r);
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @ParameterizedTest @CsvSource({"0,0,0", "0,4,0", "1,0,0", "1,4,0", "0,0,64", "0,4,64", "1,0,64", "1,4,64"})
    void objectHurtAdoptsRealNormalResultsAndPreservesCompleteCapturePartitions(int mini, int roll, int water) throws IOException {
        var fixture = new Fixture(); var f = hurtFrame(fixture, 0xB9 | roll | water, mini, 0, 0);
        var before = f.captureActor(); var shared = f.captureShared(); var a = hurtActor(f); var g = hurtGlobals(f);
        var pool = hurtPool(); var cut = pool.capture(); var r = directFrameHurt(f, g, pool, 5); int uploads = fixture.dmas.size();
        assertEquals(CdPlayerTouchResponse.Exit.HURT, r.exit()); assertEquals(2, r.player().shield());
        assertEquals(0xC7, r.player().combineRing()); assertTrue(r.changedSlots().isEmpty());
        assertEquals(roll == 0 ? List.of(new CdPlayerTouchResponse.ResetOnFloor()) : List.of(
                new CdPlayerTouchResponse.ResetOnFloor(), new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)), r.calls());
        f.bindArtPublication((request, state) -> fail("Hurt port must not publish art or execute sound observations"));
        f.validateObjectHurtResult(a, g, r); assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        f.applyObjectHurtResult(a, g, r); hurtAssertAdopted(f, before, shared, r);
        assertSame(before.state().header(), f.state().header()); assertSame(shared.environment().movement().gates(), f.environment().movement().gates());
        assertEquals(cut.slots(), pool.capture().slots()); assertEquals(uploads, fixture.dmas.size());
        var after = f.captureActor(); var afterShared = f.captureShared(); hurtAssertRejected(f, a, g, r);
        f.restoreActor(before); f.restoreShared(shared); f.applyObjectHurtResult(a, g, r);
        assertEquals(after, f.captureActor()); assertEquals(afterShared, f.captureShared()); assertEquals(uploads, fixture.dmas.size());
    }

    @ParameterizedTest @CsvSource({"0,0", "0,4", "1,0", "1,4"})
    void objectHurtAdoptsRealKillWithPostResetStickWordAndRetainsChargeAndHurt(int mini, int roll) throws IOException {
        var fixture = new Fixture(); var f = hurtFrame(fixture, 0xB9 | roll, mini, 0, 0);
        var before = f.captureActor(); var shared = f.captureShared(); var a = hurtActor(f);
        var g = hurtPlayerGlobals(hurtGlobals(f), 2, 0); var pool = hurtPool(); var cut = pool.capture();
        var r = directFrameHurt(f, g, pool, 0); int uploads = fixture.dmas.size();
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER, r.exit()); assertEquals(a.hurt(), r.actor().hurt());
        assertEquals(a.motion().charge(), r.actor().motion().charge());
        int y = (a.motion().centreY16_16() >>> 16) - (roll == 0 ? 0 : mini == 0 ? 5 : 2); y &= 0xFFFF;
        assertEquals(y >>> 8, r.actor().motion().stick()); assertEquals(y & 0xFF, r.actor().rotationDistance());
        assertEquals(roll == 0 ? List.of(new CdPlayerTouchResponse.ResetOnFloor(),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT)) : List.of(
                new CdPlayerTouchResponse.ResetOnFloor(), new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT)), r.calls());
        f.bindArtPublication((request, state) -> fail("Kill port executes no call observations"));
        f.validateObjectHurtResult(a, g, r); assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        f.applyObjectHurtResult(a, g, r); hurtAssertAdopted(f, before, shared, r);
        assertSame(before.state().timers(), f.state().timers()); assertEquals(0, f.environment().globals().invincible());
        assertEquals(cut.slots(), pool.capture().slots()); assertEquals(uploads, fixture.dmas.size());
    }

    @ParameterizedTest @ValueSource(ints = {1, 256, 32768, 65535})
    void objectKillDebugNoWritePreservesTheAlreadyCommittedLongYPrefixAndAllReferences(int debug) throws IOException {
        var fixture = new Fixture(); var f = hurtFrame(fixture, 0xBD, 0, 0, debug); var a = hurtActor(f);
        f.applyObjectPreHurtYRewind(a, 0xFFFFF2EF);
        var before = f.captureActor(); var shared = f.captureShared(); var g = hurtPlayerGlobals(hurtGlobals(f), 2, 0);
        var pool = hurtPool(); var cut = pool.capture(); var r = directFrameHurt(f, g, pool, 0); int uploads = fixture.dmas.size();
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE, r.exit());
        assertEquals(hurtActor(f), r.actor()); assertEquals(g, r.player()); assertTrue(r.calls().isEmpty());
        f.validateObjectHurtResult(hurtActor(f), g, r); f.applyObjectHurtResult(hurtActor(f), g, r);
        assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        assertSame(before.state(), f.state()); assertSame(shared.environment(), f.environment());
        assertEquals(0xFFFFF2EF, f.state().motion().centreY16_16());
        assertEquals(cut.slots(), pool.capture().slots()); assertEquals(uploads, fixture.dmas.size());
    }

    @Test void objectHurtFailureAfterYPrefixRetainsThatPrefixAndPublishesNoPartOfTheResult() throws IOException {
        var fixture = new Fixture(); var f = hurtFrame(fixture, 0xBD, 0, 0, 0); var a = hurtActor(f);
        f.applyObjectPreHurtYRewind(a, 0xFFFFF2EF);
        var before = f.captureActor(); var shared = f.captureShared(); var g = hurtPlayerGlobals(hurtGlobals(f), 2, 0);
        var pool = hurtPool(); var cut = pool.capture(); var r = directFrameHurt(f, g, pool, 5); int uploads = fixture.dmas.size();
        assertEquals(1, r.changedSlots().size()); assertEquals(0xFFFF, r.changedSlots().getFirst().centreY());
        var bad = hurtReplace(r, hurtActorChanged(r.actor(), "PREVIOUS_ANIMATION"), r.player(), r.exit());
        hurtAssertRejected(f, hurtActor(f), g, bad);
        assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        assertEquals(0xFFFFF2EF, f.state().motion().centreY16_16());
        assertEquals(cut.slots(), pool.capture().slots()); assertEquals(uploads, fixture.dmas.size());
    }

    @Test void objectHurtCasUsesExactlyTheFiveFrameOwnedGlobalsRatherThanAllEightHelperInputs() throws IOException {
        for (int field : new int[]{1, 4, 5, 6, 7}) {
            var f = hurtFrame(new Fixture(), 0xB9, 0, 0, 0); var current = hurtGlobals(f);
            var stale = hurtPlayerGlobals(current, field, hurtGlobalValue(current, field) ^ 1);
            var r = directFrameHurt(f, stale, hurtPool(), 5);
            hurtAssertRejected(f, hurtActor(f), stale, r);
        }
        for (int field : new int[]{0, 2, 3}) {
            var f = hurtFrame(new Fixture(), 0xB9, 0, 0, 0); var before = f.captureActor(); var shared = f.captureShared();
            var g = hurtGlobals(f); g = hurtPlayerGlobals(g, field, hurtGlobalValue(g, field) ^ 1);
            var r = directFrameHurt(f, g, hurtPool(), 5);
            f.validateObjectHurtResult(hurtActor(f), g, r); f.applyObjectHurtResult(hurtActor(f), g, r);
            hurtAssertAdopted(f, before, shared, r);
        }
    }

    @Test void objectHurtRejectsEveryUnownedResultFieldAndInvalidNativeExitBeforeAggregateWrites() throws IOException {
        for (boolean kill : new boolean[]{false, true}) {
            var f = hurtFrame(new Fixture(), 0xB9, 0, 0, 0); var a = hurtActor(f); var g = hurtGlobals(f);
            if (kill) g = hurtPlayerGlobals(g, 2, 0);
            var r = directFrameHurt(f, g, hurtPool(), kill ? 0 : 5);
            var fields = new ArrayList<>(List.of("CENTRE_X_WORD", "X_FRACTION", "CENTRE_Y_WORD", "Y_FRACTION", "ANGLE",
                    "SPRITE_FLAGS", "X_RADIUS", "Y_RADIUS", "PREVIOUS_ANIMATION", "MOVE_LOCK", "PLAYER_CONTROL",
                    "PREVIOUS_PRIMARY_ANGLE", "PREVIOUS_SECONDARY_ANGLE", "FLAGS_BIT_0", "FLAGS_BIT_3", "FLAGS_BIT_6", "FLAGS_BIT_7"));
            fields.addAll(kill ? List.of("CHARGE", "HURT") : List.of("TILE", "PRIORITY", "STICK", "ROTATION_DISTANCE"));
            for (var field : fields) hurtAssertRejected(f, a, g,
                    hurtReplace(r, hurtActorChanged(r.actor(), field), r.player(), r.exit()));
            for (var exit : CdPlayerTouchResponse.Exit.values()) {
                if (exit == CdPlayerTouchResponse.Exit.HURT || exit == CdPlayerTouchResponse.Exit.KILL_PLAYER
                        || exit == CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE) continue;
                hurtAssertRejected(f, a, g, hurtReplace(r, r.actor(), r.player(), exit));
            }
            for (int field : new int[]{0, 4, 5, 7}) hurtAssertRejected(f, a, g, hurtReplace(r, r.actor(),
                    hurtPlayerGlobals(r.player(), field, hurtGlobalValue(r.player(), field) ^ 1), r.exit()));
            if (kill) for (int field : new int[]{2, 3}) hurtAssertRejected(f, a, g, hurtReplace(r, r.actor(),
                    hurtPlayerGlobals(r.player(), field, hurtGlobalValue(r.player(), field) ^ 1), r.exit()));
            else hurtAssertRejected(f, a, g, hurtReplace(r, r.actor(), hurtPlayerGlobals(r.player(), 1, 0), r.exit()));
        }
    }

    @Test void objectHurtRequiredTerminalStoresRejectForgedValuesButPermitSameValueNativeStores() throws IOException {
        var f = hurtFrame(new Fixture(), 0xBD, 0, 0, 0); var a = hurtActor(f); var g = hurtGlobals(f);
        var r = directFrameHurt(f, g, hurtPool(), 5);
        for (var field : List.of("ROUTINE", "X_VELOCITY", "Y_VELOCITY", "GROUND_VELOCITY", "ANIMATION", "PLAYER_JUMP", "CHARGE", "HURT"))
            hurtAssertRejected(f, a, g, hurtReplace(r, hurtActorChanged(r.actor(), field), r.player(), r.exit()));
        hurtAssertRejected(f, a, g, hurtReplace(r, r.actor(), hurtPlayerGlobals(r.player(), 6, 1), r.exit()));
        f.applyObjectHurtResult(a, g, r);
        var sameActor = hurtActor(f); var sameGlobals = hurtGlobals(f);
        var same = directFrameHurt(f, sameGlobals, hurtPool(), 5); var before = f.captureActor(); var shared = f.captureShared();
        assertEquals(sameActor, same.actor()); assertEquals(sameGlobals.scoreChain(), same.player().scoreChain());
        f.validateObjectHurtResult(sameActor, sameGlobals, same); f.applyObjectHurtResult(sameActor, sameGlobals, same);
        assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void objectKillDebugResultCannotSmuggleAnyActorOrPlayerGlobalChange() throws IOException {
        var f = hurtFrame(new Fixture(), 0xBD, 0, 0, 1); var a = hurtActor(f);
        var g = hurtPlayerGlobals(hurtGlobals(f), 2, 0); var r = directFrameHurt(f, g, hurtPool(), 0);
        for (var field : List.of("Y_VELOCITY", "CENTRE_Y_WORD", "HURT", "ROTATION_DISTANCE", "TILE", "CHARGE"))
            hurtAssertRejected(f, a, g, hurtReplace(r, hurtActorChanged(r.actor(), field), r.player(), r.exit()));
        for (int field = 0; field < 8; field++) hurtAssertRejected(f, a, g, hurtReplace(r, r.actor(),
                hurtPlayerGlobals(g, field, hurtGlobalValue(g, field) ^ 1), r.exit()));
    }

    @Test void objectHurtPortsRejectNullPendingEntitledClosedAndReentrantCutsWithoutConsumingThem() throws IOException {
        var f = new Fixture().runtime(); var a = hurtActor(f); var g = hurtGlobals(f); var r = directFrameHurt(f, g, hurtPool(), 5);
        assertThrows(NullPointerException.class, () -> f.validateObjectPreHurtYRewind(null, 0));
        assertThrows(NullPointerException.class, () -> f.applyObjectPreHurtYRewind(null, 0));
        assertThrows(NullPointerException.class, () -> f.validateObjectHurtResult(null, g, r));
        assertThrows(NullPointerException.class, () -> f.applyObjectHurtResult(null, g, r));
        assertThrows(NullPointerException.class, () -> f.validateObjectHurtResult(a, null, r));
        assertThrows(NullPointerException.class, () -> f.applyObjectHurtResult(a, null, r));
        assertThrows(NullPointerException.class, () -> f.validateObjectHurtResult(a, g, null));
        assertThrows(NullPointerException.class, () -> f.applyObjectHurtResult(a, g, null));
        var selection = f.prepareInput(8); hurtAssertGuarded(f, a, g, r);
        int[] calls = {0}; f.serviceVBlank(selection.phase(), () -> { calls[0]++; hurtAssertGuarded(f, a, g, r); });
        assertEquals(1, calls[0]); assertEquals(8, f.environment().controls().rawHeld());
        hurtAssertGuarded(f, a, g, r);
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary());
        var state = f.state(); var env = f.environment(); var art = f.artState(); var display = f.display(); var clock = f.vIntClock();
        f.close(); hurtAssertGuarded(f, hurtActor(f), hurtGlobals(f), r);
        assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(art, f.artState());
        assertSame(display, f.display()); assertSame(clock, f.vIntClock());
    }

    private static CdPlayerTouchResponse.Actor hurtActor(CdPlayerFrameRuntime f) {
        var s = f.state(); return new CdPlayerTouchResponse.Actor(s.motion(), s.header().tile(), s.header().priority(),
                s.timers().hurt(), s.rotationDistance());
    }
    private static CdPlayerTouchResponse.PlayerGlobals hurtGlobals(CdPlayerFrameRuntime f) {
        var e = f.environment(); var b = e.globals();
        return new CdPlayerTouchResponse.PlayerGlobals(0xA5, b.invincible(), 3, 0xC7, b.debugCheat(), b.debugMode(),
                e.movement().globals().scoreChain(), e.movement().gates().miniSonic());
    }
    private static int hurtGlobalValue(CdPlayerTouchResponse.PlayerGlobals g, int field) {
        return new int[]{g.timeWarp(), g.invincible(), g.shield(), g.combineRing(), g.debugCheat(), g.debugMode(), g.scoreChain(), g.miniSonic()}[field];
    }
    private static CdPlayerTouchResponse.PlayerGlobals hurtPlayerGlobals(CdPlayerTouchResponse.PlayerGlobals g, int field, int value) {
        int[] v = {g.timeWarp(), g.invincible(), g.shield(), g.combineRing(), g.debugCheat(), g.debugMode(), g.scoreChain(), g.miniSonic()};
        v[field] = value; return new CdPlayerTouchResponse.PlayerGlobals(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
    }
    private static CdPlayerMotionState hurtMotion(CdPlayerMotionState m, int y, int yv, int flags) {
        return new CdPlayerMotionState(m.centreX16_16(), y, m.xVelocity(), yv, m.groundVelocity(), m.routine(), m.angle(), flags,
                m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(), m.playerJump(), m.charge(),
                m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
    }
    private static CdPlayerTouchResponse.Actor hurtActorChanged(CdPlayerTouchResponse.Actor a, String field) {
        return switch (field) {
            case "TILE" -> new CdPlayerTouchResponse.Actor(a.motion(), a.tile() ^ 1, a.priority(), a.hurt(), a.rotationDistance());
            case "PRIORITY" -> new CdPlayerTouchResponse.Actor(a.motion(), a.tile(), a.priority() ^ 1, a.hurt(), a.rotationDistance());
            case "HURT" -> new CdPlayerTouchResponse.Actor(a.motion(), a.tile(), a.priority(), a.hurt() ^ 1, a.rotationDistance());
            case "ROTATION_DISTANCE" -> new CdPlayerTouchResponse.Actor(a.motion(), a.tile(), a.priority(), a.hurt(), a.rotationDistance() ^ 1);
            default -> new CdPlayerTouchResponse.Actor(spinTunnelChanged(a.motion(), field), a.tile(), a.priority(), a.hurt(), a.rotationDistance());
        };
    }
    private static CdPlayerFrameRuntime hurtFrame(Fixture fixture, int flags, int mini, int cheat, int debug) throws IOException {
        var f = spinTunnelDirtyFrame(fixture); var a = f.captureActor(); var s = a.state();
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(a.owner(), new CdPlayerBody.State(hurtMotion(s.motion(),
                s.motion().centreY16_16(), s.motion().yVelocity(), flags), s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle()), a.display()));
        var shared = f.captureShared(); var e = shared.environment(); var m = e.movement(); var b = e.globals(); var gates = m.gates();
        var next = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), mini, gates.waterSlide(), gates.windTunnel(),
                        gates.speedShoes(), gates.timeWarpDirection()), m.globals()), e.controls(),
                new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), debug, cheat, b.timeWarpTimer(),
                        b.invincible(), b.bossMusic()), e.dynamicSlots());
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), next, shared.artState(), shared.paused(), shared.vIntClock()));
        return f;
    }
    private static CdObjectPool hurtPool() {
        var pool = new CdObjectPool(); pool.write(pool.state(61).moveByte(CdObjectState.ByteField.ID, 0x26)
                .moveWord(CdObjectState.WordField.X, 0xFFFF).moveWord(CdObjectState.WordField.Y, 0x040E));
        pool.write(pool.state(32).moveWord(CdObjectState.WordField.X_FRACTION, 0x1357)
                .moveWord(CdObjectState.WordField.Y_FRACTION, 0x2468).moveByte(CdObjectState.ByteField.ROUTINE, 0xFE));
        return pool;
    }
    private static CdPlayerTouchResponse.Result directFrameHurt(CdPlayerFrameRuntime f, CdPlayerTouchResponse.PlayerGlobals g,
            CdObjectPool pool, int rings) {
        var cut = pool.capture(); return CdPlayerTouchResponse.hurtPlayer(hurtActor(f), g,
                new CdPlayerTouchResponse.WorldGlobals(rings, 0xA5, 0xC7, 0x89ABCDEF, 0xFEDCBA98, 0xE9), cut.slots().get(29), cut);
    }
    private static CdPlayerTouchResponse.Result hurtReplace(CdPlayerTouchResponse.Result r, CdPlayerTouchResponse.Actor a,
            CdPlayerTouchResponse.PlayerGlobals g, CdPlayerTouchResponse.Exit exit) {
        return new CdPlayerTouchResponse.Result(a, g, r.world(), r.changedSlots(), r.calls(),
                exit.explicitMinusOne() ? OptionalInt.of(-1) : OptionalInt.empty(), exit);
    }
    private static void hurtAssertRejected(CdPlayerFrameRuntime f, CdPlayerTouchResponse.Actor a,
            CdPlayerTouchResponse.PlayerGlobals g, CdPlayerTouchResponse.Result r) {
        var actor = f.captureActor(); var shared = f.captureShared(); var observation = observe(f);
        assertThrows(IllegalArgumentException.class, () -> f.validateObjectHurtResult(a, g, r));
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectHurtResult(a, g, r));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared()); assertEquals(observation, observe(f));
        assertSame(actor.state(), f.state()); assertSame(shared.environment(), f.environment());
    }
    private static void hurtAssertGuarded(CdPlayerFrameRuntime f, CdPlayerTouchResponse.Actor a,
            CdPlayerTouchResponse.PlayerGlobals g, CdPlayerTouchResponse.Result r) {
        var state = f.state(); var env = f.environment(); var art = f.artState(); var display = f.display(); var clock = f.vIntClock();
        assertThrows(IllegalStateException.class, () -> f.validateObjectPreHurtYRewind(a, 0));
        assertThrows(IllegalStateException.class, () -> f.applyObjectPreHurtYRewind(a, 0));
        assertThrows(IllegalStateException.class, () -> f.validateObjectHurtResult(a, g, r));
        assertThrows(IllegalStateException.class, () -> f.applyObjectHurtResult(a, g, r));
        assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(art, f.artState());
        assertSame(display, f.display()); assertSame(clock, f.vIntClock());
    }
    private static void hurtAssertAdopted(CdPlayerFrameRuntime f, CdPlayerFrameRuntime.ActorState before,
            CdPlayerFrameRuntime.SharedState shared, CdPlayerTouchResponse.Result r) {
        var s = before.state(); var h = s.header(); var t = s.timers(); var a = r.actor();
        var expected = new CdPlayerBody.State(a.motion(), s.animation(), new CdPlayerBody.Header(h.slotId(), h.mappingAddress(),
                a.tile(), a.priority(), h.width()), new CdPlayerBody.Timers(a.hurt(), t.invincibility(), t.speedShoes(), t.reset()),
                s.history(), a.rotationDistance(), s.objectLinks(), s.rotationAngle());
        assertEquals(new CdPlayerFrameRuntime.ActorState(before.owner(), expected, before.display()), f.captureActor());
        var e = shared.environment(); var m = e.movement(); var g = m.globals(); var b = e.globals();
        var movementGlobals = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(),
                g.boredTimer(), r.player().scoreChain(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance());
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(), movementGlobals),
                e.controls(), new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(), b.debugCheat(),
                        b.timeWarpTimer(), r.player().invincible(), b.bossMusic()), e.dynamicSlots());
        assertEquals(new CdPlayerFrameRuntime.SharedState(shared.owner(), environment, shared.artState(), shared.paused(), shared.vIntClock()), f.captureShared());
        assertSame(s.animation(), f.state().animation()); assertSame(s.history(), f.state().history());
        assertSame(s.objectLinks(), f.state().objectLinks()); assertSame(before.display(), f.display());
        assertSame(m.physics(), f.environment().movement().physics()); assertSame(m.bounds(), f.environment().movement().bounds());
        assertSame(e.controls(), f.environment().controls()); assertSame(e.dynamicSlots(), f.environment().dynamicSlots());
        assertSame(shared.artState(), f.artState()); assertSame(shared.vIntClock(), f.vIntClock());
    }
    // END direct object Hurt Frame ports.
    // BEGIN Time Post direction byte value port; Root owns the source object/phase.
    @ParameterizedTest @ValueSource(ints = {0, 1, 128, 255})
    void timePostDirectionChangesOnlyItsRawGateByteAndPreservesTheCompleteCut(int next) throws IOException {
        var fixture = new Fixture();
        try (var f = speedShoesDirtyFrame(fixture)) {
            var actor = f.captureActor(); var shared = f.captureShared(); var before = observe(f);
            int uploads = fixture.dmas.size();
            f.bindArtPublication((dma, value) -> fail("A direction-byte store cannot publish art"));
            f.validateObjectTimePostDirection(255, next);
            assertEquals(before, observe(f)); assertSame(shared.environment(), f.environment());
            f.applyObjectTimePostDirection(255, next);
            var e = shared.environment(); var m = e.movement(); var g = m.gates();
            var expected = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                    new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), g.miniSonic(), g.waterSlide(),
                            g.windTunnel(), g.speedShoes(), next), m.globals()),
                    e.controls(), e.globals(), e.dynamicSlots());
            assertEquals(expected, f.environment()); assertEquals(actor, f.captureActor());
            assertSharedValues(new CdPlayerFrameRuntime.SharedState(shared.owner(), expected, shared.artState(),
                    shared.paused(), shared.vIntClock()), f.captureShared());
            assertEquals(next == 255 ? Set.of() : Set.of("gates"), differences(before, observe(f)));
            assertEquals(uploads, fixture.dmas.size());
        }
    }

    @Test void timePostFutureRequiresTheAlreadyCommittedFfBeforeItsSecondByteStore() throws IOException {
        try (var f = new Fixture().runtime()) {
            var actor = f.captureActor();
            f.applyObjectTimePostDirection(0, 255);
            var afterFirst = observe(f); var environment = f.environment();
            assertThrows(IllegalArgumentException.class, () -> f.validateObjectTimePostDirection(0, 1));
            assertThrows(IllegalArgumentException.class, () -> f.applyObjectTimePostDirection(0, 1));
            assertEquals(afterFirst, observe(f)); assertSame(environment, f.environment());
            f.applyObjectTimePostDirection(255, 1);
            assertEquals(1, f.environment().movement().gates().timeWarpDirection());
            assertEquals(actor, f.captureActor());
            f.applyObjectTimePostDirection(1, 1); // Same-value native stores do not consume a token.
            assertEquals(1, f.environment().movement().gates().timeWarpDirection());
        }
    }

    @ParameterizedTest @ValueSource(ints = {-1, 256, 65535})
    void timePostDirectionRejectsEitherNonByteBeforeChangingAnyValue(int invalid) throws IOException {
        try (var f = speedShoesDirtyFrame(new Fixture())) {
            var before = observe(f); var environment = f.environment(); var state = f.state();
            assertThrows(IllegalArgumentException.class, () -> f.validateObjectTimePostDirection(invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> f.applyObjectTimePostDirection(invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> f.validateObjectTimePostDirection(255, invalid));
            assertThrows(IllegalArgumentException.class, () -> f.applyObjectTimePostDirection(255, invalid));
            assertEquals(before, observe(f)); assertSame(environment, f.environment()); assertSame(state, f.state());
        }
    }

    @Test void timePostDirectionRequiresTheExistingClosedFrameCutWithoutConsumingItsInputOrTurn() throws IOException {
        try (var f = new Fixture().runtime()) {
            var selection = f.prepareInput(8);
            assertThrows(IllegalStateException.class, () -> f.validateObjectTimePostDirection(0, 255));
            assertThrows(IllegalStateException.class, () -> f.applyObjectTimePostDirection(0, 255));
            f.serviceVBlank(selection.phase());
            assertThrows(IllegalStateException.class, () -> f.validateObjectTimePostDirection(0, 255));
            assertThrows(IllegalStateException.class, () -> f.applyObjectTimePostDirection(0, 255));
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary());
            assertEquals(0, f.environment().movement().gates().timeWarpDirection());
            f.close();
            assertThrows(IllegalStateException.class, () -> f.validateObjectTimePostDirection(0, 255));
            assertThrows(IllegalStateException.class, () -> f.applyObjectTimePostDirection(0, 255));
        }
    }

    @Test void timePostDirectionRejectsReentryWhileTheRealServiceStillCompletesOnce() throws IOException {
        try (var f = new Fixture().runtime()) {
            int[] calls = {0}; var selection = f.prepareInput(8);
            f.serviceVBlank(selection.phase(), () -> {
                calls[0]++;
                assertThrows(IllegalStateException.class, () -> f.validateObjectTimePostDirection(0, 1));
                assertThrows(IllegalStateException.class, () -> f.applyObjectTimePostDirection(0, 1));
            });
            assertEquals(1, calls[0]); assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
            assertEquals(8, f.environment().controls().rawHeld());
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary());
            assertEquals(0, f.environment().movement().gates().timeWarpDirection());
        }
    }

    @Test void timePostDirectionNonzeroSharedCaptureRestoresAndTheUnownedWarpStillStopsTheNextMain() throws IOException {
        try (var f = new Fixture().runtime()) {
            service(f, 0); f.runBody(1);
            f.applyObjectTimePostDirection(0, 1);
            var actor = f.captureActor(); var active = f.captureShared(); var first = observe(f);
            f.applyObjectTimePostDirection(1, 255);
            var second = observe(f);
            f.restoreShared(active); assertSharedValues(active, f.captureShared()); assertEquals(first, observe(f));
            f.applyObjectTimePostDirection(1, 255); assertEquals(second, observe(f)); assertEquals(actor, f.captureActor());
            service(f, 0);
            var stopped = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.runBody(2));
            assertEquals(CdPlayerMovement.UnsupportedBoundary.TIME_WARP_ACTIVE, stopped.boundary());
            // Standalone Frame consumes a failed Body turn; the World adapter owns fail-closed admission.
            assertFalse(f.closed()); assertEquals(255, f.environment().movement().gates().timeWarpDirection());
            assertEquals(actor, f.captureActor());
            assertThrows(IllegalStateException.class, () -> f.runBody(2));
        }
    }
    // END Time Post direction byte value port additions.

    // BEGIN shared native GiveSpeedShoes value port: values never certify source phase or identity.
    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void speedShoesRewardChangesExactlyFiveStoresAndPreservesEveryOtherFrameValue(CdReleaseVariant sourceVariant)
            throws IOException {
        var fixture = new Fixture();
        try (var f = speedShoesDirtyFrame(fixture)) {
            var actor = f.captureActor(); var shared = f.captureShared(); var observation = observe(f);
            var expected = speedShoesProjection(f);
            var result = CdPlayerSpeedShoesReward.give(expected, sourceVariant, 0);
            int uploads = fixture.dmas.size();
            f.bindArtPublication((dma, state) -> fail("A shoes value commit must not publish art"));
            f.validateObjectSpeedShoesReward(expected, result);
            assertEquals(observation, observe(f)); assertEquals(actor, f.captureActor());
            assertSharedValues(shared, f.captureShared());
            f.applyObjectSpeedShoesReward(expected, result);
            var s = actor.state(); var t = s.timers(); var value = result.state();
            assertEquals(new CdPlayerBody.State(s.motion(), s.animation(), s.header(),
                    new CdPlayerBody.Timers(t.hurt(), t.invincibility(), value.speedShoesTimer(), t.reset()),
                    s.history(), s.rotationDistance(), s.objectLinks(), s.rotationAngle()), f.state());
            var e = shared.environment(); var m = e.movement(); var g = m.gates();
            var next = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(value.physics(), m.bounds(),
                    new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), g.miniSonic(), g.waterSlide(),
                            g.windTunnel(), value.speedShoes(), g.timeWarpDirection()), m.globals()),
                    e.controls(), e.globals(), e.dynamicSlots());
            assertEquals(next, f.environment());
            assertSharedValues(new CdPlayerFrameRuntime.SharedState(shared.owner(), next, shared.artState(),
                    shared.paused(), shared.vIntClock()), f.captureShared());
            assertEquals(Set.of("timers", "gates", "physics"), differences(observation, observe(f)));
            assertEquals(0x1234, f.state().motion().centreX16_16() & 0xFFFF);
            assertEquals(0xBEEF, f.state().motion().centreY16_16() & 0xFFFF);
            assertEquals(new CdPlayerBody.ObjectLinks(0x7B, 0x9D), f.state().objectLinks());
            assertEquals(actor.display(), f.display()); assertEquals(uploads, fixture.dmas.size());
            assertEquals(List.of(CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_FADEPCM,
                    CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS), result.commands(),
                    "Only root maps these ordered requests; Frame has no playback or ledger side effect");
            f.restoreActor(actor); f.restoreShared(shared);
            assertEquals(observation, observe(f));
            f.applyObjectSpeedShoesReward(expected, result);
            assertEquals(next, f.environment()); assertEquals(value, speedShoesProjection(f));
            assertEquals(uploads, fixture.dmas.size());
        }
    }

    @Test void speedShoesSameValueStoresAreLegalAndCarryNoConsumptionOrOwnerToken() throws IOException {
        var fixture = new Fixture();
        try (var f = speedShoesDirtyFrame(fixture)) {
            var state = new CdPlayerSpeedShoesReward.State(1, 1320, new CdPlayerMovement.Physics(0xC00, 0x18, 0x80));
            speedShoesSeed(f, state);
            var actor = f.captureActor(); var shared = f.captureShared(); var observation = observe(f);
            var result = CdPlayerSpeedShoesReward.give(state, CdReleaseVariant.USA, 1);
            int uploads = fixture.dmas.size();
            for (int i = 0; i < 2; i++) {
                f.validateObjectSpeedShoesReward(state, result); f.applyObjectSpeedShoesReward(state, result);
                assertEquals(observation, observe(f)); assertEquals(actor, f.captureActor());
                assertSharedValues(shared, f.captureShared());
            }
            assertEquals(uploads, fixture.dmas.size());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"flag", "timer", "topSpeed", "acceleration", "deceleration"})
    void speedShoesRejectsEveryIndependentlyStaleNativeValueBeforeAnyEffect(String field) throws IOException {
        try (var f = speedShoesDirtyFrame(new Fixture())) {
            var expected = speedShoesProjection(f);
            var result = CdPlayerSpeedShoesReward.give(expected, CdReleaseVariant.USA, 1);
            var p = expected.physics();
            var changed = new CdPlayerSpeedShoesReward.State(expected.speedShoes() ^ (field.equals("flag") ? 1 : 0),
                    expected.speedShoesTimer() ^ (field.equals("timer") ? 1 : 0),
                    new CdPlayerMovement.Physics(p.topSpeed() ^ (field.equals("topSpeed") ? 1 : 0),
                            p.acceleration() ^ (field.equals("acceleration") ? 1 : 0),
                            p.deceleration() ^ (field.equals("deceleration") ? 1 : 0)));
            speedShoesSeed(f, changed);
            speedShoesAssertRejected(f, expected, result, IllegalArgumentException.class);
            assertEquals(changed, speedShoesProjection(f));
        }
    }

    @Test void speedShoesNullPreflightsAndAppliesLeaveAllValuesUntouched() throws IOException {
        try (var f = speedShoesDirtyFrame(new Fixture())) {
            var expected = speedShoesProjection(f);
            var result = CdPlayerSpeedShoesReward.give(expected, CdReleaseVariant.USA, 1);
            speedShoesAssertRejected(f, null, result, NullPointerException.class);
            speedShoesAssertRejected(f, expected, null, NullPointerException.class);
        }
    }

    @Test void speedShoesRequiresTheExistingOpenIdleClosedFrameCutAndNeverConsumesItsInputOrEntitlement()
            throws IOException {
        var fixture = new Fixture();
        try (var f = fixture.runtime()) {
            var expected = speedShoesProjection(f);
            var result = CdPlayerSpeedShoesReward.give(expected, CdReleaseVariant.USA, 1);
            var selection = f.prepareInput(8);
            speedShoesAssertRejected(f, expected, result, IllegalStateException.class);
            f.serviceVBlank(selection.phase());
            speedShoesAssertRejected(f, expected, result, IllegalStateException.class);
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary());
            expected = speedShoesProjection(f);
            result = CdPlayerSpeedShoesReward.give(expected, CdReleaseVariant.USA, 1);
            f.close();
            speedShoesAssertRejected(f, expected, result, IllegalStateException.class);
        }
    }

    @Test void speedShoesReentryDuringServiceRejectsWithoutChangingTheServiceOrBodyTurn() throws IOException {
        try (var f = new Fixture().runtime()) {
            var expected = speedShoesProjection(f);
            var result = CdPlayerSpeedShoesReward.give(expected, CdReleaseVariant.USA, 0);
            int[] callbacks = {0}; var selection = f.prepareInput(8);
            f.serviceVBlank(selection.phase(), () -> {
                callbacks[0]++;
                speedShoesAssertRejected(f, expected, result, IllegalStateException.class);
            });
            assertEquals(1, callbacks[0]); assertEquals(8, f.environment().controls().rawHeld());
            assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary());
        }
    }

    @Test void speedShoesWrongFrameValuesRejectButEqualValuesDoNotPretendToProveIdentity() throws IOException {
        try (var a = speedShoesDirtyFrame(new Fixture()); var b = speedShoesDirtyFrame(new Fixture())) {
            var expected = speedShoesProjection(a);
            var result = CdPlayerSpeedShoesReward.give(expected, CdReleaseVariant.USA, 1);
            speedShoesSeed(b, new CdPlayerSpeedShoesReward.State(0, 17, new CdPlayerMovement.Physics(1, 2, 3)));
            speedShoesAssertRejected(b, expected, result, IllegalArgumentException.class);
            speedShoesSeed(b, expected);
            b.validateObjectSpeedShoesReward(expected, result); b.applyObjectSpeedShoesReward(expected, result);
            assertEquals(result.state(), speedShoesProjection(b)); assertEquals(expected, speedShoesProjection(a));
            var actor = a.captureActor(); var shared = a.captureShared(); var observation = observe(b);
            assertThrows(IllegalArgumentException.class, () -> b.restoreActor(actor));
            assertThrows(IllegalArgumentException.class, () -> b.restoreShared(shared));
            assertEquals(observation, observe(b), "Existing capture identity remains separate from plain reward values");
        }
    }

    @Test void speedShoesAwardIsCapturedNonzeroThenTheNextActualDisplayDecrementsAndReplaysWithDmaContent()
            throws IOException {
        var fixture = new Fixture();
        try (var f = fixture.runtime()) {
            service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
            var expected = speedShoesProjection(f);
            var result = CdPlayerSpeedShoesReward.give(expected, CdReleaseVariant.USA, 1);
            int awardUploads = fixture.dmas.size();
            f.applyObjectSpeedShoesReward(expected, result);
            assertEquals(1320, f.state().timers().speedShoes(), "Award follows the already-completed Display");
            assertEquals(awardUploads, fixture.dmas.size());
            var actor = f.captureActor(); var shared = f.captureShared(); var awarded = observe(f);
            speedShoesSeed(f, new CdPlayerSpeedShoesReward.State(0x80, 7, new CdPlayerMovement.Physics(-3, 4, -5)));
            f.restoreActor(actor); f.restoreShared(shared);
            assertEquals(awarded, observe(f)); assertEquals(result.state(), speedShoesProjection(f));
            int start = fixture.dmas.size(); service(f, 0); var publication = f.runBody(3);
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, publication.result().boundary());
            assertTrue(calls(publication).contains(CdPlayerBody.NativeCall.DISPLAY));
            assertEquals(1319, f.state().timers().speedShoes(), "Sonic/Main.asm:413-419, not an award-time decrement");
            assertEquals(1, f.environment().movement().gates().speedShoes());
            assertEquals(new CdPlayerMovement.Physics(0xC00, 0x18, 0x80), f.environment().movement().physics());
            assertEquals(List.of(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_CHARGESTOP)),
                    publication.sounds(), "Standing MODE requests the charge stop before Display (Sonic/Main.asm:1033-1036)");
            var after = f.captureActor(); var afterShared = f.captureShared(); var afterValues = observe(f);
            var requests = List.copyOf(fixture.dmas.subList(start, fixture.dmas.size()));
            assertEquals(1, requests.size(), "The previous Main's changed frame actually services a DMA");
            f.restoreActor(actor); f.restoreShared(shared);
            int replayStart = fixture.dmas.size(); service(f, 0); var replay = f.runBody(3);
            assertEquals(publication, replay); assertEquals(after, f.captureActor());
            assertSharedValues(afterShared, f.captureShared()); assertEquals(afterValues, observe(f));
            var replayRequests = fixture.dmas.subList(replayStart, fixture.dmas.size());
            assertEquals(requests.size(), replayRequests.size());
            for (int i = 0; i < requests.size(); i++) {
                assertEquals(requests.get(i).vramByteAddress(), replayRequests.get(i).vramByteAddress());
                assertArrayEquals(requests.get(i).bytes().copy(), replayRequests.get(i).bytes().copy());
            }
        }
    }

    @ParameterizedTest @CsvSource({"0,0,0,1", "1,1,0,1", "2,1,0,0", "0,0,1,0"})
    void speedShoesExistingDisplayExpiryRestoresNormalPhysicsAndRequestsMusicAtMostOnce(int invincibility,
            int invincible, int bossMusic, int musicCount) throws IOException {
        // Sonic/Main.asm:391-438: invincibility expires first, shoes follows, both music gates use live flags.
        try (var f = new Fixture().runtime()) {
            service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
            var expected = speedShoesProjection(f);
            f.applyObjectSpeedShoesReward(expected, CdPlayerSpeedShoesReward.give(expected, CdReleaseVariant.USA, 1));
            speedShoesSeed(f, new CdPlayerSpeedShoesReward.State(1, 1, new CdPlayerMovement.Physics(0xC00, 0x18, 0x80)));
            var actor = f.captureActor(); var s = actor.state(); var t = s.timers();
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(s.motion(),
                    s.animation(), s.header(), new CdPlayerBody.Timers(t.hurt(), invincibility, t.speedShoes(), t.reset()),
                    s.history(), s.rotationDistance(), s.objectLinks(), s.rotationAngle()), actor.display()));
            var shared = f.captureShared(); var e = shared.environment(); var g = e.globals();
            f.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), new CdPlayerBody.Environment(e.movement(),
                    e.controls(), new CdPlayerBody.Globals(g.levelFrames(), g.cameraXCentre(), g.timeAttackMode(),
                            g.debugMode(), g.debugCheat(), g.timeWarpTimer(), invincible, bossMusic), e.dynamicSlots()),
                    shared.artState(), shared.paused(), shared.vIntClock()));
            service(f, 0); var expiry = f.runBody(3);
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, expiry.result().boundary());
            assertEquals(new CdPlayerSpeedShoesReward.State(0, 0, new CdPlayerMovement.Physics(0x600, 0xC, 0x80)),
                    speedShoesProjection(f));
            var chargeStop = new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_CHARGESTOP);
            assertEquals(musicCount == 0 ? List.of(chargeStop)
                            : List.of(chargeStop, new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_R1AMUS)),
                    expiry.sounds(), "The MODE stop precedes Display's optional expiry music");
            if (invincibility == 1) {
                var effects = expiry.result().effects();
                assertTrue(effects.indexOf(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.INVINCIBILITY_TIMER, 0))
                        < effects.indexOf(new CdPlayerBody.ActorWrite(CdPlayerBody.ActorField.SHOES_TIMER, 0)));
                assertEquals(0, f.environment().globals().invincible());
            }
            // A still-live invincibility path is outside the repeat-shoes observation: it expires on this next call.
            if (invincibility != 2) {
                service(f, 0); var next = f.runBody(4);
                assertEquals(List.of(chargeStop), next.sounds()); assertEquals(0, f.state().timers().speedShoes());
                assertEquals(new CdPlayerMovement.Physics(0x600, 0xC, 0x80), f.environment().movement().physics());
                assertFalse(next.result().effects().stream().anyMatch(effect -> effect instanceof CdPlayerBody.ActorWrite write
                        && write.field() == CdPlayerBody.ActorField.SHOES_TIMER));
            }
        }
    }

    private static CdPlayerSpeedShoesReward.State speedShoesProjection(CdPlayerFrameRuntime f) {
        return new CdPlayerSpeedShoesReward.State(f.environment().movement().gates().speedShoes(),
                f.state().timers().speedShoes(), f.environment().movement().physics());
    }

    /** Same-owner constructed capture only; never an additional production setter or gameplay ingress. */
    private static void speedShoesSeed(CdPlayerFrameRuntime f, CdPlayerSpeedShoesReward.State value) {
        var actor = f.captureActor(); var s = actor.state(); var t = s.timers();
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(s.motion(), s.animation(),
                s.header(), new CdPlayerBody.Timers(t.hurt(), t.invincibility(), value.speedShoesTimer(), t.reset()),
                s.history(), s.rotationDistance(), s.objectLinks(), s.rotationAngle()), actor.display()));
        var shared = f.captureShared(); var e = shared.environment(); var m = e.movement(); var g = m.gates();
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), new CdPlayerBody.Environment(
                new CdPlayerMovement.Environment(value.physics(), m.bounds(), new CdPlayerMovement.Gates(g.zoneAct(),
                        g.timeZone(), g.miniSonic(), g.waterSlide(), g.windTunnel(), value.speedShoes(), g.timeWarpDirection()),
                        m.globals()), e.controls(), e.globals(), e.dynamicSlots()), shared.artState(), shared.paused(), shared.vIntClock()));
    }

    private static CdPlayerFrameRuntime speedShoesDirtyFrame(Fixture fixture) throws IOException {
        var f = spinTunnelDirtyFrame(fixture);
        speedShoesSeed(f, new CdPlayerSpeedShoesReward.State(0xA5, 0xFEDC, new CdPlayerMovement.Physics(-1, 2, -3)));
        var shared = f.captureShared(); var e = shared.environment(); var m = e.movement();
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),
                new CdPlayerMovement.Bounds(1, 0x4567, 0x789A, 0xFE),
                new CdPlayerMovement.Gates(0xBEEF, 0x81, 0x91, 0x82, 0x73, 0xA5, 0xFF), m.globals()),
                e.controls(), e.globals(), e.dynamicSlots());
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), environment, shared.artState(), true, shared.vIntClock()));
        return f;
    }

    private static void speedShoesAssertRejected(CdPlayerFrameRuntime f, CdPlayerSpeedShoesReward.State expected,
            CdPlayerSpeedShoesReward.Result result, Class<? extends Throwable> type) {
        var before = observe(f); var state = f.state(); var environment = f.environment();
        assertThrows(type, () -> f.validateObjectSpeedShoesReward(expected, result));
        assertEquals(before, observe(f)); assertSame(state, f.state()); assertSame(environment, f.environment());
        assertThrows(type, () -> f.applyObjectSpeedShoesReward(expected, result));
        assertEquals(before, observe(f)); assertSame(state, f.state()); assertSame(environment, f.environment());
    }
    // END shared native GiveSpeedShoes value port additions.

    // BEGIN Spin tunnel tag Frame port: constructed cuts, never execution/physical-visit authority.
    @Test void spinTunnelActualFirstRollPreservesDirtyActorDisplayAndSharedStateAndReplays() throws IOException {
        var fixture = new Fixture(); var f = spinTunnelDirtyFrame(fixture);
        var before = f.captureActor(); var shared = f.captureShared(); var s = before.state();
        assertTrue(before.display().isPresent()); assertTrue(before.display().orElseThrow().queued());
        assertEquals(new CdPlayerBody.AnimationCursor(5, 7, 0x23), s.animation());
        assertNotEquals(0, s.history().bytes().unsignedByte(0)); assertEquals(0xBC, s.history().index());
        assertEquals(new CdPlayerBody.Timers(0x1357, 0x2468, 0x369A, 0x48BC), s.timers());
        assertEquals(new CdPlayerBody.ObjectLinks(0x7B, 0x9D), s.objectLinks());
        var pool = new CdObjectPool(); var object = spinTunnelObject(pool, s.motion(), 0);
        // Explicit constructed mapping pointer, not an authenticated Powerup operand/media claim.
        var tag = new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(0x200000));
        var result = tag.step(object, s.motion(), new CdSpinTunnelTag.Gates(0, 0, 0, 0)).player();
        var expected = new CdPlayerMotionState(0xFFFC1234, 0x0002BEEF, 0xA00, -0x234, -0xA00,
                4, 0x60, 0xED, 0xD5, 7, 14, 2, 0x35, 0x76, 0x11, -0x1234, 0x85, 0xA5, 0x44, 0x88);
        assertEquals(expected, result.finalMotion(), "Literal subtype0/angle60 and wrapped integer-Y+5");
        assertEquals(Set.of(CdSpinTunnelTag.PlayerField.CENTRE_Y_WORD, CdSpinTunnelTag.PlayerField.X_VELOCITY,
                CdSpinTunnelTag.PlayerField.GROUND_VELOCITY, CdSpinTunnelTag.PlayerField.FLAGS,
                CdSpinTunnelTag.PlayerField.Y_RADIUS, CdSpinTunnelTag.PlayerField.X_RADIUS,
                CdSpinTunnelTag.PlayerField.ANIMATION), result.executedWrites());
        int uploads = fixture.dmas.size();
        f.bindArtPublication((request, state) -> fail("The tag port must never service or publish art"));
        f.validateObjectSpinTunnelResult(result);
        assertSame(s, f.state()); assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        f.applyObjectSpinTunnelResult(result);
        assertEquals(new CdPlayerBody.State(expected, s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle()), f.state());
        assertSame(result.finalMotion(), f.state().motion());
        assertSame(s.animation(), f.state().animation()); assertSame(s.header(), f.state().header());
        assertSame(s.timers(), f.state().timers()); assertSame(s.history(), f.state().history());
        assertSame(s.objectLinks(), f.state().objectLinks()); assertEquals(0xAB, f.state().rotationDistance());
        assertSame(before.display(), f.display()); assertSame(shared.environment(), f.environment());
        assertSame(shared.artState(), f.artState()); assertSame(shared.vIntClock(), f.vIntClock());
        assertEquals(shared, f.captureShared()); assertEquals(uploads, fixture.dmas.size()); assertFalse(f.closed());
        assertEquals(object, pool.state(32), "Frame cannot publish a staged object image");
        var after = f.captureActor();
        spinTunnelAssertRejected(f, result); // The first apply consumed these old values, without a second +5.
        assertEquals(after, f.captureActor());
        f.restoreActor(before); f.restoreShared(shared); f.applyObjectSpinTunnelResult(result);
        assertEquals(after, f.captureActor()); assertEquals(shared, f.captureShared());
        assertEquals(uploads, fixture.dmas.size());
    }

    @ParameterizedTest @EnumSource(CdSpinTunnelTag.PlayerField.class)
    void spinTunnelAcceptsOnlyOneDeclaredFieldAndItsSameValueStore(CdSpinTunnelTag.PlayerField field) throws IOException {
        var fixture = new Fixture(); var f = spinTunnelDirtyFrame(fixture);
        var before = f.captureActor(); var shared = f.captureShared(); var m = before.state().motion();
        var after = spinTunnelChanged(m, field.name());
        // Honest constructed port result: this is an authority/preservation check, not a native step claim.
        var result = new CdSpinTunnelTag.PlayerResult(m, after, Set.of(field));
        f.validateObjectSpinTunnelResult(result); assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        f.applyObjectSpinTunnelResult(result);
        var s = before.state();
        assertEquals(new CdPlayerBody.State(after, s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle()), f.state());
        assertEquals(before.display(), f.display()); assertEquals(shared, f.captureShared());
        var applied = f.captureActor(); var same = new CdSpinTunnelTag.PlayerResult(after, after, Set.of(field));
        f.validateObjectSpinTunnelResult(same); f.applyObjectSpinTunnelResult(same);
        assertEquals(applied, f.captureActor()); assertEquals(shared, f.captureShared());
        assertTrue(fixture.dmas.isEmpty());
    }

    @ParameterizedTest @EnumSource(CdSpinTunnelTag.PlayerField.class)
    void spinTunnelRejectsEveryUndeclaredChangedFieldBeforeMutation(CdSpinTunnelTag.PlayerField field) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var m = f.state().motion();
        spinTunnelAssertRejected(f, new CdSpinTunnelTag.PlayerResult(m, spinTunnelChanged(m, field.name()), Set.of()));
    }

    @ParameterizedTest @ValueSource(strings = {
            "CENTRE_X_WORD", "X_FRACTION", "CENTRE_Y_WORD", "Y_FRACTION", "X_VELOCITY", "Y_VELOCITY",
            "GROUND_VELOCITY", "ROUTINE", "ANGLE", "FLAGS", "SPRITE_FLAGS", "X_RADIUS", "Y_RADIUS", "ANIMATION",
            "PREVIOUS_ANIMATION", "PLAYER_JUMP", "CHARGE", "MOVE_LOCK", "STICK", "PLAYER_CONTROL",
            "PREVIOUS_PRIMARY_ANGLE", "PREVIOUS_SECONDARY_ANGLE"})
    void spinTunnelRejectsStaleEntireExpectedMotionIncludingUnownedFields(String field) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var stale = spinTunnelChanged(f.state().motion(), field);
        spinTunnelAssertRejected(f, new CdSpinTunnelTag.PlayerResult(stale, stale, Set.of()));
    }

    @ParameterizedTest @ValueSource(strings = {
            "CENTRE_X_WORD", "X_FRACTION", "Y_FRACTION", "ROUTINE", "ANGLE", "SPRITE_FLAGS", "PREVIOUS_ANIMATION",
            "PLAYER_JUMP", "CHARGE", "MOVE_LOCK", "STICK", "PLAYER_CONTROL", "PREVIOUS_PRIMARY_ANGLE",
            "PREVIOUS_SECONDARY_ANGLE", "FLAGS_BIT_0", "FLAGS_BIT_3", "FLAGS_BIT_4", "FLAGS_BIT_5", "FLAGS_BIT_6", "FLAGS_BIT_7"})
    void spinTunnelAllDeclarationsCannotAuthorizeAnUnownedWordFractionOrFlagBit(String field) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var m = f.state().motion();
        // Include an otherwise legal speed change so a rejecting apply cannot publish even a prefix.
        var changed = spinTunnelChanged(spinTunnelChanged(m, "X_VELOCITY"), field);
        spinTunnelAssertRejected(f, new CdSpinTunnelTag.PlayerResult(m, changed,
                java.util.EnumSet.allOf(CdSpinTunnelTag.PlayerField.class)));
    }

    @ParameterizedTest @ValueSource(ints = {2, 4, 6})
    void spinTunnelDeclaredFlagsPermitOnlyTheNativeAirAndRollBits(int mask) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var before = f.captureActor(); var shared = f.captureShared();
        var m = f.state().motion();
        var changed = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags() ^ mask, m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(),
                m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        f.applyObjectSpinTunnelResult(new CdSpinTunnelTag.PlayerResult(m, changed, Set.of(CdSpinTunnelTag.PlayerField.FLAGS)));
        var s = before.state();
        assertEquals(new CdPlayerBody.State(changed, s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle()), f.state());
        assertEquals(before.display(), f.display()); assertEquals(shared, f.captureShared());
    }

    @Test void spinTunnelPureSkipAndAllSameValueDeclarationsKeepEveryPartition() throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var actor = f.captureActor(); var shared = f.captureShared();
        var pool = new CdObjectPool(); var object = spinTunnelObject(pool, actor.state().motion(), 1);
        var skip = new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(0x200000)).step(object, actor.state().motion(),
                new CdSpinTunnelTag.Gates(0, 0x0100, 0, 0)).player();
        assertTrue(skip.executedWrites().isEmpty()); assertEquals(skip.expectedMotion(), skip.finalMotion());
        f.validateObjectSpinTunnelResult(skip); f.applyObjectSpinTunnelResult(skip);
        var same = new CdSpinTunnelTag.PlayerResult(f.state().motion(), f.state().motion(),
                java.util.EnumSet.allOf(CdSpinTunnelTag.PlayerField.class));
        f.validateObjectSpinTunnelResult(same); f.applyObjectSpinTunnelResult(same);
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @ParameterizedTest @CsvSource({"1, 0, -1, 0, 0, -3328, -3328, 6", "2, 0, -1, 12, 3328, -1, 3328, 4",
            "3, 0, 0, 4, -3328, 0, -3328, 6", "4, 0, 123, 1, 0, -2560, -2560, 6"})
    void spinTunnelAcceptsActualVerticalAndDirectionalPureTagResults(int subtype, int xv, int yv, int raw,
            int expectedXv, int expectedYv, int expectedGv, int bits) throws IOException {
        var f = new Fixture().runtime(); var actor = f.captureActor(); var s = actor.state(); var m = s.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16() | 0x1357, m.centreY16_16() | 0xBEEF, xv, yv, 0x111,
                4, 0, 0xE9, 0xD5, 9, 19, 0x1A, 0x35, 1, 2, -3, 4, 0x85, 0x16, 0x27);
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(motion, s.animation(),
                s.header(), s.timers(), s.history(), s.rotationDistance(), s.objectLinks(), s.rotationAngle()), actor.display()));
        var before = f.captureActor(); var shared = f.captureShared(); var pool = new CdObjectPool();
        var result = new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(0x200000)).step(spinTunnelObject(pool, motion, subtype),
                motion, new CdSpinTunnelTag.Gates(0, 0, 0, raw)).player();
        var expected = new CdPlayerMotionState(motion.centreX16_16(), ((1899 + 5) << 16) | 0xBEEF,
                expectedXv, expectedYv, expectedGv, 4, 0, 0xE9 | bits, 0xD5, 7, 14, 2, 0x35, 1, 2, -3, 4, 0x85, 0x16, 0x27);
        assertEquals(expected, result.finalMotion());
        f.validateObjectSpinTunnelResult(result); assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        f.applyObjectSpinTunnelResult(result);
        assertEquals(new CdPlayerBody.State(expected, s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle()), f.state());
        assertEquals(before.display(), f.display()); assertEquals(shared, f.captureShared());
    }

    @Test void spinTunnelNullPendingEntitlementAndClosedGuardsLeaveTheOwnerUntouched() throws IOException {
        var fixture = new Fixture(); var f = fixture.runtime(); var actor = f.captureActor(); var shared = f.captureShared();
        var m = f.state().motion(); var same = new CdSpinTunnelTag.PlayerResult(m, m, Set.of());
        assertThrows(NullPointerException.class, () -> f.validateObjectSpinTunnelResult(null));
        assertThrows(NullPointerException.class, () -> f.applyObjectSpinTunnelResult(null));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        var selection = f.prepareInput(8);
        assertThrows(IllegalStateException.class, () -> f.validateObjectSpinTunnelResult(same));
        assertThrows(IllegalStateException.class, () -> f.applyObjectSpinTunnelResult(same));
        assertSame(actor.state(), f.state()); assertSame(shared.environment(), f.environment());
        assertSame(shared.artState(), f.artState()); assertEquals(shared.vIntClock(), f.vIntClock());
        f.serviceVBlank(selection.phase()); // Rejection did not consume the pending sample.
        var servicedState = f.state(); var servicedEnvironment = f.environment(); var servicedArt = f.artState(); var clock = f.vIntClock();
        assertThrows(IllegalStateException.class, () -> f.validateObjectSpinTunnelResult(same));
        assertThrows(IllegalStateException.class, () -> f.applyObjectSpinTunnelResult(same));
        assertSame(servicedState, f.state()); assertSame(servicedEnvironment, f.environment());
        assertSame(servicedArt, f.artState()); assertSame(clock, f.vIntClock());
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary(), "Entitlement survived rejection");
        var now = f.state().motion(); var closedCall = new CdSpinTunnelTag.PlayerResult(now, now, Set.of());
        var beforeClose = f.state(); var env = f.environment(); var art = f.artState(); var display = f.display();
        f.close();
        assertThrows(IllegalStateException.class, () -> f.validateObjectSpinTunnelResult(closedCall));
        assertThrows(IllegalStateException.class, () -> f.applyObjectSpinTunnelResult(closedCall));
        assertSame(beforeClose, f.state()); assertSame(env, f.environment()); assertSame(art, f.artState());
        assertSame(display, f.display()); assertTrue(fixture.dmas.isEmpty());
    }

    @Test void spinTunnelReentryDuringServiceIsRejectedWithoutConsumingTheServiceOrBody() throws IOException {
        var f = new Fixture().runtime(); var m = f.state().motion();
        var same = new CdSpinTunnelTag.PlayerResult(m, m, Set.of()); int[] calls = {0};
        var selection = f.prepareInput(8);
        f.serviceVBlank(selection.phase(), () -> {
            calls[0]++; var state = f.state(); var env = f.environment(); var art = f.artState();
            var display = f.display(); var clock = f.vIntClock();
            assertThrows(IllegalStateException.class, () -> f.validateObjectSpinTunnelResult(same));
            assertThrows(IllegalStateException.class, () -> f.applyObjectSpinTunnelResult(same));
            assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(art, f.artState());
            assertSame(display, f.display()); assertSame(clock, f.vIntClock()); assertFalse(f.closed());
        });
        assertEquals(1, calls[0]); assertEquals(8, f.environment().controls().rawHeld());
        assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary());
    }

    @Test void spinTunnelClosedCutReplaysTheNextRealServicedBodyAndArt() throws IOException {
        var fixture = new Fixture(); var f = fixture.runtime();
        service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        var actor = f.captureActor(); var shared = f.captureShared(); var pool = new CdObjectPool();
        var result = new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(0x200000)).step(spinTunnelObject(pool, f.state().motion(), 0),
                f.state().motion(), new CdSpinTunnelTag.Gates(0, 0, 0, 0)).player();
        f.applyObjectSpinTunnelResult(result); var closedCut = f.captureActor(); int uploads = fixture.dmas.size();
        service(f, 8); var publication = f.runBody(3); var after = f.captureActor(); var afterShared = f.captureShared();
        var requests = List.copyOf(fixture.dmas.subList(uploads, fixture.dmas.size()));
        f.restoreActor(actor); f.restoreShared(shared); f.applyObjectSpinTunnelResult(result);
        assertEquals(closedCut, f.captureActor());
        int replayStart = fixture.dmas.size(); service(f, 8); var replay = f.runBody(3);
        assertEquals(publication, replay); assertEquals(after, f.captureActor());
        assertSharedValues(afterShared, f.captureShared());
        var replayRequests = fixture.dmas.subList(replayStart, fixture.dmas.size());
        assertEquals(1, requests.size(), "The queued frame1 cue makes one real art transfer");
        assertEquals(requests.size(), replayRequests.size());
        for (int i = 0; i < requests.size(); i++) {
            assertEquals(requests.get(i).vramByteAddress(), replayRequests.get(i).vramByteAddress());
            assertArrayEquals(requests.get(i).bytes().copy(), replayRequests.get(i).bytes().copy());
        }
    }

    private static CdPlayerFrameRuntime spinTunnelDirtyFrame(Fixture fixture) throws IOException {
        var f = fixture.runtime(); service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        f.publishDrawObjectOnScreen(true);
        var actor = f.captureActor(); var h = actor.state().header(); byte[] history = new byte[0x100];
        for (int i = 0; i < history.length; i++) history[i] = (byte) (0x33 + 17 * i);
        var motion = new CdPlayerMotionState(0xFFFC1234, 0xFFFDBEEF, 0x100, -0x234, 0x345,
                4, 0x60, 0xE9, 0xD5, 9, 19, 0x1A, 0x35, 0x76, 0x11, -0x1234, 0x85, 0xA5, 0x44, 0x88);
        // Constructed dirty fixture ingress explicitly declares raw $2B = $D7.
        var state = new CdPlayerBody.State(motion, new CdPlayerBody.AnimationCursor(5, 7, 0x23),
                new CdPlayerBody.Header(h.slotId(), h.mappingAddress(), 0x6780, 5, 0x27),
                new CdPlayerBody.Timers(0x1357, 0x2468, 0x369A, 0x48BC),
                new CdPlayerBody.History(CdAssetBytes.copyOf(history), 0xBC), 0xAB, new CdPlayerBody.ObjectLinks(0x7B, 0x9D), 0xD7);
        // Earlier actual DrawObject publication is deliberately retained while the closed-cut values are dirtied.
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), state, actor.display()));
        var shared = f.captureShared(); var movement = shared.environment().movement();
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(),
                movement.gates(), new CdPlayerMovement.Globals(0xA5, 0x3456, 0x43, 0x87, 0x2345, 0x7654, 0x91, 0x92, 0x93, 0x94)),
                new CdPlayerBody.Controls(0x2C, 0x24, 0x08, 0x10, 0x81),
                new CdPlayerBody.Globals(0x3456, 0x4567, 1, 0x0001, 0x0123, 0x5678, 1, 1), shared.environment().dynamicSlots());
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), environment,
                new CdPlayerArtRuntime.State(fill(0x300, 0x31), fill(0x300, 0x73), 0x23, true), false,
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFEDCBA98L), 0x1234)));
        return f;
    }

    private static CdObjectState spinTunnelObject(CdObjectPool pool, CdPlayerMotionState motion, int subtype) {
        var object = pool.state(32).moveByte(CdObjectState.ByteField.ID, 0x34)
                .moveByte(CdObjectState.ByteField.SUBTYPE, subtype)
                .moveWord(CdObjectState.WordField.X, motion.centreX16_16() >>> 16)
                .moveWord(CdObjectState.WordField.Y, motion.centreY16_16() >>> 16);
        pool.write(object); return object;
    }

    private static void spinTunnelAssertRejected(CdPlayerFrameRuntime f, CdSpinTunnelTag.PlayerResult result) {
        var actor = f.captureActor(); var shared = f.captureShared();
        assertThrows(IllegalArgumentException.class, () -> f.validateObjectSpinTunnelResult(result));
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSpinTunnelResult(result));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        assertSame(actor.state(), f.state()); assertSame(actor.display(), f.display());
        assertSame(shared.environment(), f.environment()); assertSame(shared.artState(), f.artState());
        assertSame(shared.vIntClock(), f.vIntClock()); assertFalse(f.closed());
    }

    private static CdPlayerMotionState spinTunnelChanged(CdPlayerMotionState m, String field) {
        int[] v = {m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(), m.routine(),
                m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle()};
        String[] keys = {"CENTRE_X_WORD", "CENTRE_Y_WORD", "X_VELOCITY", "Y_VELOCITY", "GROUND_VELOCITY", "ROUTINE", "ANGLE", "FLAGS",
                "SPRITE_FLAGS", "X_RADIUS", "Y_RADIUS", "ANIMATION", "PREVIOUS_ANIMATION", "PLAYER_JUMP", "CHARGE", "MOVE_LOCK",
                "STICK", "PLAYER_CONTROL", "PREVIOUS_PRIMARY_ANGLE", "PREVIOUS_SECONDARY_ANGLE"};
        boolean found = false;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(field)) { v[i] ^= i < 2 ? 0x10000 : i == 7 ? 2 : 1; found = true; }
        if (field.equals("X_FRACTION")) { v[0] ^= 1; found = true; }
        if (field.equals("Y_FRACTION")) { v[1] ^= 1; found = true; }
        if (field.startsWith("FLAGS_BIT_")) { v[7] ^= 1 << Integer.parseInt(field.substring("FLAGS_BIT_".length())); found = true; }
        if (!found) throw new IllegalArgumentException("Unknown spin-tunnel test field " + field);
        return new CdPlayerMotionState(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9],
                v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17], v[18], v[19]);
    }
    // END Spin tunnel tag Frame port additions.

    @Test void namedDevelopmentIngressKeepsClearedActorUntilActualInitAndUnknownAbsoluteClock() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); var s = f.state(); var e = f.environment();
        assertEquals(64 << 16, s.motion().centreX16_16()); assertEquals(1899 << 16, s.motion().centreY16_16());
        assertEquals(new CdPlayerMotionState(64 << 16, 1899 << 16, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0), s.motion());
        assertEquals(new CdPlayerBody.Header(1, 0, 0, 0, 0), s.header());
        assertEquals(new CdPlayerBody.AnimationCursor(0, 0, 0), s.animation());
        assertEquals(new CdPlayerBody.Timers(0, 0, 0, 0), s.timers(), "Explicit cold ClearObjects words, including oPlayerReset");
        assertEquals(0, s.rotationDistance(), "Explicit cold ClearObjects byte39");
        assertArrayEquals(new byte[256], s.history().bytes().copy()); assertEquals(0, s.history().index());
        assertEquals(new CdPlayerMovement.Physics(0, 0, 0), e.movement().physics(), "Init writes physics, not ingress");
        assertEquals(new CdPlayerMovement.Bounds(0, 0x2897, 0x710, 0), e.movement().bounds());
        assertEquals(new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 0), e.movement().gates());
        assertEquals(new CdPlayerMovement.Globals(0, 96, 0, 0, 0, 0, 0, 0, 0, 0), e.movement().globals());
        assertEquals(new CdPlayerBody.Globals(0, 160, 0, 0, 0, 0, 0, 0), e.globals());
        assertEquals(new CdPlayerBody.Controls(0, 0, 0, 0, 0), e.controls(), "Explicit title-omission unlocked policy");
        assertEquals(CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96, e.dynamicSlots());
        assertArrayEquals(new byte[0x300], f.artState().cpuBuffer().copy());
        assertArrayEquals(new byte[0x300], f.artState().residentBank().copy());
        assertEquals(0, f.artState().lastFrame()); assertFalse(f.artState().dirty());
        assertEquals(OptionalLong.empty(), f.captureShared().vIntClock().absoluteSeed());
        assertEquals(OptionalLong.empty(), f.captureShared().vIntClock().absoluteValue());
        assertEquals(0, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertTrue(f.display().isEmpty()); assertFalse(f.paused()); assertFalse(f.closed());
        assertTrue(fixture.dmas.isEmpty()); assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    // Spring action writes are a separate native call cut from shared solidity.
    @Test void springLaunchProjectsNativeWordsOnceAndReplaysWithoutTouchingOtherPartitions() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        var captured = f.captureActor(); var s = captured.state(); var m = s.motion();
        var seededMotion = new CdPlayerMotionState(m.centreX16_16() | 0x1234, m.centreY16_16() | 0x5678,
                m.xVelocity(), m.yVelocity(), m.groundVelocity(), m.routine(), m.angle(), m.flags(), m.spriteFlags(),
                m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(), m.playerJump(), m.charge(),
                0x4567, m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        // Constructed Spring fixture ingress explicitly declares raw $2B = $E3.
        var seeded = new CdPlayerBody.State(seededMotion, s.animation(), s.header(),
                new CdPlayerBody.Timers(0x55, 0x2233, 0x4455, 0x6677), s.history(), 0xAA,
                new CdPlayerBody.ObjectLinks(47, 63), 0xE3);
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(), seeded, captured.display()));
        var player = solidPlayer(f); var globals = solidGlobals(f);
        var next = player;
        for (var field : CdSpring.LaunchPlayerField.values()) next = springChanged(next, field.name());
        var call = springLaunch(player, globals, next, globals,
                java.util.EnumSet.allOf(CdSpring.LaunchPlayerField.class), Set.of());
        var before = f.captureActor(); var shared = f.captureShared(); var display = f.display();
        f.validateObjectSpringLaunch(call);
        assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        f.applyObjectSpringLaunch(call);
        assertEquals(new CdPlayerBody.State(next.actor().motion(), seeded.animation(), seeded.header(), seeded.timers(),
                seeded.history(), seeded.rotationDistance(), seeded.objectLinks(), seeded.rotationAngle()), f.state());
        assertEquals(0x1234, f.state().motion().centreX16_16() & 0xFFFF);
        assertEquals(0x5678, f.state().motion().centreY16_16() & 0xFFFF);
        assertEquals(0x4566, f.state().motion().moveLock());
        assertEquals(0x2233, f.state().timers().invincibility(), "Move-lock is word3E, never timer32");
        assertEquals(shared, f.captureShared()); assertEquals(display, f.display());
        var after = f.captureActor();
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSpringLaunch(call));
        assertEquals(after, f.captureActor()); assertEquals(shared, f.captureShared());
        f.restoreActor(before); f.restoreShared(shared); f.applyObjectSpringLaunch(call);
        assertEquals(after, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @ParameterizedTest @EnumSource(CdSpring.LaunchPlayerField.class)
    void springLaunchRejectsEachUndeclaredNativeStore(CdSpring.LaunchPlayerField field) throws IOException {
        var f = new Fixture().runtime(); var player = solidPlayer(f); var globals = solidGlobals(f);
        var actor = f.captureActor(); var shared = f.captureShared();
        var call = springLaunch(player, globals, springChanged(player, field.name()), globals, Set.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> f.validateObjectSpringLaunch(call));
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSpringLaunch(call));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {
            "X_FRACTION", "Y_FRACTION", "ROUTINE", "SPRITE_FLAGS", "X_RADIUS", "Y_RADIUS", "PREVIOUS_ANIMATION",
            "PLAYER_JUMP", "CHARGE", "STICK", "PLAYER_CONTROL", "PREVIOUS_PRIMARY", "PREVIOUS_SECONDARY",
            "TILE", "PRIORITY", "HURT", "ROTATION_DISTANCE", "SLOT_ID", "PUSH_OBJECT", "STAND_OBJECT"})
    void springLaunchCannotAuthorizeAnyOtherActorOrSlotField(String field) throws IOException {
        var f = new Fixture().runtime(); var player = solidPlayer(f); var globals = solidGlobals(f);
        var actor = f.captureActor(); var shared = f.captureShared();
        var call = springLaunch(player, globals, springChanged(player, field), globals,
                java.util.EnumSet.allOf(CdSpring.LaunchPlayerField.class), Set.of());
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSpringLaunch(call));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void springLaunchRejectsStaleExpectedPlayerAndGlobalsWithoutAnyWrite() throws IOException {
        var f = new Fixture().runtime(); var player = solidPlayer(f); var globals = solidGlobals(f);
        var actor = f.captureActor(); var shared = f.captureShared();
        var changed = springChanged(player, "FLAGS");
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSpringLaunch(
                springLaunch(changed, globals, changed, globals, Set.of(), Set.of())));
        var stale = new CdSolidObject.Globals(globals.invincible() ^ 1, globals.scoreChain(), globals.primaryAngle(), globals.secondaryAngle());
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSpringLaunch(
                springLaunch(player, stale, player, stale, Set.of(), Set.of())));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void springLaunchHasNoAuthorityForGlobalWritesEvenWhenTheyKeepTheSameValue() throws IOException {
        var f = new Fixture().runtime(); var player = solidPlayer(f); var globals = solidGlobals(f);
        var actor = f.captureActor(); var shared = f.captureShared();
        var changed = new CdSolidObject.Globals(globals.invincible(), globals.scoreChain() ^ 2, globals.primaryAngle(), globals.secondaryAngle());
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSpringLaunch(
                springLaunch(player, globals, player, changed, Set.of(), Set.of())));
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSpringLaunch(
                springLaunch(player, globals, player, globals, Set.of(), Set.of(CdSolidObject.GlobalField.SCORE_CHAIN))));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void springLaunchRequiresAnOpenOwnerAtAClosedFrameCut() throws IOException {
        var f = new Fixture().runtime(); var player = solidPlayer(f); var globals = solidGlobals(f);
        var call = springLaunch(player, globals, player, globals, Set.of(), Set.of());
        var actor = f.captureActor(); var shared = f.captureShared();
        assertThrows(NullPointerException.class, () -> f.validateObjectSpringLaunch(null));
        f.validateObjectSpringLaunch(call); f.applyObjectSpringLaunch(call);
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.validateObjectSpringLaunch(call));
        assertThrows(IllegalStateException.class, () -> f.applyObjectSpringLaunch(call));
        f.clearPendingInput(); f.close();
        assertThrows(IllegalStateException.class, () -> f.applyObjectSpringLaunch(call));
    }

    private static CdSpring.LaunchCall springLaunch(CdSolidObject.Player before, CdSolidObject.Globals globals,
            CdSolidObject.Player after, CdSolidObject.Globals nextGlobals, Set<CdSpring.LaunchPlayerField> writes,
            Set<CdSolidObject.GlobalField> globalWrites) {
        return new CdSpring.LaunchCall(new CdObjectPool().state(32).identity(), before, globals, after, nextGlobals, writes, globalWrites);
    }
    private static CdSolidObject.Player springChanged(CdSolidObject.Player player, String field) {
        var a = player.actor(); var m = a.motion();
        int[] v = {m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(), m.routine(),
                m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle()};
        String[] keys = {"CENTRE_X", "CENTRE_Y", "X_VELOCITY", "Y_VELOCITY", "GROUND_VELOCITY", "ROUTINE", "ANGLE", "FLAGS",
                "SPRITE_FLAGS", "X_RADIUS", "Y_RADIUS", "ANIMATION", "PREVIOUS_ANIMATION", "PLAYER_JUMP", "CHARGE", "MOVE_LOCK",
                "STICK", "PLAYER_CONTROL", "PREVIOUS_PRIMARY", "PREVIOUS_SECONDARY"};
        boolean found = false;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(field)) { v[i] ^= i < 2 ? 0x10000 : 1; found = true; }
        if (field.equals("X_FRACTION")) { v[0] ^= 1; found = true; }
        if (field.equals("Y_FRACTION")) { v[1] ^= 1; found = true; }
        var next = new CdPlayerMotionState(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9],
                v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17], v[18], v[19]);
        int tile = a.tile(), priority = a.priority(), hurt = a.hurt(), rotation = a.rotationDistance();
        int id = player.slotId(), push = player.pushObject(), stand = player.standObject();
        switch (field) {
            case "TILE" -> { tile ^= 1; found = true; }
            case "PRIORITY" -> { priority ^= 1; found = true; }
            case "HURT" -> { hurt ^= 1; found = true; }
            case "ROTATION_DISTANCE" -> { rotation ^= 1; found = true; }
            case "SLOT_ID" -> { id ^= 1; found = true; }
            case "PUSH_OBJECT" -> { push ^= 1; found = true; }
            case "STAND_OBJECT" -> { stand ^= 1; found = true; }
        }
        if (!found) throw new IllegalArgumentException("Unknown Spring test mutation " + field);
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(next, tile, priority, hurt, rotation), id, push, stand);
    }

    // S3 validates a pure call's final values before the pool owner commits that call.
    @Test void nativeStandAndReleaseCommitOnlyTheirActorAndGlobalPartitionsAndReplay() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        var captured = f.captureActor(); var s = captured.state(); var m = s.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), 1899 << 16, 0x180, 0x111, 0x77,
                4, 0x66, 4, m.spriteFlags(), 7, 14, 2, m.previousAnimation(), 1, m.charge(),
                m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var seeded = new CdPlayerBody.State(motion, s.animation(), s.header(),
                new CdPlayerBody.Timers(7, s.timers().invincibility(), s.timers().speedShoes(), s.timers().reset()),
                s.history(), s.rotationDistance(), new CdPlayerBody.ObjectLinks(47, 0), s.rotationAngle());
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(), seeded, captured.display()));
        var player = solidPlayer(f); var globals = solidGlobals(f);
        var pool = new CdObjectPool(); var object = solidObject(pool);
        var result = CdSolidObject.fromAssets(f.assets()).run(CdSolidObject.Entry.STAND_ON_OBJECT, object,
                player, new CdSolidObject.Gates(0, 0), globals, pool.capture());
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared(); var display = f.display();
        f.validateObjectSolidResult(player, globals, result);
        assertEquals(beforeActor, f.captureActor()); assertEquals(beforeShared, f.captureShared());
        f.applyObjectSolidResult(player, globals, result);
        var after = f.captureActor(); var afterShared = f.captureShared();
        assertEquals(2, f.state().motion().routine()); assertEquals(120, f.state().timers().hurt());
        assertEquals(1894 << 16, f.state().motion().centreY16_16());
        assertEquals(9, f.state().motion().xRadius()); assertEquals(19, f.state().motion().yRadius());
        assertEquals(8, f.state().motion().flags()); assertEquals(0, f.state().motion().animation());
        assertEquals(0, f.state().motion().playerJump()); assertEquals(0, f.state().motion().angle());
        assertEquals(0, f.state().motion().yVelocity()); assertEquals(0x180, f.state().motion().groundVelocity());
        assertEquals(new CdPlayerBody.ObjectLinks(47, 32), f.state().objectLinks());
        assertEquals(seeded.history(), f.state().history()); assertEquals(seeded.animation(), f.state().animation());
        assertEquals(display, f.display(), "The earlier native DrawObject cut is not republished");
        assertEquals(object, pool.state(32), "The Frame does not commit a pool image");
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSolidResult(player, globals, result));
        assertEquals(after, f.captureActor()); assertEquals(afterShared, f.captureShared());
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        f.applyObjectSolidResult(player, globals, result);
        assertEquals(after, f.captureActor()); assertEquals(afterShared, f.captureShared());
        var current = solidPlayer(f); var currentGlobals = solidGlobals(f);
        var release = CdSolidObject.fromAssets(f.assets()).run(CdSolidObject.Entry.GET_OFF_OBJECT, result.object(),
                current, new CdSolidObject.Gates(0, 0), currentGlobals, pool.capture());
        f.applyObjectSolidResult(current, currentGlobals, release);
        assertEquals(new CdPlayerBody.ObjectLinks(47, 0), f.state().objectLinks());
        assertEquals(2, f.state().motion().flags() & 10);
    }

    @Test void declaredSolidGlobalWritesReachTheirSingleStoresAndKeepTheActorAndServiceState() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        var player = solidPlayer(f); var globals = solidGlobals(f);
        var after = new CdSolidObject.Globals(1, 0x1234, 0x45, 0x67);
        var result = solidResult(solidObject(new CdObjectPool()), player, after, java.util.Set.of(),
                java.util.EnumSet.allOf(CdSolidObject.GlobalField.class));
        var actor = f.captureActor(); var shared = f.captureShared(); var env = f.environment();
        f.validateObjectSolidResult(player, globals, result); assertEquals(shared, f.captureShared());
        f.applyObjectSolidResult(player, globals, result);
        assertEquals(1, f.environment().globals().invincible());
        assertEquals(0x1234, f.environment().movement().globals().scoreChain());
        assertEquals(0x45, f.environment().movement().globals().primaryAngle());
        assertEquals(0x67, f.environment().movement().globals().secondaryAngle());
        assertEquals(actor, f.captureActor()); assertEquals(shared.artState(), f.artState());
        assertEquals(shared.vIntClock(), f.vIntClock()); assertEquals(env.controls(), f.environment().controls());
        assertEquals(env.movement().physics(), f.environment().movement().physics());
        assertEquals(env.movement().bounds(), f.environment().movement().bounds());
        assertEquals(env.movement().gates(), f.environment().movement().gates());
        assertEquals(env.globals().levelFrames(), f.environment().globals().levelFrames());
    }

    @Test void objectSolidResultRejectsStaleGlobalsAndUndeclaredValuesBeforeAnyCommit() throws IOException {
        var f = new Fixture().runtime(); var player = solidPlayer(f); var globals = solidGlobals(f);
        var pool = new CdObjectPool(); var object = solidObject(pool);
        var same = solidResult(object, player, globals, java.util.Set.of(), java.util.Set.of());
        var actor = f.captureActor(); var shared = f.captureShared();
        var staleGlobals = new CdSolidObject.Globals(globals.invincible() ^ 1, globals.scoreChain(), globals.primaryAngle(), globals.secondaryAngle());
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSolidResult(player, staleGlobals, same));
        var a = player.actor();
        var tile = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(a.motion(), a.tile() ^ 0x8000,
                a.priority(), a.hurt(), a.rotationDistance()), player.slotId(), player.pushObject(), player.standObject());
        var undeclaredTile = solidResult(object, tile, globals, java.util.Set.of(), java.util.Set.of());
        assertThrows(IllegalArgumentException.class, () -> f.validateObjectSolidResult(player, globals, undeclaredTile));
        var undeclaredGlobal = solidResult(object, player, staleGlobals, java.util.Set.of(), java.util.Set.of());
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSolidResult(player, globals, undeclaredGlobal));
        var undeclaredLink = new CdSolidObject.Player(a, player.slotId(), 32, player.standObject());
        var links = solidResult(object, undeclaredLink, globals, java.util.Set.of(), java.util.Set.of());
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSolidResult(player, globals, links));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void knownWriteSetsCannotAuthorizeAnUnrepresentedMotionChange() throws IOException {
        var f = new Fixture().runtime(); var player = solidPlayer(f); var globals = solidGlobals(f);
        var a = player.actor(); var m = a.motion();
        var charge = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                m.playerJump(), m.charge() ^ 1, m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var changed = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(charge, a.tile(), a.priority(), a.hurt(), a.rotationDistance()),
                player.slotId(), player.pushObject(), player.standObject());
        var pool = new CdObjectPool();
        var result = solidResult(solidObject(pool), changed, globals,
                java.util.EnumSet.allOf(CdSolidObject.PlayerField.class), java.util.EnumSet.allOf(CdSolidObject.GlobalField.class));
        var actor = f.captureActor(); var shared = f.captureShared();
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectSolidResult(player, globals, result));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void objectSolidPortsRequireClosedFrameAndRejectNullWithoutAdvancingAnything() throws IOException {
        var f = new Fixture().runtime(); var player = solidPlayer(f); var globals = solidGlobals(f);
        var result = solidResult(solidObject(new CdObjectPool()), player, globals, java.util.Set.of(), java.util.Set.of());
        var before = f.captureActor(); var shared = f.captureShared();
        assertThrows(NullPointerException.class, () -> f.validateObjectSolidResult(null, globals, result));
        assertThrows(NullPointerException.class, () -> f.validateObjectSolidResult(player, null, result));
        assertThrows(NullPointerException.class, () -> f.validateObjectSolidResult(player, globals, null));
        assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.validateObjectSolidResult(player, globals, result));
        assertThrows(IllegalStateException.class, () -> f.applyObjectSolidResult(player, globals, result));
        f.clearPendingInput(); f.close();
        assertThrows(IllegalStateException.class, () -> f.applyObjectSolidResult(player, globals, result));
    }

    /** Explicit pool/player inputs for a component test, not World gameplay or a source identity. */
    private static CdObjectState solidObject(CdObjectPool pool) {
        var object = pool.state(32).moveByte(CdObjectState.ByteField.ID, 0x12)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x80)
                .moveByte(CdObjectState.ByteField.WIDTH, 12).moveByte(CdObjectState.ByteField.Y_RADIUS, 12)
                .moveWord(CdObjectState.WordField.X, 64).moveWord(CdObjectState.WordField.Y, 1920);
        pool.write(object); return object;
    }
    private static CdSolidObject.Player solidPlayer(CdPlayerFrameRuntime f) {
        var s = f.state(); var a = new CdPlayerTouchResponse.Actor(s.motion(), s.header().tile(),
                s.header().priority(), s.timers().hurt(), s.rotationDistance());
        return new CdSolidObject.Player(a, s.header().slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
    }
    private static CdSolidObject.Globals solidGlobals(CdPlayerFrameRuntime f) {
        var e = f.environment(); var g = e.movement().globals();
        return new CdSolidObject.Globals(e.globals().invincible(), g.scoreChain(), g.primaryAngle(), g.secondaryAngle());
    }
    private static CdSolidObject.Result solidResult(CdObjectState object, CdSolidObject.Player player,
            CdSolidObject.Globals globals, java.util.Set<CdSolidObject.PlayerField> writes,
            java.util.Set<CdSolidObject.GlobalField> globalWrites) {
        return new CdSolidObject.Result(CdSolidObject.Entry.GET_OFF_OBJECT, object, player, globals, List.of(),
                java.util.Set.of(), writes, globalWrites, List.of(), CdSolidObject.Path.ROUTINE_RETURN,
                Optional.empty(), java.util.OptionalInt.empty());
    }

    @ParameterizedTest @CsvSource({"0,0", "32,127", "161,254"})
    void objectLinksSurviveBodyCallsProjectionPortsActorRestoreAndReplay(int push, int stand) throws IOException {
        var f = new Fixture().runtime();
        var links = new CdPlayerBody.ObjectLinks(push, stand);
        var seed = new Actor(f); seed.objectLinks = links; seed.install(f);
        service(f, 0); f.runBody(1);
        assertEquals(links, f.state().objectLinks(), "Init preserves both object links");
        var hurt = new Actor(f); hurt.routine = 4; hurt.flags = 6; hurt.y = 1886 << 16; hurt.install(f);
        service(f, 0); f.runBody(2);
        assertEquals(links, f.state().objectLinks(), "Hurt landing preserves both links");
        service(f, 0); f.runBody(3);
        assertEquals(links, f.state().objectLinks(), "Main does not clear the source object bytes");
        f.publishDrawObjectOnScreen(true); f.applyObjectYVelocity(-0xC00); f.applyObjectPrimaryAngle(0x40);
        assertEquals(links, f.state().objectLinks(), "The existing narrow ports preserve both links");
        var actor = f.captureActor(); var shared = f.captureShared();
        var changed = new Actor(f); changed.objectLinks = new CdPlayerBody.ObjectLinks(push ^ 0xFF, stand ^ 0xFF);
        changed.install(f); assertNotEquals(links, f.state().objectLinks());
        f.restoreActor(actor); f.restoreShared(shared); assertEquals(links, f.state().objectLinks());
        service(f, 0); f.runBody(4); var after = f.captureActor();
        f.restoreActor(actor); f.restoreShared(shared); service(f, 0); f.runBody(4);
        assertEquals(after, f.captureActor()); assertEquals(links, f.state().objectLinks());
    }

    @Test void coldAndRestartFactoriesExplicitlyClearThePlayerObjectLinks() throws IOException {
        var assets = new Fixture().assets();
        try (var cold = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0);
             var restarted = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, coldIngress())) {
            assertEquals(new CdPlayerBody.ObjectLinks(0, 0), cold.state().objectLinks());
            assertEquals(new CdPlayerBody.ObjectLinks(0, 0), restarted.state().objectLinks());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 128, 255})
    void rotationByteSurvivesInitMainVIntProjectionObjectWritesAndActorReplay(int rotation) throws IOException {
        var f = new Fixture().runtime();
        var seeded = new Actor(f); seeded.rotationDistance = rotation; seeded.install(f);
        service(f, 0); f.runBody(1);
        assertEquals(rotation, f.state().rotationDistance(), "Init does not write oPlayerRotDist");
        service(f, 0); f.runBody(2);
        assertEquals(rotation, f.state().rotationDistance(), "Main/MODE/Animate preserve byte39");
        service(f, 0); f.runBody(3);
        assertEquals(rotation, f.state().rotationDistance(), "V-int art/input service preserves the actor byte");
        var saved = f.captureActor(); var savedShared = f.captureShared();
        f.publishDrawObjectOnScreen(true);
        f.applyObjectYVelocity(-0xC00);
        f.applyObjectPrimaryAngle(0x40);
        assertEquals(rotation, f.state().rotationDistance(), "Projection/word write must not reset the adjacent byte");
        var changed = new Actor(f); changed.rotationDistance = rotation ^ 0xFF; changed.install(f);
        assertEquals(rotation ^ 0xFF, f.state().rotationDistance());
        f.restoreActor(saved);
        assertEquals(rotation, f.state().rotationDistance());
        assertEquals(saved, f.captureActor());
        f.restoreShared(savedShared);
        service(f, 0); f.runBody(4);
        var replay = f.captureActor();
        f.restoreActor(saved); f.restoreShared(savedShared);
        service(f, 0); f.runBody(4);
        assertEquals(replay, f.captureActor(), "The byte belongs to actor replay, not shared state");
    }

    @ParameterizedTest @ValueSource(ints = {-1, 256})
    void rotationByteRequiresExplicitUnsignedNativeStorage(int invalid) throws IOException {
        var s = new Fixture().runtime().state();
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.State(s.motion(), s.animation(),
                s.header(), s.timers(), s.history(), invalid, s.objectLinks(), s.rotationAngle()));
    }

    // U5-B0, written before production. oPlayerReset ($3A, Level/_Definitions.inc:86) is actor state:
    // only routines 6 and 8 address it (Sonic/Main.asm:2439-2457), so every owned call and port keeps it.
    @ParameterizedTest @ValueSource(ints = {1, 0x003C, 0x01E0, 0xFFFF})
    void resetWordSurvivesInitHurtMainVIntProjectionObjectWritesAndActorReplay(int reset) throws IOException {
        var f = new Fixture().runtime();
        var seeded = new Actor(f); seeded.reset = reset; seeded.install(f);
        service(f, 0); f.runBody(1);
        assertEquals(reset, f.state().timers().reset(), "Init does not write oPlayerReset");
        // The existing Hurt landing fixture: its HurtChkLand rewrites the adjacent hurt word only.
        var hurt = new Actor(f); hurt.routine = 4; hurt.flags = 6; hurt.y = 1886 << 16; hurt.install(f);
        service(f, 0); var landing = f.runBody(2);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, landing.result().boundary());
        assertEquals(120, f.state().timers().hurt()); assertEquals(reset, f.state().timers().reset());
        service(f, 0); var main = f.runBody(3);
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, main.result().boundary());
        assertEquals(119, f.state().timers().hurt(), "Display counts the hurt word");
        assertEquals(reset, f.state().timers().reset(), "Display/MODE/Animate preserve the reset word");
        var saved = f.captureActor(); var savedShared = f.captureShared();
        f.publishDrawObjectOnScreen(true);
        f.applyObjectYVelocity(-0xC00);
        f.applyObjectPrimaryAngle(0x40);
        assertEquals(reset, f.state().timers().reset(), "Projection and object writes keep the actor word");
        var changed = new Actor(f); changed.reset = reset ^ 0xFFFF; changed.install(f);
        assertEquals(reset ^ 0xFFFF, f.state().timers().reset());
        f.restoreActor(saved);
        assertEquals(reset, f.state().timers().reset());
        assertEquals(saved, f.captureActor());
        f.restoreShared(savedShared);
        service(f, 0); f.runBody(4);
        var replay = f.captureActor();
        assertEquals(reset, replay.state().timers().reset());
        f.restoreActor(saved); f.restoreShared(savedShared);
        service(f, 0); f.runBody(4);
        assertEquals(replay, f.captureActor(), "The word belongs to actor replay, not shared state");
    }

    @Test void serviceZeroInitOnlyServiceZeroThenMainPublishesForLaterArtService() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime();
        service(f, 0); assertEquals(0, f.state().motion().routine()); assertTrue(fixture.dmas.isEmpty());
        var init = f.runBody(1); assertEquals(CdPlayerBody.Boundary.INIT_RETURN, init.result().boundary());
        assertEquals(List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH, CdPlayerBody.NativeCall.INIT), calls(init));
        assertEquals(new CdPlayerMovement.Physics(0x600, 12, 0x80), f.environment().movement().physics());
        assertEquals(0, f.state().animation().mapFrame()); assertTrue(f.display().isEmpty());
        service(f, 0); assertTrue(fixture.dmas.isEmpty()); assertEquals(0, f.artState().lastFrame());
        var main = f.runBody(2); assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, main.result().boundary());
        assertEquals(1, f.state().animation().mapFrame()); assertEquals(0, f.artState().lastFrame());
        assertEquals(1, calls(main).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
        assertEquals(1, calls(main).stream().filter(c -> c == CdPlayerBody.NativeCall.ANIMATE).count());
        assertTrue(fixture.dmas.isEmpty(), "Main publishes a map byte without servicing it");
        service(f, 0); assertEquals(1, fixture.dmas.size()); assertEquals(1, f.artState().lastFrame());
        assertEquals(0x2E0, fixture.dmas.getFirst().bytes().length());
        for (int i = 0; i < 32; i++) assertEquals(0x5A, f.artState().residentBank().unsignedByte(i));
        for (int i = 32; i < 0x300; i++) assertEquals(0, f.artState().residentBank().unsignedByte(i));
        f.clearPendingInput();
    }

    @Test void twoStageActorBindingUsesOwnersExactAssetsAndCannotServiceBeforeBankCallbackIsBound() throws IOException {
        var assets = new Fixture().assets(); var f = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets);
        assertSame(assets, f.assets()); var original = f.captureShared(); f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertEquals(original, f.captureShared()); assertFalse(f.closed());
        f.bindArtPublication((request, full) -> fail("No forced first cue")); service(f, 0); f.runBody(1);
        assertSame(assets, f.assets());
    }

    @Test void prepareIsProspectiveAndRequiresOnePendingSampleAndClosedCaptureBoundary() throws IOException {
        var f = new Fixture().runtime(); var actor = f.captureActor(); var shared = f.captureShared();
        var selected = f.prepareInput(0x80); assertTrue(selected.nextPaused());
        assertEquals(PlcLifecyclePhase.NORMAL_PAUSE, selected.phase());
        assertEquals(actor.state(), f.state()); assertEquals(shared.environment(), f.environment());
        assertSame(shared.artState(), f.artState()); assertFalse(f.paused());
        assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
        assertThrows(IllegalStateException.class, f::captureActor); assertThrows(IllegalStateException.class, f::captureShared);
        f.serviceVBlank(selected.phase()); assertTrue(f.paused());
        assertEquals(0x80, f.environment().controls().rawHeld()); assertEquals(0x80, f.environment().controls().rawTap());
        assertEquals(1, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE));
        assertFalse(f.closed());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128})
    void everyNativeButtonBitIsReadAsItsOwnHeldAndTapBit(int held) throws IOException {
        var f = new Fixture().runtime(); service(f, held);
        assertEquals(held, f.environment().controls().rawHeld()); assertEquals(held, f.environment().controls().rawTap());
        f.clearPendingInput(); service(f, held);
        assertEquals(held, f.environment().controls().rawHeld()); assertEquals(0, f.environment().controls().rawTap());
        f.clearPendingInput();
    }

    @Test void actionChangesWhileAnotherRemainsHeldUseLastActuallyReadByte() throws IOException {
        var f = new Fixture().runtime(); service(f, 0x10); f.clearPendingInput();
        service(f, 0x30); assertEquals(0x20, f.environment().controls().rawTap()); f.clearPendingInput();
        service(f, 0x60); assertEquals(0x40, f.environment().controls().rawTap()); f.clearPendingInput();
        service(f, 0x40); assertEquals(0, f.environment().controls().rawTap()); f.clearPendingInput();
    }

    @Test void pauseEntryRunsLastOldUnpausedTimersAndResumeRunsOldPausedGates() throws IOException {
        var f = new Fixture().runtime(); Env e = new Env(f); e.bored = 10; e.warp = 20; e.install(f);
        service(f, 0x80); assertTrue(f.paused()); assertEquals(11, f.environment().movement().globals().boredTimer());
        assertEquals(21, f.environment().globals().timeWarpTimer());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
        service(f, 0); assertTrue(f.paused()); assertEquals(11, f.environment().movement().globals().boredTimer());
        service(f, 0x80); assertFalse(f.paused());
        assertEquals(11, f.environment().movement().globals().boredTimer()); assertEquals(21, f.environment().globals().timeWarpTimer());
        f.runBody(1); assertEquals(1, f.environment().globals().levelFrames());
        assertEquals(3, f.captureShared().vIntClock().deltaSinceAdmittedCut());
    }

    @Test void heldPauseServicesArtAndRawInputButNeverDispatchesBodyOrChangesLogicalPair() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); service(f, 0x10); f.clearPendingInput();
        Env e = new Env(f); e.logicalHeld = 8; e.logicalTap = 8; e.install(f);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x24), new byte[0x300], 0, true));
        var actor = f.captureActor(); service(f, 0x90);
        assertTrue(f.paused()); assertEquals(1, fixture.dmas.size()); assertEquals(0x80, f.environment().controls().rawTap());
        assertEquals(8, f.environment().controls().logicalHeld()); assertEquals(8, f.environment().controls().logicalTap());
        assertEquals(actor, f.captureActor()); assertEquals(0, f.environment().globals().levelFrames());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @Test void alreadyHeldActionThroughPauseIsNotAResampledTapOrBody() throws IOException {
        var f = new Fixture().runtime(); service(f, 0x10); f.clearPendingInput();
        var original = f.state(); service(f, 0x90); assertTrue(f.paused());
        assertEquals(0x80, f.environment().controls().rawTap()); assertEquals(original, f.state());
        service(f, 0x90); assertEquals(0, f.environment().controls().rawTap()); assertEquals(original, f.state());
        assertEquals(0, f.environment().controls().logicalHeld()); assertEquals(0, f.environment().globals().levelFrames());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @Test void lagConsumesPendingWithoutControllerReadStartToggleOrDplcAndStillRunsFinish() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); Env e = new Env(f); e.bored = 1; e.warp = 1; e.install(f);
        byte[] cpu = fill(0x300, 0x33), resident = fill(0x300, 0x66);
        restoreArt(f, new CdPlayerArtRuntime.State(cpu, resident, 0, true));
        f.prepareInput(0x80); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(0, f.environment().controls().rawHeld()); assertEquals(0, f.environment().controls().rawTap());
        assertFalse(f.paused()); assertTrue(f.artState().dirty()); assertArrayEquals(resident, f.artState().residentBank().copy());
        assertTrue(fixture.dmas.isEmpty()); assertEquals(2, f.environment().movement().globals().boredTimer());
        assertEquals(2, f.environment().globals().timeWarpTimer()); assertEquals(1, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
        service(f, 0x80); assertTrue(f.paused(), "Lag's unpolled Start must still be a new edge");
        assertEquals(1, fixture.dmas.size());
    }

    @Test void absentSlotStillUploadsPendingDirtyAndDoesNoBodyCharge() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); Actor a = new Actor(f); a.slot = 0; a.charge = 20; a.install(f);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x22), fill(0x300, 0x77), 0, true));
        service(f, 0); assertEquals(1, fixture.dmas.size()); assertFalse(f.artState().dirty());
        assertEquals(0x22, f.artState().residentBank().unsignedByte(0x2DF));
        assertEquals(0x77, f.artState().residentBank().unsignedByte(0x2E0));
        var result = f.runBody(1); assertEquals(CdPlayerBody.Boundary.SLOT_ABSENT, result.result().boundary());
        assertEquals(20, f.state().motion().charge()); assertTrue(result.sounds().isEmpty());
    }

    @ParameterizedTest @CsvSource({"0,0,0,0", "1,0,2,0", "65535,65535,0,0", "9,20,10,21"})
    void finishTimersUseNativeActiveWordAndWrap(int bored, int warp, int expectedBored, int expectedWarp) throws IOException {
        var f = new Fixture().runtime(); Env e = new Env(f); e.bored = bored; e.warp = warp; e.install(f);
        f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(expectedBored, f.environment().movement().globals().boredTimer());
        assertEquals(expectedWarp, f.environment().globals().timeWarpTimer());
    }

    @Test void chargeGateBlocksWarpOnlyAndNeverGetsPredispatchIncrementDuringService() throws IOException {
        var f = new Fixture().runtime(); Actor a = new Actor(f); a.charge = 20; a.install(f);
        Env e = new Env(f); e.bored = 1; e.warp = 7; e.install(f); service(f, 0);
        assertEquals(20, f.state().motion().charge()); assertEquals(2, f.environment().movement().globals().boredTimer());
        assertEquals(7, f.environment().globals().timeWarpTimer()); f.runBody(1);
        assertEquals(21, f.state().motion().charge(), "Only Body predispatch advances charge during Init");
    }

    @Test void lockedNonzeroLogicalPairRetainsActualValuesAndStartDoesNotToggle() throws IOException {
        var f = new Fixture().runtime(); Env e = new Env(f); e.locked = 1; e.logicalHeld = 8; e.logicalTap = 8; e.install(f);
        service(f, 0x84); f.runBody(1); service(f, 4); f.runBody(2);
        assertFalse(f.paused()); assertEquals(4, f.environment().controls().rawHeld());
        assertEquals(8, f.environment().controls().logicalHeld()); assertEquals(8, f.environment().controls().logicalTap());
    }

    @Test void levelWordComesOnlyFromBodyArgumentWhileVIntDeltaCountsServices() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(0x1234FFFF);
        assertEquals(0xFFFF, f.environment().globals().levelFrames());
        f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(0xFFFF, f.environment().globals().levelFrames());
        assertEquals(2, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        service(f, 0); f.runBody(0x12340000); assertEquals(0, f.environment().globals().levelFrames());
    }

    @Test void bodyMainPredispatchChargeRunsOnceAndReturnsTypedSoundsInSourceOrder() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        Actor a = new Actor(f); a.charge = 29; a.gv = 0xC00; a.invincibility = 1; a.install(f);
        Env e = new Env(f); e.invincible = 1; e.install(f); service(f, 0);
        var publication = f.runBody(2);
        assertEquals(1, calls(publication).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
        assertEquals(0, f.state().motion().charge()); assertEquals(3059, f.state().motion().groundVelocity());
        assertEquals(List.of(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_91),
                new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_R1AMUS)), publication.sounds());
        assertThrows(UnsupportedOperationException.class, () -> publication.sounds().clear());
        assertThrows(IllegalStateException.class, () -> f.runBody(2));
    }

    @Test void modeSoundKeepsItsTypedIdentityWithoutPlayback() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        service(f, 0x10); var p = f.runBody(2);
        assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP)), p.sounds());
    }

    @Test void ordinaryMainAirChargeIsIncrementedByBodyPredispatchExactlyOnce() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        Actor a = new Actor(f); a.flags = 2; a.y = 200 << 16; a.charge = 20; a.install(f);
        service(f, 0); var p = f.runBody(2);
        assertEquals(21, f.state().motion().charge());
        assertEquals(1, calls(p).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, p.result().boundary());
    }

    @Test void failedLateBodyPublishesNoHalfStateEnvironmentDisplayOrSoundsAndCannotRetrySlot() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        Actor a = new Actor(f); a.invincibility = 1; a.install(f);
        Env e = new Env(f); e.invincible = 1; e.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED; e.install(f);
        service(f, 0); var state = f.state(); var environment = f.environment(); var display = f.display(); var art = f.artState();
        assertEquals(CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(2)).boundary());
        assertSame(state, f.state()); assertSame(environment, f.environment()); assertEquals(display, f.display());
        assertSame(art, f.artState()); assertEquals(1, f.state().timers().invincibility());
        assertEquals(0, f.state().history().index()); assertEquals(1, f.environment().globals().invincible());
        assertEquals(1, f.environment().globals().levelFrames(), "Failed Body does not publish its staged level-word argument");
        assertFalse(f.closed()); assertThrows(IllegalStateException.class, () -> f.runBody(2));
        assertNotNull(f.captureActor()); assertNotNull(f.captureShared());
        e = new Env(f); e.slots = CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96; e.install(f);
        service(f, 0); var completed = f.runBody(2);
        assertEquals(1, completed.sounds().stream().filter(CdPlayerFrameRuntime.BodySound.class::isInstance).count());
    }

    @Test void failedArtPublicationClosesAdmissionRetainsActualReadAndNeverRetriesPartialService() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); var oldActor = f.captureActor(); var oldShared = f.captureShared();
        Env e = new Env(f); e.bored = 1; e.warp = 1; e.install(f);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x44), new byte[0x300], 0, true));
        fixture.failPublication = true; f.prepareInput(8);
        assertThrows(PublicationFailure.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertTrue(f.closed()); assertEquals(8, f.environment().controls().rawHeld()); assertEquals(8, f.environment().controls().rawTap());
        assertEquals(1, f.environment().movement().globals().boredTimer()); assertEquals(1, f.environment().globals().timeWarpTimer());
        assertEquals(1, fixture.dmas.size()); assertFalse(f.artState().dirty(), "Logical service advanced; do not claim rollback");
        assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
        assertThrows(IllegalStateException.class, () -> f.restoreActor(oldActor));
        assertThrows(IllegalStateException.class, () -> f.restoreShared(oldShared));
        assertEquals(1, fixture.dmas.size()); f.clearPendingInput(); f.close();
    }

    @ParameterizedTest @EnumSource(value = PlcLifecyclePhase.class, names = {"ORDINARY_LEVEL", "NORMAL_PAUSE", "LAG"}, mode = EnumSource.Mode.EXCLUDE)
    void unsupportedServicePhaseIsRejectedAtPredicateAndClearsFrameWithoutNativeMutation(PlcLifecyclePhase phase) throws IOException {
        var f = new Fixture().runtime(); var actor = f.captureActor(); var shared = f.captureShared();
        assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, () -> f.hasPreparationBoundary(phase));
        f.prepareInput(8); assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, () -> f.serviceVBlank(phase));
        assertEquals(actor.state(), f.state()); assertEquals(shared.environment(), f.environment());
        assertSame(shared.artState(), f.artState()); assertFalse(f.closed());
        assertNotNull(f.captureShared()); assertNotNull(f.prepareInput(0)); f.clearPendingInput();
    }

    @Test void supportedPhasesHaveNoPlcPreparationAndMismatchedSelectionNeverCommitsRaw() throws IOException {
        var f = new Fixture().runtime();
        for (var phase : List.of(PlcLifecyclePhase.ORDINARY_LEVEL, PlcLifecyclePhase.NORMAL_PAUSE, PlcLifecyclePhase.LAG)) {
            assertFalse(f.hasPreparationBoundary(phase)); f.prepareAfterLoop(phase);
        }
        assertTrue(f.captureQueueDiagnostics().isEmpty()); f.prepareInput(0x80);
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertEquals(0, f.environment().controls().rawHeld()); assertFalse(f.paused()); assertFalse(f.closed());
        assertNotNull(f.captureShared());
    }

    @ParameterizedTest @EnumSource(value = PlcLifecyclePhase.class, names = {"ORDINARY_LEVEL", "NORMAL_PAUSE", "LAG"})
    void everyAdmittedServiceRequiresOnePreparedSample(PlcLifecyclePhase phase) throws IOException {
        var f = new Fixture().runtime(); var before = f.captureShared();
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(phase));
        assertEquals(before, f.captureShared()); assertFalse(f.closed());
    }

    @Test void usaDeadRoutineSkipsStartBeforeUnsupportedBodyPredicate() throws IOException {
        var f = new Fixture().runtime(); Actor a = new Actor(f); a.routine = 6; a.install(f);
        assertFalse(f.prepareInput(0x80).nextPaused()); f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertFalse(f.paused()); assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(1)).boundary());
        assertEquals(6, f.state().motion().routine());
    }

    @ParameterizedTest @CsvSource({"USA,16,true", "JAPAN,16,false", "EUROPE,16,false", "JAPAN,112,true", "EUROPE,112,true"})
    void pausedResetIsRejectedAtItsActualRegionalActionTapPredicate(CdReleaseVariant variant, int action, boolean reset) throws IOException {
        Fixture fixture = new Fixture(); fixture.variant = variant; var f = fixture.runtime(); service(f, 0x80);
        f.prepareInput(action);
        if (reset) {
            assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.PAUSED_RESET,
                    assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,
                            () -> f.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE)).boundary());
            assertTrue(f.closed()); assertEquals(action, f.environment().controls().rawHeld());
        } else { f.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE); assertTrue(f.paused()); assertFalse(f.closed()); }
    }

    @Test void sourceFamilyUnsupportedServiceDoesNotInventPpLagBehavior() throws IOException {
        var f = new Fixture().runtime(); Env e = new Env(f); e.zoneAct = 0x200; e.install(f);
        f.prepareInput(8); assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,
                assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, () -> f.serviceVBlank(PlcLifecyclePhase.LAG)).boundary());
        assertEquals(0, f.environment().controls().rawHeld()); assertFalse(f.closed()); assertNotNull(f.captureShared());
    }

    @Test void repeatedDisplayProjectionReadsDoNoSourceWork() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        var actor = f.captureActor(); var shared = f.captureShared();
        for (int i = 0; i < 20; i++) {
            assertSame(actor.state(), f.state()); assertEquals(actor.display(), f.display());
            assertSame(shared.environment(), f.environment()); assertSame(shared.artState(), f.artState());
            assertFalse(f.paused()); assertFalse(f.closed());
        }
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared()); assertTrue(fixture.dmas.isEmpty());
    }

    @Test void capturesAreImmutableAndRestoreIsExactPartitionedAndDoesNotReinitializeOrPublishArt() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x31), fill(0x300, 0x73), 1, true));
        var actor = f.captureActor(); var shared = f.captureShared(); byte[] history = actor.state().history().bytes().copy();
        byte[] resident = shared.artState().residentBank().copy(); byte[] cpu = shared.artState().cpuBuffer().copy();
        Arrays.fill(history, (byte) 0xEE); Arrays.fill(resident, (byte) 0xEE); Arrays.fill(cpu, (byte) 0xEE);
        assertNotEquals(0xEE, actor.state().history().bytes().unsignedByte(0));
        assertEquals(0x31, shared.artState().cpuBuffer().unsignedByte(0)); assertEquals(0x73, shared.artState().residentBank().unsignedByte(0));
        service(f, 8); f.runBody(3); int uploads = fixture.dmas.size(); var changedEnvironment = f.environment();
        f.restoreActor(actor); assertEquals(changedEnvironment, f.environment(), "Actor restore never captures shared globals twice");
        assertEquals(actor.state(), f.state()); assertEquals(actor.display(), f.display());
        f.prepareInput(0x80); f.restoreShared(shared);
        assertEquals(actor.state(), f.state()); assertEquals(shared, f.captureShared());
        assertEquals(uploads, fixture.dmas.size()); assertEquals(2, f.state().motion().routine());
        assertEquals(1, f.state().animation().mapFrame()); assertTrue(f.artState().dirty());
        service(f, 8); var first = f.runBody(3); var firstActor = f.captureActor(); var firstShared = f.captureShared();
        f.restoreActor(actor); f.restoreShared(shared); service(f, 8); var second = f.runBody(3);
        assertEquals(first, second); assertEquals(firstActor, f.captureActor()); assertSharedValues(firstShared, f.captureShared());
    }

    @Test void sameSourceDifferentRuntimeSnapshotsRejectBeforeAnyMutationIncludingPendingSample() throws IOException {
        Fixture fixture = new Fixture(); var assets = fixture.assets();
        var a = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets, (dma, state) -> { });
        var b = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets, (dma, state) -> { });
        var actor = a.captureActor(); var shared = a.captureShared(); var before = b.state();
        b.prepareInput(8); assertThrows(IllegalArgumentException.class, () -> b.restoreActor(actor));
        assertThrows(IllegalArgumentException.class, () -> b.restoreShared(shared)); assertSame(before, b.state());
        assertThrows(IllegalStateException.class, b::captureShared, "Foreign rejection preserves target pending sample");
        b.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL); b.runBody(1); assertEquals(8, b.environment().controls().rawHeld());
    }

    @Test void restoreClearsUnconsumedEntitlementAndPendingWithoutColdInitialization() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1); var a = f.captureActor(); var s = f.captureShared();
        service(f, 8); assertThrows(IllegalStateException.class, f::captureActor); assertThrows(IllegalStateException.class, f::captureShared);
        assertThrows(IllegalStateException.class, () -> f.prepareInput(0)); f.restoreActor(a);
        assertThrows(IllegalStateException.class, () -> f.runBody(2)); f.prepareInput(0x80); f.restoreShared(s);
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertEquals(2, f.state().motion().routine());
    }

    @Test void scrollFeedbackChangesOnlyExistingSharedBoundsAndCameraY() throws IOException {
        var f = new Fixture().runtime(); var actor = f.captureActor(); var old = f.environment(); var art = f.artState();
        var bounds = new CdPlayerMovement.Bounds(8, 0x2600, 0x720, 0);
        f.applyScrollFeedback(bounds, 120); var next = f.environment();
        assertEquals(bounds, next.movement().bounds()); assertEquals(120, next.movement().globals().cameraYCentre());
        assertEquals(old.movement().physics(), next.movement().physics()); assertEquals(old.movement().gates(), next.movement().gates());
        assertEquals(old.controls(), next.controls()); assertEquals(old.globals(), next.globals());
        assertEquals(actor, f.captureActor()); assertSame(art, f.artState());
        f.prepareInput(8); assertThrows(IllegalStateException.class, () -> f.applyScrollFeedback(bounds, 96));
        f.clearPendingInput(); service(f, 0); assertThrows(IllegalStateException.class, () -> f.applyScrollFeedback(bounds, 96));
        f.runBody(1); assertEquals(bounds, f.environment().movement().bounds());
    }

    @Test void nextBodyLevelBoundReadsScrollFeedbackAndFailedFeedbackCannotHalfReplaceBounds() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1); var original = f.environment();
        var bounds = new CdPlayerMovement.Bounds(0, 0x2897, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> f.applyScrollFeedback(bounds, 65536));
        assertSame(original, f.environment()); f.applyScrollFeedback(bounds, 96);
        service(f, 0);
        var p = f.runBody(2);
        // MdGround's LevelBound reads the fed-back bottom: signed 0+224 < 1899 calls KillPlayer at Y $076B
        // (Sonic/Main.asm:1697-1705). Its RTS resumes the ground tail: ObjMove -7, then GroundCol's -8 (702-707).
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, p.result().boundary());
        assertEquals(CdPlayerMovement.Boundary.MODE_RETURN, p.result().modeBoundary().orElseThrow());
        var s = f.state();
        assertEquals(6, s.motion().routine()); assertEquals(1884 << 16, s.motion().centreY16_16());
        assertEquals(0x07, s.motion().stick()); assertEquals(0x6B, s.rotationDistance());
        assertEquals(0x8780, s.header().tile()); assertEquals(0, s.header().priority());
        // The standing prefix's CheckCharge stop request, then KillPlayer's FM_HURT at its call position.
        assertEquals(List.of(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_CHARGESTOP),
                new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_HURT)), p.sounds());
        assertEquals(0, displayCut(p).priority(), "Main's Display follows the MODE kill");
        assertEquals(bounds, f.environment().movement().bounds());
        service(f, 0); var state = f.state(); var environment = f.environment();
        assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(3)).boundary(),
                "without a LevelContext the next ObjSonic_Dead keeps its typed stop");
        assertSame(state, f.state()); assertSame(environment, f.environment());
    }

    // U5-B1/B2 routines 6 and 8 through the real Frame/Body path. Written before production.
    // Source pin 1ecc5f3a: Sonic/Main.asm:2397-2495. Root owns the world commit of DeadCheckGone/RestartCut.

    @Test void deadAndRestartThroughTheFrameAdoptTheResetWordOnceAndReplayFromActorCaptures() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        Actor dead = new Actor(f); dead.routine = 6; dead.flags = 2; dead.y = 0x0811 << 16; dead.yv = -0x700;
        dead.animation = 0x18; dead.install(f);
        var context = Optional.of(new CdPlayerBody.LevelContext(new CdObjectPool().capture(),
                new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0), new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0, 0)));
        service(f, 0); var gone = f.runBody(2, Optional.empty(), context);
        assertEquals(CdPlayerBody.Boundary.DEAD_RETURN, gone.result().boundary());
        assertTrue(gone.sounds().isEmpty());
        assertEquals(8, f.state().motion().routine()); assertEquals(0, f.state().motion().yVelocity());
        assertEquals(0x0810C800, f.state().motion().centreY16_16());
        assertEquals(0x3C, f.state().timers().reset(), "8*60 then 60: only the final word is committed");
        assertTrue(f.display().orElseThrow().queued(), "JMP DrawObject is unconditional");
        var summary = gone.result().effects().stream().filter(CdPlayerBody.DeadCheckGone.class::isInstance)
                .map(CdPlayerBody.DeadCheckGone.class::cast).toList();
        assertEquals(1, summary.size()); assertEquals(2, summary.getFirst().lives());
        assertEquals(Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(32))), summary.getFirst().allocation());
        var savedActor = f.captureActor(); var savedShared = f.captureShared();
        service(f, 0); var first = f.runBody(3, Optional.empty(), context);
        assertEquals(CdPlayerBody.Boundary.RESTART_RETURN, first.result().boundary());
        assertEquals(0x3B, f.state().timers().reset()); assertTrue(f.display().isEmpty(), "ObjSonic_Restart has no DrawObject");
        assertTrue(first.sounds().isEmpty());
        var afterFirst = f.captureActor();
        f.restoreActor(savedActor); f.restoreShared(savedShared);
        service(f, 0); var replay = f.runBody(3, Optional.empty(), context);
        assertEquals(first, replay); assertEquals(afterFirst, f.captureActor());
        for (int expected = 0x3A; expected >= 1; expected--) {
            service(f, 0); var p = f.runBody(4, Optional.empty(), context);
            assertEquals(expected, f.state().timers().reset()); assertTrue(p.sounds().isEmpty());
        }
        service(f, 0); var cut = f.runBody(5, Optional.empty(), context);
        // StopZ80 / MOVE.B #1,Z80RAM+$1C3E / StartZ80 (2461-2463), then BRA.W SubCPUCmd with SCMD_FADECDA (2475,2491).
        assertEquals(List.of(new CdPlayerFrameRuntime.DriverRamWrite(0x1C3E, 1),
                new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA)), cut.sounds());
        assertEquals(List.of(new CdPlayerBody.RestartCut(new CdLevelGlobals(1, 0, 0, 0, 1, 0, 0, 0, 0), true)),
                cut.result().effects().stream().filter(CdPlayerBody.RestartCut.class::isInstance).toList());
        assertEquals(0, f.state().timers().reset());
        service(f, 0); var state = f.state(); var environment = f.environment();
        assertEquals(CdPlayerBody.UnsupportedBoundary.RESTART_ROUTINE,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(6)).boundary(),
                "the previous ephemeral LevelContext is never retained");
        assertSame(state, f.state()); assertSame(environment, f.environment());
    }

    @Test void driverRamWriteIsATypedRelativeZ80OffsetWithoutPlayback() {
        var write = new CdPlayerFrameRuntime.DriverRamWrite(0x1C3E, 1);
        assertEquals(0x1C3E, write.address()); assertEquals(1, write.value());
        // Z80RAM is $2000 bytes from $A00000 (Main CPU.inc:31-33); the 68000 bus address is not this offset.
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerFrameRuntime.DriverRamWrite(0xA01C3E, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerFrameRuntime.DriverRamWrite(0x2000, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerFrameRuntime.DriverRamWrite(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerFrameRuntime.DriverRamWrite(0x1C3E, 256));
    }

    @Test void frameCapturesAndPublicationsNeverDeclareALevelContextOrRuntimeLevelValues() {
        for (var type : List.of(CdPlayerFrameRuntime.ActorState.class, CdPlayerFrameRuntime.SharedState.class,
                CdPlayerFrameRuntime.BodyPublication.class)) {
            for (var component : type.getRecordComponents()) {
                String name = component.getGenericType().getTypeName();
                for (String forbidden : List.of("LevelContext", "CdLevelGlobals", "CdRingGlobals", "Snapshot")) {
                    assertFalse(name.contains(forbidden), type.getSimpleName() + "." + component.getName() + " " + name);
                }
            }
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 0x34, 0x7F, 0x80, 0xFF})
    void queuePublicationChangesOnlySpriteBitSevenAndMatchingDisplayProjection(int flags) throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        Actor a = new Actor(f); a.spriteFlags = flags; a.install(f);
        var actor = f.captureActor(); var shared = f.captureShared();
        f.publishDrawObjectOnScreen(true); var set = f.state();
        assertEquals(flags | 0x80, set.motion().spriteFlags()); assertEquals(flags | 0x80, f.display().orElseThrow().spriteFlags());
        f.publishDrawObjectOnScreen(false); var clear = f.state(); var m = clear.motion(); var original = actor.state().motion();
        assertEquals(flags & 0x7F, m.spriteFlags()); assertEquals(flags & 0x7F, f.display().orElseThrow().spriteFlags());
        assertEquals(original, new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), original.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle()));
        assertSame(actor.state().animation(), clear.animation()); assertSame(actor.state().header(), clear.header());
        assertSame(actor.state().timers(), clear.timers()); assertSame(actor.state().history(), clear.history());
        var d = f.display().orElseThrow(); var originalDisplay = actor.display().orElseThrow();
        assertEquals(originalDisplay, new CdPlayerBody.DisplayPublication(d.queued(), d.mappingAddress(), d.tile(), d.priority(),
                d.width(), d.mapFrame(), originalDisplay.spriteFlags(), d.centreX16_16(), d.centreY16_16()));
        assertEquals(shared, f.captureShared()); assertTrue(fixture.dmas.isEmpty());
        var off = f.captureActor(); f.publishDrawObjectOnScreen(true); f.restoreActor(off);
        assertEquals(off, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void queuePublicationRequiresClosedFrameAndItsBitIsCapturedWithoutSourceWork() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        var before = f.state(); f.prepareInput(8);
        assertThrows(IllegalStateException.class, () -> f.publishDrawObjectOnScreen(false)); assertSame(before, f.state());
        f.clearPendingInput(); service(f, 0);
        assertThrows(IllegalStateException.class, () -> f.publishDrawObjectOnScreen(true));
        assertThrows(IllegalStateException.class, f::captureActor); f.runBody(3);
        var shared = f.captureShared(); f.publishDrawObjectOnScreen(true); var on = f.captureActor();
        assertEquals(0x80, on.state().motion().spriteFlags() & 0x80); assertEquals(shared, f.captureShared());
        f.publishDrawObjectOnScreen(false); assertEquals(0, f.state().motion().spriteFlags() & 0x80);
        f.restoreActor(on); assertEquals(on, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void queuePublicationRejectsClosedFailedArtAdmissionWithoutMutation() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime();
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x22), new byte[0x300], 0, true));
        fixture.failPublication = true; f.prepareInput(8);
        assertThrows(PublicationFailure.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        var actor = f.state(); var environment = f.environment(); var display = f.display(); var art = f.artState();
        assertThrows(IllegalStateException.class, () -> f.publishDrawObjectOnScreen(true));
        assertThrows(IllegalStateException.class, () -> f.publishDrawObjectOnScreen(false));
        assertSame(actor, f.state()); assertSame(environment, f.environment()); assertEquals(display, f.display()); assertSame(art, f.artState());
    }

    @Test void clockRestorePreservesUnknownSeedAndWrapsOnlyKnownDeltaOrAdmittedAbsoluteSeed() throws IOException {
        var f = new Fixture().runtime(); var s = f.captureShared();
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), s.environment(), s.artState(), false,
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 0xFFFFFFFFL)));
        f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(0, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertTrue(f.captureShared().vIntClock().absoluteValue().isEmpty());
        s = f.captureShared(); f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), s.environment(), s.artState(), false,
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFFFFFFFFL), 0)));
        f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(OptionalLong.of(0), f.captureShared().vIntClock().absoluteValue());
    }

    @Test void transitionCancellationDropsPreparedOnlyAndRetainsActuallyReadLatch() throws IOException {
        var f = new Fixture().runtime(); service(f, 8); f.clearPendingInput();
        f.prepareInput(4); f.clearPendingInput(); assertEquals(8, f.environment().controls().rawHeld());
        service(f, 8); assertEquals(0, f.environment().controls().rawTap()); f.clearPendingInput();
        f.close(); assertTrue(f.closed()); assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 256, 65536})
    void inputRequiresAnActualUnsignedByte(int held) throws IOException {
        var f = new Fixture().runtime(); assertThrows(IllegalArgumentException.class, () -> f.prepareInput(held));
        assertNotNull(f.captureActor());
    }

    @Test void externalTouchFactoryRequiresArtBindingAndNeverDeclaresEmptyDynamicRam() throws IOException {
        var assets = new Fixture().assets();
        var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets);
        assertSame(assets, f.assets());
        assertEquals(CdPlayerBody.DynamicSlots.UNIMPLEMENTED, f.environment().dynamicSlots());
        var before = f.captureShared();
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertSharedValues(before, f.captureShared());
        f.bindArtPublication((dma, complete) -> { });
        service(f, 0);
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary());
        service(f, 0);
        var state = f.state(); var environment = f.environment();
        assertEquals(CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(2)).boundary());
        assertEquals(state, f.state()); assertEquals(environment, f.environment());
        assertThrows(IllegalStateException.class, () -> f.runBody(2));
    }

    @Test void frameStagesDetachedTouchAndReplaysOnlyWhenTheCallerResuppliesItsSnapshot() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0x47);
        var selected = pool.state(32); var touch = frameTouchContext(pool);
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared();
        service(f, 0);
        var result = f.runBody(2, Optional.of(touch));
        var item = frameTouchResult(result);
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, item.kind());
        assertSame(selected, item.selectedSlot().orElseThrow());
        assertEquals(4, item.changedSlot().orElseThrow().routine());
        assertSame(selected, pool.state(32)); assertEquals(2, pool.state(32).routine());
        var afterActor = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        service(f, 0);
        var replay = f.runBody(2, Optional.of(touch));
        assertEquals(afterActor, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        assertEquals(result.sounds(), replay.sounds());
        assertArrayEquals(item.changedSlot().orElseThrow().copyBytes(),
                frameTouchResult(replay).changedSlot().orElseThrow().copyBytes());
        service(f, 0);
        assertEquals(CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(3)).boundary(),
                "The previous ephemeral context must not be retained");
    }

    @Test void framePublishesNothingForAnUnsupportedSelectedHandlerAndConsumesOnlyThatSlotTurn() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 7);
        var selected = pool.state(32); var context = frameTouchContext(pool);
        service(f, 0);
        var state = f.state(); var environment = f.environment(); var display = f.display(); var art = f.artState();
        var failure = assertThrows(CdPlayerBody.UnsupportedTouchHandler.class,
                () -> f.runBody(2, Optional.of(context)));
        assertEquals(CdPlayerObjectTouch.Kind.ENEMY, failure.kind());
        assertSame(selected, failure.selectedSlot());
        assertEquals(state, f.state()); assertEquals(environment, f.environment()); assertEquals(display, f.display());
        assertSame(art, f.artState()); assertSame(selected, pool.state(32));
        assertThrows(IllegalStateException.class, () -> f.runBody(2, Optional.of(context)));
        // The world/actor closes a failed serviced cycle; the pure Frame does not pretend to roll V-int back.
        f.close(); assertTrue(f.closed());
    }

    @Test void externalTouchOverloadKeepsTheExistingUnservicedAndPausedSlotAdmission() throws IOException {
        var f = externalTouchRuntime(); var context = frameTouchContext(new CdObjectPool());
        assertThrows(IllegalStateException.class, () -> f.runBody(2, Optional.of(context)));
        service(f, 0x80); assertTrue(f.paused());
        var before = f.captureActor();
        assertThrows(IllegalStateException.class, () -> f.runBody(2, Optional.of(context)));
        assertEquals(before, f.captureActor());
        service(f, 0); service(f, 0x80); assertFalse(f.paused());
        var result = f.runBody(2, Optional.of(context));
        assertEquals(CdPlayerObjectTouch.Kind.NONE, frameTouchResult(result).kind());
    }

    // Pure U1 response through the real Frame/Body path. Written before the composition.
    // Source pin 1ecc5f3a: Sonic/Main.asm:340-351, PP Player Object Collision.asm:130-298, HUD and Points/Main.asm:157-180.

    @Test void frameJumpKillCommitsTheAdoptedActorOnceAndUnwrapsContactRequestsAtTheTouchPosition() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0x2B); var selected = pool.state(32);
        var context = frameResponseTouch(pool, 5, 4990);
        service(f, 0x10); // CheckJump: roll bit, oAnim 2 and yv -$680 before Player_TouchEnemy.
        var p = f.runBody(2, Optional.of(context));
        assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP),
                new CdPlayerFrameRuntime.ContactSubCpuCommand(CdPlayerTouchResponse.SubCpuCommand.SCMD_YESSFX),
                new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_DESTROY)), p.sounds());
        var response = frameResponse(p);
        assertEquals(List.of(new CdPlayerTouchResponse.AddPoints(10, true),
                new CdPlayerTouchResponse.SubCpu(CdPlayerTouchResponse.SubCpuCommand.SCMD_YESSFX),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_DESTROY)), response.calls());
        assertEquals(List.of(selected.moveByte(CdObjectState.ByteField.FLAGS, 0x80)
                .moveWord(CdObjectState.WordField.EXPLOSION_POINTS_FRAME, 0).moveByte(CdObjectState.ByteField.ID, 0x18)
                .moveByte(CdObjectState.ByteField.ROUTINE, 0).moveByte(CdObjectState.ByteField.SUBTYPE, 1)),
                response.changedSlots());
        assertEquals(new CdPlayerTouchResponse.WorldGlobals(5, 4, 1, 5000, 10000, 1), response.world());
        assertEquals(response.actor().motion(), f.state().motion());
        assertEquals(-0x580, f.state().motion().yVelocity()); // .BounceDown: -$680 + $100.
        assertEquals(2, f.environment().movement().globals().scoreChain());
        assertSame(p.result().state(), f.state()); assertSame(p.result().environment(), f.environment());
        assertSame(selected, pool.state(32)); assertEquals(2, pool.state(32).routine());
    }

    @Test void frameRestoreReplaysIdenticalPureImagesAndSoundsWithoutCapturingTheCallCut() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0x2B); var context = frameResponseTouch(pool, 5, 0);
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared();
        service(f, 0x10); var first = f.runBody(2, Optional.of(context));
        var afterActor = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        service(f, 0x10); var replay = f.runBody(2, Optional.of(context));
        assertEquals(first, replay); assertEquals(frameResponse(first), frameResponse(replay));
        assertEquals(afterActor, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        for (var type : List.of(CdPlayerFrameRuntime.ActorState.class, CdPlayerFrameRuntime.SharedState.class)) {
            for (var component : type.getRecordComponents()) {
                String name = component.getGenericType().getTypeName();
                for (String forbidden : List.of("TouchContext", "ResponseContext", "ContactGlobals", "ObjectContext", "Snapshot",
                        "CdPlayerTouchResponse")) {
                    assertFalse(name.contains(forbidden), type.getSimpleName() + "." + component.getName() + " " + name);
                }
            }
        }
    }

    @ParameterizedTest @CsvSource({"5,4", "0,6"})
    void frameCommitsAHazardHurtOrKillOnceAndTheNextBodyCallRunsHurtOrStopsAtTheUnownedDeadRoutine(int rings, int routine)
            throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0xAB);
        service(f, 0x10);
        var p = f.runBody(2, Optional.of(frameResponseTouch(pool, rings, 0)));
        var s = f.state(); var response = frameResponse(p);
        assertEquals(routine, s.motion().routine());
        assertEquals(0x076B0000, s.motion().centreY16_16()); // CheckJump's +5, then ResetOnFloor's SUBQ.W #5.
        if (rings != 0) {
            assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP),
                    new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)), p.sounds());
            assertEquals(List.of(pool.state(33).moveByte(CdObjectState.ByteField.ID, 0x11)
                    .moveWord(CdObjectState.WordField.X, 64).moveWord(CdObjectState.WordField.Y, 0x770)),
                    response.changedSlots()); // The ring takes the pre-reset oY word.
            assertEquals(0x200, s.motion().xVelocity()); // Equal X: CMP.W/BCS falls through to NEG.W (:258-261).
            assertEquals(0x78, s.timers().hurt());
        } else {
            assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP),
                    new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),
                    new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_HURT)), p.sounds());
            assertEquals(0x07, s.motion().stick()); assertEquals(0x6B, s.rotationDistance());
            assertEquals(0x8780, s.header().tile()); assertEquals(0, s.header().priority());
            assertEquals(0x8780, f.display().orElseThrow().tile()); assertEquals(0, f.display().orElseThrow().priority());
            assertEquals(2, displayCut(p).priority(), "The Display cut precedes the touch");
        }
        service(f, 0); var state = f.state(); var environment = f.environment();
        if (rings != 0) {
            var hurt = f.runBody(3); // ObjSonic_Hurt (Sonic/Main.asm:2351-2366) is now the owned next routine.
            assertEquals(CdPlayerBody.Boundary.HURT_RETURN, hurt.result().boundary());
            assertSame(hurt.result().state(), f.state());
        } else {
            assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE,
                    assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(3)).boundary());
            assertSame(state, f.state()); assertSame(environment, f.environment());
        }
    }

    // Routine-4 ObjSonic_Hurt through the real Frame/Body path. Written before production.
    // Source pin 1ecc5f3a: Sonic/Main.asm:1673-1714,2351-2391; PP Player Object Collision.asm:279-298.

    @Test void frameHurtCallCommitsOnceThenRestoresAndReplaysForwardWithoutATouchContext() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0xAB);
        service(f, 0x10); f.runBody(2, Optional.of(frameResponseTouch(pool, 5, 0)));
        assertEquals(4, f.state().motion().routine());
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared();
        service(f, 0);
        // UNIMPLEMENTED dynamic RAM and no context: Hurt makes no Player_ObjCollide call to reject.
        var p = f.runBody(3);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, p.result().boundary()); assertTrue(p.sounds().isEmpty());
        assertSame(p.result().state(), f.state()); assertSame(p.result().environment(), f.environment());
        var m = f.state().motion();
        // ObjMove's old words: X 64+2, Y 1899-4; then ADDI.W #$30. Nothing is probed in the empty row-6 band.
        assertEquals(66 << 16, m.centreX16_16()); assertEquals(1895 << 16, m.centreY16_16());
        assertEquals(0x200, m.xVelocity()); assertEquals(-0x3D0, m.yVelocity()); assertEquals(4, m.routine());
        assertEquals(0x78, f.state().timers().hurt()); assertEquals(8, f.state().history().index());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 66, 1895, 19, 4, 24, 2), displayCut(p));
        assertEquals(p.result().display(), f.display()); assertTrue(f.display().orElseThrow().queued());
        var afterActor = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        service(f, 0); var replay = f.runBody(3);
        assertEquals(p, replay); assertEquals(afterActor, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        service(f, 0); var forward = f.runBody(4);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, forward.result().boundary());
        assertEquals(68 << 16, f.state().motion().centreX16_16());
        assertEquals((1891 << 16) | 0x3000, f.state().motion().centreY16_16());
        assertEquals(-0x3A0, f.state().motion().yVelocity()); assertEquals(12, f.state().history().index());
        assertEquals(0x78, f.state().timers().hurt(), "the airborne hurt word stays fixed");
    }

    @Test void frameHurtPitUnwrapsBothSharedKillPlayerRequestsInCallOrderAndTheNextCallStopsAtDead() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        Actor a = new Actor(f); a.routine = 4; a.flags = 2; a.y = 2040 << 16; a.install(f);
        service(f, 0); var p = f.runBody(2);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, p.result().boundary());
        // HurtChkLand's unsigned and LevelBound's signed bottom tests both call KillPlayer (:2376, :1705).
        assertEquals(List.of(new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_HURT),
                new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_HURT)), p.sounds());
        var s = f.state();
        assertEquals(6, s.motion().routine()); assertEquals(0x07, s.motion().stick()); assertEquals(0xF8, s.rotationDistance());
        assertEquals(0x8780, s.header().tile()); assertEquals(0, s.header().priority());
        assertEquals(0, displayCut(p).priority(), "Hurt's DrawObject follows both kills");
        assertTrue(f.display().orElseThrow().queued());
        service(f, 0); var state = f.state(); var environment = f.environment();
        assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(3)).boundary());
        assertSame(state, f.state()); assertSame(environment, f.environment());
    }

    @Test void frameHurtLandingResetRequestIsAMovementSoundAtTheAirCollisionPosition() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        // Ingress bit 2 lets Player_ResetOnFloor request FM_CHARGESTOP; Y1905 sensors meet the row-7 band at -2.
        Actor a = new Actor(f); a.routine = 4; a.flags = 6; a.y = 1886 << 16; a.install(f);
        service(f, 0); var p = f.runBody(2);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, p.result().boundary());
        assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_CHARGESTOP)), p.sounds());
        var m = f.state().motion();
        assertEquals(2, m.routine()); assertEquals(0, m.flags()); assertEquals(1879 << 16, m.centreY16_16());
        assertEquals(120, f.state().timers().hurt());
    }

    @Test void frameResponseBoundaryInsideBodyCommitsNoStateEnvironmentDisplayOrSoundsAndConsumesTheSlot() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0x2B);
        Env e = new Env(f); e.scoreChain = 3; e.install(f); // Odd clamped chain: odd-address EnemyPoints word read (:166).
        service(f, 0x10);
        var state = f.state(); var environment = f.environment(); var display = f.display();
        var context = frameResponseTouch(pool, 5, 0);
        assertEquals(CdPlayerTouchResponse.UnsupportedBoundary.ODD_ENEMY_POINTS_ADDRESS,
                assertThrows(CdPlayerTouchResponse.UnsupportedResponse.class,
                        () -> f.runBody(2, Optional.of(context))).boundary());
        assertSame(state, f.state()); assertSame(environment, f.environment()); assertEquals(display, f.display());
        assertThrows(IllegalStateException.class, () -> f.runBody(2, Optional.of(context)));
        assertFalse(f.closed());
    }


    // Per-call support arguments do not add an actor/global/capture owner.
    @ParameterizedTest @CsvSource({"16,-1608,32210944", "0,-968,32374784"})
    void frameSupportedFloatActionAndReleaseReplayFromTheSameActorAndSharedPartitions(int held, int velocity, int y)
            throws IOException {
        var f = externalTouchRuntime(); Actor actor = new Actor(f); actor.flags = 8; actor.y = 0x01ED0000;
        actor.objectLinks = new CdPlayerBody.ObjectLinks(127, 32); actor.install(f);
        var pool = frameSupportPool(f, 0x12, 0); var snapshot = pool.capture();
        var objects = frameSupportContext(f, snapshot); var touch = frameSupportTouch(snapshot, false);
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared();
        service(f, 0x10); var first = f.runBody(2, Optional.of(touch), Optional.empty(), objects);
        assertEquals(-0x680, f.state().motion().yVelocity()); assertEquals(0x01F20000, f.state().motion().centreY16_16());
        assertEquals(new CdPlayerBody.ObjectLinks(127, 32), f.state().objectLinks());
        assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP)), first.sounds());
        service(f, held); f.runBody(3, Optional.of(touch)); // no support context at the air cut
        assertEquals(velocity, f.state().motion().yVelocity()); assertEquals(y, f.state().motion().centreY16_16());
        var afterActor = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        service(f, 0x10); var replay = f.runBody(2, Optional.of(touch), Optional.empty(), objects);
        assertEquals(first.sounds(), replay.sounds());
        service(f, held); f.runBody(3, Optional.of(touch));
        assertEquals(afterActor, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test void frameFlipperSameSlotEnemyPreservesAnimationAndForwardsSpringPatchJumpExactlyOnce() throws IOException {
        var f = externalTouchRuntime(); Actor actor = new Actor(f); actor.flags = 8; actor.y = 0x01ED0000;
        actor.objectLinks = new CdPlayerBody.ObjectLinks(0, 32); actor.install(f);
        var pool = frameSupportPool(f, 0x1E, 0x2F); var snapshot = pool.capture(); var before = pool.state(32);
        var objects = frameSupportContext(f, snapshot); var touch = frameSupportTouch(snapshot, true);
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared();
        service(f, 0x10); var p = f.runBody(2, Optional.of(touch), Optional.empty(), objects);
        assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_SPRING),
                new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP),
                new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_DESTROY)), p.sounds());
        var mode = p.result().effects().stream().filter(CdPlayerBody.ModeEffect.class::isInstance)
                .map(CdPlayerBody.ModeEffect.class::cast).map(CdPlayerBody.ModeEffect::effect).toList();
        assertEquals(3, mode.size());
        assertEquals(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_SPRING), mode.get(0));
        var patch = assertInstanceOf(CdPlayerMovement.ObjectPatch.class, mode.get(1)).patch();
        assertEquals(new CdPlayerMovement.SoundRequest(CdPlayerMovement.Sound.FM_JUMP), mode.get(2));
        assertEquals(before, patch.before()); assertEquals(1, patch.after().unsignedByte(CdObjectState.ByteField.ANIMATION));
        var response = frameResponse(p); assertEquals(CdPlayerTouchResponse.Exit.KILL_ENEMY, response.exit());
        assertEquals(1, response.changedSlots().size()); assertEquals(0x18, response.changedSlots().getFirst().id());
        assertEquals(1, response.changedSlots().getFirst().unsignedByte(CdObjectState.ByteField.ANIMATION));
        assertSame(before, pool.state(32)); assertEquals(0, before.unsignedByte(CdObjectState.ByteField.ANIMATION));
        var afterActor = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        service(f, 0x10); var replay = f.runBody(2, Optional.of(touch), Optional.empty(), objects);
        assertEquals(p, replay); assertEquals(afterActor, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test void frameRejectsADifferentFlipperTouchCaptureWithoutPublishingAndConsumesTheSlot() throws IOException {
        var f = externalTouchRuntime(); Actor actor = new Actor(f); actor.flags = 8; actor.y = 0x01ED0000;
        actor.objectLinks = new CdPlayerBody.ObjectLinks(0, 32); actor.install(f);
        var pool = frameSupportPool(f, 0x1E, 0x2F); var original = pool.capture(); var other = pool.capture();
        var objects = frameSupportContext(f, original); var touch = frameSupportTouch(other, true);
        assertNotSame(original, other); assertSame(original.slots().getFirst(), other.slots().getFirst());
        service(f, 0x10); var state = f.state(); var env = f.environment(); var display = f.display(); var art = f.artState();
        assertThrows(IllegalArgumentException.class, () -> f.runBody(2, Optional.of(touch), Optional.empty(), objects));
        assertSame(state, f.state()); assertSame(env, f.environment()); assertEquals(display, f.display()); assertSame(art, f.artState());
        assertEquals(original.slots(), pool.capture().slots());
        assertThrows(IllegalStateException.class, () -> f.runBody(2, Optional.of(touch), Optional.empty(), objects));
    }

    @Test void frameDoesNotRetainSupportAndItsCaptureShapesDoNotAcquireBorrowedInputs() throws IOException {
        var f = externalTouchRuntime(); Actor actor = new Actor(f); actor.flags = 8; actor.y = 0x01ED0000;
        actor.objectLinks = new CdPlayerBody.ObjectLinks(255, 32); actor.install(f);
        var pool = frameSupportPool(f, 0x12, 0); var snapshot = pool.capture(); var objects = frameSupportContext(f, snapshot);
        var touch = frameSupportTouch(snapshot, false);
        service(f, 0); f.runBody(2, Optional.of(touch), Optional.empty(), objects);
        service(f, 0); var state = f.state(); var env = f.environment();
        assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT,
                assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.runBody(3, Optional.of(touch))).boundary());
        assertSame(state, f.state()); assertSame(env, f.environment());
        for (var type : List.of(CdPlayerFrameRuntime.ActorState.class, CdPlayerFrameRuntime.SharedState.class,
                CdPlayerFrameRuntime.BodyPublication.class)) {
            for (var component : type.getRecordComponents()) {
                assertFalse(component.getGenericType().getTypeName().contains("ObjectContext"));
                assertFalse(component.getGenericType().getTypeName().contains("Snapshot"));
            }
        }
        for (var field : CdPlayerFrameRuntime.class.getDeclaredFields()) {
            assertFalse(field.getGenericType().getTypeName().contains("ObjectContext"));
            assertNotEquals(CdObjectPool.Snapshot.class, field.getType());
        }
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    @Test void frameHurtBorrowsSupportWithoutAnyTouchInputAndReplaysItsSingleActorStore() throws IOException {
        var f = externalTouchRuntime(); Actor actor = new Actor(f); actor.routine = 4; actor.flags = 10;
        actor.y = 0x01ED0000; actor.yv = -0x200; actor.animation = 0x1A;
        actor.objectLinks = new CdPlayerBody.ObjectLinks(127, 32); actor.install(f);
        var pool = frameSupportPool(f, 0x12, 0); var snapshot = pool.capture(); var objects = frameSupportContext(f, snapshot);
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared();
        service(f, 0); var p = f.runBody(2, Optional.empty(), Optional.empty(), objects);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, p.result().boundary()); assertTrue(p.sounds().isEmpty());
        assertEquals(0x01EB0000, f.state().motion().centreY16_16()); assertEquals(-0x1D0, f.state().motion().yVelocity());
        assertEquals(new CdPlayerBody.ObjectLinks(127, 32), f.state().objectLinks());
        var afterActor = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        service(f, 0); var replay = f.runBody(2, Optional.empty(), Optional.empty(), objects);
        assertEquals(p, replay); assertEquals(afterActor, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        assertEquals(snapshot.slots(), pool.capture().slots());
    }

    private static CdObjectPool frameSupportPool(CdPlayerFrameRuntime f, int id, int collisionType) {
        var pool = new CdObjectPool();
        pool.write(pool.state(32).moveByte(CdObjectState.ByteField.ID, id).moveByte(CdObjectState.ByteField.WIDTH, 12)
                .moveByte(CdObjectState.ByteField.ROUTINE, 2).moveByte(CdObjectState.ByteField.SPRITE_FLAGS, collisionType == 0 ? 0 : 0x80)
                .moveByte(CdObjectState.ByteField.COLLISION_TYPE, collisionType)
                .moveWord(CdObjectState.WordField.X, f.state().motion().centreX16_16() >>> 16)
                .moveWord(CdObjectState.WordField.Y, 0x20C));
        return pool;
    }
    private static Optional<CdPlayerMovement.ObjectContext> frameSupportContext(CdPlayerFrameRuntime f, CdObjectPool.Snapshot snapshot) {
        var s = f.state(); return Optional.of(new CdPlayerMovement.ObjectContext(snapshot, s.objectLinks().standObject(),
                s.header().slotId(), s.header().width()));
    }
    private static CdPlayerBody.TouchContext frameSupportTouch(CdObjectPool.Snapshot snapshot, boolean response) {
        var scanner = TestCdPlayerBody.touchScanner();
        if (!response) return new CdPlayerBody.TouchContext(scanner, snapshot);
        var owner = new CdPlayerTouchResponse(CdAssetBytes.copyOf(new byte[]{0, 10, 0, 20, 0, 50, 0, 100}));
        return new CdPlayerBody.TouchContext(scanner, snapshot, new CdPlayerBody.ResponseContext(owner,
                new CdPlayerBody.ContactGlobals(0, 0, 1), new CdPlayerTouchResponse.WorldGlobals(5, 3, 0, 0, 5000, 0x80)));
    }

    private static CdPlayerFrameRuntime externalTouchRuntime() throws IOException {
        var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(new Fixture().assets());
        f.bindArtPublication((dma, complete) -> { });
        service(f, 0); f.runBody(1);
        return f;
    }
    private static CdObjectPool frameTouchPool(CdPlayerFrameRuntime frame, int collisionType) {
        var pool = new CdObjectPool();
        pool.write(pool.state(32).moveByte(CdObjectState.ByteField.ID, 0x10)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x80)
                .moveByte(CdObjectState.ByteField.COLLISION_TYPE, collisionType)
                .moveByte(CdObjectState.ByteField.ROUTINE, 2)
                .moveWord(CdObjectState.WordField.X, frame.state().motion().centreX16_16() >>> 16)
                .moveWord(CdObjectState.WordField.Y, frame.state().motion().centreY16_16() >>> 16));
        return pool;
    }
    private static CdPlayerBody.TouchContext frameTouchContext(CdObjectPool pool) {
        var scanner = new CdPlayerObjectTouch(java.util.Collections.nCopies(63,
                new CdR11aObjectAssets.TouchSize(16, 16)));
        return new CdPlayerBody.TouchContext(scanner, pool.capture());
    }
    private static CdPlayerObjectTouch.Result frameTouchResult(CdPlayerFrameRuntime.BodyPublication publication) {
        return publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResult.class::isInstance)
                .map(CdPlayerBody.ObjectTouchResult.class::cast).map(CdPlayerBody.ObjectTouchResult::result)
                .findFirst().orElseThrow();
    }
    /** Runtime-shaped ephemeral owner: one response, contact (timeWarp, shield, combineRing) and world bytes. */
    private static CdPlayerBody.TouchContext frameResponseTouch(CdObjectPool pool, int rings, int score) {
        var scanner = new CdPlayerObjectTouch(java.util.Collections.nCopies(63, new CdR11aObjectAssets.TouchSize(16, 16)));
        // EnemyPoints dc.w 10,20,50,100 (PP Player Object Collision.asm:200-204): constructed literal only.
        var response = new CdPlayerTouchResponse(CdAssetBytes.copyOf(new byte[]{0, 10, 0, 20, 0, 50, 0, 100}));
        return new CdPlayerBody.TouchContext(scanner, pool.capture(), new CdPlayerBody.ResponseContext(response,
                new CdPlayerBody.ContactGlobals(0, 0, 1), new CdPlayerTouchResponse.WorldGlobals(rings, 3, 0, score, 5000, 0x80)));
    }
    private static CdPlayerTouchResponse.Result frameResponse(CdPlayerFrameRuntime.BodyPublication publication) {
        var responses = publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance)
                .map(CdPlayerBody.ObjectTouchResponse.class::cast).toList();
        assertEquals(1, responses.size()); return responses.getFirst().result();
    }
    private static CdPlayerBody.DisplayEligibility displayCut(CdPlayerFrameRuntime.BodyPublication publication) {
        return publication.result().effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                .map(CdPlayerBody.DisplayEligibility.class::cast).findFirst().orElseThrow();
    }

    @Test void objectFloorScratchUpdatesOnlyTheExistingSharedByteAndRestoresWithoutActorWrites() throws IOException {
        var f = new Fixture().runtime();
        var actor = f.captureActor(); var shared = f.captureShared(); var oldEnvironment = f.environment();
        var oldMovement = oldEnvironment.movement(); var oldGlobals = oldMovement.globals();
        f.applyObjectPrimaryAngle(0x81);
        assertEquals(0x81, f.environment().movement().globals().primaryAngle(), "Odd scratch byte remains raw");
        assertEquals(oldGlobals.secondaryAngle(), f.environment().movement().globals().secondaryAngle());
        assertEquals(oldGlobals.lookMode(), f.environment().movement().globals().lookMode());
        assertEquals(oldGlobals.cameraYCentre(), f.environment().movement().globals().cameraYCentre());
        assertEquals(oldGlobals.boredTimer(), f.environment().movement().globals().boredTimer());
        assertEquals(oldGlobals.scoreChain(), f.environment().movement().globals().scoreChain());
        assertEquals(oldGlobals.debugAngle(), f.environment().movement().globals().debugAngle());
        assertEquals(oldGlobals.debugAngleShift(), f.environment().movement().globals().debugAngleShift());
        assertEquals(oldGlobals.debugQuadrant(), f.environment().movement().globals().debugQuadrant());
        assertEquals(oldGlobals.debugFloorDistance(), f.environment().movement().globals().debugFloorDistance());
        assertSame(oldMovement.physics(), f.environment().movement().physics());
        assertSame(oldMovement.bounds(), f.environment().movement().bounds());
        assertSame(oldMovement.gates(), f.environment().movement().gates());
        assertSame(oldEnvironment.controls(), f.environment().controls());
        assertSame(oldEnvironment.globals(), f.environment().globals());
        assertEquals(actor, f.captureActor());
        assertEquals(shared.vIntClock(), f.captureShared().vIntClock());
        f.restoreShared(shared); assertSharedValues(shared, f.captureShared());
    }

    @Test void objectScratchRequiresAnOpenClosedFrameCutAndAnUnsignedByte() throws IOException {
        var f = new Fixture().runtime();
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectPrimaryAngle(-1));
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectPrimaryAngle(256));
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.applyObjectPrimaryAngle(0x22));
        f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertThrows(IllegalStateException.class, () -> f.applyObjectPrimaryAngle(0x22));
        f.runBody(1);
        f.applyObjectPrimaryAngle(255); assertEquals(255, f.environment().movement().globals().primaryAngle());
        f.close(); assertThrows(IllegalStateException.class, () -> f.applyObjectPrimaryAngle(0));
    }

    @Test void objectLaunchWriteUpdatesOnlyTheActorYVelocityAndRestoresWithoutSharedWrites() throws IOException {
        var f = new Fixture().runtime();
        var actor = f.captureActor(); var shared = f.captureShared(); var oldState = f.state(); var m = oldState.motion();
        f.applyObjectYVelocity(-0xC00);
        var next = f.state().motion();
        assertEquals(-0xC00, next.yVelocity(), "Obj3DRamp .TimerSet: MOVE.W #-$C00,oYVel(a6)");
        assertEquals(m.centreX16_16(), next.centreX16_16()); assertEquals(m.centreY16_16(), next.centreY16_16());
        assertEquals(m.xVelocity(), next.xVelocity()); assertEquals(m.groundVelocity(), next.groundVelocity());
        assertEquals(m.routine(), next.routine()); assertEquals(m.angle(), next.angle()); assertEquals(m.flags(), next.flags());
        assertEquals(m.spriteFlags(), next.spriteFlags()); assertEquals(m.xRadius(), next.xRadius()); assertEquals(m.yRadius(), next.yRadius());
        assertEquals(m.animation(), next.animation()); assertEquals(m.previousAnimation(), next.previousAnimation());
        assertEquals(m.playerJump(), next.playerJump()); assertEquals(m.charge(), next.charge()); assertEquals(m.moveLock(), next.moveLock());
        assertEquals(m.stick(), next.stick()); assertEquals(m.playerControl(), next.playerControl());
        assertEquals(m.previousPrimaryAngle(), next.previousPrimaryAngle()); assertEquals(m.previousSecondaryAngle(), next.previousSecondaryAngle());
        assertSame(oldState.animation(), f.state().animation()); assertSame(oldState.header(), f.state().header());
        assertSame(oldState.timers(), f.state().timers()); assertSame(oldState.history(), f.state().history());
        assertEquals(actor.display(), f.display(), "the optional display projection carries no velocity");
        assertSharedValues(shared, f.captureShared());
        f.applyObjectYVelocity(0xC00); assertEquals(0xC00, f.state().motion().yVelocity());
        f.restoreActor(actor); assertEquals(m.yVelocity(), f.state().motion().yVelocity()); assertEquals(actor, f.captureActor());
    }

    @Test void objectLaunchWriteRequiresAnOpenClosedFrameCutAndASignedWord() throws IOException {
        var f = new Fixture().runtime();
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectYVelocity(0x8000));
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectYVelocity(-0x8001));
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.applyObjectYVelocity(0xC00));
        f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertThrows(IllegalStateException.class, () -> f.applyObjectYVelocity(0xC00), "an entitled body slot is not a closed cut");
        f.runBody(1);
        f.applyObjectYVelocity(-0x8000); assertEquals(-0x8000, f.state().motion().yVelocity());
        f.applyObjectYVelocity(0x7FFF); assertEquals(0x7FFF, f.state().motion().yVelocity());
        f.close(); assertThrows(IllegalStateException.class, () -> f.applyObjectYVelocity(0));
    }

    // VInt_Level:829-837 services Sonic art then LoadShieldArt before Finish/count (:844-847).
    @ParameterizedTest @CsvSource({"ORDINARY_LEVEL,16", "NORMAL_PAUSE,128", "LAG,16"})
    void levelArtCallUsesOnlySelectorEightAfterPlayerDmaBeforeFinishAndClock(
            PlcLifecyclePhase phase, int held) throws IOException {
        var fixture = new Fixture(); var f = fixture.runtime();
        var env = new Env(f); env.bored = 10; env.warp = 20; env.install(f);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x24), new byte[0x300], 0, true));
        var calls = new ArrayList<String>();
        f.prepareInput(held);
        f.serviceVBlank(phase, () -> {
            calls.add("level-art");
            assertEquals(held, f.environment().controls().rawHeld());
            assertEquals(1, fixture.dmas.size(), "Sonic's pending DMA already published");
            assertFalse(f.artState().dirty());
            assertEquals(10, f.environment().movement().globals().boredTimer());
            assertEquals(20, f.environment().globals().timeWarpTimer());
            assertEquals(0, f.vIntClock().deltaSinceAdmittedCut());
            assertFalse(f.paused(), "Start toggle follows Finish");
        });
        assertEquals(phase == PlcLifecyclePhase.LAG ? List.of() : List.of("level-art"), calls);
        assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
        assertEquals(11, f.environment().movement().globals().boredTimer());
        assertEquals(21, f.environment().globals().timeWarpTimer());
        assertEquals(phase == PlcLifecyclePhase.NORMAL_PAUSE, f.paused());
        if (phase == PlcLifecyclePhase.LAG) {
            assertEquals(0, f.environment().controls().rawHeld());
            assertTrue(f.artState().dirty()); assertTrue(fixture.dmas.isEmpty());
        }
        f.clearPendingInput();
    }

    @Test void selectorEightCallsLevelArtEvenWithoutAnyPlayerDma() throws IOException {
        var fixture = new Fixture(); var f = fixture.runtime();
        var calls = new ArrayList<String>(); f.prepareInput(0);
        f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL, () -> calls.add("level-art"));
        assertEquals(List.of("level-art"), calls); assertTrue(fixture.dmas.isEmpty());
        f.clearPendingInput();
    }

    @Test void failedLevelArtKeepsReadControllerAndPlayerDmaButClosesBeforeFinishClockOrBody() throws IOException {
        var fixture = new Fixture(); var f = fixture.runtime();
        var env = new Env(f); env.bored = 10; env.warp = 20; env.install(f);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x24), new byte[0x300], 0, true));
        var failure = new PublicationFailure(); f.prepareInput(0x10);
        assertSame(failure, assertThrows(PublicationFailure.class,
                () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL, () -> { throw failure; })));
        assertEquals(0x10, f.environment().controls().rawHeld());
        assertEquals(1, fixture.dmas.size()); assertFalse(f.artState().dirty());
        assertEquals(10, f.environment().movement().globals().boredTimer());
        assertEquals(20, f.environment().globals().timeWarpTimer());
        assertEquals(0, f.vIntClock().deltaSinceAdmittedCut()); assertTrue(f.closed());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
        assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
    }

    @Test void missingLevelArtCallbackRejectsBeforeConsumingPreparedInput() throws IOException {
        var f = new Fixture().runtime(); var before = f.environment(); f.prepareInput(0x10);
        assertThrows(NullPointerException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL, null));
        assertSame(before, f.environment()); assertEquals(0, f.vIntClock().deltaSinceAdmittedCut());
        assertFalse(f.closed());
        f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(0x10, f.environment().controls().rawHeld()); f.clearPendingInput();
    }

    private static void service(CdPlayerFrameRuntime f, int held) { f.serviceVBlank(f.prepareInput(held).phase()); }
    private static List<CdPlayerBody.NativeCall> calls(CdPlayerFrameRuntime.BodyPublication p) {
        return p.result().effects().stream().filter(CdPlayerBody.CallReturned.class::isInstance)
                .map(CdPlayerBody.CallReturned.class::cast).map(CdPlayerBody.CallReturned::call).toList();
    }
    private static void restoreArt(CdPlayerFrameRuntime f, CdPlayerArtRuntime.State art) {
        var s = f.captureShared(); f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), s.environment(), art, s.paused(), s.vIntClock()));
    }
    private static void assertSharedValues(CdPlayerFrameRuntime.SharedState a, CdPlayerFrameRuntime.SharedState b) {
        assertSame(a.owner(), b.owner()); assertEquals(a.environment(), b.environment()); assertEquals(a.paused(), b.paused());
        assertEquals(a.vIntClock(), b.vIntClock()); assertEquals(a.artState().lastFrame(), b.artState().lastFrame());
        assertEquals(a.artState().dirty(), b.artState().dirty());
        assertArrayEquals(a.artState().cpuBuffer().copy(), b.artState().cpuBuffer().copy());
        assertArrayEquals(a.artState().residentBank().copy(), b.artState().residentBank().copy());
    }
    private static byte[] fill(int count, int value) { byte[] bytes = new byte[count]; Arrays.fill(bytes, (byte) value); return bytes; }

    /** Test edit of an explicit same-owner immutable capture, not another load/factory ingress. */
    private static final class Actor {
        int rotationAngle;
        final CdPlayerBody.State base;
        CdPlayerBody.ObjectLinks objectLinks;
        int slot, charge, gv, invincibility, reset, flags, y, yv, routine, spriteFlags, animation, rotationDistance;
        Actor(CdPlayerFrameRuntime f) { base = f.state(); objectLinks = base.objectLinks(); slot = base.header().slotId(); charge = base.motion().charge();
            gv = base.motion().groundVelocity(); invincibility = base.timers().invincibility(); reset = base.timers().reset();
            flags = base.motion().flags(); yv = base.motion().yVelocity(); animation = base.motion().animation();
            y = base.motion().centreY16_16(); routine = base.motion().routine(); spriteFlags = base.motion().spriteFlags(); rotationDistance = base.rotationDistance(); rotationAngle = base.rotationAngle(); }
        void install(CdPlayerFrameRuntime f) {
            var a = f.captureActor(); var m = base.motion(); var h = base.header();
            var motion = new CdPlayerMotionState(m.centreX16_16(), y, m.xVelocity(), yv, gv,
                    routine, m.angle(), flags, spriteFlags, m.xRadius(), m.yRadius(), animation,
                    m.previousAnimation(), m.playerJump(), charge, m.moveLock(), m.stick(), m.playerControl(),
                    m.previousPrimaryAngle(), m.previousSecondaryAngle());
            var state = new CdPlayerBody.State(motion, base.animation(), new CdPlayerBody.Header(slot, h.mappingAddress(), h.tile(), h.priority(), h.width()),
                    new CdPlayerBody.Timers(base.timers().hurt(), invincibility, base.timers().speedShoes(), reset), base.history(), rotationDistance, objectLinks, rotationAngle);
            var display = a.display().map(p -> new CdPlayerBody.DisplayPublication(p.queued(), p.mappingAddress(), p.tile(), p.priority(),
                    p.width(), p.mapFrame(), spriteFlags, p.centreX16_16(), p.centreY16_16()));
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(a.owner(), state, display));
        }
    }
    private static final class Env {
        final CdPlayerBody.Environment base;
        int bored, warp, logicalHeld, logicalTap, locked, invincible, zoneAct, scoreChain;
        CdPlayerBody.DynamicSlots slots;
        Env(CdPlayerFrameRuntime f) {
            base = f.environment(); bored = base.movement().globals().boredTimer(); warp = base.globals().timeWarpTimer();
            logicalHeld = base.controls().logicalHeld(); logicalTap = base.controls().logicalTap(); locked = base.controls().controlLocked();
            invincible = base.globals().invincible(); slots = base.dynamicSlots(); zoneAct = base.movement().gates().zoneAct();
            scoreChain = base.movement().globals().scoreChain();
        }
        void install(CdPlayerFrameRuntime f) {
            var s = f.captureShared(); var m = base.movement(); var g = m.globals(); var c = base.controls(); var b = base.globals(); var gates = m.gates();
            var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                    new CdPlayerMovement.Gates(zoneAct, gates.timeZone(), gates.miniSonic(), gates.waterSlide(), gates.windTunnel(), gates.speedShoes(), gates.timeWarpDirection()),
                    new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(), bored,
                            scoreChain, g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())),
                    new CdPlayerBody.Controls(c.rawHeld(), c.rawTap(), logicalHeld, logicalTap, locked),
                    new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(), b.debugCheat(), warp, invincible, b.bossMusic()), slots);
            f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), environment, s.artState(), s.paused(), s.vIntClock()));
        }
    }

    private static final class PublicationFailure extends RuntimeException { }
    @Test void explicitDevelopmentVIntSeedDoesNotChangeAnyColdPlayerOrArtIngress() throws IOException {
        var assets = new Fixture().assets();
        try (var unknown = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets);
             var seeded = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0x12345678L)) {
            assertEquals(OptionalLong.empty(), unknown.vIntClock().absoluteValue());
            assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0x12345678L), 0), seeded.vIntClock());
            assertEquals(unknown.state(), seeded.state()); assertEquals(unknown.environment(), seeded.environment());
            assertArrayEquals(unknown.artState().cpuBuffer().copy(), seeded.artState().cpuBuffer().copy());
            assertArrayEquals(unknown.artState().residentBank().copy(), seeded.artState().residentBank().copy());
            assertEquals(unknown.artState().lastFrame(), seeded.artState().lastFrame());
            assertEquals(unknown.artState().dirty(), seeded.artState().dirty());
            assertEquals(unknown.display(), seeded.display());
            assertThrows(IllegalStateException.class, () -> seeded.serviceVBlank(PlcLifecyclePhase.LAG));
            assertEquals(0x12345678L, seeded.vIntClock().absoluteValue().orElseThrow());
        }
    }

    @Test void explicitNativeClockWrapsOnServiceAndRestoresThroughItsExistingSharedPartition() throws IOException {
        var assets = new Fixture().assets();
        try (var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0xFFFFFFFEL)) {
            f.bindArtPublication((request, state) -> fail("PP LAG cannot publish art"));
            var initialActor = f.state();
            f.prepareInput(0x10); f.serviceVBlank(PlcLifecyclePhase.LAG);
            assertEquals(0xFFFFFFFFL, f.vIntClock().absoluteValue().orElseThrow());
            assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
            var saved = f.captureShared();
            f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
            assertEquals(0, f.vIntClock().absoluteValue().orElseThrow());
            assertEquals(2, f.vIntClock().deltaSinceAdmittedCut());
            assertEquals(initialActor, f.state());
            f.restoreShared(saved);
            assertEquals(saved.vIntClock(), f.vIntClock());
            f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
            assertEquals(0, f.vIntClock().absoluteValue().orElseThrow());
            assertEquals(2, f.vIntClock().deltaSinceAdmittedCut());
            assertEquals(OptionalLong.of(0xFFFFFFFEL), f.vIntClock().absoluteSeed());
        }
    }

    @Test void declaredVIntSeedRequiresUnsignedLongwordAndAnActualAggregate() throws IOException {
        var assets = new Fixture().assets();
        assertThrows(IllegalArgumentException.class, () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, -1));
        assertThrows(IllegalArgumentException.class,
                () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0x100000000L));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(null, 0));
    }

    // U5-D restart ingress, written before production. Source pin 1ecc5f3a: sonicArtBuf rs.b $300 and sonicRecordBuf
    // rs.b $100 (Level/_Definitions.inc:125-126), p1CtrlHold rs.b 1 (:185), debugAngle..debugFloorDist rs.b 1 each
    // and debugCheat rs.w 1 (_Include/Main CPU Variables.inc:130-133,139); the $F000 resident window is $300 bytes.
    @Test void restartIngressRejectsEveryNullAndNonNativeWidthBeforeAFrameExists() throws IOException {
        var h = CdAssetBytes.copyOf(new byte[0x100]);
        var c = CdAssetBytes.copyOf(new byte[0x300]);
        var r = CdAssetBytes.copyOf(new byte[0x300]);
        assertThrows(NullPointerException.class, () -> new CdPlayerFrameRuntime.RestartIngress(null, c, r, 0, 0, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> new CdPlayerFrameRuntime.RestartIngress(h, null, r, 0, 0, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> new CdPlayerFrameRuntime.RestartIngress(h, c, null, 0, 0, 0, 0, 0, 0));
        for (int length : new int[]{0, 0xFF, 0x101, 0x300}) {
            var bad = CdAssetBytes.copyOf(new byte[length]);
            assertThrows(IllegalArgumentException.class,
                    () -> new CdPlayerFrameRuntime.RestartIngress(bad, c, r, 0, 0, 0, 0, 0, 0), "history " + length);
        }
        for (int length : new int[]{0, 0x100, 0x2E0, 0x2FF, 0x301, 0x480}) {
            var bad = CdAssetBytes.copyOf(new byte[length]);
            assertThrows(IllegalArgumentException.class,
                    () -> new CdPlayerFrameRuntime.RestartIngress(h, bad, r, 0, 0, 0, 0, 0, 0), "CPU " + length);
            assertThrows(IllegalArgumentException.class,
                    () -> new CdPlayerFrameRuntime.RestartIngress(h, c, bad, 0, 0, 0, 0, 0, 0), "resident " + length);
        }
        for (int bad : new int[]{-1, 0x100, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (int field : new int[]{0, 1, 2, 3, 5}) {
                int[] v = new int[6];
                v[field] = bad;
                assertThrows(IllegalArgumentException.class,
                        () -> new CdPlayerFrameRuntime.RestartIngress(h, c, r, v[0], v[1], v[2], v[3], v[4], v[5]),
                        "byte field " + field + " = " + bad);
            }
        }
        for (int bad : new int[]{-1, 0x10000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdPlayerFrameRuntime.RestartIngress(h, c, r, 0, 0, 0, 0, bad, 0), "debugCheat " + bad);
        }
        var widest = new CdPlayerFrameRuntime.RestartIngress(h, c, r, 0xFF, 0xFF, 0xFF, 0xFF, 0xFFFF, 0xFF);
        assertEquals(0xFFFF, widest.debugCheat());
        var assets = new Fixture().assets();
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, null));
        assertThrows(NullPointerException.class,
                () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(null, 0, coldIngress()));
        assertThrows(IllegalArgumentException.class,
                () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, -1, coldIngress()));
        assertThrows(IllegalArgumentException.class,
                () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0x100000000L, coldIngress()));
    }

    // Catches a missing or extra carry: each of the nine components changes exactly its own observable field, and
    // cold-valued ingress reproduces the cold DEV cut (fresh Body actor, physics/bounds/gates, controls, globals,
    // art lastFrame/dirty, clock, display, pause). .ClearCamera clears sonicRecordIndex and the art flags
    // (Level/Main.asm:210-216; _Definitions.inc:285-286,315); :278-279 clear the logical pair and tap.
    @Test void restartFactoryCarriesExactlyItsNineDeclaredFieldsAndEveryOtherFieldIsTheColdCut() throws IOException {
        var assets = new Fixture().assets();
        try (var cold = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0);
             var same = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, coldIngress())) {
            assertEquals(observe(cold), observe(same), "cold-valued ingress is exactly the cold DEV cut");
            assertSame(assets, same.assets());
            assertNotSame(cold.captureShared().owner(), same.captureShared().owner());
        }
        var carried = carriedIngress(0x80);
        String[] fields = {"history.bytes", "art.cpu", "art.resident", "debugAngle", "debugAngleShift", "debugQuadrant",
                "debugFloorDistance", "debugCheat", "rawHeld"};
        for (int component = 0; component < fields.length; component++) {
            try (var cold = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0);
                 var one = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, only(component, carried))) {
                assertEquals(Set.of(fields[component]), differences(observe(cold), observe(one)), fields[component]);
            }
        }
        try (var cold = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0);
             var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, carried)) {
            assertEquals(Set.of(fields), differences(observe(cold), observe(f)));
            var s = f.state(); var e = f.environment(); var g = e.movement().globals();
            assertEquals(new CdPlayerBody.History(carried.history(), 0), s.history(), "bytes carried, index zero");
            assertEquals(List.of(0x81, 0x61, 0x40, 0x0E),
                    List.of(g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance()));
            assertEquals(0xBEEF, e.globals().debugCheat());
            assertEquals(0, e.globals().debugMode(), "debugMode is SRC-cleared at :298, unlike debugCheat");
            assertEquals(new CdPlayerBody.Controls(0x80, 0, 0, 0, 0), e.controls());
            assertEquals(CdPlayerBody.DynamicSlots.UNIMPLEMENTED, e.dynamicSlots());
            assertArrayEquals(carried.artCpu().copy(), f.artState().cpuBuffer().copy());
            assertArrayEquals(carried.artResident().copy(), f.artState().residentBank().copy());
            assertEquals(0, f.artState().lastFrame()); assertFalse(f.artState().dirty());
            assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0), 0), f.vIntClock());
            assertTrue(f.display().isEmpty()); assertFalse(f.paused()); assertFalse(f.closed());
        }
        try (var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0x12345678L, carried)) {
            assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0x12345678L), 0), f.vIntClock(),
                    "only the explicitly declared seed, never a carried or engine count");
        }
    }

    // Catches an implicit art callback, a pending sample or entitlement before root binds the restart Frame, a cleared
    // carry at Init, a RecordPos outside index 0, or a cue/DMA that overwrites the carried CPU or resident tails.
    // LoadSonicDynPLC then VInt_Level's fixed $2E0 DMA (Sonic/Main.asm:3055-3100, Level/Main.asm:821-847).
    @Test void restartFrameNeedsRootArtBindingAndKeepsCarriedHistoryAndArtTailsThroughInitMainAndDma() throws IOException {
        var assets = new Fixture().assets();
        var carried = carriedIngress(0);
        var dmas = new ArrayList<CdPlayerArtRuntime.DmaRequest>();
        try (var cold = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0);
             var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, carried)) {
            assertThrows(IllegalStateException.class, () -> f.runBody(1), "no entitlement before a service");
            var before = f.captureShared();
            f.prepareInput(0);
            assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL),
                    "no art callback until root binds it");
            assertSharedValues(before, f.captureShared()); assertFalse(f.closed());
            f.bindArtPublication((dma, full) -> dmas.add(dma));
            cold.bindArtPublication((dma, full) -> { });
            var none = Optional.of(frameTouchContext(new CdObjectPool()));
            service(f, 0); service(cold, 0);
            assertTrue(dmas.isEmpty(), "lastFrame 0 equals the published frame and dirty is clear");
            var init = f.runBody(1); var coldInit = cold.runBody(1);
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, init.result().boundary());
            assertEquals(coldInit.result().state().motion(), f.state().motion());
            assertEquals(new CdPlayerBody.History(carried.history(), 0), f.state().history(), "Init records nothing");
            service(f, 0); service(cold, 0);
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.runBody(2, none).result().boundary());
            cold.runBody(2, none);
            assertEquals(cold.state().motion(), f.state().motion()); assertEquals(cold.display(), f.display());
            // RecordPos writes its first entry at index 0 (Sonic/Main.asm:447-456); the rest stays carried.
            byte[] history = carried.history().copy();
            System.arraycopy(cold.state().history().bytes().copy(), 0, history, 0, 4);
            assertArrayEquals(history, f.state().history().bytes().copy());
            assertEquals(4, f.state().history().index()); assertEquals(cold.state().history().index(), f.state().history().index());
            var g = f.environment().movement().globals();
            assertEquals(List.of(0x81, 0x61, 0x40, 0x0E),
                    List.of(g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance()),
                    "a standing Main writes no debug probe byte");
            assertTrue(dmas.isEmpty());
            service(f, 0);
            assertEquals(1, dmas.size()); assertEquals(1, f.artState().lastFrame()); assertFalse(f.artState().dirty());
            byte[] cpu = carried.artCpu().copy();
            Arrays.fill(cpu, 0, 32, (byte) 0x5A);
            assertArrayEquals(cpu, f.artState().cpuBuffer().copy(), "the one-tile cue leaves the carried CPU tail");
            byte[] resident = carried.artResident().copy();
            System.arraycopy(cpu, 0, resident, 0, 0x2E0);
            assertArrayEquals(resident, f.artState().residentBank().copy(), "tile 24 ($2E0..$2FF) stays carried");
            f.clearPendingInput(); cold.clearPendingInput();
        }
    }

    // Catches a zeroed raw latch: Start held across the restart is no new tap (DEV for the fade V-ints' pad reads,
    // Level/Main.asm:860-862,894-895,942-944), while the cold zero latch sees a tap and selects pause.
    @Test void heldStartAcrossTheRestartIsNotANewTapWhileTheColdZeroLatchSeesOne() throws IOException {
        var assets = new Fixture().assets();
        try (var cold = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0);
             var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, carriedIngress(0x80))) {
            f.bindArtPublication((dma, full) -> { });
            assertEquals(new CdPlayerFrameRuntime.InputSelection(true, PlcLifecyclePhase.NORMAL_PAUSE), cold.prepareInput(0x80));
            assertEquals(new CdPlayerFrameRuntime.InputSelection(false, PlcLifecyclePhase.ORDINARY_LEVEL), f.prepareInput(0x80));
            f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
            assertEquals(new CdPlayerBody.Controls(0x80, 0, 0, 0, 0), f.environment().controls());
            assertFalse(f.paused());
            f.clearPendingInput();
            service(f, 0);
            f.clearPendingInput();
            assertEquals(new CdPlayerFrameRuntime.InputSelection(true, PlcLifecyclePhase.NORMAL_PAUSE), f.prepareInput(0x80),
                    "a released then pressed Start is an actual tap");
            f.clearPendingInput(); cold.clearPendingInput();
        }
    }

    // Catches a restart Frame that shares or accepts the previous visit's capture owner, or a replay that loses a
    // carried field: only its own captures restore, exactly, and forward replay repeats the same publications.
    @Test void restartFrameHasAFreshOwnerAndReplaysFromItsOwnCapturesOnly() throws IOException {
        var assets = new Fixture().assets();
        var carried = carriedIngress(0x80);
        try (var old = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0);
             var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, carried);
             var twin = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, carried)) {
            for (var foreign : List.of(old, twin)) {
                var actor = foreign.captureActor(); var shared = foreign.captureShared();
                assertThrows(IllegalArgumentException.class, () -> f.validateActorRestore(actor));
                assertThrows(IllegalArgumentException.class, () -> f.validateSharedRestore(shared));
                assertThrows(IllegalArgumentException.class, () -> f.restoreActor(actor));
                assertThrows(IllegalArgumentException.class, () -> f.restoreShared(shared));
                var own = f.captureActor();
                assertThrows(IllegalArgumentException.class, () -> foreign.restoreActor(own));
            }
            assertEquals(observe(twin), observe(f), "foreign rejections changed nothing");
            f.bindArtPublication((dma, full) -> { });
            var none = Optional.of(frameTouchContext(new CdObjectPool()));
            var actor = f.captureActor(); var shared = f.captureShared();
            service(f, 0x80); var init = f.runBody(1);
            service(f, 0); var main = f.runBody(2, none);
            service(f, 0); var after = observe(f); f.clearPendingInput();
            f.restoreActor(actor); f.restoreShared(shared);
            assertEquals(observe(twin), observe(f), "the admitted restart cut restores exactly, carried fields included");
            service(f, 0x80); assertEquals(init, f.runBody(1));
            service(f, 0); assertEquals(main, f.runBody(2, none));
            service(f, 0); assertEquals(after, observe(f));
            f.clearPendingInput();
        }
    }

    private static CdPlayerFrameRuntime.RestartIngress coldIngress() {
        return new CdPlayerFrameRuntime.RestartIngress(CdAssetBytes.copyOf(new byte[0x100]),
                CdAssetBytes.copyOf(new byte[0x300]), CdAssetBytes.copyOf(new byte[0x300]), 0, 0, 0, 0, 0, 0);
    }
    /** Constructed nonzero carry values; explicit ingress, not a route to a native restart. */
    private static CdPlayerFrameRuntime.RestartIngress carriedIngress(int rawHeld) {
        return new CdPlayerFrameRuntime.RestartIngress(CdAssetBytes.copyOf(pattern(0x100, 0x11)),
                CdAssetBytes.copyOf(pattern(0x300, 0x22)), CdAssetBytes.copyOf(pattern(0x300, 0x33)),
                0x81, 0x61, 0x40, 0x0E, 0xBEEF, rawHeld);
    }
    private static CdPlayerFrameRuntime.RestartIngress only(int component, CdPlayerFrameRuntime.RestartIngress from) {
        var c = coldIngress();
        return new CdPlayerFrameRuntime.RestartIngress(component == 0 ? from.history() : c.history(),
                component == 1 ? from.artCpu() : c.artCpu(), component == 2 ? from.artResident() : c.artResident(),
                component == 3 ? from.debugAngle() : 0, component == 4 ? from.debugAngleShift() : 0,
                component == 5 ? from.debugQuadrant() : 0, component == 6 ? from.debugFloorDistance() : 0,
                component == 7 ? from.debugCheat() : 0, component == 8 ? from.rawHeld() : 0);
    }
    private static byte[] pattern(int count, int seed) {
        byte[] bytes = new byte[count];
        for (int i = 0; i < count; i++) bytes[i] = (byte) (seed + i * 29);
        return bytes;
    }
    /** Every represented Frame field by name; byte windows compare by content. */
    private static Map<String, Object> observe(CdPlayerFrameRuntime f) {
        var s = f.state(); var e = f.environment(); var m = e.movement(); var g = m.globals();
        var c = e.controls(); var b = e.globals(); var art = f.artState(); var v = new LinkedHashMap<String, Object>();
        v.put("motion", s.motion()); v.put("animation", s.animation()); v.put("header", s.header()); v.put("timers", s.timers());
        v.put("history.bytes", HexFormat.of().formatHex(s.history().bytes().copy())); v.put("history.index", s.history().index());
        v.put("rotationDistance", s.rotationDistance());
        v.put("physics", m.physics()); v.put("bounds", m.bounds()); v.put("gates", m.gates());
        v.put("lookMode", g.lookMode()); v.put("cameraYCentre", g.cameraYCentre()); v.put("primaryAngle", g.primaryAngle());
        v.put("secondaryAngle", g.secondaryAngle()); v.put("boredTimer", g.boredTimer()); v.put("scoreChain", g.scoreChain());
        v.put("debugAngle", g.debugAngle()); v.put("debugAngleShift", g.debugAngleShift());
        v.put("debugQuadrant", g.debugQuadrant()); v.put("debugFloorDistance", g.debugFloorDistance());
        v.put("rawHeld", c.rawHeld()); v.put("rawTap", c.rawTap()); v.put("logicalHeld", c.logicalHeld());
        v.put("logicalTap", c.logicalTap()); v.put("controlLocked", c.controlLocked());
        v.put("levelFrames", b.levelFrames()); v.put("cameraXCentre", b.cameraXCentre()); v.put("timeAttackMode", b.timeAttackMode());
        v.put("debugMode", b.debugMode()); v.put("debugCheat", b.debugCheat()); v.put("timeWarpTimer", b.timeWarpTimer());
        v.put("invincible", b.invincible()); v.put("bossMusic", b.bossMusic()); v.put("dynamicSlots", e.dynamicSlots());
        v.put("art.cpu", HexFormat.of().formatHex(art.cpuBuffer().copy()));
        v.put("art.resident", HexFormat.of().formatHex(art.residentBank().copy()));
        v.put("art.lastFrame", art.lastFrame()); v.put("art.dirty", art.dirty());
        v.put("vIntClock", f.vIntClock()); v.put("display", f.display()); v.put("paused", f.paused()); v.put("closed", f.closed());
        return v;
    }
    private static Set<String> differences(Map<String, Object> a, Map<String, Object> b) {
        assertEquals(a.keySet(), b.keySet());
        var keys = new TreeSet<String>();
        for (var key : a.keySet()) if (!a.get(key).equals(b.get(key))) keys.add(key);
        return keys;
    }


    // Native LoadCheckpointData uses a fresh-visit, callback-scoped writer before any input/Body.
    @Test void checkpointLoadPortBeginsWithClearedCoordinatesAndPreservesEveryOtherCarriedPartition() throws IOException {
        var assets=new Fixture().assets();var carry=checkpointFrameCarry();
        var baseline=CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets,7,carry);
        var frame=CdPlayerFrameRuntime.checkpointRestartDevelopment(assets,7,carry,port->{
            assertEquals(0,port.read(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X));
            assertEquals(0,port.read(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_Y));
            int[] values={0x1234,0xFEDC,0x4567,0xFF60,0xE1};int i=0;
            for(var field:CdPlayerFrameRuntime.CheckpointLoadField.values())
                port.store(new CdPlayerFrameRuntime.CheckpointLoadStore(field,port.read(field),values[i++]));
        });
        assertEquals(Set.of("motion","bounds","gates"),differences(observe(baseline),observe(frame)));
        assertEquals(0x12340000,frame.state().motion().centreX16_16());
        assertEquals(0xFEDC0000,frame.state().motion().centreY16_16());
        assertEquals(0x4567,frame.environment().movement().bounds().bottom());
        assertEquals(0xFF60,frame.environment().movement().bounds().left());
        assertEquals(0xE1,frame.environment().movement().gates().miniSonic());
        assertEquals(baseline.state().objectLinks(),frame.state().objectLinks());
        assertArrayEquals(carry.history().copy(),frame.state().history().bytes().copy());
        assertArrayEquals(carry.artCpu().copy(),frame.artState().cpuBuffer().copy());
        assertArrayEquals(carry.artResident().copy(),frame.artState().residentBank().copy());
        assertEquals(0,frame.state().history().index());assertEquals(0,frame.state().motion().routine());
        assertEquals(OptionalLong.of(7),frame.vIntClock().absoluteValue());assertEquals(0,frame.vIntClock().deltaSinceAdmittedCut());
        assertFalse(frame.paused());assertTrue(frame.display().isEmpty());assertFalse(frame.closed());
    }

    @Test void checkpointLoadPortRejectsStaleValuesAndBadWidthsBeforeAnyWrite() throws IOException {
        CdPlayerFrameRuntime.checkpointRestartDevelopment(new Fixture().assets(),0,checkpointFrameCarry(),port->{
            var before=new java.util.EnumMap<CdPlayerFrameRuntime.CheckpointLoadField,Integer>(CdPlayerFrameRuntime.CheckpointLoadField.class);
            for(var field:CdPlayerFrameRuntime.CheckpointLoadField.values())before.put(field,port.read(field));
            for(var field:CdPlayerFrameRuntime.CheckpointLoadField.values()) {
                int current=port.read(field);
                assertThrows(IllegalArgumentException.class,()->port.store(new CdPlayerFrameRuntime.CheckpointLoadStore(field,current^1,0)));
            }
            assertThrows(NullPointerException.class,()->port.read(null));
            assertThrows(NullPointerException.class,()->port.store(null));
            for(var field:CdPlayerFrameRuntime.CheckpointLoadField.values()) {
                assertEquals(before.get(field).intValue(),port.read(field));int maximum=field==CdPlayerFrameRuntime.CheckpointLoadField.MINI_SONIC?255:65535;
                assertThrows(IllegalArgumentException.class,()->new CdPlayerFrameRuntime.CheckpointLoadStore(field,-1,0));
                assertThrows(IllegalArgumentException.class,()->new CdPlayerFrameRuntime.CheckpointLoadStore(field,0,maximum+1));
            }
        });
    }

    @Test void checkpointLoadPortPreservesAllOtherFieldsAcrossEverySameValueAndWrappingWordStore() throws IOException {
        var frame=CdPlayerFrameRuntime.checkpointRestartDevelopment(new Fixture().assets(),0,checkpointFrameCarry(),port->{
            for(var field:CdPlayerFrameRuntime.CheckpointLoadField.values()) {
                int current=port.read(field);port.store(new CdPlayerFrameRuntime.CheckpointLoadStore(field,current,current));assertEquals(current,port.read(field));
                int maximum=field==CdPlayerFrameRuntime.CheckpointLoadField.MINI_SONIC?255:65535;
                port.store(new CdPlayerFrameRuntime.CheckpointLoadStore(field,current,maximum));assertEquals(maximum,port.read(field));
                port.store(new CdPlayerFrameRuntime.CheckpointLoadStore(field,maximum,0));assertEquals(0,port.read(field));
            }
        });
        assertEquals(0,frame.state().motion().centreX16_16());assertEquals(0,frame.state().motion().centreY16_16());
        assertEquals(0,frame.state().motion().xVelocity());assertEquals(0,frame.state().motion().yVelocity());
        assertEquals(0,frame.environment().movement().bounds().left());assertEquals(0,frame.environment().movement().bounds().bottom());
    }

    @Test void checkpointLoadPortCannotBeRetainedAsAClosedFrameWriterOrCapturedCapability() throws IOException {
        var ports=new CdPlayerFrameRuntime.CheckpointLoadPort[1];
        var frame=CdPlayerFrameRuntime.checkpointRestartDevelopment(new Fixture().assets(),0,checkpointFrameCarry(),port->ports[0]=port);
        var before=observe(frame);var saved=frame.captureActor();
        for(var field:CdPlayerFrameRuntime.CheckpointLoadField.values()) {
            assertThrows(IllegalStateException.class,()->ports[0].read(field));
            assertThrows(IllegalStateException.class,()->ports[0].store(new CdPlayerFrameRuntime.CheckpointLoadStore(field,0,0)));
        }
        frame.restoreActor(saved);assertEquals(before,observe(frame));
        assertThrows(IllegalStateException.class,()->ports[0].read(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X));
    }

    @Test void checkpointLoadCallbackFailureInvalidatesItsPortAndRetainsTheEarlierObservedStore() throws IOException {
        var ports=new CdPlayerFrameRuntime.CheckpointLoadPort[1];var reached=new ArrayList<Integer>();var failure=new IllegalStateException("reached native load stop");
        var thrown=assertThrows(IllegalStateException.class,()->CdPlayerFrameRuntime.checkpointRestartDevelopment(new Fixture().assets(),0,checkpointFrameCarry(),port->{
            ports[0]=port;port.store(new CdPlayerFrameRuntime.CheckpointLoadStore(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X,0,0xBEEF));
            reached.add(port.read(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X));throw failure;
        }));
        assertSame(failure,thrown);assertEquals(List.of(0xBEEF),reached);
        assertThrows(IllegalStateException.class,()->ports[0].read(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X));
    }

    @Test void checkpointLoadFactoryValidatesRequiredIngressBeforeCallingTheInitializer() throws IOException {
        var assets=new Fixture().assets();var carry=checkpointFrameCarry();var calls=new ArrayList<Integer>();
        CdPlayerFrameRuntime.CheckpointLoadInitializer callback=port->calls.add(1);
        assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.checkpointRestartDevelopment(null,0,carry,callback));
        assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.checkpointRestartDevelopment(assets,0,null,callback));
        assertThrows(NullPointerException.class,()->CdPlayerFrameRuntime.checkpointRestartDevelopment(assets,0,carry,null));
        assertThrows(IllegalArgumentException.class,()->CdPlayerFrameRuntime.checkpointRestartDevelopment(assets,-1,carry,callback));
        assertTrue(calls.isEmpty());
    }

    private static CdPlayerFrameRuntime.RestartIngress checkpointFrameCarry() {
        byte[] history=new byte[256],cpu=new byte[768],resident=new byte[768];
        Arrays.fill(history,(byte)0xA5);Arrays.fill(cpu,(byte)0xB6);Arrays.fill(resident,(byte)0xC7);
        return new CdPlayerFrameRuntime.RestartIngress(CdAssetBytes.copyOf(history),CdAssetBytes.copyOf(cpu),CdAssetBytes.copyOf(resident),1,2,3,4,0x1234,0x80);
    }

    private static final class Fixture {
        CdReleaseVariant variant = CdReleaseVariant.USA;
        boolean failPublication;
        final List<CdPlayerArtRuntime.DmaRequest> dmas = new ArrayList<>();
        CdPlayerFrameRuntime runtime() throws IOException {
            return CdPlayerFrameRuntime.terrainOnlyDevelopment(assets(), (dma, state) -> {
                dmas.add(dma); assertFalse(state.dirty());
                if (failPublication) throw new PublicationFailure();
            });
        }
        CdR11aAssets assets() throws IOException {
            var profile = CdR11aProfile.forStage(variant, CdStagePayload.R11A.stage());
            byte[] fg = new byte[338], bg = new byte[50], chunks = new byte[61952], ids = new byte[900], heights = new byte[4096];
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; Arrays.fill(heights, 16, 32, (byte) 16);
            for (int x = 0; x < 16; x++) word(chunks, 512 + (7 * 16 + x) * 2, 0x6001);
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, new byte[256], heights, new byte[4096]);
            byte[] maps = new byte[398], dplcs = new byte[402], art = new byte[69472], animations = new byte[578];
            for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplcs, i * 2, 396); }
            word(dplcs, 2, 398); word(dplcs, 398, 1); word(dplcs, 400, 0); Arrays.fill(art, 0, 32, (byte) 0x5A);
            var sonic = CdSonicVisualAssets.decode(maps, dplcs, art);
            for (int i = 0; i < 60; i++) word(animations, i * 2, 120);
            animations[120] = 3; animations[121] = 1; animations[122] = (byte) 0xFF;
            byte[] sine = new byte[640]; word(sine, 0x40 * 2, 256); word(sine, 0xC0 * 2, -256);
            byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var player = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[]{0x7F, 0x7F, 0x7F, 0x7F}, sine, atan, sonic);
            var image = loadPlanImage(profile); var plan = CdR11aLoadPlan.decode(profile, image);
            return new CdR11aAssets(profile, null, image, null, null, terrain, plan, null, sonic, player, null);
        }
    }

    /** Complete bounded inputs for the real load-plan decoder, deliberately no retail identity assertion. */
    private static CdMmdImage loadPlanImage(CdR11aProfile profile) throws IOException {
        byte[] bytes = new byte[CdR11aProfile.FILE_BYTES];
        int index = profile.range(CdR11aAsset.LEVEL_DATA_INDEX).fileOffset();
        longword(bytes, index, 0x0323C4EE); longword(bytes, index + 4, 0x0223B0A8); longword(bytes, index + 8, 0x210000);
        word(bytes, index + 12, 0x81); word(bytes, index + 14, 0x0505);
        int start = profile.range(CdR11aAsset.LEVEL_START_LOC).fileOffset(); word(bytes, start, 64); word(bytes, start + 2, 1899);
        int bound = profile.range(CdR11aAsset.CAM_BOUNDS).fileOffset();
        int[] bounds = {4, 0, 0x2897, 0, 0x710, 96}; for (int i = 0; i < bounds.length; i++) word(bytes, bound + i * 2, bounds[i]);
        int layouts = profile.range(CdR11aAsset.LEVEL_LAYOUTS).fileOffset();
        word(bytes, layouts, profile.range(CdR11aAsset.LAYOUT_FG).fileOffset() - layouts);
        word(bytes, layouts + 2, profile.range(CdR11aAsset.LAYOUT_BG).fileOffset() - layouts);
        int collision = profile.range(CdR11aAsset.LEVEL_COL_INDEX).fileOffset();
        for (int i = 0; i < 8; i++) longword(bytes, collision + i * 4, profile.range(CdR11aAsset.LEVEL_COLLISION).nativeAddress());
        int plc = profile.range(CdR11aAsset.PLC_LISTS).fileOffset();
        for (int i = 0; i < 19; i++) word(bytes, plc + i * 2, 38);
        word(bytes, plc + 38, 0); longword(bytes, plc + 40, profile.range(CdR11aAsset.ART_LEVEL_TILES).nativeAddress()); word(bytes, plc + 44, 0);
        for (int i = 0; i < 5; i++) {
            word(bytes, profile.range(CdR11aAsset.SECTION_RANGES).fileOffset() + 2 * i, i == 4 ? 65535 : (i + 1) * 0x800);
            word(bytes, profile.range(CdR11aAsset.SECTION_INIT_PLCS).fileOffset() + 2 * i, 0);
            word(bytes, profile.range(CdR11aAsset.SECTION_UPDATE_PLCS).fileOffset() + 2 * i, 0);
        }
        int palette = profile.range(CdR11aAsset.PALETTE_TABLE).fileOffset();
        longword(bytes, palette + 3 * 8, profile.range(CdR11aAsset.PAL_SONIC).nativeAddress()); word(bytes, palette + 3 * 8 + 4, 0xFB00); word(bytes, palette + 3 * 8 + 6, 7);
        longword(bytes, palette + 5 * 8, profile.range(CdR11aAsset.PAL_LEVEL).nativeAddress()); word(bytes, palette + 5 * 8 + 4, 0xFB20); word(bytes, palette + 5 * 8 + 6, 23);
        return CdMmdImage.parse(bytes);
    }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void longword(byte[] bytes, int at, int value) { word(bytes, at, value >>> 16); word(bytes, at + 2, value); }

    // Shared Solid's native-stop values have no dispatch authority. Added source-only, JAVA UNRUN.
    @Test void objectSolidPrefixAdoptsAnActualHurtRolledStandFailureAndRetainsEveryOtherPartition() throws IOException {
        var fixture = new Fixture(); var f = spinTunnelDirtyFrame(fixture); var captured = f.captureActor();
        var s = captured.state(); var m = s.motion();
        var motion = new CdPlayerMotionState(0x01008000, 0x00731234, 0x123, 0, 0x345,
                4, 0x60, 0x0C, 4, 9, 19, 2, m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(),
                m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var seeded = new CdPlayerBody.State(motion, s.animation(), s.header(), s.timers(), s.history(), s.rotationDistance(),
                new CdPlayerBody.ObjectLinks(0x7B, 5), s.rotationAngle());
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(), seeded, captured.display()));
        var pool = new CdObjectPool(); var object = pool.state(40).moveByte(CdObjectState.ByteField.ID, 0x20)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x84).moveByte(CdObjectState.ByteField.WIDTH, 0x10)
                .moveByte(CdObjectState.ByteField.Y_RADIUS, 0x0C).moveWord(CdObjectState.WordField.X, 0x100)
                .moveWord(CdObjectState.WordField.Y, 0x90); pool.write(object);
        var player = solidPlayer(f); var globals = solidGlobals(f); var poolBefore = pool.capture();
        var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, CdSolidObject.fromAssets(f.assets())
                .runWithPrefix(CdSolidObject.Entry.TOP_SOLID_OBJECT, object, player, new CdSolidObject.Gates(0, 0), globals, poolBefore));
        assertEquals(CdSolidObject.StopSite.STAND_RELEASE, stopped.stop().site());
        assertEquals(new CdSolidObject.SlotAddress(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS, 5,
                CdSolidObject.Routine.STAND_ON_OBJECT), stopped.stop().detail());
        var p = stopped.prefix(); var actor = f.captureActor(); var shared = f.captureShared(); int dmas = fixture.dmas.size();
        assertEquals(0x006C1234, p.player().actor().motion().centreY16_16());
        assertEquals(2, p.player().actor().motion().routine()); assertEquals(120, p.player().actor().hurt());
        assertEquals(8, p.player().actor().motion().flags()); assertEquals(5, p.player().standObject());
        assertEquals(0x60, p.player().actor().motion().angle()); assertEquals(0x345, p.player().actor().motion().groundVelocity());
        assertEquals(Set.of(CdSolidObject.PlayerField.CENTRE_X, CdSolidObject.PlayerField.CENTRE_X_FRACTION,
                CdSolidObject.PlayerField.CENTRE_Y, CdSolidObject.PlayerField.CENTRE_Y_FRACTION, CdSolidObject.PlayerField.ROUTINE,
                CdSolidObject.PlayerField.HURT, CdSolidObject.PlayerField.PLAYER_JUMP, CdSolidObject.PlayerField.FLAGS,
                CdSolidObject.PlayerField.Y_RADIUS, CdSolidObject.PlayerField.X_RADIUS, CdSolidObject.PlayerField.ANIMATION), p.playerWrites());
        f.validateObjectSolidPrefix(player, globals, p);
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        assertSame(actor.state(), f.state()); assertSame(shared.environment(), f.environment());
        f.applyObjectSolidPrefix(player, globals, p);
        prefixAssertAdopted(f, actor, shared, p); assertEquals(poolBefore.slots(), pool.capture().slots());
        assertEquals(dmas, fixture.dmas.size()); assertEquals(List.of(), p.fmRequests());
        var after = f.captureActor(); var afterShared = f.captureShared();
        prefixAssertRejected(f, player, globals, p, IllegalArgumentException.class);
        f.restoreActor(actor); f.restoreShared(shared); f.applyObjectSolidPrefix(player, globals, p);
        assertEquals(after, f.captureActor()); assertEquals(afterShared, f.captureShared());
        assertInstanceOf(CdSolidObject.UnsupportedSolidObject.class, stopped.stop().originalException());
    }

    @ParameterizedTest @ValueSource(strings = {"CENTRE_X", "X_FRACTION", "CENTRE_Y", "Y_FRACTION", "X_VELOCITY",
            "Y_VELOCITY", "GROUND_VELOCITY", "ROUTINE", "ANGLE", "FLAGS", "SPRITE_FLAGS", "X_RADIUS", "Y_RADIUS",
            "ANIMATION", "PREVIOUS_ANIMATION", "PLAYER_JUMP", "CHARGE", "MOVE_LOCK", "STICK", "PLAYER_CONTROL",
            "PREVIOUS_PRIMARY", "PREVIOUS_SECONDARY", "TILE", "PRIORITY", "HURT", "ROTATION_DISTANCE", "SLOT_ID",
            "PUSH_OBJECT", "STAND_OBJECT"})
    void objectSolidPrefixCasChecksEveryExpectedPlayerFieldBeforeApplyingAnything(String field) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var player = solidPlayer(f); var globals = solidGlobals(f);
        var p = prefixFrameValues(player, globals, Set.of(), Set.of());
        prefixAssertRejected(f, springChanged(player, field), globals, p, IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void objectSolidPrefixCasChecksBothRawAnglesInvincibilityAndScoreChain(int field) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var player = solidPlayer(f); var globals = solidGlobals(f);
        var p = prefixFrameValues(player, globals, Set.of(), Set.of());
        prefixAssertRejected(f, player, prefixChangedGlobals(globals, field), p, IllegalArgumentException.class);
    }

    @ParameterizedTest @EnumSource(CdSolidObject.PlayerField.class)
    void objectSolidPrefixRejectsEveryUndeclaredRepresentedPlayerWrite(CdSolidObject.PlayerField field) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var player = solidPlayer(f); var globals = solidGlobals(f);
        String key = switch (field) {
            case CENTRE_X_FRACTION -> "X_FRACTION";
            case CENTRE_Y_FRACTION -> "Y_FRACTION";
            default -> field.name();
        };
        var p = prefixFrameValues(springChanged(player, key), globals, Set.of(), Set.of());
        prefixAssertRejected(f, player, globals, p, IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void objectSolidPrefixRejectsEveryUndeclaredGlobalWrite(int field) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var player = solidPlayer(f); var globals = solidGlobals(f);
        var p = prefixFrameValues(player, prefixChangedGlobals(globals, field), Set.of(), Set.of());
        prefixAssertRejected(f, player, globals, p, IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"SLOT_ID", "SPRITE_FLAGS", "PREVIOUS_ANIMATION", "CHARGE", "MOVE_LOCK",
            "PREVIOUS_PRIMARY", "PREVIOUS_SECONDARY"})
    void objectSolidPrefixKnownMasksCannotAuthorizeAnUnownedPlayerField(String field) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var player = solidPlayer(f); var globals = solidGlobals(f);
        var p = prefixFrameValues(springChanged(player, field), globals,
                java.util.EnumSet.allOf(CdSolidObject.PlayerField.class), java.util.EnumSet.allOf(CdSolidObject.GlobalField.class));
        prefixAssertRejected(f, player, globals, p, IllegalArgumentException.class);
    }

    @ParameterizedTest @EnumSource(CdSolidObject.PlayerField.class)
    void objectSolidPrefixDeclaredSameValueStoresAreLegalAndPreserveAllValues(CdSolidObject.PlayerField field) throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var player = solidPlayer(f); var globals = solidGlobals(f);
        var p = prefixFrameValues(player, globals, Set.of(field), java.util.EnumSet.allOf(CdSolidObject.GlobalField.class));
        var actor = f.captureActor(); var shared = f.captureShared();
        f.validateObjectSolidPrefix(player, globals, p); assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        f.applyObjectSolidPrefix(player, globals, p); assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        assertSame(actor.state().history(), f.state().history()); assertSame(actor.display(), f.display());
        assertSame(shared.artState(), f.artState()); assertSame(shared.vIntClock(), f.vIntClock());
    }

    @Test void objectSolidPrefixGlobalWritesUpdateTheSingleEnvironmentWithoutCopyingRawAnglesToActorHistory() throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var player = solidPlayer(f); var globals = solidGlobals(f);
        var p = prefixFrameValues(player, new CdSolidObject.Globals(0, 0x1234, 3, 0), Set.of(),
                java.util.EnumSet.allOf(CdSolidObject.GlobalField.class));
        var actor = f.captureActor(); var shared = f.captureShared();
        f.validateObjectSolidPrefix(player, globals, p); assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        f.applyObjectSolidPrefix(player, globals, p); prefixAssertAdopted(f, actor, shared, p);
        assertEquals(player, solidPlayer(f)); assertEquals(0x44, f.state().motion().previousPrimaryAngle());
        assertEquals(0x88, f.state().motion().previousSecondaryAngle());
    }

    @Test void objectSolidPrefixMetadataConveysNoPoolOwnerOrCallExecutionAuthority() throws IOException {
        var fixture = new Fixture(); var f = spinTunnelDirtyFrame(fixture); var player = solidPlayer(f); var globals = solidGlobals(f);
        var pool = new CdObjectPool(); var cut = pool.capture();
        var p = new CdSolidObject.Prefix(CdSolidObject.Entry.TOP_SOLID_OBJECT, pool.state(40), player, globals,
                List.of(pool.state(41)), Set.of(CdObjectState.ByteField.SOLID_TYPE), Set.of(), Set.of(),
                List.of(new CdSolidObject.RoutineCall(CdSolidObject.Routine.STAND_ON_OBJECT),
                        new CdSolidObject.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)));
        var actor = f.captureActor(); var shared = f.captureShared(); int dmas = fixture.dmas.size();
        f.validateObjectSolidPrefix(player, globals, p); f.applyObjectSolidPrefix(player, globals, p);
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        assertEquals(cut.slots(), pool.capture().slots()); assertEquals(dmas, fixture.dmas.size());
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP), p.fmRequests());
    }

    @Test void objectSolidPrefixPrimaryAliasFlagsStayInTheActorAndNeverCreateAnotherPlayerSlot() throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var player = solidPlayer(f); var globals = solidGlobals(f);
        var a = player.actor(); var m = a.motion();
        var nextMotion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), m.routine(), m.angle(), m.flags() & ~8, m.spriteFlags(), m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var next = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(nextMotion, a.tile(), a.priority(), a.hurt(),
                a.rotationDistance()), player.slotId(), player.pushObject(), player.standObject());
        var p = prefixFrameValues(next, globals, Set.of(CdSolidObject.PlayerField.FLAGS), Set.of());
        var actor = f.captureActor(); var shared = f.captureShared();
        f.applyObjectSolidPrefix(player, globals, p); prefixAssertAdopted(f, actor, shared, p);
        assertEquals(List.of(), p.otherSlots()); assertEquals(player.slotId(), f.state().header().slotId());
        assertEquals(player.pushObject(), f.state().objectLinks().pushObject());
    }

    @Test void objectSolidPrefixNullPendingEntitlementReentryAndClosedGuardsRetainTheActualCut() throws IOException {
        var f = new Fixture().runtime(); var player = solidPlayer(f); var globals = solidGlobals(f);
        var p = prefixFrameValues(player, globals, Set.of(), Set.of());
        prefixAssertRejected(f, null, globals, p, NullPointerException.class);
        prefixAssertRejected(f, player, null, p, NullPointerException.class);
        prefixAssertRejected(f, player, globals, null, NullPointerException.class);
        var selection = f.prepareInput(8);
        prefixAssertRejected(f, player, globals, p, IllegalStateException.class);
        int[] callbacks = {0};
        f.serviceVBlank(selection.phase(), () -> {
            callbacks[0]++;
            var state = f.state(); var env = f.environment(); var art = f.artState(); var clock = f.vIntClock(); var display = f.display();
            assertThrows(IllegalStateException.class, () -> f.validateObjectSolidPrefix(player, globals, p));
            assertThrows(IllegalStateException.class, () -> f.applyObjectSolidPrefix(player, globals, p));
            assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(art, f.artState());
            assertSame(clock, f.vIntClock()); assertSame(display, f.display());
        });
        assertEquals(1, callbacks[0]);
        prefixAssertRejected(f, player, globals, p, IllegalStateException.class);
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary());
        var closedPlayer = solidPlayer(f); var closedGlobals = solidGlobals(f);
        var closedPrefix = prefixFrameValues(closedPlayer, closedGlobals, Set.of(), Set.of());
        f.close();
        var state = f.state(); var env = f.environment(); var art = f.artState(); var clock = f.vIntClock(); var display = f.display();
        assertThrows(IllegalStateException.class, () -> f.validateObjectSolidPrefix(closedPlayer, closedGlobals, closedPrefix));
        assertThrows(IllegalStateException.class, () -> f.applyObjectSolidPrefix(closedPlayer, closedGlobals, closedPrefix));
        assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(art, f.artState());
        assertSame(clock, f.vIntClock()); assertSame(display, f.display());
    }

    private static CdSolidObject.Prefix prefixFrameValues(CdSolidObject.Player player, CdSolidObject.Globals globals,
            Set<CdSolidObject.PlayerField> players, Set<CdSolidObject.GlobalField> globalWrites) {
        return new CdSolidObject.Prefix(CdSolidObject.Entry.TOP_SOLID_OBJECT, new CdObjectPool().state(40), player, globals,
                List.of(), Set.of(), players, globalWrites, List.of());
    }
    private static CdSolidObject.Globals prefixChangedGlobals(CdSolidObject.Globals g, int field) {
        int[] values = {g.invincible(), g.scoreChain(), g.primaryAngle(), g.secondaryAngle()}; values[field] ^= 1;
        return new CdSolidObject.Globals(values[0], values[1], values[2], values[3]);
    }
    private static void prefixAssertRejected(CdPlayerFrameRuntime f, CdSolidObject.Player player, CdSolidObject.Globals globals,
            CdSolidObject.Prefix prefix, Class<? extends RuntimeException> type) {
        // Pending/reentrant cuts cannot be captured; read only already exposed immutable values in all cases.
        var state = f.state(); var env = f.environment(); var display = f.display(); var art = f.artState(); var clock = f.vIntClock();
        assertThrows(type, () -> f.validateObjectSolidPrefix(player, globals, prefix));
        assertThrows(type, () -> f.applyObjectSolidPrefix(player, globals, prefix));
        assertSame(state, f.state()); assertSame(env, f.environment()); assertSame(display, f.display());
        assertSame(art, f.artState()); assertSame(clock, f.vIntClock());
    }
    private static void prefixAssertAdopted(CdPlayerFrameRuntime f, CdPlayerFrameRuntime.ActorState before,
            CdPlayerFrameRuntime.SharedState shared, CdSolidObject.Prefix prefix) {
        var s = before.state(); var h = s.header(); var a = prefix.player().actor(); var links = prefix.player(); var g = prefix.globals();
        assertEquals(new CdPlayerBody.State(a.motion(), s.animation(), new CdPlayerBody.Header(h.slotId(), h.mappingAddress(),
                a.tile(), a.priority(), h.width()), new CdPlayerBody.Timers(a.hurt(), s.timers().invincibility(),
                s.timers().speedShoes(), s.timers().reset()), s.history(), a.rotationDistance(),
                new CdPlayerBody.ObjectLinks(links.pushObject(), links.standObject()), s.rotationAngle()), f.state());
        var e = shared.environment(); var movement = e.movement(); var old = movement.globals(); var body = e.globals();
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(), movement.gates(),
                new CdPlayerMovement.Globals(old.lookMode(), old.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(), old.boredTimer(),
                        g.scoreChain(), old.debugAngle(), old.debugAngleShift(), old.debugQuadrant(), old.debugFloorDistance())), e.controls(),
                new CdPlayerBody.Globals(body.levelFrames(), body.cameraXCentre(), body.timeAttackMode(), body.debugMode(), body.debugCheat(),
                        body.timeWarpTimer(), g.invincible(), body.bossMusic()), e.dynamicSlots()), f.environment());
        assertSame(s.animation(), f.state().animation()); assertSame(s.history(), f.state().history());
        assertSame(before.display(), f.display()); assertSame(shared.artState(), f.artState()); assertSame(shared.vIntClock(), f.vIntClock());
        assertEquals(shared.paused(), f.captureShared().paused()); assertSame(e.controls(), f.environment().controls());
        assertSame(e.dynamicSlots(), f.environment().dynamicSlots()); assertSame(movement.physics(), f.environment().movement().physics());
        assertSame(movement.bounds(), f.environment().movement().bounds()); assertSame(movement.gates(), f.environment().movement().gates());
    }

    // Explicit test-only $2B ingress; every owner below is the real public Frame component.
    @Test void rotationAngleUsesTheExistingActorCaptureAndDoesNotWidenRestartOrSharedContracts() {
        assertEquals(3, CdPlayerFrameRuntime.ActorState.class.getRecordComponents().length);
        assertEquals(5, CdPlayerFrameRuntime.SharedState.class.getRecordComponents().length);
        assertEquals(9, CdPlayerFrameRuntime.RestartIngress.class.getRecordComponents().length);
        assertEquals(List.of("history", "artCpu", "artResident", "debugAngle", "debugAngleShift",
                "debugQuadrant", "debugFloorDistance", "debugCheat", "rawHeld"),
                Arrays.stream(CdPlayerFrameRuntime.RestartIngress.class.getRecordComponents()).map(c -> c.getName()).toList());
        assertEquals(5, CdPlayerTouchResponse.Actor.class.getRecordComponents().length);
        assertEquals(4, CdSolidObject.Player.class.getRecordComponents().length);
    }

    @ParameterizedTest @ValueSource(ints = {1, 0x2B, 0x80, 0xFF})
    void rotationAngleSurvivesActualInitMainServicesProjectionRestoreAndForwardReplay(int rotationAngle) throws IOException {
        var fixture = new Fixture(); var f = fixture.runtime();
        var seed = new Actor(f); seed.rotationAngle = rotationAngle; seed.rotationDistance = 0x39; seed.install(f);
        service(f, 0); var init = f.runBody(1);
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, init.result().boundary());
        assertEquals(rotationAngle, f.state().rotationAngle());
        service(f, 0); var main = f.runBody(2);
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, main.result().boundary());
        assertEquals(rotationAngle, f.state().rotationAngle());
        var actor = f.captureActor(); var shared = f.captureShared();
        f.publishDrawObjectOnScreen(true); f.applyObjectYVelocity(-0xC00); f.applyObjectPrimaryAngle(0x40);
        assertEquals(rotationAngle, f.state().rotationAngle(), "Draw/Y-velocity/shared-angle ports preserve actual $2B");
        var changed = new Actor(f); changed.rotationAngle = rotationAngle ^ 0xFF; changed.install(f);
        assertEquals(rotationAngle ^ 0xFF, f.state().rotationAngle());
        f.restoreActor(actor); f.restoreShared(shared);
        assertEquals(actor, f.captureActor()); assertEquals(rotationAngle, f.state().rotationAngle());
        service(f, 8); var first = f.runBody(3);
        var forwardActor = f.captureActor(); var forwardShared = f.captureShared();
        assertEquals(rotationAngle, forwardActor.state().rotationAngle());
        f.restoreActor(actor); f.restoreShared(shared); service(f, 8); var replay = f.runBody(3);
        assertEquals(first, replay); assertEquals(forwardActor, f.captureActor());
        assertSharedValues(forwardShared, f.captureShared());
        assertEquals(rotationAngle, f.state().rotationAngle());
    }

    @ParameterizedTest @ValueSource(ints = {0, 5})
    void rotationAngleSurvivesActualPreHurtYPrefixHurtAndKillIncludingTheStickDistanceAlias(int rings) throws IOException {
        var fixture = new Fixture(); var f = hurtFrame(fixture, 0xBD, 0, 0, 0);
        assertEquals(0xD7, f.state().rotationAngle(), "Explicit dirty constructed ingress");
        var a = hurtActor(f); int y = a.motion().centreY16_16() - (a.motion().yVelocity() << 8);
        var beforeY = f.captureActor(); var sharedY = f.captureShared();
        f.applyObjectPreHurtYRewind(a, y);
        assertEquals(0xD7, f.state().rotationAngle());
        var afterY = f.captureActor();
        f.restoreActor(beforeY); f.applyObjectPreHurtYRewind(a, y);
        assertEquals(afterY, f.captureActor()); assertEquals(sharedY, f.captureShared());
        var actor = hurtActor(f); var globals = hurtPlayerGlobals(hurtGlobals(f), 2, 0);
        var pool = hurtPool(); var result = directFrameHurt(f, globals, pool, rings);
        assertEquals(rings == 0 ? CdPlayerTouchResponse.Exit.KILL_PLAYER : CdPlayerTouchResponse.Exit.HURT, result.exit());
        var before = f.captureActor(); var shared = f.captureShared();
        f.applyObjectHurtResult(actor, globals, result);
        hurtAssertAdopted(f, before, shared, result);
        assertEquals(0xD7, f.state().rotationAngle());
        if (rings == 0) {
            int postResetY = ((y >>> 16) - 5) & 0xFFFF;
            assertEquals(postResetY >>> 8, f.state().motion().stick());
            assertEquals(postResetY & 0xFF, f.state().rotationDistance());
            assertNotEquals(before.state().rotationDistance(), f.state().rotationDistance());
        }
        var after = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(before); f.restoreShared(shared); f.applyObjectHurtResult(actor, globals, result);
        assertEquals(after, f.captureActor()); assertEquals(afterShared, f.captureShared());
    }

    @Test void rotationAngleSurvivesCheckedSpringMotionAdoptionWithWholeStateEquality() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        var seed = new Actor(f); seed.rotationAngle = 0xE3; seed.install(f);
        var player = solidPlayer(f); var globals = solidGlobals(f); var next = player;
        for (var field : CdSpring.LaunchPlayerField.values()) next = springChanged(next, field.name());
        // This is the existing declared pure LaunchCall fixture, not a claim that an object dispatched.
        var call = springLaunch(player, globals, next, globals, java.util.EnumSet.allOf(CdSpring.LaunchPlayerField.class), Set.of());
        var before = f.captureActor(); var shared = f.captureShared(); var s = before.state();
        f.applyObjectSpringLaunch(call);
        assertEquals(new CdPlayerBody.State(next.actor().motion(), s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle()), f.state());
        assertEquals(0xE3, f.state().rotationAngle()); assertEquals(shared, f.captureShared());
        var after = f.captureActor(); f.restoreActor(before); f.applyObjectSpringLaunch(call);
        assertEquals(after, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void rotationAngleSurvivesTheActualPureTunnelResultAndWholeStateReplay() throws IOException {
        var fixture = new Fixture(); var f = spinTunnelDirtyFrame(fixture);
        var before = f.captureActor(); var shared = f.captureShared(); var s = before.state();
        assertEquals(0xD7, s.rotationAngle());
        var pool = new CdObjectPool();
        // Existing bounded format fixture; no authenticated mapping/retail or dispatch authority.
        var result = new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(0x200000))
                .step(spinTunnelObject(pool, s.motion(), 0), s.motion(), new CdSpinTunnelTag.Gates(0, 0, 0, 0)).player();
        f.applyObjectSpinTunnelResult(result);
        assertEquals(new CdPlayerBody.State(result.finalMotion(), s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle()), f.state());
        assertEquals(0xD7, f.state().rotationAngle()); assertEquals(shared, f.captureShared());
        var after = f.captureActor(); f.restoreActor(before); f.applyObjectSpinTunnelResult(result);
        assertEquals(after, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rotationAngleSurvivesRealSolidSuccessOrStoppedPrefixAdoptionAndReplay(boolean stopped) throws IOException {
        var fixture = new Fixture(); var f = spinTunnelDirtyFrame(fixture); var captured = f.captureActor();
        var s = captured.state(); var m = s.motion();
        var motion = new CdPlayerMotionState(stopped ? 0x01008000 : 64 << 16, stopped ? 0x00731234 : 1899 << 16,
                0x180, stopped ? 0 : 0x111, 0x345, 4, 0x60, stopped ? 0x0C : 4, 4, 9, 19, 2,
                m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var state = new CdPlayerBody.State(motion, s.animation(), s.header(), s.timers(), s.history(), s.rotationDistance(),
                new CdPlayerBody.ObjectLinks(0x7B, stopped ? 5 : 0), s.rotationAngle());
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(), state, captured.display()));
        var pool = new CdObjectPool();
        var object = stopped ? pool.state(40).moveByte(CdObjectState.ByteField.ID, 0x20)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x84).moveByte(CdObjectState.ByteField.WIDTH, 0x10)
                .moveByte(CdObjectState.ByteField.Y_RADIUS, 0x0C).moveWord(CdObjectState.WordField.X, 0x100)
                .moveWord(CdObjectState.WordField.Y, 0x90) : solidObject(pool);
        pool.write(object);
        var player = solidPlayer(f); var globals = solidGlobals(f); var actor = f.captureActor(); var shared = f.captureShared();
        var solid = CdSolidObject.fromAssets(f.assets());
        if (stopped) {
            var stop = assertInstanceOf(CdSolidObject.NativeStopped.class, solid.runWithPrefix(CdSolidObject.Entry.TOP_SOLID_OBJECT,
                    object, player, new CdSolidObject.Gates(0, 0), globals, pool.capture()));
            assertEquals(CdSolidObject.StopSite.STAND_RELEASE, stop.stop().site());
            var prefix = stop.prefix();
            f.applyObjectSolidPrefix(player, globals, prefix); prefixAssertAdopted(f, actor, shared, prefix);
            assertEquals(0xD7, f.state().rotationAngle());
            var after = f.captureActor(); var afterShared = f.captureShared();
            f.restoreActor(actor); f.restoreShared(shared); f.applyObjectSolidPrefix(player, globals, prefix);
            assertEquals(after, f.captureActor()); assertEquals(afterShared, f.captureShared());
        } else {
            var result = solid.run(CdSolidObject.Entry.STAND_ON_OBJECT, object, player, new CdSolidObject.Gates(0, 0),
                    globals, pool.capture());
            f.applyObjectSolidResult(player, globals, result);
            assertEquals(rotationAngleSolidExpected(state, result.player()), f.state());
            assertEquals(0xD7, f.state().rotationAngle());
            var after = f.captureActor(); var afterShared = f.captureShared();
            f.restoreActor(actor); f.restoreShared(shared); f.applyObjectSolidResult(player, globals, result);
            assertEquals(after, f.captureActor()); assertEquals(afterShared, f.captureShared());
        }
    }

    @Test void solidProjectionsWithoutRotationAngleCarryTheCurrentByteRatherThanCasAnUnrepresentedOldByte() throws IOException {
        var f = spinTunnelDirtyFrame(new Fixture()); var player = solidPlayer(f); var globals = solidGlobals(f);
        var result = solidResult(solidObject(new CdObjectPool()), player, globals, Set.of(), Set.of());
        var prefix = prefixFrameValues(player, globals, Set.of(), Set.of());
        assertEquals(0xD7, f.state().rotationAngle());
        var edit = new Actor(f); edit.rotationAngle = 0x35; edit.install(f);
        var actual = f.state(); var shared = f.captureShared();
        assertEquals(player, solidPlayer(f), "The old projection contains no $2B");
        f.applyObjectSolidResult(player, globals, result);
        assertEquals(actual, f.state()); assertEquals(0x35, f.state().rotationAngle());
        f.applyObjectSolidPrefix(player, globals, prefix);
        assertEquals(actual, f.state()); assertEquals(0x35, f.state().rotationAngle());
        assertEquals(shared, f.captureShared());
    }

    @Test void routineEightKeepsNonzeroRotationAngleThroughTheActualFrameAndTerminalResetReplay() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        var seed = new Actor(f); seed.routine = 8; seed.reset = 1; seed.rotationAngle = 0xA9; seed.install(f);
        var actor = f.captureActor(); var shared = f.captureShared();
        var context = Optional.of(new CdPlayerBody.LevelContext(new CdObjectPool().capture(),
                new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0), new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0, 0)));
        service(f, 0); var first = f.runBody(2, Optional.empty(), context);
        assertEquals(CdPlayerBody.Boundary.RESTART_RETURN, first.result().boundary());
        assertEquals(0, f.state().timers().reset()); assertEquals(0xA9, f.state().rotationAngle());
        assertEquals(1, first.result().effects().stream().filter(CdPlayerBody.RestartCut.class::isInstance).count());
        var after = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(actor); f.restoreShared(shared); service(f, 0); var replay = f.runBody(2, Optional.empty(), context);
        assertEquals(first, replay); assertEquals(after, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        assertEquals(0xA9, f.state().rotationAngle());
    }

    @Test void onlyANewLevelStartFactoryVisitClearsRotationAngleAndRestartIngressDoesNotCarryThePreviousByte() throws IOException {
        var assets = new Fixture().assets();
        try (var old = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets)) {
            var edit = new Actor(old); edit.rotationAngle = 0xBD; edit.install(old);
            var saved = old.captureActor(); var e = old.environment(); var g = e.movement().globals();
            var carried = new CdPlayerFrameRuntime.RestartIngress(old.state().history().bytes(), old.artState().cpuBuffer(),
                    old.artState().residentBank(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance(),
                    e.globals().debugCheat(), e.controls().rawHeld());
            try (var cold = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets);
                 var external = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets);
                 var declared = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 7);
                 var restarted = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 7, carried);
                 var checkpoint = CdPlayerFrameRuntime.checkpointRestartDevelopment(assets, 7, carried, port -> {
                     port.store(new CdPlayerFrameRuntime.CheckpointLoadStore(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X,
                             port.read(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X), 0x1234));
                     port.store(new CdPlayerFrameRuntime.CheckpointLoadStore(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_Y,
                             port.read(CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_Y), 0x5678));
                 })) {
                for (var fresh : List.of(cold, external, declared, restarted, checkpoint)) {
                    assertEquals(0, fresh.state().rotationAngle(), "Fresh ClearObjects-based LevelStart development visit");
                    assertEquals(0, fresh.state().motion().routine());
                    assertTrue(fresh.display().isEmpty()); assertEquals(0, fresh.vIntClock().deltaSinceAdmittedCut());
                }
                assertEquals(0x12340000, checkpoint.state().motion().centreX16_16());
                assertEquals(0x56780000, checkpoint.state().motion().centreY16_16());
                assertEquals(0xBD, old.state().rotationAngle(), "Constructing a new owner does not clear the previous one");
                old.restoreActor(saved); assertEquals(saved, old.captureActor()); assertEquals(0xBD, old.state().rotationAngle());
            }
        }
    }

    /** Expected full actor value only; Solid's projection has no authority to supply or compare $2B. */
    private static CdPlayerBody.State rotationAngleSolidExpected(CdPlayerBody.State s, CdSolidObject.Player player) {
        var a = player.actor(); var h = s.header(); var t = s.timers();
        return new CdPlayerBody.State(a.motion(), s.animation(),
                new CdPlayerBody.Header(h.slotId(), h.mappingAddress(), a.tile(), a.priority(), h.width()),
                new CdPlayerBody.Timers(a.hurt(), t.invincibility(), t.speedShoes(), t.reset()), s.history(), a.rotationDistance(),
                new CdPlayerBody.ObjectLinks(player.pushObject(), player.standObject()), s.rotationAngle());
    }
    // BEGIN prepared Solid/direct Disc Frame ports. Source-only additions: every Java check is UNRUN.
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void preparedSolidBuildsWithoutMutationThenAdoptsTheSameReferencesAfterARealPoolWrite(boolean stopped)
            throws IOException {
        var fixture = new Fixture();
        try (var f = framePortSolidFrame(fixture, stopped)) {
            var pool = new CdObjectPool(); var object = framePortSolidObject(pool);
            var player = solidPlayer(f); var globals = solidGlobals(f); var poolBefore = pool.capture();
            var actor = f.captureActor(); var shared = f.captureShared(); int uploads = fixture.dmas.size();
            f.bindArtPublication((request, value) -> fail("A prepared player publication cannot service art"));
            var outcome = CdSolidObject.fromAssets(f.assets()).runWithPrefix(CdSolidObject.Entry.SOLID_OBJECT,
                    object, player, new CdSolidObject.Gates(0, 0), globals, poolBefore);
            CdPlayerFrameRuntime.ObjectSolidPublication publication;
            CdObjectState finalObject; List<CdObjectState> otherSlots;
            if (stopped) {
                var nativeStop = assertInstanceOf(CdSolidObject.NativeStopped.class, outcome);
                assertEquals(CdSolidObject.StopSite.STAND_RELEASE, nativeStop.stop().site());
                var prefix = nativeStop.prefix();
                assertEquals(CdSolidObject.Entry.SOLID_OBJECT, prefix.entry());
                publication = f.prepareObjectSolidPrefix(player, globals, prefix);
                finalObject = prefix.object(); otherSlots = prefix.otherSlots();
            } else {
                var result = assertInstanceOf(CdSolidObject.Completed.class, outcome).completedResult();
                assertEquals(CdSolidObject.Entry.SOLID_OBJECT, result.entry());
                assertEquals(CdSolidObject.Path.STAND_ON, result.path());
                assertEquals(OptionalInt.of(1), result.d0(), "The real Solid producer owns this return");
                publication = f.prepareObjectSolidResult(player, globals, result);
                finalObject = result.object(); otherSlots = result.otherSlots();
            }
            assertSame(actor.state(), publication.expectedState());
            assertSame(shared.environment(), publication.expectedEnvironment());
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
            assertSame(actor.state(), f.state()); assertSame(shared.environment(), f.environment());
            assertEquals(poolBefore.slots(), pool.capture().slots());
            assertEquals(0xD7, publication.finalState().rotationAngle());
            assertSame(actor.state().animation(), publication.finalState().animation());
            assertSame(actor.state().history(), publication.finalState().history());
            assertEquals(actor.state().header().mappingAddress(), publication.finalState().header().mappingAddress());
            assertEquals(actor.state().header().width(), publication.finalState().header().width());
            assertEquals(actor.state().timers().reset(), publication.finalState().timers().reset());
            assertSame(shared.environment().controls(), publication.finalEnvironment().controls());
            assertSame(shared.environment().movement().physics(), publication.finalEnvironment().movement().physics());
            assertSame(shared.environment().movement().bounds(), publication.finalEnvironment().movement().bounds());
            assertSame(shared.environment().movement().gates(), publication.finalEnvironment().movement().gates());
            assertEquals(0x8000, publication.finalState().motion().centreX16_16() & 0xFFFF);
            assertEquals(0x1234, publication.finalState().motion().centreY16_16() & 0xFFFF);
            f.validatePreparedObjectSolidPublication(publication);
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
            var images = new ArrayList<CdObjectState>(); images.add(finalObject); images.addAll(otherSlots);
            var finalPool = CdObjectPool.overlay(poolBefore, images);
            pool.restore(finalPool);
            f.applyPreparedObjectSolidPublication(publication);
            assertSame(publication.finalState(), f.state());
            assertSame(publication.finalEnvironment(), f.environment());
            assertSame(actor.display(), f.display()); assertSame(shared.artState(), f.artState());
            assertSame(shared.vIntClock(), f.vIntClock()); assertEquals(uploads, fixture.dmas.size());
            assertEquals(finalPool.slots(), pool.capture().slots());
            var preparedActor = f.captureActor(); var preparedShared = f.captureShared();
            assertThrows(IllegalArgumentException.class, () -> f.applyPreparedObjectSolidPublication(publication));
            assertEquals(preparedActor, f.captureActor()); assertEquals(preparedShared, f.captureShared());
            f.restoreActor(actor); f.restoreShared(shared);
            if (outcome instanceof CdSolidObject.Completed completed) {
                f.applyObjectSolidResult(player, globals, completed.completedResult());
            } else {
                f.applyObjectSolidPrefix(player, globals, ((CdSolidObject.NativeStopped) outcome).prefix());
            }
            assertEquals(preparedActor, f.captureActor()); assertEquals(preparedShared, f.captureShared());
            assertEquals(finalPool.slots(), pool.capture().slots()); assertEquals(uploads, fixture.dmas.size());
        }
    }

    @Test void preparedSolidSameValueAndEqualFrameAcceptanceAreOnlyObservationalValueCas() throws IOException {
        try (var a = spinTunnelDirtyFrame(new Fixture()); var b = spinTunnelDirtyFrame(new Fixture())) {
            var pool = new CdObjectPool(); var object = framePortSolidObject(pool);
            var player = solidPlayer(a); var globals = solidGlobals(a);
            var result = CdSolidObject.fromAssets(a.assets()).run(CdSolidObject.Entry.GET_OFF_OBJECT, object,
                    player, new CdSolidObject.Gates(0, 0), globals, pool.capture());
            assertEquals(player, result.player()); assertEquals(globals, result.globals());
            var p = a.prepareObjectSolidResult(player, globals, result);
            var beforeA = a.captureActor(); var beforeB = b.captureActor(); var sharedB = b.captureShared();
            a.applyPreparedObjectSolidPublication(p); a.applyPreparedObjectSolidPublication(p);
            assertEquals(beforeA, a.captureActor()); assertSame(p.finalState(), a.state());
            b.validatePreparedObjectSolidPublication(p); b.applyPreparedObjectSolidPublication(p);
            assertEquals(beforeB, b.captureActor()); assertEquals(sharedB, b.captureShared());
            assertSame(p.finalState(), b.state()); assertSame(p.finalEnvironment(), b.environment());
            assertNotSame(beforeA.owner(), beforeB.owner(), "Data equality supplies no private invocation authority");
        }
    }

    @Test void preparedSolidCapturesCurrentUnrepresentedFieldsRatherThanAnOlderProjection() throws IOException {
        try (var f = spinTunnelDirtyFrame(new Fixture())) {
            var player = solidPlayer(f); var globals = solidGlobals(f);
            var pool = new CdObjectPool(); var object = framePortSolidObject(pool);
            var result = CdSolidObject.fromAssets(f.assets()).run(CdSolidObject.Entry.GET_OFF_OBJECT, object,
                    player, new CdSolidObject.Gates(0, 0), globals, pool.capture());
            var old = f.captureActor(); var changed = framePortChangedState(old.state(), "ROTATION_ANGLE");
            changed = framePortChangedState(changed, "SCRIPT_INDEX");
            changed = framePortChangedState(changed, "RESET");
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(old.owner(), changed, old.display()));
            assertEquals(player, solidPlayer(f), "The historical Solid projection omits these three domains");
            var p = f.prepareObjectSolidResult(player, globals, result);
            assertSame(changed, p.expectedState()); assertEquals(changed, p.finalState());
            f.applyPreparedObjectSolidPublication(p); assertEquals(changed, f.state());
        }
    }

    @Test void preparedSolidDataHasOnlyFourFinalValuesAndAPrivateFourArgumentConstructor() {
        var type = CdPlayerFrameRuntime.ObjectSolidPublication.class;
        assertTrue(java.lang.reflect.Modifier.isPublic(type.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isStatic(type.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isFinal(type.getModifiers()));
        var constructors = type.getDeclaredConstructors(); assertEquals(1, constructors.length);
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{CdPlayerBody.State.class, CdPlayerBody.Environment.class,
                CdPlayerBody.State.class, CdPlayerBody.Environment.class}, constructors[0].getParameterTypes());
        var fields = type.getDeclaredFields(); assertEquals(4, fields.length);
        var actual = new LinkedHashMap<String, Class<?>>();
        for (var field : fields) {
            assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));
            assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));
            assertFalse(java.lang.reflect.Modifier.isStatic(field.getModifiers()));
            actual.put(field.getName(), field.getType());
        }
        assertEquals(Map.of("expectedState", CdPlayerBody.State.class, "expectedEnvironment", CdPlayerBody.Environment.class,
                "finalState", CdPlayerBody.State.class, "finalEnvironment", CdPlayerBody.Environment.class), actual);
        assertEquals(Set.of("expectedState", "expectedEnvironment", "finalState", "finalEnvironment"),
                Arrays.stream(type.getDeclaredMethods()).map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet()));
        for (var method : type.getDeclaredMethods()) {
            assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
            assertFalse(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
            assertEquals(0, method.getParameterCount());
        }
    }

    @Test void preparedSolidPreparationKeepsExistingValidationOrderAndMasks() throws IOException {
        try (var f = spinTunnelDirtyFrame(new Fixture())) {
            var player = solidPlayer(f); var globals = solidGlobals(f); var pool = new CdObjectPool();
            var result = CdSolidObject.fromAssets(f.assets()).run(CdSolidObject.Entry.GET_OFF_OBJECT,
                    framePortSolidObject(pool), player, new CdSolidObject.Gates(0, 0), globals, pool.capture());
            var before = f.captureActor(); var shared = f.captureShared();
            var prefix = prefixFrameValues(player, globals, Set.of(), Set.of());
            assertThrows(NullPointerException.class, () -> f.prepareObjectSolidResult(null, globals, result));
            assertThrows(NullPointerException.class, () -> f.prepareObjectSolidResult(player, null, result));
            assertThrows(NullPointerException.class, () -> f.prepareObjectSolidResult(player, globals, null));
            assertThrows(NullPointerException.class, () -> f.prepareObjectSolidPrefix(null, globals, prefix));
            assertThrows(NullPointerException.class, () -> f.prepareObjectSolidPrefix(player, null, prefix));
            assertThrows(NullPointerException.class, () -> f.prepareObjectSolidPrefix(player, globals, null));
            assertThrows(NullPointerException.class, () -> f.validatePreparedObjectSolidPublication(null));
            assertThrows(NullPointerException.class, () -> f.applyPreparedObjectSolidPublication(null));
            var stale = springChanged(player, "CHARGE");
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSolidResult(stale, globals, result));
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSolidPrefix(stale, globals, prefix));
            var badPlayer = springChanged(player, "PREVIOUS_ANIMATION");
            var badResult = solidResult(result.object(), badPlayer, globals,
                    java.util.EnumSet.allOf(CdSolidObject.PlayerField.class), Set.of());
            var badPrefix = prefixFrameValues(badPlayer, globals,
                    java.util.EnumSet.allOf(CdSolidObject.PlayerField.class), Set.of());
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSolidResult(player, globals, badResult));
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSolidPrefix(player, globals, badPrefix));
            var undeclared = springChanged(player, "X_FRACTION");
            var hiddenResult = solidResult(result.object(), undeclared, globals, Set.of(), Set.of());
            var hiddenPrefix = prefixFrameValues(undeclared, globals, Set.of(), Set.of());
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSolidResult(player, globals, hiddenResult));
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSolidPrefix(player, globals, hiddenPrefix));
            var changedGlobals = prefixChangedGlobals(globals, 3);
            var hiddenGlobalsResult = solidResult(result.object(), player, changedGlobals, Set.of(), Set.of());
            var hiddenGlobalsPrefix = prefixFrameValues(player, changedGlobals, Set.of(), Set.of());
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSolidResult(player, globals, hiddenGlobalsResult));
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSolidPrefix(player, globals, hiddenGlobalsPrefix));
            assertEquals(before, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    private static CdPlayerFrameRuntime framePortSolidFrame(Fixture fixture, boolean stopped) throws IOException {
        var f = spinTunnelDirtyFrame(fixture); var captured = f.captureActor(); var s = captured.state(); var m = s.motion();
        var motion = new CdPlayerMotionState(0x01008000, 0x00731234, 0x123, 0, 0x345,
                4, 0x60, stopped ? 0x0C : 4, m.spriteFlags(), 9, 19, 2, m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(),
                new CdPlayerBody.State(motion, s.animation(), s.header(), s.timers(), s.history(), s.rotationDistance(),
                        new CdPlayerBody.ObjectLinks(0x7B, stopped ? 5 : 0), s.rotationAngle()), captured.display()));
        return f;
    }

    private static CdObjectState framePortSolidObject(CdObjectPool pool) {
        var object = pool.state(40).moveByte(CdObjectState.ByteField.ID, 0x20)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x84).moveByte(CdObjectState.ByteField.WIDTH, 0x10)
                .moveByte(CdObjectState.ByteField.Y_RADIUS, 0x0C).moveWord(CdObjectState.WordField.X, 0x100)
                .moveWord(CdObjectState.WordField.Y, 0x90);
        pool.write(object); return object;
    }


    @ParameterizedTest @ValueSource(strings = {"CENTRE_X", "X_FRACTION", "CENTRE_Y", "Y_FRACTION", "X_VELOCITY", "Y_VELOCITY", "GROUND_VELOCITY", "ROUTINE", "ANGLE", "FLAGS", "SPRITE_FLAGS", "X_RADIUS", "Y_RADIUS", "ANIMATION", "PREVIOUS_ANIMATION", "PLAYER_JUMP", "CHARGE", "MOVE_LOCK", "STICK", "PLAYER_CONTROL", "PREVIOUS_PRIMARY_ANGLE", "PREVIOUS_SECONDARY_ANGLE", "SCRIPT_INDEX", "ANIMATION_TIMER", "MAP_FRAME", "SLOT_ID", "MAPPING_ADDRESS", "TILE", "PRIORITY", "WIDTH", "HURT", "INVINCIBILITY", "SPEED_SHOES", "RESET", "HISTORY_INDEX", "ROTATION_DISTANCE", "PUSH_OBJECT", "STAND_OBJECT", "ROTATION_ANGLE", "HISTORY_BYTES"})
    void preparedAndDiscPortsCasEveryActualStateFieldAndSharedValidatorRejectsItsUndeclaredChange(String field)
            throws IOException {
        try (var f = framePortDiscFrame(new Fixture())) {
            var actor = f.captureActor(); var shared = f.captureShared(); var s = actor.state(); var e = shared.environment();
            var changed = framePortChangedState(s, field); assertNotEquals(s, changed, field);
            var pool = new CdObjectPool();
            var solid = CdSolidObject.fromAssets(f.assets()).run(CdSolidObject.Entry.GET_OFF_OBJECT,
                    framePortSolidObject(pool), solidPlayer(f), new CdSolidObject.Gates(0, 0), solidGlobals(f), pool.capture());
            var publication = f.prepareObjectSolidResult(solidPlayer(f), solidGlobals(f), solid);
            var noWrite = new CdSpinningDisc.PlayerResult(s, e, List.of());
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), changed, actor.display()));
            framePortPreparedRejected(f, publication, IllegalArgumentException.class);
            framePortDiscRejected(f, s, e, noWrite, IllegalArgumentException.class);
            f.restoreActor(actor);
            var malicious = new CdSpinningDisc.PlayerResult(changed, e, List.of());
            var sharedFailure = assertThrows(IllegalArgumentException.class,
                    () -> CdSpinningDisc.validatePlayerResult(new CdSpinningDisc.PlayerCut(s, e), malicious));
            var frameFailure = assertThrows(IllegalArgumentException.class,
                    () -> f.validateObjectDiscPlayerResult(s, e, malicious));
            assertEquals(sharedFailure.getMessage(), frameFailure.getMessage(), "Same shared validator owns nonwrites");
            framePortDiscRejected(f, s, e, malicious, IllegalArgumentException.class);
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"TOP_SPEED", "ACCELERATION", "DECELERATION", "BOUND_LEFT", "BOUND_RIGHT", "BOUND_BOTTOM", "BOUND_BOSS_FIGHT", "GATE_ZONE_ACT", "GATE_TIME_ZONE", "GATE_MINI_SONIC", "GATE_WATER_SLIDE", "GATE_WIND_TUNNEL", "GATE_SPEED_SHOES", "GATE_TIME_WARP_DIRECTION", "MOVEMENT_LOOK_MODE", "MOVEMENT_CAMERA_Y_CENTRE", "MOVEMENT_PRIMARY_ANGLE", "MOVEMENT_SECONDARY_ANGLE", "MOVEMENT_BORED_TIMER", "MOVEMENT_SCORE_CHAIN", "MOVEMENT_DEBUG_ANGLE", "MOVEMENT_DEBUG_ANGLE_SHIFT", "MOVEMENT_DEBUG_QUADRANT", "MOVEMENT_DEBUG_FLOOR_DISTANCE", "RAW_HELD", "RAW_TAP", "LOGICAL_HELD", "LOGICAL_TAP", "CONTROL_LOCKED", "BODY_LEVEL_FRAMES", "BODY_CAMERA_X_CENTRE", "BODY_TIME_ATTACK_MODE", "BODY_DEBUG_MODE", "BODY_DEBUG_CHEAT", "BODY_TIME_WARP_TIMER", "BODY_INVINCIBLE", "BODY_BOSS_MUSIC", "DYNAMIC_SLOTS"})
    void preparedAndDiscPortsCasEveryEnvironmentFieldAndSharedValidatorRejectsItsUndeclaredChange(String field)
            throws IOException {
        try (var f = framePortDiscFrame(new Fixture())) {
            var actor = f.captureActor(); var shared = f.captureShared(); var s = actor.state(); var e = shared.environment();
            var changed = framePortChangedEnvironment(e, field); assertNotEquals(e, changed, field);
            var pool = new CdObjectPool();
            var solid = CdSolidObject.fromAssets(f.assets()).run(CdSolidObject.Entry.GET_OFF_OBJECT,
                    framePortSolidObject(pool), solidPlayer(f), new CdSolidObject.Gates(0, 0), solidGlobals(f), pool.capture());
            var publication = f.prepareObjectSolidResult(solidPlayer(f), solidGlobals(f), solid);
            var noWrite = new CdSpinningDisc.PlayerResult(s, e, List.of());
            f.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), changed,
                    shared.artState(), shared.paused(), shared.vIntClock()));
            framePortPreparedRejected(f, publication, IllegalArgumentException.class);
            framePortDiscRejected(f, s, e, noWrite, IllegalArgumentException.class);
            f.restoreShared(shared);
            var malicious = new CdSpinningDisc.PlayerResult(s, changed, List.of());
            var sharedFailure = assertThrows(IllegalArgumentException.class,
                    () -> CdSpinningDisc.validatePlayerResult(new CdSpinningDisc.PlayerCut(s, e), malicious));
            var frameFailure = assertThrows(IllegalArgumentException.class,
                    () -> f.validateObjectDiscPlayerResult(s, e, malicious));
            assertEquals(sharedFailure.getMessage(), frameFailure.getMessage(), "Same shared validator owns nonwrites");
            framePortDiscRejected(f, s, e, malicious, IllegalArgumentException.class);
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    private static void framePortPreparedRejected(CdPlayerFrameRuntime f,
            CdPlayerFrameRuntime.ObjectSolidPublication publication, Class<? extends RuntimeException> type) {
        var s = f.state(); var e = f.environment(); var a = f.artState(); var d = f.display(); var v = f.vIntClock();
        assertThrows(type, () -> f.validatePreparedObjectSolidPublication(publication));
        assertThrows(type, () -> f.applyPreparedObjectSolidPublication(publication));
        assertSame(s, f.state()); assertSame(e, f.environment()); assertSame(a, f.artState());
        assertSame(d, f.display()); assertSame(v, f.vIntClock());
    }

    private static void framePortDiscRejected(CdPlayerFrameRuntime f, CdPlayerBody.State expectedState,
            CdPlayerBody.Environment expectedEnvironment, CdSpinningDisc.PlayerResult result,
            Class<? extends RuntimeException> type) {
        var s = f.state(); var e = f.environment(); var a = f.artState(); var d = f.display(); var v = f.vIntClock();
        assertThrows(type, () -> f.validateObjectDiscPlayerResult(expectedState, expectedEnvironment, result));
        assertThrows(type, () -> f.applyObjectDiscPlayerResult(expectedState, expectedEnvironment, result));
        assertSame(s, f.state()); assertSame(e, f.environment()); assertSame(a, f.artState());
        assertSame(d, f.display()); assertSame(v, f.vIntClock());
    }


    /** Constructed immutable single-site deltas test the real Frame/shared validator, not native execution. */
    @ParameterizedTest @CsvSource({
            "CONTROL_ACQUIRE,PLAYER_CONTROL,0xA4,0xA5",
            "ANIMATION_ACQUIRE,ANIMATION,0x1A,0x2D",
            "ROTATION_ZERO,ROTATION_ANGLE,0xD7,0",
            "ROTATION_LEFT,ROTATION_ANGLE,0xD7,0x80",
            "DISTANCE_ACQUIRE,ROTATION_DISTANCE,0xAB,0xFE",
            "ROTATION_ADVANCE,ROTATION_ANGLE,0xD7,0xDF",
            "ORBIT_X_BASE,CENTRE_X,0xFFFC,1",
            "ORBIT_X_OFFSET,CENTRE_X,0xFFFC,2",
            "SCRIPT_INDEX,SCRIPT_INDEX,5,0xF1",
            "DISTANCE_CARDINAL,ROTATION_DISTANCE,0xAB,0xAC",
            "LOGICAL_CONTROLS,LOGICAL_CONTROLS,0x0810,0x2C24",
            "DISTANCE_GROW,ROTATION_DISTANCE,0xAB,0xAC",
            "DISTANCE_SHRINK,ROTATION_DISTANCE,0xAB,0xAA",
            "DISTANCE_CLAMP,ROTATION_DISTANCE,0xFF,0",
            "CONTROL_CLEAR,PLAYER_CONTROL,0xA4,0",
            "JUMP_X_VELOCITY,X_VELOCITY,0x0100,0x8001",
            "JUMP_Y_VELOCITY,Y_VELOCITY,0xFDCC,0x7FFF",
            "FLAGS_AIR,FLAGS,0xE9,0xEB",
            "FLAGS_PUSH_CLEAR,FLAGS,0xE9,0xC9",
            "JUMP_SET,PLAYER_JUMP,0x76,1",
            "STICK_CLEAR,STICK,0x85,0",
            "JUMP_Y_RADIUS_MINI,Y_RADIUS,19,10",
            "JUMP_X_RADIUS_MINI,X_RADIUS,9,5",
            "JUMP_Y_RADIUS_NORMAL,Y_RADIUS,19,19",
            "JUMP_X_RADIUS_NORMAL,X_RADIUS,9,9",
            "ROLL_Y_RADIUS_MINI,Y_RADIUS,19,10",
            "ROLL_X_RADIUS_MINI,X_RADIUS,9,5",
            "ROLL_Y_RADIUS_NORMAL,Y_RADIUS,19,14",
            "ROLL_X_RADIUS_NORMAL,X_RADIUS,9,7",
            "ROLL_Y_ADVANCE,CENTRE_Y,0xFFFD,2",
            "FLAGS_ROLL,FLAGS,0xE9,0xED",
            "ANIMATION_ROLL,ANIMATION,0x1A,2",
            "FLAGS_ROLL_JUMP,FLAGS,0xE9,0xF9"
    })
    void directDiscAdoptsEveryFixedSiteAsAPrebuiltFullPairAndPreservesEveryNonwrite(
            CdSpinningDisc.DiscPlayerField field, String target, int expectedRaw, int value) throws IOException {
        var fixture = new Fixture();
        try (var f = framePortDiscFrame(fixture)) {
            if (field == CdSpinningDisc.DiscPlayerField.DISTANCE_CLAMP) {
                var a = f.captureActor();
                f.restoreActor(new CdPlayerFrameRuntime.ActorState(a.owner(),
                        framePortReplaceState(a.state(), "ROTATION_DISTANCE", 255), a.display()));
            }
            var actor = f.captureActor(); var shared = f.captureShared(); var s = actor.state(); var e = shared.environment();
            var finalState = target.equals("LOGICAL_CONTROLS") ? s : framePortReplaceState(s, target, value);
            var finalEnvironment = target.equals("LOGICAL_CONTROLS") ? framePortLogicalWord(e, value) : e;
            var store = new CdSpinningDisc.PlayerStore(field, expectedRaw, value);
            var stores = new ArrayList<CdSpinningDisc.PlayerStore>(); stores.add(store);
            var result = new CdSpinningDisc.PlayerResult(finalState, finalEnvironment, stores);
            stores.clear(); assertEquals(List.of(store), result.stores());
            assertThrows(UnsupportedOperationException.class, () -> result.stores().add(store));
            int uploads = fixture.dmas.size();
            f.bindArtPublication((request, state) -> fail("Direct Disc adoption cannot publish art or replay callbacks"));
            CdSpinningDisc.validatePlayerResult(new CdSpinningDisc.PlayerCut(s, e), result);
            f.validateObjectDiscPlayerResult(s, e, result);
            assertSame(s, f.state()); assertSame(e, f.environment());
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
            f.applyObjectDiscPlayerResult(s, e, result);
            assertSame(finalState, f.state()); assertSame(finalEnvironment, f.environment());
            assertEquals(new CdPlayerFrameRuntime.ActorState(actor.owner(), finalState, actor.display()), f.captureActor());
            assertEquals(new CdPlayerFrameRuntime.SharedState(shared.owner(), finalEnvironment, shared.artState(),
                    shared.paused(), shared.vIntClock()), f.captureShared());
            assertSame(actor.display(), f.display()); assertSame(shared.artState(), f.artState());
            assertSame(shared.vIntClock(), f.vIntClock()); assertEquals(uploads, fixture.dmas.size());
            assertEquals(s.motion().centreX16_16() & 0xFFFF, f.state().motion().centreX16_16() & 0xFFFF);
            assertEquals(s.motion().centreY16_16() & 0xFFFF, f.state().motion().centreY16_16() & 0xFFFF);
            assertEquals(s.animation().timer(), f.state().animation().timer());
            assertEquals(s.animation().mapFrame(), f.state().animation().mapFrame(), "Pose writes $1B, never map $1A");
            assertEquals(s.motion().previousAnimation(), f.state().motion().previousAnimation());
            assertEquals(s.motion().charge(), f.state().motion().charge());
            assertEquals(s.motion().groundVelocity(), f.state().motion().groundVelocity());
            assertEquals(s.motion().moveLock(), f.state().motion().moveLock());
            assertEquals(s.header(), f.state().header()); assertEquals(s.timers(), f.state().timers());
            assertEquals(s.history(), f.state().history()); assertEquals(s.objectLinks(), f.state().objectLinks());
            assertEquals(e.movement(), f.environment().movement()); assertEquals(e.globals(), f.environment().globals());
            assertEquals(e.dynamicSlots(), f.environment().dynamicSlots());
            assertEquals(e.controls().rawHeld(), f.environment().controls().rawHeld());
            assertEquals(e.controls().rawTap(), f.environment().controls().rawTap());
            assertEquals(e.controls().controlLocked(), f.environment().controls().controlLocked());
            if (!finalState.equals(s) || !finalEnvironment.equals(e)) {
                framePortDiscRejected(f, s, e, result, IllegalArgumentException.class);
            } else {
                f.applyObjectDiscPlayerResult(s, e, result); // Equal-valued data is not a one-use authority.
                assertSame(finalState, f.state()); assertSame(finalEnvironment, f.environment());
            }
            f.restoreActor(actor); f.restoreShared(shared); f.applyObjectDiscPlayerResult(s, e, result);
            assertSame(finalState, f.state()); assertSame(finalEnvironment, f.environment());
            assertEquals(uploads, fixture.dmas.size());
        }
    }

    @ParameterizedTest @EnumSource(CdSpinningDisc.DiscPlayerField.class)
    void directDiscMetadataRejectsBothOutOfWidthOperandsForEverySite(CdSpinningDisc.DiscPlayerField field) {
        int overflow = field.widthBits() == 8 ? 0x100 : 0x10000;
        for (int bad : new int[]{-1, overflow}) {
            assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerStore(field, bad, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerStore(field, 0, bad));
        }
        assertEquals(33, CdSpinningDisc.DiscPlayerField.values().length);
        assertEquals(9, CdSpinningDisc.StoreKind.values().length);
    }


    /** Test-only field replacement over genuine captures; never constructs or forges an owner. */
    private static CdPlayerBody.State framePortChangedState(CdPlayerBody.State s, String key) {
        if (key.equals("HISTORY_BYTES")) {
            var bytes = s.history().bytes().copy(); bytes[0xA7] ^= 1;
            return new CdPlayerBody.State(s.motion(), s.animation(), s.header(), s.timers(),
                    new CdPlayerBody.History(CdAssetBytes.copyOf(bytes), s.history().index()),
                    s.rotationDistance(), s.objectLinks(), s.rotationAngle());
        }
        var m = s.motion(); var a = s.animation(); var h = s.header(); var t = s.timers();
        int raw = switch (key) {
            case "CENTRE_X" -> m.centreX16_16() >>> 16;
            case "X_FRACTION" -> m.centreX16_16() & 0xFFFF;
            case "CENTRE_Y" -> m.centreY16_16() >>> 16;
            case "Y_FRACTION" -> m.centreY16_16() & 0xFFFF;
            case "X_VELOCITY" -> m.xVelocity();
            case "Y_VELOCITY" -> m.yVelocity();
            case "GROUND_VELOCITY" -> m.groundVelocity();
            case "ROUTINE" -> m.routine();
            case "ANGLE" -> m.angle();
            case "FLAGS" -> m.flags();
            case "SPRITE_FLAGS" -> m.spriteFlags();
            case "X_RADIUS" -> m.xRadius();
            case "Y_RADIUS" -> m.yRadius();
            case "ANIMATION" -> m.animation();
            case "PREVIOUS_ANIMATION" -> m.previousAnimation();
            case "PLAYER_JUMP" -> m.playerJump();
            case "CHARGE" -> m.charge();
            case "MOVE_LOCK" -> m.moveLock();
            case "STICK" -> m.stick();
            case "PLAYER_CONTROL" -> m.playerControl();
            case "PREVIOUS_PRIMARY_ANGLE" -> m.previousPrimaryAngle();
            case "PREVIOUS_SECONDARY_ANGLE" -> m.previousSecondaryAngle();
            case "SCRIPT_INDEX" -> a.scriptIndex();
            case "ANIMATION_TIMER" -> a.timer();
            case "MAP_FRAME" -> a.mapFrame();
            case "SLOT_ID" -> h.slotId();
            case "MAPPING_ADDRESS" -> h.mappingAddress();
            case "TILE" -> h.tile();
            case "PRIORITY" -> h.priority();
            case "WIDTH" -> h.width();
            case "HURT" -> t.hurt();
            case "INVINCIBILITY" -> t.invincibility();
            case "SPEED_SHOES" -> t.speedShoes();
            case "RESET" -> t.reset();
            case "HISTORY_INDEX" -> s.history().index();
            case "ROTATION_DISTANCE" -> s.rotationDistance();
            case "PUSH_OBJECT" -> s.objectLinks().pushObject();
            case "STAND_OBJECT" -> s.objectLinks().standObject();
            case "ROTATION_ANGLE" -> s.rotationAngle();
            default -> throw new IllegalArgumentException("Unknown State test field " + key);
        };
        return framePortReplaceState(s, key, raw ^ 1);
    }

    /** Explicit expected-value construction; native operation constants live in the parameter vectors. */
    private static CdPlayerBody.State framePortReplaceState(CdPlayerBody.State s, String key, int value) {
        var m = s.motion(); var a = s.animation(); var h = s.header(); var t = s.timers();
        var motion = new CdPlayerMotionState(
                key.equals("CENTRE_X") ? (value << 16) | (m.centreX16_16() & 0xFFFF) : key.equals("X_FRACTION") ? (m.centreX16_16() & 0xFFFF0000) | value : m.centreX16_16(),
                key.equals("CENTRE_Y") ? (value << 16) | (m.centreY16_16() & 0xFFFF) : key.equals("Y_FRACTION") ? (m.centreY16_16() & 0xFFFF0000) | value : m.centreY16_16(),
                key.equals("X_VELOCITY") ? (short) value : m.xVelocity(),
                key.equals("Y_VELOCITY") ? (short) value : m.yVelocity(),
                key.equals("GROUND_VELOCITY") ? (short) value : m.groundVelocity(),
                key.equals("ROUTINE") ? value : m.routine(),
                key.equals("ANGLE") ? value : m.angle(),
                key.equals("FLAGS") ? value : m.flags(),
                key.equals("SPRITE_FLAGS") ? value : m.spriteFlags(),
                key.equals("X_RADIUS") ? value : m.xRadius(),
                key.equals("Y_RADIUS") ? value : m.yRadius(),
                key.equals("ANIMATION") ? value : m.animation(),
                key.equals("PREVIOUS_ANIMATION") ? value : m.previousAnimation(),
                key.equals("PLAYER_JUMP") ? value : m.playerJump(),
                key.equals("CHARGE") ? value : m.charge(),
                key.equals("MOVE_LOCK") ? (short) value : m.moveLock(),
                key.equals("STICK") ? value : m.stick(),
                key.equals("PLAYER_CONTROL") ? value : m.playerControl(),
                key.equals("PREVIOUS_PRIMARY_ANGLE") ? value : m.previousPrimaryAngle(),
                key.equals("PREVIOUS_SECONDARY_ANGLE") ? value : m.previousSecondaryAngle());
        return new CdPlayerBody.State(motion,
                new CdPlayerBody.AnimationCursor(key.equals("SCRIPT_INDEX") ? value : a.scriptIndex(),
                        key.equals("ANIMATION_TIMER") ? value : a.timer(),
                        key.equals("MAP_FRAME") ? value : a.mapFrame()),
                new CdPlayerBody.Header(key.equals("SLOT_ID") ? value : h.slotId(),
                        key.equals("MAPPING_ADDRESS") ? value : h.mappingAddress(),
                        key.equals("TILE") ? value : h.tile(),
                        key.equals("PRIORITY") ? value : h.priority(),
                        key.equals("WIDTH") ? value : h.width()),
                new CdPlayerBody.Timers(key.equals("HURT") ? value : t.hurt(),
                        key.equals("INVINCIBILITY") ? value : t.invincibility(),
                        key.equals("SPEED_SHOES") ? value : t.speedShoes(),
                        key.equals("RESET") ? value : t.reset()),
                key.equals("HISTORY_INDEX") ? new CdPlayerBody.History(s.history().bytes(), value) : s.history(),
                key.equals("ROTATION_DISTANCE") ? value : s.rotationDistance(),
                new CdPlayerBody.ObjectLinks(key.equals("PUSH_OBJECT") ? value : s.objectLinks().pushObject(),
                        key.equals("STAND_OBJECT") ? value : s.objectLinks().standObject()),
                key.equals("ROTATION_ANGLE") ? value : s.rotationAngle());
    }

    private static CdPlayerBody.Environment framePortChangedEnvironment(CdPlayerBody.Environment e, String key) {
        if (key.equals("DYNAMIC_SLOTS")) {
            var other = e.dynamicSlots() == CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96
                    ? CdPlayerBody.DynamicSlots.UNIMPLEMENTED : CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96;
            return new CdPlayerBody.Environment(e.movement(), e.controls(), e.globals(), other);
        }
        var m = e.movement(); var p = m.physics(); var b = m.bounds(); var q = m.gates(); var g = m.globals();
        var c = e.controls(); var z = e.globals();
        int raw = switch (key) {
            case "TOP_SPEED" -> p.topSpeed();
            case "ACCELERATION" -> p.acceleration();
            case "DECELERATION" -> p.deceleration();
            case "BOUND_LEFT" -> b.left();
            case "BOUND_RIGHT" -> b.right();
            case "BOUND_BOTTOM" -> b.bottom();
            case "BOUND_BOSS_FIGHT" -> b.bossFight();
            case "GATE_ZONE_ACT" -> q.zoneAct();
            case "GATE_TIME_ZONE" -> q.timeZone();
            case "GATE_MINI_SONIC" -> q.miniSonic();
            case "GATE_WATER_SLIDE" -> q.waterSlide();
            case "GATE_WIND_TUNNEL" -> q.windTunnel();
            case "GATE_SPEED_SHOES" -> q.speedShoes();
            case "GATE_TIME_WARP_DIRECTION" -> q.timeWarpDirection();
            case "MOVEMENT_LOOK_MODE" -> g.lookMode();
            case "MOVEMENT_CAMERA_Y_CENTRE" -> g.cameraYCentre();
            case "MOVEMENT_PRIMARY_ANGLE" -> g.primaryAngle();
            case "MOVEMENT_SECONDARY_ANGLE" -> g.secondaryAngle();
            case "MOVEMENT_BORED_TIMER" -> g.boredTimer();
            case "MOVEMENT_SCORE_CHAIN" -> g.scoreChain();
            case "MOVEMENT_DEBUG_ANGLE" -> g.debugAngle();
            case "MOVEMENT_DEBUG_ANGLE_SHIFT" -> g.debugAngleShift();
            case "MOVEMENT_DEBUG_QUADRANT" -> g.debugQuadrant();
            case "MOVEMENT_DEBUG_FLOOR_DISTANCE" -> g.debugFloorDistance();
            case "RAW_HELD" -> c.rawHeld();
            case "RAW_TAP" -> c.rawTap();
            case "LOGICAL_HELD" -> c.logicalHeld();
            case "LOGICAL_TAP" -> c.logicalTap();
            case "CONTROL_LOCKED" -> c.controlLocked();
            case "BODY_LEVEL_FRAMES" -> z.levelFrames();
            case "BODY_CAMERA_X_CENTRE" -> z.cameraXCentre();
            case "BODY_TIME_ATTACK_MODE" -> z.timeAttackMode();
            case "BODY_DEBUG_MODE" -> z.debugMode();
            case "BODY_DEBUG_CHEAT" -> z.debugCheat();
            case "BODY_TIME_WARP_TIMER" -> z.timeWarpTimer();
            case "BODY_INVINCIBLE" -> z.invincible();
            case "BODY_BOSS_MUSIC" -> z.bossMusic();
            default -> throw new IllegalArgumentException("Unknown Environment test field " + key);
        };
        int value = raw ^ 1;
        var movement = new CdPlayerMovement.Environment(
                new CdPlayerMovement.Physics(key.equals("TOP_SPEED") ? (short) value : p.topSpeed(),
                        key.equals("ACCELERATION") ? (short) value : p.acceleration(),
                        key.equals("DECELERATION") ? (short) value : p.deceleration()),
                new CdPlayerMovement.Bounds(key.equals("BOUND_LEFT") ? value : b.left(),
                        key.equals("BOUND_RIGHT") ? value : b.right(),
                        key.equals("BOUND_BOTTOM") ? value : b.bottom(),
                        key.equals("BOUND_BOSS_FIGHT") ? value : b.bossFight()),
                new CdPlayerMovement.Gates(key.equals("GATE_ZONE_ACT") ? value : q.zoneAct(),
                        key.equals("GATE_TIME_ZONE") ? value : q.timeZone(),
                        key.equals("GATE_MINI_SONIC") ? value : q.miniSonic(),
                        key.equals("GATE_WATER_SLIDE") ? value : q.waterSlide(),
                        key.equals("GATE_WIND_TUNNEL") ? value : q.windTunnel(),
                        key.equals("GATE_SPEED_SHOES") ? value : q.speedShoes(),
                        key.equals("GATE_TIME_WARP_DIRECTION") ? value : q.timeWarpDirection()),
                new CdPlayerMovement.Globals(key.equals("MOVEMENT_LOOK_MODE") ? value : g.lookMode(),
                        key.equals("MOVEMENT_CAMERA_Y_CENTRE") ? value : g.cameraYCentre(),
                        key.equals("MOVEMENT_PRIMARY_ANGLE") ? value : g.primaryAngle(),
                        key.equals("MOVEMENT_SECONDARY_ANGLE") ? value : g.secondaryAngle(),
                        key.equals("MOVEMENT_BORED_TIMER") ? value : g.boredTimer(),
                        key.equals("MOVEMENT_SCORE_CHAIN") ? value : g.scoreChain(),
                        key.equals("MOVEMENT_DEBUG_ANGLE") ? value : g.debugAngle(),
                        key.equals("MOVEMENT_DEBUG_ANGLE_SHIFT") ? value : g.debugAngleShift(),
                        key.equals("MOVEMENT_DEBUG_QUADRANT") ? value : g.debugQuadrant(),
                        key.equals("MOVEMENT_DEBUG_FLOOR_DISTANCE") ? value : g.debugFloorDistance()));
        return new CdPlayerBody.Environment(movement,
                new CdPlayerBody.Controls(key.equals("RAW_HELD") ? value : c.rawHeld(),
                        key.equals("RAW_TAP") ? value : c.rawTap(),
                        key.equals("LOGICAL_HELD") ? value : c.logicalHeld(),
                        key.equals("LOGICAL_TAP") ? value : c.logicalTap(),
                        key.equals("CONTROL_LOCKED") ? value : c.controlLocked()),
                new CdPlayerBody.Globals(key.equals("BODY_LEVEL_FRAMES") ? value : z.levelFrames(),
                        key.equals("BODY_CAMERA_X_CENTRE") ? value : z.cameraXCentre(),
                        key.equals("BODY_TIME_ATTACK_MODE") ? value : z.timeAttackMode(),
                        key.equals("BODY_DEBUG_MODE") ? value : z.debugMode(),
                        key.equals("BODY_DEBUG_CHEAT") ? value : z.debugCheat(),
                        key.equals("BODY_TIME_WARP_TIMER") ? value : z.timeWarpTimer(),
                        key.equals("BODY_INVINCIBLE") ? value : z.invincible(),
                        key.equals("BODY_BOSS_MUSIC") ? value : z.bossMusic()),
                e.dynamicSlots());
    }

    private static CdPlayerBody.Environment framePortLogicalWord(CdPlayerBody.Environment e, int value) {
        var c = e.controls();
        return new CdPlayerBody.Environment(e.movement(),
                new CdPlayerBody.Controls(c.rawHeld(), c.rawTap(), value >>> 8, value & 0xFF, c.controlLocked()),
                e.globals(), e.dynamicSlots());
    }

    private static CdPlayerFrameRuntime framePortDiscFrame(Fixture fixture) throws IOException {
        var f = spinTunnelDirtyFrame(fixture); var actor = f.captureActor();
        // Constructed entry makes BSET0 change $A4->$A5; it grants no actual Disc invocation.
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(),
                framePortReplaceState(actor.state(), "PLAYER_CONTROL", 0xA4), actor.display()));
        return f;
    }


    @Test void directDiscPreservesTwoOrbitWordStoresAndDuplicateMiniatureStoresAsObservations() throws IOException {
        try (var f = framePortDiscFrame(new Fixture())) {
            var actor = f.captureActor(); var shared = f.captureShared(); var s = actor.state(); var e = shared.environment();
            var finalState = framePortReplaceState(s, "CENTRE_X", 0);
            finalState = framePortReplaceState(finalState, "CENTRE_Y", 2);
            finalState = framePortReplaceState(finalState, "Y_RADIUS", 10);
            finalState = framePortReplaceState(finalState, "X_RADIUS", 5);
            var stores = List.of(
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.ORBIT_X_BASE, 0xFFFC, 0xFFFF),
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.ORBIT_X_OFFSET, 0xFFFF, 0),
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.ROLL_Y_ADVANCE, 0xFFFD, 2),
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.JUMP_Y_RADIUS_MINI, 19, 10),
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.JUMP_X_RADIUS_MINI, 9, 5),
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.ROLL_Y_RADIUS_MINI, 10, 10),
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.ROLL_X_RADIUS_MINI, 5, 5));
            var result = new CdSpinningDisc.PlayerResult(finalState, e, stores);
            f.applyObjectDiscPlayerResult(s, e, result);
            assertSame(finalState, f.state()); assertSame(e, f.environment()); assertEquals(stores, result.stores());
            assertEquals(0x00001234, f.state().motion().centreX16_16());
            assertEquals(0x0002BEEF, f.state().motion().centreY16_16());
            assertEquals(10, f.state().motion().yRadius()); assertEquals(5, f.state().motion().xRadius());
            assertEquals(s.rotationAngle(), f.state().rotationAngle());
            assertEquals(s.rotationDistance(), f.state().rotationDistance());
            assertEquals(s.motion().stick(), f.state().motion().stick());
            assertEquals(s.motion().charge(), f.state().motion().charge());
            f.restoreActor(actor); f.restoreShared(shared);
            var wrongOld = new ArrayList<>(stores);
            wrongOld.set(1, new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.ORBIT_X_OFFSET, 0xFFFC, 0));
            framePortDiscRejected(f, s, e, new CdSpinningDisc.PlayerResult(finalState, e, wrongOld), IllegalArgumentException.class);
            var wrongMini = new ArrayList<>(stores);
            wrongMini.set(5, new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.ROLL_Y_RADIUS_MINI, 19, 10));
            framePortDiscRejected(f, s, e, new CdSpinningDisc.PlayerResult(finalState, e, wrongMini), IllegalArgumentException.class);
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    @Test void directDiscSubtractionPrefixKeepsByteWrapBeforeItsSeparateClampAndRotationWrapsIndependently()
            throws IOException {
        try (var f = framePortDiscFrame(new Fixture())) {
            var original = f.captureActor(); var seeded = framePortReplaceState(original.state(), "ROTATION_DISTANCE", 0);
            seeded = framePortReplaceState(seeded, "ROTATION_ANGLE", 0xFC);
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(original.owner(), seeded, original.display()));
            var e = f.environment(); var s = f.state();
            var finalState = framePortReplaceState(s, "ROTATION_ANGLE", 4);
            var stores = List.of(
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.DISTANCE_SHRINK, 0, 255),
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.DISTANCE_CLAMP, 255, 0),
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.ROTATION_ADVANCE, 0xFC, 4));
            var result = new CdSpinningDisc.PlayerResult(finalState, e, stores);
            f.applyObjectDiscPlayerResult(s, e, result);
            assertSame(finalState, f.state()); assertEquals(0, f.state().rotationDistance());
            assertEquals(4, f.state().rotationAngle()); assertEquals(s.motion().charge(), f.state().motion().charge());
            assertEquals(List.of(255, 0, 4), result.stores().stream().map(CdSpinningDisc.PlayerStore::value).toList());
        }
    }

    @Test void directDiscLogicalWordMustCopyActualRawPairEvenWithAControlLock() throws IOException {
        try (var f = framePortDiscFrame(new Fixture())) {
            var s = f.state(); var e = f.environment();
            assertEquals(0x81, e.controls().controlLocked());
            assertEquals(0x2C24, (e.controls().rawHeld() << 8) | e.controls().rawTap());
            var wrongEnvironment = framePortLogicalWord(e, 0x2C25);
            var wrong = new CdSpinningDisc.PlayerResult(s, wrongEnvironment, List.of(
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.LOGICAL_CONTROLS, 0x0810, 0x2C25)));
            framePortDiscRejected(f, s, e, wrong, IllegalArgumentException.class);
            var finalEnvironment = framePortLogicalWord(e, 0x2C24);
            var result = new CdSpinningDisc.PlayerResult(s, finalEnvironment, List.of(
                    new CdSpinningDisc.PlayerStore(CdSpinningDisc.DiscPlayerField.LOGICAL_CONTROLS, 0x0810, 0x2C24)));
            f.applyObjectDiscPlayerResult(s, e, result);
            assertSame(s, f.state()); assertSame(finalEnvironment, f.environment());
            assertEquals(new CdPlayerBody.Controls(0x2C, 0x24, 0x2C, 0x24, 0x81), f.environment().controls());
        }
    }

    @ParameterizedTest @CsvSource({
            "CONTROL_ACQUIRE,0xA4,1", "ROTATION_ADVANCE,0xFC,0xFC",
            "FLAGS_AIR,0xE9,2", "FLAGS_PUSH_CLEAR,0xE9,0", "FLAGS_ROLL,0xE9,4",
            "FLAGS_ROLL_JUMP,0xE9,0x10", "CONTROL_CLEAR,0xA4,0xA4",
            "STICK_CLEAR,0x85,0x85", "ROLL_Y_ADVANCE,0xFFFD,1", "DISTANCE_CLAMP,0,0"
    })
    void directDiscFixedOperationsCannotEraseOtherBitsOrChangeTheirNativeStore(
            CdSpinningDisc.DiscPlayerField field, int expectedRaw, int value) {
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerStore(field, expectedRaw, value));
    }

    @Test void preparedAndDirectPortsRequireIdleClosedCutsBeforeNullCasOrSharedValidation() throws IOException {
        try (var f = new Fixture().runtime()) {
            var pool = new CdObjectPool(); var object = framePortSolidObject(pool);
            var player = solidPlayer(f); var globals = solidGlobals(f);
            var result = CdSolidObject.fromAssets(f.assets()).run(CdSolidObject.Entry.GET_OFF_OBJECT,
                    object, player, new CdSolidObject.Gates(0, 0), globals, pool.capture());
            var prefix = prefixFrameValues(player, globals, Set.of(), Set.of());
            var publication = f.prepareObjectSolidResult(player, globals, result);
            var s = f.state(); var e = f.environment(); var direct = new CdSpinningDisc.PlayerResult(s, e, List.of());
            framePortDiscRejected(f, null, e, direct, NullPointerException.class);
            framePortDiscRejected(f, s, null, direct, NullPointerException.class);
            framePortDiscRejected(f, s, e, null, NullPointerException.class);
            var selection = f.prepareInput(8);
            framePortAllGuarded(f, player, globals, result, prefix, publication, s, e, direct);
            int[] activeService = {0};
            f.serviceVBlank(selection.phase(), () -> {
                activeService[0]++;
                framePortAllGuarded(f, player, globals, result, prefix, publication, s, e, direct);
            });
            assertEquals(1, activeService[0]);
            framePortAllGuarded(f, player, globals, result, prefix, publication, s, e, direct);
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary(),
                    "Rejected ports preserve the actual pending input and resulting Body entitlement");
            f.close();
            framePortAllGuarded(f, player, globals, result, prefix, publication, s, e, direct);
        }
    }

    private static void framePortAllGuarded(CdPlayerFrameRuntime f, CdSolidObject.Player player,
            CdSolidObject.Globals globals, CdSolidObject.Result result, CdSolidObject.Prefix prefix,
            CdPlayerFrameRuntime.ObjectSolidPublication publication, CdPlayerBody.State s,
            CdPlayerBody.Environment e, CdSpinningDisc.PlayerResult direct) {
        var actualState = f.state(); var actualEnvironment = f.environment(); var art = f.artState();
        var display = f.display(); var clock = f.vIntClock();
        assertThrows(IllegalStateException.class, () -> f.prepareObjectSolidResult(player, globals, result));
        assertThrows(IllegalStateException.class, () -> f.prepareObjectSolidPrefix(player, globals, prefix));
        assertThrows(IllegalStateException.class, () -> f.prepareObjectSolidResult(null, null, null));
        assertThrows(IllegalStateException.class, () -> f.prepareObjectSolidPrefix(null, null, null));
        framePortPreparedRejected(f, publication, IllegalStateException.class);
        framePortPreparedRejected(f, null, IllegalStateException.class);
        framePortDiscRejected(f, s, e, direct, IllegalStateException.class);
        framePortDiscRejected(f, null, null, null, IllegalStateException.class);
        assertSame(actualState, f.state()); assertSame(actualEnvironment, f.environment()); assertSame(art, f.artState());
        assertSame(display, f.display()); assertSame(clock, f.vIntClock());
    }
    // END prepared Solid/direct Disc Frame ports.


    // BEGIN Tunnel Path Frame preparation. Constructed public values; Java checks are UNRUN.
    // These cases exercise the pure Frame port, never a private Runtime/native-visit authority.
    @ParameterizedTest @CsvSource({
            "HURT_ROUTINE, ROUTINE, 04, 02",
            "HURT_TIMER, HURT, 1357, 0078",
            "ACQUIRE_CONTROL, PLAYER_CONTROL, 00, 81",
            "ACQUIRE_INVISIBLE, PLAYER_CONTROL, 81, C1",
            "ACQUIRE_ANIMATION, ANIMATION, 1A, 02",
            "ACQUIRE_GVEL_BASE, GROUND_VELOCITY, F123, 1000",
            "ACQUIRE_GVEL_RAISE, GROUND_VELOCITY, 1000, 1001",
            "ACQUIRE_X_VELOCITY, X_VELOCITY, F123, 0000",
            "ACQUIRE_Y_VELOCITY, Y_VELOCITY, 8123, 0000",
            "ACQUIRE_PUSH_CLEAR, FLAGS, E9, C9",
            "ACQUIRE_AIR, FLAGS, C9, CB",
            "ACQUIRE_JUMP_CLEAR, PLAYER_JUMP, 76, 00",
            "ACQUIRE_X_SNAP, CENTRE_X, FFFC, 13F0",
            "ACQUIRE_Y_SNAP, CENTRE_Y, FFFD, 00F0",
            "SPEED_Y_X_VELOCITY, X_VELOCITY, 8123, 0000",
            "SPEED_Y_Y_VELOCITY, Y_VELOCITY, 0123, F000",
            "SPEED_X_Y_VELOCITY, Y_VELOCITY, F123, 0000",
            "SPEED_X_X_VELOCITY, X_VELOCITY, 0123, 1000",
            "COMPLETE_X_SNAP, CENTRE_X, FFFC, 1700",
            "COMPLETE_Y_SNAP, CENTRE_Y, FFFD, 02F0",
            "MOVE_X_LONG, X_LONG, 7FFFFFF0, 800000F0",
            "MOVE_Y_LONG, Y_LONG, 00000080, FFFFFF80",
            "RELEASE_Y_MASK, CENTRE_Y, F923, 0123",
            "RELEASE_CONTROL_CLEAR, PLAYER_CONTROL, C1, 00"})
    void tunnelPreparationChecksEveryFiniteStoreAndAdoptsOnlyReadyFullPairReferences(
            CdTunnelPath.TunnelPlayerField field, String key, String oldHex, String valueHex) throws IOException {
        var fixture = new Fixture();
        try (var f = spinTunnelDirtyFrame(fixture)) {
            int oldRaw = (int) Long.parseLong(oldHex, 16), value = (int) Long.parseLong(valueHex, 16);
            var captured = f.captureActor();
            var input = tunnelFrameReplace(captured.state(), key, oldRaw);
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(), input, captured.display()));
            var actor = f.captureActor(); var shared = f.captureShared(); int uploads = fixture.dmas.size();
            var expected = tunnelFrameReplace(input, key, value);
            var stores = new ArrayList<CdTunnelPath.PlayerStore>();
            stores.add(new CdTunnelPath.PlayerStore(field, oldRaw, value));
            var result = new CdTunnelPath.PlayerResult(expected, shared.environment(), stores);
            stores.clear();
            assertEquals(List.of(new CdTunnelPath.PlayerStore(field, oldRaw, value)), result.stores());
            assertThrows(UnsupportedOperationException.class, () -> result.stores().clear());
            f.bindArtPublication((request, art) -> fail("Tunnel preparation must not service art"));
            var pool = new CdObjectPool(); var poolBefore = pool.capture();
            var publication = tunnelFramePrepare(f, input, shared.environment(), result);
            assertSame(input, publication.expectedState());
            assertSame(shared.environment(), publication.expectedEnvironment());
            assertSame(expected, publication.finalState());
            assertSame(shared.environment(), publication.finalEnvironment());
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
            assertEquals(poolBefore.slots(), pool.capture().slots()); assertEquals(uploads, fixture.dmas.size());
            if (field.widthBits() != 32) {
                int beyond = 1 << field.widthBits();
                assertThrows(IllegalArgumentException.class, () -> new CdTunnelPath.PlayerStore(field, oldRaw | beyond, value));
                assertThrows(IllegalArgumentException.class, () -> new CdTunnelPath.PlayerStore(field, oldRaw, value | beyond));
                assertThrows(IllegalArgumentException.class, () -> new CdTunnelPath.PlayerStore(field, -1, value));
                assertThrows(IllegalArgumentException.class, () -> new CdTunnelPath.PlayerStore(field, oldRaw, -1));
            } else {
                // Every int bit pattern is a native LONG; no invented narrower LONG range.
                assertDoesNotThrow(() -> new CdTunnelPath.PlayerStore(field, Integer.MIN_VALUE, Integer.MAX_VALUE));
            }
            f.validatePreparedObjectSolidPublication(publication);
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
            f.applyPreparedObjectSolidPublication(publication);
            assertSame(expected, f.state()); assertSame(shared.environment(), f.environment());
            assertEquals(expected, f.captureActor().state());
            assertSame(actor.display(), f.display()); assertSame(shared.artState(), f.artState());
            assertSame(shared.vIntClock(), f.vIntClock()); assertEquals(shared.paused(), f.paused());
            assertEquals(poolBefore.slots(), pool.capture().slots()); assertEquals(uploads, fixture.dmas.size());
            framePortPreparedRejected(f, publication, IllegalArgumentException.class);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"CENTRE_X", "X_FRACTION", "CENTRE_Y", "Y_FRACTION", "X_VELOCITY", "Y_VELOCITY", "GROUND_VELOCITY", "ROUTINE", "ANGLE", "FLAGS", "SPRITE_FLAGS", "X_RADIUS", "Y_RADIUS", "ANIMATION", "PREVIOUS_ANIMATION", "PLAYER_JUMP", "CHARGE", "MOVE_LOCK", "STICK", "PLAYER_CONTROL", "PREVIOUS_PRIMARY_ANGLE", "PREVIOUS_SECONDARY_ANGLE", "SCRIPT_INDEX", "ANIMATION_TIMER", "MAP_FRAME", "SLOT_ID", "MAPPING_ADDRESS", "TILE", "PRIORITY", "WIDTH", "HURT", "INVINCIBILITY", "SPEED_SHOES", "RESET", "HISTORY_INDEX", "ROTATION_DISTANCE", "PUSH_OBJECT", "STAND_OBJECT", "ROTATION_ANGLE", "HISTORY_BYTES"})
    void tunnelPreparationCasChecksEveryStateFieldAndDelegatesEveryUndeclaredChange(String field) throws IOException {
        try (var f = spinTunnelDirtyFrame(new Fixture())) {
            var actor = f.captureActor(); var shared = f.captureShared(); var s = actor.state(); var e = shared.environment();
            var changed = framePortChangedState(s, field); assertNotEquals(s, changed, field);
            var noWrite = new CdTunnelPath.PlayerResult(s, e, List.of());
            var ready = tunnelFramePrepare(f, s, e, noWrite);
            var stale = tunnelFrameRejected(f, changed, e, noWrite, IllegalArgumentException.class);
            assertEquals("Stale Tunnel Path player State/Environment cut", stale.getMessage());
            var malicious = new CdTunnelPath.PlayerResult(changed, e, List.of());
            var pure = assertThrows(IllegalArgumentException.class,
                    () -> CdTunnelPath.validatePlayerResult(new CdTunnelPath.PlayerCut(s, e), malicious));
            var delegated = tunnelFrameRejected(f, s, e, malicious, IllegalArgumentException.class);
            assertEquals(pure.getMessage(), delegated.getMessage());
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), changed, actor.display()));
            tunnelFrameRejected(f, s, e, noWrite, IllegalArgumentException.class);
            framePortPreparedRejected(f, ready, IllegalArgumentException.class);
            f.restoreActor(actor);
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"TOP_SPEED", "ACCELERATION", "DECELERATION", "BOUND_LEFT", "BOUND_RIGHT", "BOUND_BOTTOM", "BOUND_BOSS_FIGHT", "GATE_ZONE_ACT", "GATE_TIME_ZONE", "GATE_MINI_SONIC", "GATE_WATER_SLIDE", "GATE_WIND_TUNNEL", "GATE_SPEED_SHOES", "GATE_TIME_WARP_DIRECTION", "MOVEMENT_LOOK_MODE", "MOVEMENT_CAMERA_Y_CENTRE", "MOVEMENT_PRIMARY_ANGLE", "MOVEMENT_SECONDARY_ANGLE", "MOVEMENT_BORED_TIMER", "MOVEMENT_SCORE_CHAIN", "MOVEMENT_DEBUG_ANGLE", "MOVEMENT_DEBUG_ANGLE_SHIFT", "MOVEMENT_DEBUG_QUADRANT", "MOVEMENT_DEBUG_FLOOR_DISTANCE", "RAW_HELD", "RAW_TAP", "LOGICAL_HELD", "LOGICAL_TAP", "CONTROL_LOCKED", "BODY_LEVEL_FRAMES", "BODY_CAMERA_X_CENTRE", "BODY_TIME_ATTACK_MODE", "BODY_DEBUG_MODE", "BODY_DEBUG_CHEAT", "BODY_TIME_WARP_TIMER", "BODY_INVINCIBLE", "BODY_BOSS_MUSIC", "DYNAMIC_SLOTS"})
    void tunnelPreparationCasChecksEveryEnvironmentFieldAndNeverPublishesEnvironmentWrites(String field) throws IOException {
        try (var f = spinTunnelDirtyFrame(new Fixture())) {
            var actor = f.captureActor(); var shared = f.captureShared(); var s = actor.state(); var e = shared.environment();
            var changed = framePortChangedEnvironment(e, field); assertNotEquals(e, changed, field);
            var noWrite = new CdTunnelPath.PlayerResult(s, e, List.of());
            var ready = tunnelFramePrepare(f, s, e, noWrite);
            var stale = tunnelFrameRejected(f, s, changed, noWrite, IllegalArgumentException.class);
            assertEquals("Stale Tunnel Path player State/Environment cut", stale.getMessage());
            var malicious = new CdTunnelPath.PlayerResult(s, changed, List.of());
            var pure = assertThrows(IllegalArgumentException.class,
                    () -> CdTunnelPath.validatePlayerResult(new CdTunnelPath.PlayerCut(s, e), malicious));
            var delegated = tunnelFrameRejected(f, s, e, malicious, IllegalArgumentException.class);
            assertEquals(pure.getMessage(), delegated.getMessage());
            f.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), changed,
                    shared.artState(), shared.paused(), shared.vIntClock()));
            tunnelFrameRejected(f, s, e, noWrite, IllegalArgumentException.class);
            framePortPreparedRejected(f, ready, IllegalArgumentException.class);
            f.restoreShared(shared);
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    @ParameterizedTest @CsvSource({"2, 0", "2, 1", "4, 0", "4, 1"})
    void tunnelPreparationCarriesExactAcquisitionValuesWithoutResettingOtherPlayerPartitions(
            int routine, int secondary) throws IOException {
        var fixture = new Fixture();
        try (var f = spinTunnelDirtyFrame(fixture)) {
            var actor = f.captureActor(); var input = framePortReplaceState(actor.state(), "ROUTINE", routine);
            input = framePortReplaceState(input, "PLAYER_CONTROL", 0);
            input = framePortReplaceState(input, "CENTRE_X", 0x13F0);
            input = framePortReplaceState(input, "CENTRE_Y", 0x00F0);
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), input, actor.display()));
            var s = f.state(); var e = f.environment(); var m = s.motion(); var shared = f.captureShared();
            var stores = new ArrayList<CdTunnelPath.PlayerStore>();
            if (routine == 4) {
                stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.HURT_ROUTINE, 4, 2));
                stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.HURT_TIMER, 0x1357, 0x78));
            }
            stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_CONTROL, 0, 0x81));
            if (secondary != 0)
                stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_INVISIBLE, 0x81, 0xC1));
            stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_ANIMATION, 0x1A, 2));
            stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_GVEL_BASE, 0x345, 0x1000));
            var baseMotion = new CdPlayerMotionState(0x13F01234, 0x00F0BEEF, 0x100, -0x234, 0x1000,
                    2, m.angle(), 0xE9, m.spriteFlags(), m.xRadius(), m.yRadius(), 2, m.previousAnimation(),
                    m.playerJump(), m.charge(), m.moveLock(), m.stick(), secondary != 0 ? 0xC1 : 0x81,
                    m.previousPrimaryAngle(), m.previousSecondaryAngle());
            var baseState = new CdPlayerBody.State(baseMotion, s.animation(), s.header(),
                    new CdPlayerBody.Timers(routine == 4 ? 0x78 : 0x1357, s.timers().invincibility(),
                            s.timers().speedShoes(), s.timers().reset()), s.history(), s.rotationDistance(),
                    s.objectLinks(), s.rotationAngle());
            var base = tunnelFramePrepare(f, s, e, new CdTunnelPath.PlayerResult(baseState, e, stores));
            assertEquals(routine == 4 ? 0x78 : 0x1357, base.finalState().timers().hurt());
            assertSame(s.animation(), base.finalState().animation()); assertSame(s.header(), base.finalState().header());
            assertSame(s.history(), base.finalState().history()); assertSame(s.objectLinks(), base.finalState().objectLinks());
            assertEquals(0xD7, base.finalState().rotationAngle()); assertEquals(0xAB, base.finalState().rotationDistance());
            f.applyPreparedObjectSolidPublication(base);
            var finishStores = List.of(
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_X_VELOCITY, 0x100, 0),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_Y_VELOCITY, 0xFDCC, 0),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_PUSH_CLEAR, 0xE9, 0xC9),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_AIR, 0xC9, 0xCB),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_JUMP_CLEAR, 0x76, 0),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_X_SNAP, 0x13F0, 0x13F0),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_Y_SNAP, 0xF0, 0xF0));
            var finishMotion = new CdPlayerMotionState(0x13F01234, 0x00F0BEEF, 0, 0, 0x1000,
                    2, m.angle(), 0xCB, m.spriteFlags(), m.xRadius(), m.yRadius(), 2, m.previousAnimation(),
                    0, m.charge(), m.moveLock(), m.stick(), secondary != 0 ? 0xC1 : 0x81,
                    m.previousPrimaryAngle(), m.previousSecondaryAngle());
            var finishState = new CdPlayerBody.State(finishMotion, s.animation(), s.header(), baseState.timers(),
                    s.history(), s.rotationDistance(), s.objectLinks(), s.rotationAngle());
            var finish = tunnelFramePrepare(f, baseState, e, new CdTunnelPath.PlayerResult(finishState, e, finishStores));
            f.applyPreparedObjectSolidPublication(finish);
            assertSame(finishState, f.state()); assertSame(e, f.environment());
            assertEquals(shared, f.captureShared()); assertSame(actor.display(), f.display());
            assertEquals(0x1234, f.state().motion().centreX16_16() & 0xFFFF);
            assertEquals(0xBEEF, f.state().motion().centreY16_16() & 0xFFFF);
            assertEquals(0, f.state().motion().flags() & 4, "Animation 02 does not invent a rolling/radius reset");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void tunnelPreparationRetainsBothNativeVelocityStoreOrdersAsData(boolean yDominant) throws IOException {
        try (var f = spinTunnelDirtyFrame(new Fixture())) {
            var s = f.state(); var e = f.environment();
            int xv = yDominant ? 0 : 0x1000, yv = yDominant ? 0x1000 : 0;
            var first = new CdTunnelPath.PlayerStore(yDominant ? CdTunnelPath.TunnelPlayerField.SPEED_Y_X_VELOCITY
                    : CdTunnelPath.TunnelPlayerField.SPEED_X_Y_VELOCITY, yDominant ? 0x100 : 0xFDCC, yDominant ? xv : yv);
            var second = new CdTunnelPath.PlayerStore(yDominant ? CdTunnelPath.TunnelPlayerField.SPEED_Y_Y_VELOCITY
                    : CdTunnelPath.TunnelPlayerField.SPEED_X_X_VELOCITY, yDominant ? 0xFDCC : 0x100, yDominant ? yv : xv);
            var expected = framePortReplaceState(framePortReplaceState(s, "X_VELOCITY", xv), "Y_VELOCITY", yv);
            var result = new CdTunnelPath.PlayerResult(expected, e, List.of(first, second));
            var publication = tunnelFramePrepare(f, s, e, result);
            assertEquals(List.of(first, second), result.stores());
            f.applyPreparedObjectSolidPublication(publication);
            assertSame(expected, f.state()); assertSame(e, f.environment());
        }
    }

    @ParameterizedTest @CsvSource({
            "7FFFFFF0, 00000080, 0001, FFFF, 800000F0, FFFFFF80",
            "FFFF0000, 00000000, 1000, 8000, 000F0000, FF800000"})
    void tunnelPreparationPreservesSignedLongIntegrationWrapAndCarries(
            String xHex, String yHex, String xvHex, String yvHex, String finalXHex, String finalYHex) throws IOException {
        try (var f = spinTunnelDirtyFrame(new Fixture())) {
            int x = (int) Long.parseLong(xHex, 16), y = (int) Long.parseLong(yHex, 16);
            int xv = Integer.parseInt(xvHex, 16), yv = Integer.parseInt(yvHex, 16);
            int finalX = (int) Long.parseLong(finalXHex, 16), finalY = (int) Long.parseLong(finalYHex, 16);
            var actor = f.captureActor();
            var input = tunnelFrameReplace(tunnelFrameReplace(actor.state(), "X_LONG", x), "Y_LONG", y);
            input = framePortReplaceState(framePortReplaceState(input, "X_VELOCITY", xv), "Y_VELOCITY", yv);
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), input, actor.display()));
            var s = f.state(); var e = f.environment();
            assertEquals(finalX, x + ((short) xv << 8)); assertEquals(finalY, y + ((short) yv << 8));
            var expected = tunnelFrameReplace(tunnelFrameReplace(s, "X_LONG", finalX), "Y_LONG", finalY);
            var result = new CdTunnelPath.PlayerResult(expected, e, List.of(
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.MOVE_X_LONG, x, finalX),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.MOVE_Y_LONG, y, finalY)));
            var publication = tunnelFramePrepare(f, s, e, result);
            f.applyPreparedObjectSolidPublication(publication);
            assertSame(expected, f.state()); assertSame(e, f.environment());
            assertEquals(s.timers(), f.state().timers()); assertSame(s.history(), f.state().history());
        }
    }

    @Test void tunnelPreparationCompletionThenReleaseKeepsFractionsVelocitiesAndEveryUnlistedField() throws IOException {
        try (var f = spinTunnelDirtyFrame(new Fixture())) {
            var actor = f.captureActor(); var initial = framePortReplaceState(actor.state(), "PLAYER_CONTROL", 0xC1);
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), initial, actor.display()));
            var s = f.state(); var e = f.environment();
            var snapped = framePortReplaceState(framePortReplaceState(s, "CENTRE_X", 0x1700), "CENTRE_Y", 0xF923);
            var snapResult = new CdTunnelPath.PlayerResult(snapped, e, List.of(
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.COMPLETE_X_SNAP, 0xFFFC, 0x1700),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.COMPLETE_Y_SNAP, 0xFFFD, 0xF923)));
            var snap = tunnelFramePrepare(f, s, e, snapResult);
            f.applyPreparedObjectSolidPublication(snap);
            var released = framePortReplaceState(framePortReplaceState(snapped, "CENTRE_Y", 0x0123), "PLAYER_CONTROL", 0);
            var release = tunnelFramePrepare(f, snapped, e, new CdTunnelPath.PlayerResult(released, e, List.of(
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.RELEASE_Y_MASK, 0xF923, 0x0123),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.RELEASE_CONTROL_CLEAR, 0xC1, 0))));
            f.applyPreparedObjectSolidPublication(release);
            assertSame(released, f.state()); assertSame(e, f.environment());
            assertEquals(0x17001234, f.state().motion().centreX16_16());
            assertEquals(0x0123BEEF, f.state().motion().centreY16_16());
            assertEquals(s.motion().xVelocity(), f.state().motion().xVelocity());
            assertEquals(s.motion().yVelocity(), f.state().motion().yVelocity());
            assertEquals(s.motion().groundVelocity(), f.state().motion().groundVelocity());
            assertEquals(s.motion().flags(), f.state().motion().flags()); assertEquals(s.timers(), f.state().timers());
            assertEquals(s.animation(), f.state().animation()); assertEquals(s.header(), f.state().header());
            assertSame(s.history(), f.state().history()); assertEquals(s.objectLinks(), f.state().objectLinks());
            assertEquals(s.rotationAngle(), f.state().rotationAngle()); assertEquals(s.rotationDistance(), f.state().rotationDistance());
            assertSame(actor.display(), f.display());
        }
    }

    @Test void tunnelPreparationRejectsWrongOldValuesMissingEffectsAndConflictingDuplicatePrefixes() throws IOException {
        try (var f = spinTunnelDirtyFrame(new Fixture())) {
            var s = f.state(); var e = f.environment(); var expected = framePortReplaceState(s, "X_VELOCITY", 0x200);
            var store = new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.SPEED_Y_X_VELOCITY, 0x100, 0x200);
            var wrongOld = new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.SPEED_Y_X_VELOCITY, 0x101, 0x200);
            var wrongFlags = List.of(
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_PUSH_CLEAR, 0xE9, 0xC9),
                    new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_AIR, 0xE9, 0xEB));
            var malformed = List.of(
                    new CdTunnelPath.PlayerResult(expected, e, List.of(wrongOld)),
                    new CdTunnelPath.PlayerResult(expected, e, List.of()),
                    new CdTunnelPath.PlayerResult(s, e, List.of(store)),
                    new CdTunnelPath.PlayerResult(expected, e, List.of(store, store)),
                    new CdTunnelPath.PlayerResult(s, e, wrongFlags));
            for (var result : malformed) {
                var pure = assertThrows(IllegalArgumentException.class,
                        () -> CdTunnelPath.validatePlayerResult(new CdTunnelPath.PlayerCut(s, e), result));
                var delegated = tunnelFrameRejected(f, s, e, result, IllegalArgumentException.class);
                assertEquals(pure.getMessage(), delegated.getMessage());
            }
            var stale = framePortChangedState(s, "ROTATION_ANGLE");
            var precedence = tunnelFrameRejected(f, stale, e, malformed.getFirst(), IllegalArgumentException.class);
            assertEquals("Stale Tunnel Path player State/Environment cut", precedence.getMessage(),
                    "Complete pair CAS precedes the pure field predicate");
        }
    }

    @Test void tunnelPreparationSameValueDuplicatesAndEqualFramesConveyOnlyValueCas() throws IOException {
        try (var a = spinTunnelDirtyFrame(new Fixture()); var b = spinTunnelDirtyFrame(new Fixture())) {
            var s = a.state(); var e = a.environment(); var beforeA = a.captureActor(); var beforeB = b.captureActor();
            var sharedA = a.captureShared(); var sharedB = b.captureShared();
            var same = new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.SPEED_Y_X_VELOCITY, 0x100, 0x100);
            var result = new CdTunnelPath.PlayerResult(s, e, List.of(same, same));
            // The pure predicate admits same-value observations; only Runtime can enforce reached-site shape/once.
            var publication = tunnelFramePrepare(a, s, e, result);
            a.applyPreparedObjectSolidPublication(publication); a.applyPreparedObjectSolidPublication(publication);
            var equalFrame = tunnelFramePrepare(b, s, e, result);
            b.applyPreparedObjectSolidPublication(equalFrame);
            assertEquals(beforeA, a.captureActor()); assertEquals(beforeB, b.captureActor());
            assertEquals(sharedA, a.captureShared()); assertEquals(sharedB, b.captureShared());
            assertSame(s, b.state()); assertSame(e, b.environment());
            assertNotSame(beforeA.owner(), beforeB.owner(), "Equal values are not a private native producer identity");
        }
    }

    @Test void tunnelPreparationNullPendingEntitlementActiveServiceAndClosedGuardsPreserveTheirCuts() throws IOException {
        var fixture = new Fixture();
        try (var f = fixture.runtime()) {
            var s = f.state(); var e = f.environment(); var result = new CdTunnelPath.PlayerResult(s, e, List.of());
            var ready = tunnelFramePrepare(f, s, e, result);
            tunnelFrameRejected(f, null, e, result, NullPointerException.class);
            tunnelFrameRejected(f, s, null, result, NullPointerException.class);
            tunnelFrameRejected(f, s, e, null, NullPointerException.class);
            assertThrows(NullPointerException.class, () -> new CdTunnelPath.PlayerResult(s, e, null));
            assertThrows(NullPointerException.class, () -> new CdTunnelPath.PlayerStore(null, 0, 0));
            var selection = f.prepareInput(8);
            tunnelFrameGuarded(f, s, e, result, ready);
            int[] callbacks = {0};
            f.serviceVBlank(selection.phase(), () -> {
                callbacks[0]++;
                tunnelFrameGuarded(f, s, e, result, ready);
            });
            assertEquals(1, callbacks[0]); assertEquals(8, f.environment().controls().rawHeld());
            assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
            tunnelFrameGuarded(f, s, e, result, ready);
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary(),
                    "Rejected preparation cannot consume the pending sample or its Body entitlement");
            var current = new CdTunnelPath.PlayerResult(f.state(), f.environment(), List.of());
            var currentReady = tunnelFramePrepare(f, f.state(), f.environment(), current);
            f.close();
            tunnelFrameGuarded(f, f.state(), f.environment(), current, currentReady);
            assertTrue(f.closed()); assertTrue(fixture.dmas.isEmpty());
        }
    }

    /** Test-only immutable replacements over an actual public Frame fixture's same-owner capture. */
    private static CdPlayerBody.State tunnelFrameReplace(CdPlayerBody.State s, String key, int value) {
        if (key.equals("X_LONG"))
            return framePortReplaceState(framePortReplaceState(s, "CENTRE_X", value >>> 16), "X_FRACTION", value & 0xFFFF);
        if (key.equals("Y_LONG"))
            return framePortReplaceState(framePortReplaceState(s, "CENTRE_Y", value >>> 16), "Y_FRACTION", value & 0xFFFF);
        return framePortReplaceState(s, key, value);
    }

    private static CdPlayerFrameRuntime.ObjectSolidPublication tunnelFramePrepare(CdPlayerFrameRuntime f,
            CdPlayerBody.State s, CdPlayerBody.Environment e, CdTunnelPath.PlayerResult result) {
        var values = observe(f); var actualState = f.state(); var actualEnvironment = f.environment();
        var art = f.artState(); var display = f.display(); var clock = f.vIntClock();
        var publication = f.prepareObjectTunnelPathPlayerResult(s, e, result);
        assertEquals(values, observe(f)); assertSame(actualState, f.state()); assertSame(actualEnvironment, f.environment());
        assertSame(art, f.artState()); assertSame(display, f.display()); assertSame(clock, f.vIntClock());
        return publication;
    }

    private static <T extends RuntimeException> T tunnelFrameRejected(CdPlayerFrameRuntime f,
            CdPlayerBody.State s, CdPlayerBody.Environment e, CdTunnelPath.PlayerResult result, Class<T> type) {
        var values = observe(f); var actualState = f.state(); var actualEnvironment = f.environment();
        var art = f.artState(); var display = f.display(); var clock = f.vIntClock();
        var failure = assertThrows(type, () -> f.prepareObjectTunnelPathPlayerResult(s, e, result));
        assertEquals(values, observe(f)); assertSame(actualState, f.state()); assertSame(actualEnvironment, f.environment());
        assertSame(art, f.artState()); assertSame(display, f.display()); assertSame(clock, f.vIntClock());
        return failure;
    }

    private static void tunnelFrameGuarded(CdPlayerFrameRuntime f, CdPlayerBody.State s, CdPlayerBody.Environment e,
            CdTunnelPath.PlayerResult result, CdPlayerFrameRuntime.ObjectSolidPublication publication) {
        tunnelFrameRejected(f, s, e, result, IllegalStateException.class);
        tunnelFrameRejected(f, null, null, null, IllegalStateException.class);
        framePortPreparedRejected(f, publication, IllegalStateException.class);
        framePortPreparedRejected(f, null, IllegalStateException.class);
    }
    // END Tunnel Path Frame preparation.

}
