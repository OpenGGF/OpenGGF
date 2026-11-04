package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in Past Spikes prefix witnesses. B metadata comes from original USA media. Missing-leaf cases
 * deliberately use the labelled constructed A-format terrain below through REAL existing decoders,
 * probes and Solid algorithms; no fabricated Outcome, d0, native fault, Frame or capture is supplied.
 * Native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: common Spikes/Main.asm:36-83;
 * Object Solid.asm:15-146,188-607. These component publication stand-ins own only the test pool/pair;
 * actual Runtime/Frame authority, FM ledger, same-value adoption and World prerequisites remain Root's.
 */
class TestCdSpikesPastPrefix {
    private static final int SLOT = 40;
    private static CdR11bAssets retained;

    @Test void distinctNamedFactoryPreservesBothCompletedOnlyApisAndFourFieldPrefixValue() throws Exception {
        assertNotNull(CdSpikes.class.getMethod("fromPastAssetsWithPrefix", CdObjectPlacementRuntime.class, CdR11bAssets.class,
                CdSolidObject.class, CdSpikes.PrefixSolidCommit.class, CdSpikes.YRewindCommit.class,
                CdSpikes.HurtCommit.class, CdSpikes.DrawObjectCall.class));
        assertNotNull(CdSpikes.class.getMethod("fromPastAssets", CdObjectPlacementRuntime.class, CdR11bAssets.class,
                CdSolidObject.class, CdSpikes.SolidCommit.class, CdSpikes.YRewindCommit.class,
                CdSpikes.HurtCommit.class, CdSpikes.DrawObjectCall.class));
        assertEquals(1, CdSpikes.class.getConstructors().length); assertEquals(7, CdSpikes.class.getConstructors()[0].getParameterCount());
        assertEquals(List.of("actualCut", "expectedPlayer", "expectedGlobals", "outcome"),
                Arrays.stream(CdSpikes.PrefixSolidCall.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
        var h = new H(); assertTrue(h.calls.isEmpty()); assertEquals(0, h.legacyCalls);
    }

    @Test void completedActualBMovementDebugPathPublishesTheFull96BeforeDraw() throws Exception {
        var solid = new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromPast(past())));
        var h = new H(solid, false); h.debug = 0x100; h.put(h.state().moveByte(SOLID_TYPE, 0xA5));
        var before = h.placement.capture(); var result = h.run(); var call = only(h);
        var done = assertInstanceOf(CdSolidObject.Completed.class, call.outcome()).completedResult();
        assertEquals(Optional.of(CdSolidObject.GetOffReason.DEBUG_MODE), done.getOffReason());
        assertEquals(0, done.object().unsignedByte(SOLID_TYPE)); assertEquals(done.player(), result.player());
        assertEquals(done.globals(), h.liveGlobals); assertEquals(0, h.legacyCalls);
        assertAllImages(before, h, done.object().moveByte(SPRITE_FLAGS, 4), done.otherSlots());
        assertEquals(1, h.draws.size()); assertEquals(0, h.rewinds); assertEquals(0, h.hurts);
    }

    @Test void completedLandingPublishesActualOtherSlotReleaseAndGenuineOneBeforeRemainingGates() throws Exception {
        var h = new H(); h.invincible = 1; h.p.flags = 8; h.p.stand = 41; h.p.yv = 0;
        h.pool().write(h.pool().state(41).moveByte(ID, 0).moveByte(FLAGS, 8).moveByte(SUBTYPE, 0xC7));
        var before = h.placement.capture(); var result = h.run();
        var done = assertInstanceOf(CdSolidObject.Completed.class, only(h).outcome()).completedResult();
        assertEquals(CdSolidObject.Path.STAND_ON, done.path()); assertEquals(OptionalInt.of(1), done.d0());
        assertEquals(1, done.otherSlots().size()); assertEquals(0, h.pool().state(41).flags());
        assertEquals(SLOT, result.player().standObject()); assertEquals(done.player(), h.livePlayer);
        assertAllImages(before, h, done.object().moveByte(SPRITE_FLAGS, 4), done.otherSlots());
        assertEquals(0, h.rewinds); assertEquals(0, h.hurts); assertEquals(1, h.draws.size());
    }

