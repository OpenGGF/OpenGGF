package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdSpringboard;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import static com.openggf.game.soniccd.objects.CdSpringboard.PrimaryField.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Independent literal one-store vectors on an authenticated original-USA R12B Frame source.
 * J=bc23403ed0b12be1105a92738adab9deb913036d; N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Springboard/Main.asm:165-373 owns the finite nine player targets. This is value preparation,
 * not Springboard invocation, pending-receipt, placement, private-pass or route certification.
 * Authored before the preparation port; all Java/JUnit execution UNRUN in this external packet.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdR12bSpringboardFrame {
    private static CdR12bTerrainAssets terrain;
    private static CdR12bPlayerAssets player;
    private static CdObjectPlacementRuntime metadata;

    @BeforeAll
    static void loadConfiguredOriginalUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root to original USA media");
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured media must contain exactly one original-USA CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            terrain = CdR12bTerrainAssets.fromPrograms(source);
            player = CdR12bPlayerAssets.fromPrograms(source);
        }
        metadata = CdObjectPlacementRuntime.fromAssets(terrain,
                CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));
    }

    private record Vector(CdSpringboard.PrimaryField field, int old, int value, int[] motion) { }

    /** All twenty motion values are literal expectations, independently of the component projector. */
    private static List<Vector> vectors() {
        return List.of(
            new Vector(CENTRE_Y_LONG, 0xFEDC89AB, 0x80001234, new int[]{
                0x81234567,0x80001234,-0x123,0x234,-0x345,2,0x56,0x42,0xA3,7,11,6,7,9,0xEF,-0x1234,0x44,0xD1,0x77,0x88}),
            new Vector(CENTRE_Y_WORD, 0xFEDC, 0x8000, new int[]{
                0x81234567,0x800089AB,-0x123,0x234,-0x345,2,0x56,0x42,0xA3,7,11,6,7,9,0xEF,-0x1234,0x44,0xD1,0x77,0x88}),
            new Vector(Y_VELOCITY, 0x234, -0x7FFF, new int[]{
                0x81234567,0xFEDC89AB,-0x123,-0x7FFF,-0x345,2,0x56,0x42,0xA3,7,11,6,7,9,0xEF,-0x1234,0x44,0xD1,0x77,0x88}),
            new Vector(FLAGS, 0x42, 0xFD, new int[]{
                0x81234567,0xFEDC89AB,-0x123,0x234,-0x345,2,0x56,0xFD,0xA3,7,11,6,7,9,0xEF,-0x1234,0x44,0xD1,0x77,0x88}),
            new Vector(PLAYER_JUMP, 9, 0xFE, new int[]{
                0x81234567,0xFEDC89AB,-0x123,0x234,-0x345,2,0x56,0x42,0xA3,7,11,6,7,0xFE,0xEF,-0x1234,0x44,0xD1,0x77,0x88}),
            new Vector(STICK, 0x44, 0x81, new int[]{
                0x81234567,0xFEDC89AB,-0x123,0x234,-0x345,2,0x56,0x42,0xA3,7,11,6,7,9,0xEF,-0x1234,0x81,0xD1,0x77,0x88}),
            new Vector(Y_RADIUS, 11, 0x80, new int[]{
                0x81234567,0xFEDC89AB,-0x123,0x234,-0x345,2,0x56,0x42,0xA3,7,0x80,6,7,9,0xEF,-0x1234,0x44,0xD1,0x77,0x88}),
            new Vector(X_RADIUS, 7, 0xFF, new int[]{
                0x81234567,0xFEDC89AB,-0x123,0x234,-0x345,2,0x56,0x42,0xA3,0xFF,11,6,7,9,0xEF,-0x1234,0x44,0xD1,0x77,0x88}),
            new Vector(ANIMATION, 6, 0xAA, new int[]{
                0x81234567,0xFEDC89AB,-0x123,0x234,-0x345,2,0x56,0x42,0xA3,7,11,0xAA,7,9,0xEF,-0x1234,0x44,0xD1,0x77,0x88}));
    }

    @Test
    void nineLiteralNativeTargetsPrepareOnlyThePrebuiltStateWithoutPublishingAnyFrameOwner() {
        for (var v : vectors()) {
            try (var f = frame(state(), environment())) {
                var actor = f.captureActor(); var shared = f.captureShared(); var source = f.stageSource();
                var output = withMotion(f.state(), motion(v.motion()));
                var call = call(f.state(), f.environment(), output, v.field(), v.old(), v.value());
                var ready = f.prepareObjectSpringboardPlayerStore(call);
                assertSame(call.expectedState(), ready.expectedState());
                assertSame(call.expectedEnvironment(), ready.expectedEnvironment());
                assertSame(output, ready.finalState(), "Preparation must retain the already-built output");
                assertSame(call.expectedEnvironment(), ready.finalEnvironment());
                assertEquals(motion(v.motion()), ready.finalState().motion());
                assertUnwrittenState(actor.state(), ready.finalState());
                assertEquals(0xE7, ready.finalState().rotationAngle(), "Raw native byte $2B is not angle-normalized");
                assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
                assertSame(source, f.stageSource()); assertFalse(f.closed()); assertFalse(f.paused());
                f.validatePreparedObjectSolidPublication(ready);
                f.applyPreparedObjectSolidPublication(ready);
                assertSame(output, f.state()); assertSame(shared.environment(), f.environment());
                assertSame(shared.artState(), f.artState()); assertSame(shared.vIntClock(), f.vIntClock());
                assertEquals(actor.display(), f.display());
            }
        }
    }

    @Test
    void expectedOldAndBothStoreWidthsAreValidatedIndependentlyOfThePrebuiltOutput() {
        try (var f = frame(state(), environment())) {
            var actor = f.captureActor(); var shared = f.captureShared();
            for (var v : vectors()) {
                var output = withMotion(f.state(), motion(v.motion()));
                assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSpringboardPlayerStore(
                        call(f.state(), f.environment(), output, v.field(), v.old() ^ 1, v.value())), v.field().name());
                int[] bad = switch (v.field()) {
                    case CENTRE_Y_LONG -> new int[0]; // Every int is a native LONG bit pattern.
                    case CENTRE_Y_WORD -> new int[]{-1, 0x10000};
                    case Y_VELOCITY -> new int[]{-0x8001, 0x8000, 0xFFFF};
                    default -> new int[]{-1, 0x100};
                };
                for (int value : bad) {
                    assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSpringboardPlayerStore(
                            call(f.state(), f.environment(), output, v.field(), value, v.value())));
                    assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSpringboardPlayerStore(
                            call(f.state(), f.environment(), output, v.field(), v.old(), value)));
                }
            }
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    @Test
    void everyOutputStateLeafIncludingAllHistoryBytesMustMatchExactlyTheOneDeclaredStore() throws Exception {
        try (var f = frame(state(), environment())) {
            var actor = f.captureActor(); var shared = f.captureShared(); var v = vectors().getLast();
            var output = withMotion(f.state(), motion(v.motion()));
            var changes = oneLeafChanges(output, "State");
            assertEquals(293, changes.size(), "20 motion + 3 cursor + 5 header + 4 timers + 257 history + 1+2+1");
            for (var change : changes) {
                assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSpringboardPlayerStore(
                        call(f.state(), f.environment(), (CdPlayerBody.State) change.value(), ANIMATION, 6, 0xAA)), change.path());
            }
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    @Test
    void completeStateAndEveryEnvironmentLeafAreComparedByValueBeforePreparation() throws Exception {
        try (var f = frame(state(), environment())) {
            var actor = f.captureActor(); var shared = f.captureShared(); var v = vectors().getLast();
            var output = withMotion(f.state(), motion(v.motion()));
            for (var change : oneLeafChanges(f.state(), "expectedState")) {
                assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSpringboardPlayerStore(
                        call((CdPlayerBody.State) change.value(), f.environment(), output, ANIMATION, 6, 0xAA)), change.path());
            }
            var changes = oneLeafChanges(f.environment(), "expectedEnvironment");
            assertEquals(38, changes.size(), "Movement 3+4+7+10, controls5, globals8, dynamic-slots1");
            for (var change : changes) {
                assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSpringboardPlayerStore(
                        call(f.state(), (CdPlayerBody.Environment) change.value(), output, ANIMATION, 6, 0xAA)), change.path());
            }
            assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    @Test
    void unsignedWordYRetainsFractionWhileLongYReplacesEveryBitAndAllLimitsRemainNative() throws Exception {
        try (var f = frame(state(), environment())) {
            for (int[] pair : new int[][]{{0,0x000089AB},{0xFFFF,0xFFFF89AB}}) {
                var output = withMotion(f.state(), (CdPlayerMotionState) replace(f.state().motion(), 1, pair[1]));
                var ready = f.prepareObjectSpringboardPlayerStore(call(f.state(), f.environment(), output, CENTRE_Y_WORD, 0xFEDC, pair[0]));
                assertEquals(pair[1], ready.finalState().motion().centreY16_16());
                assertEquals(0x89AB, ready.finalState().motion().centreY16_16() & 0xFFFF);
            }
            for (int value : new int[]{0, Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0x0000FFFF}) {
                var output = withMotion(f.state(), (CdPlayerMotionState) replace(f.state().motion(), 1, value));
                var ready = f.prepareObjectSpringboardPlayerStore(call(f.state(), f.environment(), output, CENTRE_Y_LONG, 0xFEDC89AB, value));
                assertEquals(value, ready.finalState().motion().centreY16_16());
            }
            for (var v : vectors()) {
                if (v.field() == CENTRE_Y_LONG || v.field() == CENTRE_Y_WORD) continue;
                int index = switch (v.field()) {
                    case Y_VELOCITY -> 3; case FLAGS -> 7; case PLAYER_JUMP -> 13; case STICK -> 16;
                    case Y_RADIUS -> 10; case X_RADIUS -> 9; case ANIMATION -> 11;
                    default -> throw new AssertionError(v.field());
                };
                int[] limits = v.field() == Y_VELOCITY ? new int[]{-0x8000, 0x7FFF} : new int[]{0, 0xFF};
                for (int value : limits) {
                    var output = withMotion(f.state(), (CdPlayerMotionState) replace(f.state().motion(), index, value));
                    assertSame(output, f.prepareObjectSpringboardPlayerStore(
                            call(f.state(), f.environment(), output, v.field(), v.old(), value)).finalState());
                }
            }
        }
    }

    @Test
    void malformedOutputCannotOmitTheStoreUseAnotherTargetOrChangeTheWordYFraction() throws Exception {
        try (var f = frame(state(), environment())) {
            for (var v : vectors()) {
                assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSpringboardPlayerStore(
                        call(f.state(), f.environment(), f.state(), v.field(), v.old(), v.value())));
            }
            var wrongFraction = withMotion(f.state(), (CdPlayerMotionState) replace(f.state().motion(), 1, 0x800089AA));
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSpringboardPlayerStore(
                    call(f.state(), f.environment(), wrongFraction, CENTRE_Y_WORD, 0xFEDC, 0x8000)));
            var wrongTarget = withMotion(f.state(), motion(vectors().get(7).motion()));
            assertThrows(IllegalArgumentException.class, () -> f.prepareObjectSpringboardPlayerStore(
                    call(f.state(), f.environment(), wrongTarget, Y_RADIUS, 11, 0xFF)));
        }
    }

    @Test
    void sameValueStoresEqualConstructedPairsOtherFramesAndPublicMetadataDoNotBecomeReceiptAuthority() {
        var s = state(); var e = environment();
        var equalState = new CdPlayerBody.State(s.motion(), s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle());
        var equalEnvironment = new CdPlayerBody.Environment(e.movement(), e.controls(), e.globals(), e.dynamicSlots());
        try (var f = frame(s, e); var other = frame(state(), environment())) {
            var same = call(equalState, equalEnvironment, equalState, ANIMATION, 6, 6);
            var ready = f.prepareObjectSpringboardPlayerStore(same);
            assertNotSame(s, equalState); assertNotSame(e, equalEnvironment);
            assertSame(equalState, ready.expectedState()); assertSame(equalEnvironment, ready.expectedEnvironment());
            assertSame(equalState, ready.finalState()); assertSame(equalEnvironment, ready.finalEnvironment());
            assertNotSame(ready, f.prepareObjectSpringboardPlayerStore(same));
            var alternate = new CdSpringboard.PrimaryCall(Long.MIN_VALUE, metadata.capture(), metadata.pool().state(127),
                    equalState, equalEnvironment, equalState, ANIMATION, 6, 6);
            assertSame(equalState, other.prepareObjectSpringboardPlayerStore(alternate).finalState());
            other.validatePreparedObjectSolidPublication(ready);
            other.applyPreparedObjectSolidPublication(ready); other.applyPreparedObjectSolidPublication(ready);
            assertSame(equalState, other.state()); assertSame(equalEnvironment, other.environment());
            assertSame(s, f.state()); assertSame(e, f.environment());
            // Private caller must still authenticate source/full96/site/ordinal/original pending call.
        }
    }

    @Test
    void theExistingPreparedCarrierRejectsInterveningActorAndEnvironmentOwnerChanges() {
        for (boolean changeActor : new boolean[]{true, false}) {
            try (var f = frame(state(), environment())) {
                var output = withMotion(f.state(), motion(vectors().getLast().motion()));
                var ready = f.prepareObjectSpringboardPlayerStore(call(f.state(), f.environment(), output, ANIMATION, 6, 0xAA));
                if (changeActor) f.applyObjectYVelocity(0x345); else f.applyObjectPrimaryAngle(0x65);
                var actor = f.captureActor(); var shared = f.captureShared();
                assertThrows(IllegalArgumentException.class, () -> f.validatePreparedObjectSolidPublication(ready));
                assertThrows(IllegalArgumentException.class, () -> f.applyPreparedObjectSolidPublication(ready));
                assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
            }
        }
    }

    @Test
    void nullPendingReentryEntitledAndClosedGuardsUseTheActualFrameWithoutAddingAuthority() {
        try (var f = frame(state(), environment())) {
            var output = withMotion(f.state(), motion(vectors().getLast().motion()));
            var call = call(f.state(), f.environment(), output, ANIMATION, 6, 0xAA);
            var actor = f.captureActor(); var shared = f.captureShared();
            assertThrows(NullPointerException.class, () -> f.prepareObjectSpringboardPlayerStore(null));
            f.prepareInput(8);
            assertThrows(IllegalStateException.class, () -> f.prepareObjectSpringboardPlayerStore(call));
            f.clearPendingInput(); assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
            f.serviceLevelLoad(0, () -> {
                var live = call(f.state(), f.environment(), output, ANIMATION, 6, 0xAA);
                assertThrows(IllegalStateException.class, () -> f.prepareObjectSpringboardPlayerStore(live));
            }, () -> { }, () -> { });
            f.bindArtPublication((request, state) -> fail("Matched clean map frame has no DMA"));
            var sample = f.prepareInput(0); f.serviceVBlank(sample.phase());
            var entitled = call(f.state(), f.environment(), output, ANIMATION, 6, 0xAA);
            assertThrows(IllegalStateException.class, () -> f.prepareObjectSpringboardPlayerStore(entitled));
            f.clearPendingInput();
            assertSame(output, f.prepareObjectSpringboardPlayerStore(entitled).finalState());
            f.close();
            assertThrows(IllegalStateException.class, () -> f.prepareObjectSpringboardPlayerStore(entitled));
        }
    }

    @Test
    void componentCallRejectsEveryMissingRequiredValueBeforeItCanReachTheFrame() {
        var s = state(); var e = environment(); var cut = metadata.capture(); var source = metadata.pool().state(32);
        assertThrows(NullPointerException.class, () -> new CdSpringboard.PrimaryCall(1,null,source,s,e,s,FLAGS,0x42,0));
        assertThrows(NullPointerException.class, () -> new CdSpringboard.PrimaryCall(1,cut,null,s,e,s,FLAGS,0x42,0));
        assertThrows(NullPointerException.class, () -> new CdSpringboard.PrimaryCall(1,cut,source,null,e,s,FLAGS,0x42,0));
        assertThrows(NullPointerException.class, () -> new CdSpringboard.PrimaryCall(1,cut,source,s,null,s,FLAGS,0x42,0));
        assertThrows(NullPointerException.class, () -> new CdSpringboard.PrimaryCall(1,cut,source,s,e,null,FLAGS,0x42,0));
        assertThrows(NullPointerException.class, () -> new CdSpringboard.PrimaryCall(1,cut,source,s,e,s,null,0x42,0));
    }

    @Test
    void theOnlyNewPublicPortUsesOneComponentCallAndTheExistingFourValueCarrier() throws Exception {
        var method = CdPlayerFrameRuntime.class.getDeclaredMethod("prepareObjectSpringboardPlayerStore", CdSpringboard.PrimaryCall.class);
        assertTrue(Modifier.isPublic(method.getModifiers())); assertFalse(Modifier.isStatic(method.getModifiers()));
        assertEquals(CdPlayerFrameRuntime.ObjectSolidPublication.class, method.getReturnType());
        assertEquals(1, Arrays.stream(CdPlayerFrameRuntime.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("prepareObjectSpringboardPlayerStore")).count());
        assertTrue(Arrays.stream(CdPlayerFrameRuntime.ObjectSolidPublication.class.getDeclaredConstructors())
                .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertEquals(3, CdPlayerFrameRuntime.ActorState.class.getRecordComponents().length);
        assertEquals(5, CdPlayerFrameRuntime.SharedState.class.getRecordComponents().length);
        assertEquals(8, CdPlayerBody.State.class.getRecordComponents().length);
        assertEquals(4, CdPlayerBody.Environment.class.getRecordComponents().length);
        assertArrayEquals(new CdSpringboard.PrimaryField[]{CENTRE_Y_LONG,CENTRE_Y_WORD,Y_VELOCITY,
                FLAGS,PLAYER_JUMP,STICK,Y_RADIUS,X_RADIUS,ANIMATION}, CdSpringboard.PrimaryField.values());
        int[][] sites = {{0x0C,4},{0x0C,2},{0x12,2},{0x22,1},{0x3C,1},{0x38,1},{0x16,1},{0x17,1},{0x1C,1}};
        for (int i=0;i<sites.length;i++) {
            assertEquals(sites[i][0], CdSpringboard.PrimaryField.values()[i].byteOffset());
            assertEquals(sites[i][1], CdSpringboard.PrimaryField.values()[i].byteWidth());
        }
    }

    private static CdSpringboard.PrimaryCall call(CdPlayerBody.State state, CdPlayerBody.Environment environment,
                                                  CdPlayerBody.State output, CdSpringboard.PrimaryField field, int old, int value) {
        // Descriptive detached metadata deliberately does not prove a live producer call.
        return new CdSpringboard.PrimaryCall(37, metadata.capture(), metadata.pool().state(32), state, environment, output, field, old, value);
    }
    private static void assertUnwrittenState(CdPlayerBody.State before, CdPlayerBody.State after) {
        assertSame(before.animation(), after.animation()); assertSame(before.header(), after.header());
        assertSame(before.timers(), after.timers()); assertSame(before.history(), after.history());
        assertEquals(before.rotationDistance(), after.rotationDistance()); assertSame(before.objectLinks(), after.objectLinks());
        assertEquals(before.rotationAngle(), after.rotationAngle());
    }
    private static CdPlayerMotionState motion(int[] a) {
        assertEquals(20, a.length);
        return new CdPlayerMotionState(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9],a[10],a[11],a[12],a[13],a[14],a[15],a[16],a[17],a[18],a[19]);
    }
    private static CdPlayerBody.State withMotion(CdPlayerBody.State s, CdPlayerMotionState m) {
        return new CdPlayerBody.State(m,s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());
    }
    private static CdPlayerFrameRuntime frame(CdPlayerBody.State s, CdPlayerBody.Environment e) {
        byte[] cpu = new byte[0x300], resident = new byte[0x300];
        for (int i=0;i<cpu.length;i++) { cpu[i]=(byte)(i*7+3); resident[i]=(byte)(i*11+5); }
        return CdPlayerFrameRuntime.fromAct2PastEntry(terrain,player,s,e,
                new CdPlayerArtRuntime.State(cpu,resident,3,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFEDCBA98L),0x1234));
    }
    private static CdPlayerBody.State state() {
        byte[] history = new byte[0x100]; for (int i=0;i<history.length;i++) history[i]=(byte)(i*13+7);
        return new CdPlayerBody.State(motion(new int[]{0x81234567,0xFEDC89AB,-0x123,0x234,-0x345,2,0x56,0x42,0xA3,7,11,6,7,9,0xEF,-0x1234,0x44,0xD1,0x77,0x88}),
                new CdPlayerBody.AnimationCursor(5,6,3),new CdPlayerBody.Header(1,0xA1234567,0x2345,3,0x21),
                new CdPlayerBody.Timers(0x78,0x1234,0x2345,0x3456),new CdPlayerBody.History(CdAssetBytes.copyOf(history),0x42),
                0x67,new CdPlayerBody.ObjectLinks(0x20,0x7F),0xE7);
    }
    private static CdPlayerBody.Environment environment() {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x123,0x23,0x34),
                new CdPlayerMovement.Bounds(0x12,0x2345,0x3456,0x78),new CdPlayerMovement.Gates(1,0,0x12,0x34,0x56,0x78,0xFF),
                new CdPlayerMovement.Globals(0x12,0x2345,0x34,0x56,0x789A,0x4567,0x78,0x89,0x9A,0xAB)),
                new CdPlayerBody.Controls(0x24,0x18,8,0x10,0x81),new CdPlayerBody.Globals(0x1234,0x2345,0x12,0x34,0x4567,0xABCD,0x56,0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }

    private record Change(String path, Object value) { }
    /** Test-only exhaustive one-leaf substitutions; never accesses Frame fields or private constructors. */
    private static List<Change> oneLeafChanges(Object value, String path) throws Exception {
        var result = new ArrayList<Change>();
        if (value instanceof Integer n) result.add(new Change(path,n ^ 1));
        else if (value instanceof CdAssetBytes bytes) {
            for (int i=0;i<bytes.length();i++) { byte[] copy=bytes.copy(); copy[i]^=1; result.add(new Change(path+"["+i+"]",CdAssetBytes.copyOf(copy))); }
        } else if (value instanceof CdPlayerBody.DynamicSlots slots) {
            result.add(new Change(path, slots == CdPlayerBody.DynamicSlots.UNIMPLEMENTED
                    ? CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96 : CdPlayerBody.DynamicSlots.UNIMPLEMENTED));
        } else {
            assertTrue(value.getClass().isRecord(), path);
            var components = value.getClass().getRecordComponents();
            for (int i=0;i<components.length;i++) {
                var current=components[i].getAccessor().invoke(value);
                for (var change : oneLeafChanges(current,path+"."+components[i].getName()))
                    result.add(new Change(change.path(),replace(value,i,change.value())));
            }
        }
        return result;
    }
    private static Object replace(Object record, int index, Object value) throws Exception {
        var components=record.getClass().getRecordComponents();
        var types=new Class<?>[components.length]; var values=new Object[components.length];
        for (int i=0;i<components.length;i++) { types[i]=components[i].getType(); values[i]=components[i].getAccessor().invoke(record); }
        values[index]=value;
        return record.getClass().getDeclaredConstructor(types).newInstance(values);
    }
}
