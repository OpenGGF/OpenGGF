package com.openggf.game.soniccd.level;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdSolidObject;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.player.CdPlayerCharge;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
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

    // S3 validates a pure call's final values before the pool owner commits that call.
    @Test void nativeStandAndReleaseCommitOnlyTheirActorAndGlobalPartitionsAndReplay() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        var captured = f.captureActor(); var s = captured.state(); var m = s.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), 1899 << 16, 0x180, 0x111, 0x77,
                4, 0x66, 4, m.spriteFlags(), 7, 14, 2, m.previousAnimation(), 1, m.charge(),
                m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var seeded = new CdPlayerBody.State(motion, s.animation(), s.header(),
                new CdPlayerBody.Timers(7, s.timers().invincibility(), s.timers().speedShoes(), s.timers().reset()),
                s.history(), s.rotationDistance(), new CdPlayerBody.ObjectLinks(47, 0));
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
                s.header(), s.timers(), s.history(), invalid, s.objectLinks()));
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
                for (String forbidden : List.of("TouchContext", "ResponseContext", "ContactGlobals", "Snapshot",
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
        final CdPlayerBody.State base;
        CdPlayerBody.ObjectLinks objectLinks;
        int slot, charge, gv, invincibility, reset, flags, y, yv, routine, spriteFlags, animation, rotationDistance;
        Actor(CdPlayerFrameRuntime f) { base = f.state(); objectLinks = base.objectLinks(); slot = base.header().slotId(); charge = base.motion().charge();
            gv = base.motion().groundVelocity(); invincibility = base.timers().invincibility(); reset = base.timers().reset();
            flags = base.motion().flags(); yv = base.motion().yVelocity(); animation = base.motion().animation();
            y = base.motion().centreY16_16(); routine = base.motion().routine(); spriteFlags = base.motion().spriteFlags(); rotationDistance = base.rotationDistance(); }
        void install(CdPlayerFrameRuntime f) {
            var a = f.captureActor(); var m = base.motion(); var h = base.header();
            var motion = new CdPlayerMotionState(m.centreX16_16(), y, m.xVelocity(), yv, gv,
                    routine, m.angle(), flags, spriteFlags, m.xRadius(), m.yRadius(), animation,
                    m.previousAnimation(), m.playerJump(), charge, m.moveLock(), m.stick(), m.playerControl(),
                    m.previousPrimaryAngle(), m.previousSecondaryAngle());
            var state = new CdPlayerBody.State(motion, base.animation(), new CdPlayerBody.Header(slot, h.mappingAddress(), h.tile(), h.priority(), h.width()),
                    new CdPlayerBody.Timers(base.timers().hurt(), invincibility, base.timers().speedShoes(), reset), base.history(), rotationDistance, objectLinks);
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
}