    @Test void genuineStoppedStandReleasePublishesPrefixThenThrowsIdenticalOriginalWithoutLaterWork() throws Exception {
        for (int stand : new int[]{1, 5, 31, 128, 255}) {
            var h = stoppedStand(stand); var before = h.placement.capture();
            var failure = assertThrows(CdSolidObject.UnsupportedSolidObject.class, h::run);
            var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, only(h).outcome());
            assertSame(stopped.stop().originalException(), failure); assertEquals(CdSolidObject.StopSite.STAND_RELEASE, stopped.stop().site());
            assertEquals(stand, assertInstanceOf(CdSolidObject.SlotAddress.class, stopped.stop().detail()).slotByte());
            assertEquals(8, stopped.prefix().object().flags()); assertEquals(stopped.prefix().player(), h.livePlayer);
            assertEquals(stopped.prefix().globals(), h.liveGlobals);
            assertAllImages(before, h, stopped.prefix().object(), stopped.prefix().otherSlots()); assertNoLater(h);
        }
    }

    @Test void genuineMissingFloorLeafPublishesItsActualPrefixBeforeTheSameProbeFailure() throws Exception {
        var terrain = new Terrain(); terrain.fg[2 + 42 + 16] = (byte) 0xF9;
        var h = new H(terrain.owner(), false); h.p.spr = 0x44; h.put(h.state().moveByte(SOLID_TYPE, 0xA7));
        var before = h.placement.capture(); var failure = assertThrows(IllegalStateException.class, h::run);
        var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, only(h).outcome());
        assertSame(stopped.stop().originalException(), failure); assertEquals(CdSolidObject.StopSite.FLOOR_PRIMARY, stopped.stop().site());
        assertEquals(stopped.prefix().player(), h.livePlayer); assertEquals(stopped.prefix().globals(), h.liveGlobals);
        assertAllImages(before, h, stopped.prefix().object(), stopped.prefix().otherSlots()); assertNoLater(h);
    }

    @Test void stoppedNoopPublicationCannotReachTheNativeRethrowOrAnyLaterGate() throws Exception {
        var h = stoppedStand(1); h.publish = false;
        var failure = assertThrows(IllegalStateException.class, h::run);
        var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, only(h).outcome());
        assertNotSame(stopped.stop().originalException(), failure); assertTrue(failure.getMessage().contains("full96")); assertNoLater(h);
    }

    @Test void completedNoopAndUnrelatedNormalReturnWriteBothFailExactFull96Verification() throws Exception {
        var noop = new H(); noop.debug = 0x100; noop.put(noop.state().moveByte(SOLID_TYPE, 0xA5)); noop.publish = false;
        assertThrows(IllegalStateException.class, noop::run); assertInstanceOf(CdSolidObject.Completed.class, only(noop).outcome()); assertNoLater(noop);
        var corrupt = new H(); corrupt.debug = 0x100;
        corrupt.before = call -> corrupt.pool().write(corrupt.pool().state(41).moveByte(SUBTYPE, 0xA5));
        assertThrows(IllegalStateException.class, corrupt::run); assertNoLater(corrupt);
    }

    @Test void throwingCallbackRetainsItsIdentityAndPartialPrefixWithNonwriteFaultSuppressed() throws Exception {
        var h = stoppedStand(5); var fault = new Fault();
        h.before = call -> {
            var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, call.outcome()); h.pool().write(stopped.prefix().object());
            h.pool().write(h.pool().state(41).moveByte(SUBTYPE, 0xA5)); throw fault;
        };
        var caught = assertThrows(Fault.class, h::run); assertSame(fault, caught);
        assertEquals(1, fault.getSuppressed().length); assertInstanceOf(IllegalStateException.class, fault.getSuppressed()[0]);
        assertEquals(8, h.state().flags()); assertEquals(0xA5, h.pool().state(41).subtype()); assertNoLater(h);
    }

    @Test void completedCallbackFaultIsNotReplacedByANativeStopOrCompletedMetadata() throws Exception {
        var h = new H(); h.debug = 0x100; var fault = new Fault(); h.before = call -> { throw fault; };
        assertSame(fault, assertThrows(Fault.class, h::run)); assertEquals(0, fault.getSuppressed().length);
        assertInstanceOf(CdSolidObject.Completed.class, only(h).outcome()); assertNoLater(h);
    }

    @Test void stoppedCallbackCannotReenterAndTheRunGuardRetiresAfterNativeFailure() throws Exception {
        var h = stoppedStand(31);
        h.before = call -> assertThrows(IllegalStateException.class, h::run);
        assertThrows(CdSolidObject.UnsupportedSolidObject.class, h::run); assertNoLater(h);
        h.before = call -> {}; h.debug = 0x100; h.calls.clear();
        assertDoesNotThrow(h::run); assertEquals(1, h.calls.size()); assertEquals(1, h.draws.size());
    }

    @Test void legacyCompletedOnlyPastFactoryStillNeverReceivesTheOptInCallback() throws Exception {
        var h = new H(new Terrain().owner(), true); h.p.flags = 8; h.p.stand = 1; h.p.yv = 0;
        h.put(h.state().moveByte(SOLID_TYPE, 2)); var before = h.placement.capture();
        assertThrows(CdSolidObject.UnsupportedSolidObject.class, h::run);
        assertTrue(h.calls.isEmpty()); assertEquals(0, h.legacyCalls); assertEquals(before.pool().slots(), h.pool().capture().slots()); assertNoLater(h);
        h.debug = 0x100; assertDoesNotThrow(h::run); assertEquals(1, h.legacyCalls); assertTrue(h.calls.isEmpty());
    }

    @Test void recordRejectsAGenuineNonPlainOutcomeAndNullValuesWithoutGrantingAuthority() throws Exception {
        var h = new H(); var cut = h.pool().capture(); h.debug = 0x100;
        var other = h.solid.runWithPrefix(CdSolidObject.Entry.TOP_SOLID_OBJECT, h.state(), h.p.player(), h.gates(), h.globals(), cut);
        assertThrows(IllegalArgumentException.class, () -> new CdSpikes.PrefixSolidCall(cut, h.p.player(), h.globals(), other));
        assertThrows(NullPointerException.class, () -> new CdSpikes.PrefixSolidCall(null, h.p.player(), h.globals(), other));
        assertTrue(h.calls.isEmpty()); assertEquals(cut.slots(), h.pool().capture().slots());
    }

    @Test void outerVerticalDistanceGateKeepsTheOptInHelperAndCallbacksUnreached() throws Exception {
        var h = new H(); h.p.y = 0; var before = h.placement.capture(); var result = h.run();
        assertTrue(h.calls.isEmpty()); assertEquals(0, h.legacyCalls); assertEquals(1, h.draws.size());
        assertAllImages(before, h, before.pool().slots().get(SLOT - 32).moveByte(SPRITE_FLAGS, 4), List.of());
        assertEquals(0, h.rewinds); assertEquals(0, h.hurts); assertEquals(h.p.player(), result.player());
    }

    private static H stoppedStand(int stand) throws Exception {
        var h = new H(); h.p.flags = 8; h.p.stand = stand; h.p.yv = 0; h.put(h.state().moveByte(SOLID_TYPE, 2)); return h;
    }
    private static CdSpikes.PrefixSolidCall only(H h) { assertEquals(1, h.calls.size()); return h.calls.getFirst(); }
    private static void assertNoLater(H h) { assertTrue(h.draws.isEmpty()); assertEquals(0, h.rewinds); assertEquals(0, h.hurts); }
    private static void assertAllImages(CdObjectPlacementRuntime.Snapshot before, H h, CdObjectState own, List<CdObjectState> others) {
        var writes = new ArrayList<CdObjectState>(); writes.add(own); writes.addAll(others);
        assertEquals(CdObjectPool.overlay(before.pool(), writes).slots(), h.pool().capture().slots());
        assertEquals(before.status(), h.placement.status()); assertArrayEquals(before.savedFlags().copy(), h.placement.capture().savedFlags().copy());
    }
    private static final class Fault extends RuntimeException { private static final long serialVersionUID = 1L; }
    private static final class H {
        final CdObjectPlacementRuntime placement; final CdSolidObject solid; final CdSpikes body; final P p = new P();
        final List<CdSpikes.PrefixSolidCall> calls = new ArrayList<>(); final List<CdObjectState> draws = new ArrayList<>();
        CdSolidObject.Player livePlayer; CdSolidObject.Globals liveGlobals; int debug, invincible, legacyCalls, rewinds, hurts;
        boolean publish = true; Consumer<CdSpikes.PrefixSolidCall> before = call -> {};
        H() throws Exception { this(new Terrain().owner(), false); }
        H(CdSolidObject solid, boolean legacy) throws Exception {
            this.solid = solid; placement = new CdObjectPlacementRuntime(past().placements());
            put(state().moveByte(ID, 0x26).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x84)
                    .moveWord(X, 0x1000).moveWord(Y, 0x200).moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 8)
                    .moveLong(MAPPINGS, 0x20C048).moveWord(TILE, 0x320).moveByte(PRIORITY, 3));
            CdSpikes.YRewindCommit rewind = call -> { rewinds++; fail("Unexpected Y rewind after protected/stopped Solid"); };
            CdSpikes.HurtCommit hurt = call -> { hurts++; fail("Unexpected Hurt after protected/stopped Solid"); };
            if (legacy) body = CdSpikes.fromPastAssets(placement, past(), solid, call -> {
                legacyCalls++; call.result().otherSlots().forEach(pool()::write); pool().write(call.result().object());
                livePlayer = call.result().player(); liveGlobals = call.result().globals();
            }, rewind, hurt, this::draw);
            else body = CdSpikes.fromPastAssetsWithPrefix(placement, past(), solid, this::commit, rewind, hurt, this::draw);
        }
        CdObjectPool pool() { return placement.pool(); } CdObjectState state() { return pool().state(SLOT); }
        void put(CdObjectState s) { pool().write(s); }
        CdSolidObject.Gates gates() { return new CdSolidObject.Gates(debug, 0); }
        CdSolidObject.Globals globals() { return new CdSolidObject.Globals(invincible, 4, 0x12, 0x34); }
        CdSpikes.Result run() {
            var player = p.player(); var globals = globals(); livePlayer = player; liveGlobals = globals;
            return body.run(SLOT, new CdSpikes.Globals(0, 0, 0x1080), new CdSpikes.Inputs(player, gates(), globals,
                    new CdPlayerTouchResponse.PlayerGlobals(0, invincible, 1, 0, 0, debug, 4, 0),
                    new CdPlayerTouchResponse.WorldGlobals(7, 3, 0, 0, 5000, 0)));
        }
        void commit(CdSpikes.PrefixSolidCall call) {
            calls.add(call); pool().validateRestore(call.actualCut());
            assertEquals(call.actualCut().slots(), pool().capture().slots());
            assertEquals(livePlayer, call.expectedPlayer()); assertEquals(liveGlobals, call.expectedGlobals());
            before.accept(call); if (!publish) return;
            CdObjectState own; List<CdObjectState> others;
            if (call.outcome() instanceof CdSolidObject.Completed done) {
                var result = done.completedResult(); own = result.object(); others = result.otherSlots(); livePlayer = result.player(); liveGlobals = result.globals();
            } else {
                var prefix = ((CdSolidObject.NativeStopped) call.outcome()).prefix(); own = prefix.object(); others = prefix.otherSlots(); livePlayer = prefix.player(); liveGlobals = prefix.globals();
            }
            others.forEach(pool()::write); pool().write(own);
        }
        void draw(CdObjectPool pool, CdObjectState.Identity identity) {
            draws.add(pool.state(identity.wholeIndex())); var state = pool.state(identity.wholeIndex());
            pool.write(state.moveByte(SPRITE_FLAGS, state.spriteFlags() & 0x7F));
        }
    }
    private static final class P {
        int x = 0x10008000, y = 0x01E51234, xv = 0x123, yv = 0x100, flags = 2, stand, spr = 4;
        CdSolidObject.Player player() {
            var motion = new CdPlayerMotionState(x, y, xv, yv, 0, 2, 0x10, flags, spr, 9, 0x13, 0, 0, 1, 0, 0, 0, 0, 0x56, 0x78);
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(motion, 0x780, 2, 0, 0), 1, 0, stand);
        }
    }
    /** Explicit constructed A-format geometry copied from the existing Boulder prefix fixture.
     * It authenticates no terrain/media. The deliberately invalidF9 foreground witness selects a real missing leaf.
     */
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2; Arrays.fill(heights, 16, 32, (byte) 16); Arrays.fill(widths, 32, 48, (byte) 16); angles[2] = (byte) 0xC0;
        }
        CdSolidObject owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578]; for (int i = 0; i < 60; i++) word(animations, i * 2, 120); animations[122] = (byte) 0xFF;
            byte[] maps = new byte[398], dplcs = new byte[398];
            for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(maps, dplcs, new byte[69472]); byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var assets = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[4], new byte[640], atan, visual);
            return new CdSolidObject(new CdPlayerMovement(terrain, assets));
        }
    }
    private static void word(byte[] bytes, int at, int value) { ByteBuffer.wrap(bytes).putShort(at, (short) value); }
    private static CdR11bAssets past() throws Exception {
        if (retained != null) return retained;
        var root = System.getProperty("soniccd.media.root"); assertNotNull(root); assertFalse(root.isBlank()); Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
        return retained;
    }
}
