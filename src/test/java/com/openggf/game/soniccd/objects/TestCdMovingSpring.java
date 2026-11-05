package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SOURCE ONLY: tests authored before either production mirror. Java/JUnit and compilation UNRUN.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Spring/Main.asm:7-96,130-167;
 * Object Functions.asm:91-128,137-196,228-237; Collision Check.asm:157-179;
 * Object Spawner.asm:329-340; Object Animate.asm:14-95. Constructed terrain/images/ports below
 * exercise real shared floor, movement and animation owners. Actual-source tests use existing media
 * ingress, never authentication stand-ins. No World, completed13423, native/current/pixel/parity proof.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdMovingSpring {
    private static final int WHOLE = 40, MAP_ADDRESS = 0x209A96;
    private static final byte[] MAP_BYTES = hex("0004000a01f8050000f801f8050004f8"), ANI_BYTES = hex("0002080001ff");
    private static CdR12aTerrainAssets retail;

    @Test void ordinarySurfaceAndTwoStorageAliasesHaveOnlyTheRequiredArities() throws Exception {
        assertEquals(1, CdMovingSpring.class.getConstructors().length); assertEquals(6, CdMovingSpring.class.getConstructors()[0].getParameterCount());
        assertEquals(3, CdMovingSpring.Assets.class.getRecordComponents().length); assertEquals(2, CdMovingSpring.Globals.class.getRecordComponents().length);
        assertNotNull(CdMovingSpring.class.getMethod("run", int.class, CdMovingSpring.Globals.class));
        assertEquals(0x32, MOVING_SPRING_BASE_Y.byteOffset()); assertEquals(0x36, MOVING_SPRING_ANCHOR_X.byteOffset());
        var p = new CdObjectPool(); byte[] b = new byte[64]; word(b, 0x32, 0x1234); word(b, 0x36, 0x5678);
        assertArrayEquals(b, p.state(WHOLE).moveWord(MOVING_SPRING_BASE_Y, 0x1234).moveWord(MOVING_SPRING_ANCHOR_X, 0x5678).copyBytes());
        assertThrows(IllegalArgumentException.class, () -> new CdMovingSpring.Globals(65536, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdMovingSpring.Globals(0, -1));
    }

    @Test void actualSourceFactoryBorrowsExactMetadataWithoutAnArtDecodeOrUnpairedPlacement() throws Exception {
        var owner = media().objects(); var decoded = new java.util.HashMap<>((java.util.Map<?, ?>) field(owner, "decoded"));
        var a = CdMovingSpring.Assets.fromAct2Assets(owner); assertSame(owner.movingSpring(), a.visual());
        assertSame(owner.view(CdR12aProfile.Asset.OBJECT_TILE_IDS), a.objectTileIds()); assertEquals(MAP_ADDRESS, a.mappingsAddress());
        assertArrayEquals(MAP_BYTES, a.visual().mappings().copy()); assertArrayEquals(ANI_BYTES, a.visual().animationTable().copy());
        assertEquals(2, a.visual().mappingFrames().size()); assertEquals(1, a.visual().animations().size()); assertEquals(decoded, field(owner, "decoded"));
        var h = new H(new Terrain(), true); assertSame(owner.movingSpring(), h.body.assets().visual());
        var unpaired = new CdObjectPlacementRuntime(owner.placements());
        assertThrows(IllegalArgumentException.class, () -> CdMovingSpring.fromAct2Assets(unpaired, owner, h.terrain,
                h::angle, h::support, h::draw, h, h));
        assertThrows(NullPointerException.class, () -> CdMovingSpring.Assets.fromAct2Assets(null));
    }

    @Test void dirtyInitWritesOnlyHeaderTileDirtyChildAndSinkWithoutDrawOrMandatoryInputs() throws Exception {
        var h = new H(); h.put(dirty(h.state()).moveByte(ID, 0x0F).moveByte(ROUTINE, 0).moveByte(SUBTYPE2, 0)
                .moveByte(SPRITE_FLAGS, 0xA1).moveWord(X, 0x270).moveWord(Y, 0x781));
        var child = dirty(h.pool().state(32)).moveByte(ID, 0); h.pool().write(child); var before = h.placement.capture();
        byte[] expected = init(h.state()); word(expected, 12, 0x782); byte[] childExpected = child.copyBytes();
        childExpected[0] = 0x0A; word(childExpected, 8, 0x270); word(childExpected, 12, 0x771); childExpected[0x39] = (byte) 0xF0;
        word(childExpected, 0x34, h.state().identity().nativeAddress24()); childExpected[0x28] = (byte) h.state().subtype();
        var result = h.run(); assertArrayEquals(expected, result.state().copyBytes()); assertArrayEquals(childExpected, h.pool().state(32).copyBytes());
        assertEquals(List.of(0), h.angles); assertTrue(h.draws.isEmpty()); assertTrue(h.supports.isEmpty());
        unchanged(before, h, List.of(WHOLE, 32)); assertEquals(1, result.effects().stream().filter(CdMovingSpring.ChildCreated.class::isInstance).count());
    }

    @Test void sourceLocalSelectorERejectsEveryNonzeroSecondaryAfterOnlyItsHeader() throws Exception {
        for (int secondary : new int[]{1, 2, 127, 128, 255}) {
            var h = new H(); h.put(dirty(h.state()).moveByte(ID, 0x0F).moveByte(ROUTINE, 0).moveByte(SUBTYPE2, secondary));
            var before = h.placement.capture(); byte[] expected = header(h.state());
            var ex = assertThrows(CdMovingSpring.UnsupportedMovingSpring.class, h::run);
            assertEquals(CdMovingSpring.UnsupportedBoundary.TILE_ID_SUBTYPE2, ex.boundary()); assertEquals(secondary, ex.value());
            assertArrayEquals(expected, h.state().copyBytes()); unchanged(before, h, List.of(WHOLE));
            assertTrue(h.angles.isEmpty()); assertTrue(h.draws.isEmpty()); assertEquals(0, h.pool().state(32).id());
        }
    }

    @Test void malformedFiniteTileDirectoryRetainsTheHeaderAndDoesNotBorrowANeighbour() throws Exception {
        byte[] table = tiles(); word(table, 0x1C, 0xFFFF); var h = new H(new Terrain(), assets(table)); h.put(h.state().moveByte(ROUTINE, 0));
        byte[] expected = header(h.state()); assertThrows(IllegalArgumentException.class, h::run); assertArrayEquals(expected, h.state().copyBytes());
        assertTrue(h.angles.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void firstFreeScansAllIdsAndPreservesBothEarlierAndLaterDirtyChildrenWithoutExecutingThem() throws Exception {
        for (int free : new int[]{32, 41}) {
            var h = new H(); for (int i = 32; i < 128; i++) if (i != WHOLE) h.pool().write(h.pool().state(i).moveByte(ID, 0x24));
            var child = dirty(h.pool().state(free)).moveByte(ID, 0).moveByte(ROUTINE, 0xF3); h.pool().write(child); h.put(h.state().moveByte(ROUTINE, 0));
            var before = h.placement.capture(); h.run(); assertEquals(0x0A, h.pool().state(free).id()); assertEquals(0xF3, h.pool().state(free).routine());
            assertEquals(child.word(X_FRACTION), h.pool().state(free).word(X_FRACTION)); assertEquals(child.word(Y_FRACTION), h.pool().state(free).word(Y_FRACTION));
            assertEquals(0xDA00, h.pool().state(free).word(SPRING_PARENT)); assertTrue(h.draws.isEmpty()); unchanged(before, h, List.of(WHOLE, free));
        }
    }

    @Test void fullPoolDeletesPlainlyThenTheWrapperReallyExaminesTheZeroAnchorAndMayDeleteAgain() throws Exception {
        for (int camera : new int[]{0, 0x270}) {
            var h = new H(new Terrain(), true); h.camera = camera;
            for (int i = 32; i < 128; i++) h.pool().write(h.pool().state(i).moveByte(ID, 0x24));
            h.put(dirty(h.state()).moveByte(ID, 0x0F).moveByte(ROUTINE, 0).moveByte(SUBTYPE2, 0)); var before = h.placement.capture();
            var result = h.run(); assertArrayEquals(new byte[64], result.state().copyBytes());
            long count = h.publications.stream().filter(c -> c.kind() == CdMovingSpring.StoreKind.DELETE).count();
            assertEquals(camera == 0 ? 1 : 2, count); // Zero anchor survives unsigned$80 at camera0, not wrapped$FE80 at camera$270.
            assertEquals(1, result.effects().stream().filter(CdMovingSpring.SlotUnavailable.class::isInstance).count());
            assertTrue(h.angles.isEmpty()); assertTrue(h.draws.isEmpty()); unchanged(before, h, List.of(WHOLE));
        }
        var h = new H(new Terrain(), true); h.camera = 0x80;
        for (int i = 32; i < 128; i++) h.pool().write(h.pool().state(i).moveByte(ID, 0x24));
        h.put(h.state().moveByte(ID, 0x0F).moveByte(ROUTINE, 0)); h.run();
        assertEquals(1, h.publications.stream().filter(c -> c.kind() == CdMovingSpring.StoreKind.DELETE).count());
    }

    @Test void negativeAlignmentSavesTheAdjustedBaseYAndAdvancesWithoutAnimationOrDraw() throws Exception {
        var h = new H(new Terrain().floor(2, 0x23), false); var before = h.placement.capture(); byte[] expected = h.state().copyBytes();
        word(expected, 12, 0x778); word(expected, 0x32, 0x778); expected[0x24] = 4;
        h.run(); assertArrayEquals(expected, h.state().copyBytes()); assertEquals(List.of(0, 0x23), h.angles);
        assertTrue(h.draws.isEmpty()); assertTrue(h.supports.isEmpty()); unchanged(before, h, List.of(WHOLE));
    }

    @Test void nonnegativeAlignmentSinksOneWordPreservingFractionsBaseAndRawTimer() throws Exception {
        var h = new H(); h.put(h.state().moveWord(Y_FRACTION, 0xBEEF).moveWord(MOVING_SPRING_BASE_Y, 0xA55A).moveByte(ANIMATION_TIME, 0xFE));
        byte[] expected = h.state().copyBytes(); word(expected, 12, 0x782); h.run(); assertArrayEquals(expected, h.state().copyBytes());
        assertEquals(2, h.state().routine()); assertEquals(List.of(0), h.angles); assertTrue(h.draws.isEmpty());
    }

    @Test void floorPrezeroPrecedesUnavailableFirstReadAndRetainsOriginalSourcePrefix() throws Exception {
        var f = new Terrain(); f.fg[2 + 7 * 42 + 2] = (byte) 0xF9; var h = new H(f, false);
        h.put(h.state().moveByte(SPRITE_FLAGS, 0x40)); var before = h.placement.capture();
        var ex = assertThrows(IllegalStateException.class, h::run); assertTrue(ex.getMessage().contains("OUTSIDE_SUPPLIED_CHUNKS"));
        assertEquals(List.of(0), h.angles); assertEquals(0, h.primary); unchanged(before, h, List.of());
    }

    @Test void floorKeepsReachedAngleBeforeLaterActualTerrainFailure() throws Exception {
        var f = new Terrain().floor(81, 0x22); f.fg[2 + 7 * 42 + 2] = (byte) 0xA8;
        int at = 80 * 512 + (7 * 16 + 7) * 2; word(f.chunks, at, 0x2400); var h = new H(f, false);
        h.put(h.state().moveByte(SPRITE_FLAGS, 0x40)); var before = h.placement.capture();
        var ex = assertThrows(IllegalStateException.class, h::run); assertTrue(ex.getMessage().contains("unavailable resident bytes: 1024"));
        assertEquals(List.of(0, 0x22), h.angles); assertEquals(0x22, h.primary); unchanged(before, h, List.of());
    }

    @Test void actualFloorFlipSequenceAndReturnedObservationAreVisibleBeforeTheOriginalAlignStore() throws Exception {
        var f = new Terrain().floor(2, 0x22); word(f.chunks, 512 + (8 * 16 + 7) * 2, 0x3001);
        var h = new H(f, true); h.beforePublication = call -> {
            if (call.kind() == CdMovingSpring.StoreKind.SOURCE_STORE) {
                var floor = h.body.currentFloorResult().orElseThrow(); assertEquals(-9, floor.distance());
                assertEquals(0, floor.primaryAngle()); assertEquals(0, h.primary);
            }
        };
        h.run(); assertEquals(List.of(0, 0x22, 0x62, 0x9E, 0x5E, 0), h.angles);
        assertTrue(h.body.currentFloorResult().isEmpty()); assertEquals(0x778, h.state().word(MOVING_SPRING_BASE_Y));
    }

    @Test void everyNonzeroRawTimeStopWordSkipsFloorSupportMotionAndAnimatorButDraws() throws Exception {
        for (int stop : new int[]{1, 0x100, 0x8000, 0xFFFF}) {
            var h = new H(); h.stop = stop; h.put(dirty(h.state()).moveByte(ID, 0x0F).moveByte(ROUTINE, 4)
                    .moveWord(MOVING_SPRING_ANCHOR_X, 0x270)); var before = h.placement.capture();
            byte[] expected = h.state().copyBytes(); expected[1] &= 0x7F; h.run(); assertArrayEquals(expected, h.state().copyBytes());
            assertEquals(1, h.draws.size()); assertTrue(h.angles.isEmpty()); assertTrue(h.supports.isEmpty()); unchanged(before, h, List.of(WHOLE));
        }
    }

    @Test void mainUsesUnsignedBaseDifferenceAndPreservesBothRawPositionFractionsAndStoredYVelocity() throws Exception {
        for (int[] row : new int[][]{{0x778, 0x180}, {0x783, 0x180}, {0x784, -0x180}, {0x777, -0x180}}) {
            var h = new H(new Terrain().floor(2, 0), false); h.put(h.state().moveByte(ROUTINE, 4)
                    .moveWord(MOVING_SPRING_BASE_Y, row[0]).moveWord(X_FRACTION, 0xFF80).moveWord(Y_FRACTION, 0x80)
                    .moveWord(Y_VELOCITY, -0x80).moveByte(ANIMATION_TIME, 2));
            byte[] expected = h.state().copyBytes(); word(expected, 12, 0x778); word(expected, 0x10, row[1]);
            ByteBuffer.wrap(expected).putInt(8, 0x0270FF80 + (row[1] << 8)).putInt(12, 0x07780080 - 0x8000);
            expected[0x1E] = 1; expected[1] &= 0x7F; h.run(); assertArrayEquals(expected, h.state().copyBytes());
            assertEquals(0xFF80, h.state().word(Y_VELOCITY)); assertTrue(h.supports.isEmpty());
        }
    }

    @Test void supportReadIsLazyAndSharedResistanceUsesActualPrimaryOrDynamicWithoutChangingStoredVelocity() throws Exception {
        for (int link : new int[]{0, 32, 127}) for (int flip : new int[]{0, 1}) {
            var h = new H(new Terrain().floor(2, 0), false); h.put(h.state().moveByte(ROUTINE, 4).moveByte(FLAGS, 8)
                    .moveByte(STAND_LINK_3D, link).moveWord(MOVING_SPRING_BASE_Y, 0x778));
            if (link != 0) h.pool().write(h.pool().state(link).moveByte(ID, 0x1E).moveByte(FLAGS, flip));
            h.primarySupport = new CdPlayerMovement.RawObjectSupport(0, 0x1E, flip); h.run();
            assertEquals(1, h.supports.size()); assertEquals(link, h.supports.getFirst().wholeIndex());
            assertEquals(0x02700000 + ((flip == 0 ? 0x80 : 0x280) << 8), h.state().longBits(X_POSITION));
            assertEquals(0x180, h.state().signedWord(X_VELOCITY));
        }
        var h = new H(new Terrain().floor(2, 0), false); h.put(h.state().moveByte(ROUTINE, 4).moveByte(STAND_LINK_3D, 255));
        h.run(); assertTrue(h.supports.isEmpty());
    }

    @Test void missingMismatchedReservedAndOutsideSupportStopAtSharedReachedReadAfterOnlyFloorYAndTurn() throws Exception {
        for (int link : new int[]{1, 31, 128, 255, 0, 32}) {
            var h = new H(new Terrain().floor(2, 0), false); h.put(h.state().moveByte(ROUTINE, 4).moveByte(FLAGS, 8).moveByte(STAND_LINK_3D, link));
            h.onSupport = call -> Optional.empty(); byte[] expected = h.state().copyBytes(); word(expected, 12, 0x778); word(expected, 0x10, -0x180);
            assertThrows(CdPlayerMovement.UnsupportedMotion.class, h::run); assertArrayEquals(expected, h.state().copyBytes());
            assertEquals(link == 0 || link == 32 ? 1 : 0, h.supports.size()); assertTrue(h.draws.isEmpty());
        }
        var h = new H(new Terrain().floor(2, 0), false); h.put(h.state().moveByte(ROUTINE, 4).moveByte(FLAGS, 8));
        h.onSupport = call -> Optional.of(new CdPlayerMovement.RawObjectSupport(32, 0, 0));
        assertThrows(CdPlayerMovement.UnsupportedMotion.class, h::run);
        var x = new H(new Terrain().floor(2, 0), false); x.put(x.state().moveByte(ROUTINE, 4).moveByte(FLAGS, 8).moveByte(STAND_LINK_3D, 32));
        x.onSupport = call -> Optional.of(new CdPlayerMovement.RawObjectSupport(32, 0x1E, 0));
        assertThrows(IllegalStateException.class, x::run); assertTrue(x.draws.isEmpty());
    }

    @Test void sharedAnimatorHoldsNineVisitsPerWheelFrameAndDoesNotPrimeOnInitAlign() throws Exception {
        var h = new H(new Terrain().floor(2, 0), false); h.put(h.state().moveByte(ROUTINE, 4).moveWord(X_VELOCITY, 0)
                .moveWord(MOVING_SPRING_BASE_Y, 0x778).moveByte(ANIMATION_TIME, 0));
        for (int visit = 1; visit <= 28; visit++) {
            h.run(); assertEquals(((visit - 1) / 9) % 2, h.state().unsignedByte(MAP_FRAME));
            assertEquals(8 - (visit - 1) % 9, h.state().unsignedByte(ANIMATION_TIME));
        }
        assertEquals(28, h.draws.size());
    }

    @Test void heldInvalidAnimationIdAvoidsDirectoryReadsAndReachedFailuresKeepOnlyTheirTruePrefixes() throws Exception {
        var h = new H(new Terrain().floor(2, 0), false); h.put(h.state().moveByte(ROUTINE, 4).moveByte(ANIMATION, 255)
                .moveByte(PREVIOUS_ANIMATION, 255).moveByte(ANIMATION_TIME, 2)); h.run(); assertEquals(1, h.state().unsignedByte(ANIMATION_TIME));
        for (boolean changed : new boolean[]{false, true}) {
            var x = new H(new Terrain().floor(2, 0), false); x.put(x.state().moveByte(ROUTINE, 4).moveByte(ANIMATION, 1)
                    .moveByte(PREVIOUS_ANIMATION, changed ? 0 : 1).moveByte(ANIMATION_TIME, 0).moveByte(ANIMATION_FRAME, 0x55));
            var ex = assertThrows(CdMovingSpring.UnsupportedMovingSpring.class, x::run);
            assertEquals(CdMovingSpring.UnsupportedBoundary.ANIMATION_DIRECTORY_READ, ex.boundary()); assertNotNull(ex.getCause());
            assertEquals(0xFF, x.state().unsignedByte(ANIMATION_TIME)); assertEquals(changed ? 0 : 0x55, x.state().unsignedByte(ANIMATION_FRAME));
            assertEquals(1, x.state().unsignedByte(PREVIOUS_ANIMATION)); assertTrue(x.draws.isEmpty());
        }
        var x = new H(new Terrain().floor(2, 0), false); x.put(x.state().moveByte(ROUTINE, 4).moveByte(ANIMATION_TIME, 0).moveByte(ANIMATION_FRAME, 255));
        var ex = assertThrows(CdMovingSpring.UnsupportedMovingSpring.class, x::run);
        assertEquals(CdMovingSpring.UnsupportedBoundary.ANIMATION_FRAME_READ, ex.boundary()); assertEquals(8, x.state().unsignedByte(ANIMATION_TIME));
        assertEquals(255, x.state().unsignedByte(ANIMATION_FRAME)); assertTrue(x.draws.isEmpty());
    }

    @Test void plainAnchorCullUsesUnsignedWordEqualityAndNeverClearsSavedFlags() throws Exception {
        for (int[] row : new int[][]{{0x480, 0}, {0x500, 1}, {0x80, 1}, {0x270, 0}}) {
            var h = new H(); h.camera = 0x280; h.stop = 1; h.put(dirty(h.state()).moveByte(ID, 0x0F).moveByte(ROUTINE, 4)
                    .moveWord(MOVING_SPRING_ANCHOR_X, row[0]).moveWord(X, 0xF000)); var before = h.placement.capture();
            var result = h.run(); assertEquals(row[1] == 1, result.deleted()); assertEquals(1, h.draws.size());
            if (result.deleted()) assertArrayEquals(new byte[64], h.state().copyBytes()); unchanged(before, h, List.of(WHOLE));
        }
    }

    @Test void originalOperationReceiptsRejectEqualClonesInactiveRecordsAndDuplicateCompletion() throws Exception {
        var h = new H(new Terrain(), true); h.put(h.state().moveByte(ROUTINE, 0));
        h.beforePublication = call -> {
            var clone = new CdMovingSpring.Publication(call.ordinal(), call.kind(), call.actualCut(), call.expectedSource(), call.before(), call.output());
            assertEquals(call, clone); assertFalse(h.body.isCurrentCall(clone)); assertThrows(IllegalStateException.class, () -> h.body.currentCallOrdinal(clone));
        };
        h.run(); assertEquals(5, h.completed.size()); assertNull(h.pending);
        Object old = h.completed.getFirst(); assertFalse(h.body.isCurrentCall(old)); assertThrows(IllegalStateException.class, () -> h.completed(old));
        assertThrows(IllegalStateException.class, () -> h.prepared(old)); assertThrows(UnsupportedOperationException.class, () -> h.result.effects().clear());
    }

    @Test void allObserverReceiptAngleSupportAndDrawCallbacksRejectReentry() throws Exception {
        var h = new H(new Terrain().floor(2, 0), true); h.put(h.state().moveByte(ROUTINE, 4).moveByte(FLAGS, 8));
        h.beforePublication = call -> assertThrows(IllegalStateException.class, h::run); h.afterPublication = h.beforePublication;
        h.beforeCall = call -> assertThrows(IllegalStateException.class, h::run); h.afterCall = h.beforeCall;
        h.onPrepared = h.beforeCall; h.onCompleted = h.beforeCall; h.onAngle = call -> assertThrows(IllegalStateException.class, h::run);
        h.onSupport = call -> { assertThrows(IllegalStateException.class, h::run); return Optional.of(h.primarySupport); };
        h.onDraw = call -> assertThrows(IllegalStateException.class, h::run); h.run(); assertEquals(1, h.draws.size()); assertNull(h.pending);
    }

    @Test void callbackMutationsAreRejectedOnReturnAndOriginalThrowKeepsSuppressedVerificationFaults() throws Exception {
        for (String port : List.of("observer", "guard", "angle", "support", "draw")) for (boolean throwing : new boolean[]{false, true}) {
            var h = new H(new Terrain().floor(2, 0), true); h.put(h.state().moveByte(ROUTINE, 4).moveByte(FLAGS, 8));
            var original = new IllegalArgumentException("original " + port); Consumer<Object> mutation = call -> {
                h.pool().write(h.pool().state(41).moveByte(SUBTYPE, 9)); if (throwing) throw original;
            };
            switch (port) {
                case "observer" -> h.beforeCall = mutation;
                case "guard" -> h.onPrepared = mutation;
                case "angle" -> h.onAngle = mutation::accept;
                case "support" -> h.onSupport = call -> { mutation.accept(call); return Optional.of(h.primarySupport); };
                case "draw" -> h.onDraw = mutation::accept;
            }
            var ex = assertThrows(RuntimeException.class, h::run);
            if (throwing) { assertSame(original, ex); assertEquals(1, ex.getSuppressed().length); }
            else assertInstanceOf(IllegalStateException.class, ex);
            assertEquals(9, h.pool().state(41).subtype()); assertFalse(h.body.isCurrentCall(h.pending));
        }
    }

    @Test void savedAndPlacementChangesInReadonlyPortsCannotCompleteAndAreNeverRepaired() throws Exception {
        var h = new H(new Terrain(), true); h.beforeCall = call -> h.placement.bsetSavedFlag(3, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        var before = h.placement.capture(); assertThrows(IllegalStateException.class, h::run);
        assertNotEquals(before.savedFlags().unsignedByte(12), h.placement.capture().savedFlags().unsignedByte(12)); assertTrue(h.completed.isEmpty());
        var x = new H(new Terrain(), true); x.beforeCall = call -> x.placement.spawnObjects(0, 1); var status = x.placement.status();
        assertThrows(IllegalStateException.class, x::run); assertNotEquals(status, x.placement.status());
    }

    @Test void publicationAndDrawFailuresKeepExactCompletedPrefixesAndReleaseAllInternalGuards() throws Exception {
        for (boolean after : new boolean[]{false, true}) {
            var h = new H(new Terrain(), true); h.put(h.state().moveByte(ROUTINE, 0)); var old = h.state(); var sentinel = new AssertionError("original publication");
            Consumer<CdMovingSpring.Publication> stop = call -> { throw sentinel; };
            if (after) h.afterPublication = stop; else h.beforePublication = stop;
            assertSame(sentinel, assertThrows(AssertionError.class, h::run)); assertArrayEquals(after ? header(old) : old.copyBytes(), h.state().copyBytes());
            h.beforePublication = call -> { }; h.afterPublication = call -> { }; h.pending = null; assertDoesNotThrow(h::run);
        }
        var h = new H(new Terrain().floor(2, 0), true); h.put(h.state().moveByte(ROUTINE, 4)); var sentinel = new AssertionError("actual draw");
        h.onDraw = call -> { var s = h.state(); h.put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() & 0x7F)); throw sentinel; };
        assertSame(sentinel, assertThrows(AssertionError.class, h::run)); assertEquals(1, h.state().unsignedByte(ANIMATION_TIME));
        assertEquals(0, h.state().spriteFlags() & 0x80); h.onDraw = call -> { }; h.pending = null; assertDoesNotThrow(h::run);
    }

    @Test void invalidRoutineOrForeignIdRejectsBeforeGlobalsCallbacksAndEveryStore() throws Exception {
        var h = new H();
        for (int routine : new int[]{1, 3, 5, 6, 0x80, 255}) {
            h.put(h.state().moveByte(ROUTINE, routine)); var before = h.placement.capture();
            var ex = assertThrows(CdMovingSpring.UnsupportedMovingSpring.class, () -> h.body.run(WHOLE, null));
            assertEquals(CdMovingSpring.UnsupportedBoundary.ROUTINE_DISPATCH, ex.boundary()); assertEquals(routine, ex.value()); unchanged(before, h, List.of());
        }
        h.put(h.state().moveByte(ID, 0x0A)); var before = h.placement.capture(); assertThrows(IllegalArgumentException.class, () -> h.body.run(WHOLE, null)); unchanged(before, h, List.of());
    }

    @Test void childUsesExistingSpringDrawBeforeLiveParentFollowEvenAfterPlainParentDeletion() throws Exception {
        var h = new H(); h.put(h.state().moveByte(ROUTINE, 0)); h.run(); var child = h.pool().state(32);
        h.pool().write(child.moveByte(ROUTINE, 2).moveByte(ROUTINE_2, 0).moveByte(SPRITE_FLAGS, 4).moveWord(SPRING_ANCHOR_X, 0x270));
        var observed = new ArrayList<CdObjectState>(); var spring = spring(h, observed); var in = springInputs();
        h.put(h.state().moveWord(X, 0x280).moveWord(Y, 0x790)); spring.run(32, new CdSpring.Globals(0x270), in);
        assertEquals(0x270, observed.getFirst().centreX()); assertEquals(0x771, observed.getFirst().centreY());
        assertEquals(0x280, h.pool().state(32).centreX()); assertEquals(0x780, h.pool().state(32).centreY());
        h.pool().deleteObject(WHOLE); spring.run(32, new CdSpring.Globals(0x270), in);
        assertEquals(0, h.pool().state(32).centreX()); assertEquals(0xFFF0, h.pool().state(32).centreY()); assertEquals(0x0A, h.pool().state(32).id());
        for (int parent : new int[]{0xD040, 0xDA01, 0xF000}) {
            var live = h.pool().state(32); h.pool().write(live.moveWord(SPRING_PARENT, parent));
            var ex = assertThrows(CdSpring.UnsupportedSpring.class, () -> spring.run(32, new CdSpring.Globals(0x270), in));
            assertEquals(CdSpring.UnsupportedBoundary.PARENT_READ, ex.boundary());
        }
    }

    private static final class H implements CdMovingSpring.DispatchObserver, CdMovingSpring.ReceiptGuard {
        final CdObjectPlacementRuntime placement; final CdPlayerTerrain terrain; final CdMovingSpring body;
        final List<Integer> angles = new ArrayList<>(); final List<CdMovingSpring.DrawCall> draws = new ArrayList<>();
        final List<CdMovingSpring.SupportCall> supports = new ArrayList<>(); final List<CdMovingSpring.Publication> publications = new ArrayList<>();
        final List<Object> completed = new ArrayList<>(); final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        Object pending; long ordinal; boolean running; int camera = 0x270, stop, primary = 0x66; CdMovingSpring.Result result;
        CdPlayerMovement.RawObjectSupport primarySupport = new CdPlayerMovement.RawObjectSupport(0, 1, 0);
        Consumer<CdMovingSpring.Publication> beforePublication = call -> { }, afterPublication = call -> { };
        Consumer<Object> beforeCall = call -> { }, afterCall = call -> { }, onPrepared = call -> { }, onCompleted = call -> { };
        Consumer<CdMovingSpring.PrimaryAngleCall> onAngle = call -> { }; Consumer<CdMovingSpring.DrawCall> onDraw = call -> { };
        Function<CdMovingSpring.SupportCall, Optional<CdPlayerMovement.RawObjectSupport>> onSupport;
        H() throws Exception { this(new Terrain(), false); }
        H(Terrain data, CdMovingSpring.Assets a) { placement = new CdObjectPlacementRuntime(layout()); terrain = data.probes(); seed();
            body = new CdMovingSpring(placement, a, terrain, this::angle, this::support, this::draw); }
        H(Terrain data, boolean observed) throws Exception {
            terrain = data.probes(); placement = observed ? CdObjectPlacementRuntime.fromAssets(media(), CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]))
                    : new CdObjectPlacementRuntime(layout()); seed();
            body = observed ? CdMovingSpring.fromAct2Assets(placement, media().objects(), terrain, this::angle, this::support, this::draw, this, this)
                    : new CdMovingSpring(placement, assets(tiles()), terrain, this::angle, this::support, this::draw);
        }
        void seed() { put(state().moveByte(ID, 0x0F).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x84).moveByte(Y_RADIUS, 7)
                .moveWord(X, 0x270).moveWord(Y, 0x781).moveWord(MOVING_SPRING_ANCHOR_X, 0x270).moveWord(X_VELOCITY, 0x180).moveByte(ANIMATION_TIME, 2)); }
        CdObjectPool pool() { return placement.pool(); } CdObjectState state() { return pool().state(WHOLE); } void put(CdObjectState s) { pool().write(s); }
        CdMovingSpring.Result run() {
            if (running) return body.run(WHOLE, new CdMovingSpring.Globals(camera, stop)); running = true; ordinal = 0;
            try { return result = body.run(WHOLE, new CdMovingSpring.Globals(camera, stop)); } finally { running = false; }
        }
        void angle(CdMovingSpring.PrimaryAngleCall call) { onAngle.accept(call); primary = call.value(); angles.add(call.value()); }
        Optional<CdPlayerMovement.RawObjectSupport> support(CdMovingSpring.SupportCall call) {
            supports.add(call); if (onSupport != null) return onSupport.apply(call);
            if (call.wholeIndex() == 0) return Optional.of(primarySupport); var s = pool().state(call.wholeIndex());
            return Optional.of(new CdPlayerMovement.RawObjectSupport(call.wholeIndex(), s.id(), s.flags()));
        }
        void draw(CdMovingSpring.DrawCall call) { draws.add(call); onDraw.accept(call); var s = state(); put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() & 0x7F)); }
        public void beforePublication(CdMovingSpring.Publication call) { publications.add(call); beforePublication.accept(call); }
        public void afterPublication(CdMovingSpring.Publication call) { afterPublication.accept(call); }
        public void beforeCall(Object call) { beforeCall.accept(call); } public void afterCall(Object call) { afterCall.accept(call); }
        public void prepared(Object call) {
            if (!body.isCurrentCall(call) || pending != null || seen.containsKey(call)) throw new IllegalStateException("not an original preparation");
            assertEquals(++ordinal, body.currentCallOrdinal(call)); pending = call; onPrepared.accept(call);
        }
        public void completed(Object call) {
            if (!body.isCurrentCall(call) || pending != call || seen.containsKey(call)) throw new IllegalStateException("not an original completion");
            onCompleted.accept(call); pending = null; completed.add(call); seen.put(call, true);
        }
    }

    /** Existing floor-fixture format: cell(2,7), block column7/row8 has a full-height top atY$780. */
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50], ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() { fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; fg[2 + 7 * 42 + 2] = 2; }
        Terrain floor(int chunk, int angle) { word(chunks, (chunk - 1) * 512 + (8 * 16 + 7) * 2, 0x2001); ids[1] = 7; angles[7] = (byte) angle;
            Arrays.fill(heights, 7 * 16, 7 * 16 + 16, (byte) 16); return this; }
        CdR11aTerrain raw() { return CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths); }
        CdPlayerTerrain probes() { return new CdPlayerTerrain(raw()); }
    }
    private static CdMovingSpring.Assets assets(byte[] table) { return new CdMovingSpring.Assets(CdAssetBytes.copyOf(table),
            CdObjectVisualAssets.decode(MAP_BYTES, 2, ANI_BYTES, 1, 8), MAP_ADDRESS); }
    /** Constructed complete74-byte directory with selectorE's distinct one-WORD body at42..44. */
    private static byte[] tiles() { byte[] b = new byte[74]; for (int i = 0; i < 18; i++) word(b, 2 * i, 36); word(b, 28, 42); word(b, 30, 44); word(b, 42, 0x338); return b; }
    private static byte[] header(CdObjectState old) { byte[] b = old.copyBytes(); b[0x24] += 2; b[1] |= 4; b[0x18] = 4;
        ByteBuffer.wrap(b).putInt(4, MAP_ADDRESS); b[0x19] = 8; b[0x16] = 7; word(b, 0x36, old.centreX()); word(b, 0x10, 0x180); return b; }
    private static byte[] init(CdObjectState old) { byte[] b = header(old); word(b, 2, 0x338); return b; }
    private static CdObjectPlacements layout() { var b = ByteBuffer.allocate(26); b.putShort((short) 12).putShort((short) 20).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        b.putShort((short) 0xFFFF).putInt(0).putShort((short) 0); b.putShort((short) 0xFFFF).putInt(0); return CdObjectPlacements.decodeLayout(b.array()); }
    private static CdObjectState dirty(CdObjectState s) throws Exception { byte[] b = new byte[64]; for (int i = 0; i < 64; i++) b[i] = (byte) (0x31 + i * 17);
        var constructor = CdObjectState.class.getDeclaredConstructor(Object.class, CdObjectState.Identity.class, byte[].class); constructor.setAccessible(true);
        return constructor.newInstance(field(s, "owner"), s.identity(), b); }
    private static void unchanged(CdObjectPlacementRuntime.Snapshot before, H h, List<Integer> except) {
        assertEquals(before.status(), h.placement.status()); assertArrayEquals(before.savedFlags().copy(), h.placement.capture().savedFlags().copy());
        assertEquals(96, before.pool().slots().size()); for (var old : before.pool().slots()) if (!except.contains(old.identity().wholeIndex())) assertEquals(old, h.pool().state(old.identity().wholeIndex()));
    }
    private static void word(byte[] b, int at, int value) { b[at] = (byte) (value >>> 8); b[at + 1] = (byte) value; }
    private static byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    private static Object field(Object object, String name) throws Exception { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private static CdR12aTerrainAssets media() throws Exception {
        if (retail != null) return retail; String root = System.getProperty("soniccd.media.root"); assertNotNull(root); assertFalse(root.isBlank()); Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) { var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); assertEquals(1, cues.size()); cue = cues.getFirst(); }
        try (var p = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) { retail = CdR12aTerrainAssets.fromPrograms(p); }
        return retail;
    }
    private static CdR11aPlayerAssets playerData() {
        byte[] anim = new byte[578], maps = new byte[398], dplcs = new byte[398], atan = new byte[257];
        for (int i = 0; i < 60; i++) word(anim, 2 * i, 120); anim[122] = (byte) 0xFF;
        for (int i = 0; i < 198; i++) { word(maps, 2 * i, 396); word(dplcs, 2 * i, 396); } for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
        return CdR11aPlayerAssets.decode(anim, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF}, new byte[4], new byte[640], atan,
                CdSonicVisualAssets.decode(maps, dplcs, new byte[69472]));
    }
    private static CdSpring spring(H h, List<CdObjectState> drawn) throws Exception {
        var owner = media().objects(); var a = new CdSpring.Assets(owner.springVertical(), owner.springHorizontal(), owner.springDiagonal(),
                owner.profile().range(CdR12aProfile.Asset.MAP_SPRING).nativeAddress(), owner.view(CdR12aProfile.Asset.SPRING_SPEEDS), owner.view(CdR12aProfile.Asset.OBJECT_TILE_IDS));
        var data = playerData(); return new CdSpring(h.placement, a, new CdPlayerMath(data), new CdSolidObject(new CdPlayerMovement(new Terrain().raw(), data)),
                call -> fail("unreached child Solid"), call -> fail("unreached child launch"), (pool, identity) -> { var s = pool.state(identity.wholeIndex()); drawn.add(s); pool.write(s.moveByte(SPRITE_FLAGS, s.spriteFlags() & 0x7F)); },
                sound -> fail("unreached child FM"));
    }
    private static CdSpring.SolidInputs springInputs() {
        var motion = new CdPlayerMotionState(0, 0, 0, 0, 0, 2, 0, 0, 4, 9, 19, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var player = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(motion, 0x780, 2, 0, 0), 1, 0, 0);
        return new CdSpring.SolidInputs(player, new CdSolidObject.Gates(0, 0), new CdSolidObject.Globals(0, 0, 0, 0));
    }
}
