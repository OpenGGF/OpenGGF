package com.openggf.game.sonic2.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/**
 * Independent REV01 curve-byte oracles and real player/manager dispatch, with no
 * trace rows or rendering. SSSetGeometryOffsets (s2.asm:10076-10099) preserves
 * its previous two RAM words until the drawing/player-timer equality gate.
 */
class TestSonic2SpecialStageGeometryOffsets {
    private Rom rom;
    private Sonic2SpecialStageDataLoader loader;
    private Sonic2SpecialStageManager manager;
    private Sonic2TrackAnimator animator;

    @BeforeEach
    void originalRomAndOrdinaryTeam() throws Exception {
        var file = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(file != null, "S2 World REV01 ROM required (sonic2.rom.path)");
        rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        TestEnvironment.configureRomFixture(rom);
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        loader = new Sonic2SpecialStageDataLoader(rom);
        animator = new Sonic2TrackAnimator(loader);
        animator.initialize(0);
        animator.setSpeedFactor(12);
        animator.tickVintTimer();
        manager = new Sonic2SpecialStageManager();
        set(manager, "dataLoader", loader);
        set(manager, "trackAnimator", animator);
        manager.setupPlayersForTest();
        var pool = (Sonic2SpecialStageSlotPool) get(manager, "nativeSlotPool");
        set(manager, "objectManager", new Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager(loader, pool));
        var intro = new Sonic2SpecialStageIntro();
        // setupIntro binds this exact pool before initialization. This short
        // fixture skips the waits, but constructs the native fixed UI owners
        // and their children before declaring an initialized gameplay graph.
        intro.attachNativeSlotPool(pool);
        int requirement = loader.getRingRequirement(0, 0, true);
        intro.initialize(0, requirement);
        intro.initializeNativeFixedOwners(true, false);
        intro.initializeNativeFixedOwnerChildren();
        set(intro, "currentPhase", Sonic2SpecialStageIntro.Phase.GAMEPLAY);
        set(intro, "specialStageStarted", true);
        set(manager, "intro", intro);
        set(manager, "currentRingRequirement", requirement);
        set(manager, "nativeRingRequirement", requirement);
        set(manager, "initialized", true);
        set(manager, "stagePalettesUploaded", true);
        set(manager, "liveLagSimulationEnabled", false);
        manager.setFineDiagnosticsOverrideForTesting(false);
    }

    @AfterEach
    void resetServices() throws Exception {
        TestEnvironment.resetAll();
        if (rom != null) rom.close();
    }

    @ParameterizedTest
    @CsvSource({"0,false,147,54", "0,true,109,54", "4,false,137,44", "4,true,119,44",
            "9,false,128,12", "16,false,137,72", "17,false,128,54", "37,false,128,40",
            "48,true,117,54", "55,false,131,54", "55,true,125,54"})
    void retailCurveBytesChooseSignedOffsetsAtTheNativeGate(
            int mapping, boolean flipped, int x, int y) throws Exception {
        display(mapping, flipped);
        set(manager, "drawingIndex", 4);
        manager.updateGeometryOffsetsAfterTrackDraw();
        assertOffsets(x, y);
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 6})
    void unequalGateRetainsLastWriterAcrossFrameAndOrientationChanges(int speed) throws Exception {
        animator.setSpeedFactor(speed);
        animator.tickVintTimer();
        int gate = animator.getPlayerAnimFrameTimer();
        assertEquals(speed == 12 ? 4 : 9, gate);
        display(4, false);
        set(manager, "drawingIndex", gate);
        manager.updateGeometryOffsetsAfterTrackDraw();
        assertOffsets(137, 44);
        display(0, true);
        set(manager, "drawingIndex", gate - 1);
        manager.updateGeometryOffsetsAfterTrackDraw();
        assertOffsets(137, 44);
        set(manager, "drawingIndex", gate);
        manager.updateGeometryOffsetsAfterTrackDraw();
        assertOffsets(109, 54);
    }

    @Test
    void suppliedRomBytesOwnTheTableAndByteNegationWrapsBeforeSignExtension() throws Exception {
        Rom patched = spy(Rom.fromReader(RomByteReader.fromBytes(new byte[0]), "geometry fixture"));
        byte[] bytes = new byte[112];
        bytes[8] = (byte) 0x80;
        bytes[9] = (byte) 0xD6;
        doReturn(bytes).when(patched).readBytes(0x768AL, 112);
        set(manager, "dataLoader", new Sonic2SpecialStageDataLoader(patched));
        display(4, true);
        set(manager, "drawingIndex", 4);
        manager.updateGeometryOffsetsAfterTrackDraw();
        assertOffsets(0, 12); // NEG.B $80 stays $80, EXT.W gives -128.
        verify(patched).readBytes(0x768AL, 112);
        Rom unreadable = spy(Rom.fromReader(RomByteReader.fromBytes(new byte[0]), "unreadable geometry fixture"));
        doThrow(new IOException("unavailable ROM bytes")).when(unreadable).readBytes(0x768AL, 112);
        set(manager, "dataLoader", new Sonic2SpecialStageDataLoader(unreadable));
        assertThrows(UncheckedIOException.class, manager::updateGeometryOffsetsAfterTrackDraw);
    }

    @Test
    void displayedMappingIsLatchedBeforeTheCursorAndSegmentAdvance() throws Exception {
        layout((byte) 4, (byte) 3);
        assertEquals(0, animator.getNativeMappingFrame(), "cleared RAM precedes the first draw");
        animator.drawTrackFrame(0);
        assertEquals(0x11, animator.getNativeMappingFrame());
        assertEquals(1, animator.getCurrentFrameInSegment());
        assertEquals(0x12, animator.getCurrentTrackFrameIndex(), "legacy cursor view stays unchanged");
        for (int draw = 1; draw < 11; draw++) animator.drawTrackFrame(0);
        assertEquals(0x37, animator.getNativeMappingFrame());
        assertEquals(1, animator.getCurrentSegmentIndex());
        assertEquals(0, animator.getCurrentFrameInSegment());
        assertEquals(0x11, animator.getCurrentTrackFrameIndex());
        animator.drawTrackFrame(4);
        assertEquals(0x37, animator.getNativeMappingFrame(), "nonzero slices cannot select new art");
    }

    @Test
    void dropOrientationWaitsForTheDisplayedTriggerRatherThanTheNextCursor() throws Exception {
        layout((byte) 0x81, (byte) 3);
        do { animator.drawTrackFrame(0); } while (animator.getNativeMappingFrame() != 0x19);
        assertEquals(0x1A, animator.getCurrentTrackFrameIndex());
        assertFalse(animator.isNativeOrientationFlipped(), "displayed Drop5 has not run Drop6's branch");
        set(manager, "drawingIndex", 4);
        manager.updateGeometryOffsetsAfterTrackDraw();
        assertOffsets(139, 66); // $19 = $0B,$0C, still unflipped.
        animator.drawTrackFrame(0);
        assertEquals(0x1A, animator.getNativeMappingFrame());
        assertTrue(animator.isNativeOrientationFlipped());
        animator.drawTrackFrame(9);
        assertTrue(animator.isNativeOrientationFlipped(), "orientation also runs on non-drawing slices");
    }

    @Test
    void deferredObjectTurnReadsItsPreparedOffsetsBeforeTheCurrentGateWritesTheNextPair() throws Exception {
        display(4, false);
        set(manager, "drawingIndex", 3);
        set(manager, "recurringMainPassPending", true);
        manager.update(); // VInt advances to4; previous pass must still consume128/54.
        assertOffsets(137, 44);
        assertEquals(128, manager.getSonicPlayer().getXPos());
        assertEquals(164, manager.getSonicPlayer().getYPos());
        assertEquals(182, manager.getTailsPlayer().getYPos());
        manager.update(); // The next pending turn consumes the prepared curve pair.
        assertEquals(137, manager.getSonicPlayer().getXPos());
        assertEquals(154, manager.getSonicPlayer().getYPos());
        assertEquals(172, manager.getTailsPlayer().getYPos());
    }

    @Test
    void playerBodiesAndFixedShadowsShareTheOwnerWhileDynamicShadowsKeepPerspectiveBases() throws Exception {
        display(4, false);
        set(manager, "drawingIndex", 4);
        manager.updateGeometryOffsetsAfterTrackDraw();
        for (var player : manager.getPlayers()) player.update(0, 0);
        invoke(manager, "executeActiveSpecialStageObjects");
        var states = manager.getNativeRenderStates();
        assertPosition(states, 0, 137, 154);
        assertPosition(states, 1, 137, 172);
        assertPosition(states, 5, 137, 178);
        assertPosition(states, 6, 137, 196);
        assertPosition(states, 7, 137, 172);

        set(manager.getSonicPlayer(), "routine", Sonic2SpecialStagePlayer.RoutineState.JUMPING);
        set(manager.getSonicPlayer(), "ssXPos", 0);
        set(manager.getSonicPlayer(), "ssYPos", 10);
        set(manager.getSonicPlayer(), "xPos", 999);
        set(manager.getSonicPlayer(), "yPos", -999);
        invoke(manager, "executeActiveSpecialStageObjects");
        assertPosition(manager.getNativeRenderStates(), 5, 137, 178);

        var perspective = new Sonic2PerspectiveData();
        perspective.load(loader);
        var pool = new Sonic2SpecialStageSlotPool();
        var ring = new Sonic2SpecialStageRing();
        ring.initialize(10, 0x40);
        ring.attachNativeSlot(pool, pool.allocate(Sonic2SpecialStageSlotPool.Kind.RING, 0x60, 3, 0, null));
        ring.initializeNativeOwner();
        ring.updateScreenPosition(perspective, 0x11, false);
        assertTrue(ring.isNativeShadowVisible());
        int x = ring.getNativeShadowScreenX(), y = ring.getNativeShadowScreenY();
        display(0, true);
        manager.updateGeometryOffsetsAfterTrackDraw();
        assertOffsets(109, 54);
        ring.updateScreenPosition(perspective, 0x11, false);
        assertEquals(x, ring.getNativeShadowScreenX());
        assertEquals(y, ring.getNativeShadowScreenY());
    }

    @Test
    void startupAndFadeOmitTheGeometryServiceAndFreshEntryResetsTheLastPair() throws Exception {
        display(0, false);
        set(manager, "playerBootstrapPhase", Sonic2SpecialStageManager.PlayerBootstrapPhase.WAIT_FIRST_DRAWING_WRAP);
        set(manager, "drawingIndex", 3);
        set(manager.getIntro(), "currentPhase", Sonic2SpecialStageIntro.Phase.ROM_STARTUP);
        manager.update();
        assertOffsets(128, 54);
        set(manager.getIntro(), "currentPhase", Sonic2SpecialStageIntro.Phase.FADE_FROM_WHITE);
        manager.update();
        assertOffsets(128, 54);
        set(manager, "drawingIndex", 4);
        manager.updateGeometryOffsetsAfterTrackDraw();
        assertOffsets(147, 54);
        manager.prepareForInitialization();
        assertOffsets(128, 54);
    }

    @Test
    void registryRestoresLastWriterAndDisplayedTrackStateThenReplaysTheCurve() throws Exception {
        layout((byte) 0, (byte) 3);
        do { animator.drawTrackFrame(0); } while (animator.getNativeMappingFrame() != 0);
        set(manager, "drawingIndex", 4);
        manager.updateGeometryOffsetsAfterTrackDraw();
        do { animator.drawTrackFrame(0); } while (animator.getNativeMappingFrame() != 4);
        set(manager, "drawingIndex", 2);
        set(manager, "recurringMainPassPending", true);
        assertOffsets(147, 54); // New frame is prepared; its equality gate has not written yet.
        var registry = new RewindRegistry();
        registry.register(new Sonic2SpecialStageRewindAdapter(manager, () -> false, ignored -> { }));
        var start = registry.capture();
        for (int frame = 0; frame < 24; frame++) manager.update();
        var expected = registry.capture();
        var expectedNativeOutputs = manager.getNativeRenderStates();
        assertNotEquals(54, manager.getGeometryOffsetY(), "the replay traverses actual rising frames");
        registry.restore(start);
        assertOffsets(147, 54);
        assertEquals(4, animator.getNativeMappingFrame());
        var restored = registry.capture();
        assertEquals(start.entries().keySet(), restored.entries().keySet());
        for (String key : start.entries().keySet()) {
            assertProviderSnapshotValues(key + ".restore", start.get(key), restored.get(key));
        }
        for (int frame = 0; frame < 24; frame++) manager.update();
        var actual = registry.capture();
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertProviderSnapshotValues(key + ".forward", expected.get(key), actual.get(key));
        }
        assertEquals(expectedNativeOutputs, manager.getNativeRenderStates(),
                "body, fixed-shadow, tail and UI publications must replay with their native owners");
    }

    private static void assertProviderSnapshotValues(String path, Object expected, Object actual) throws Exception {
        var a = assertInstanceOf(Sonic2SpecialStageProviderSnapshot.class, expected);
        var b = assertInstanceOf(Sonic2SpecialStageProviderSnapshot.class, actual);
        assertEquals(a.resultsPlcSubmitted(), b.resultsPlcSubmitted(), path + ".resultsPlcSubmitted");
        // RewindSnapshotDiff descends into records/arrays/collections, but the
        // outer manager snapshot is a value class without equals(). Compare
        // every captured instance field; no geometry, pending-pass, player,
        // native-slot, presentation or other captured state is excluded.
        List<String> differences = new ArrayList<>();
        for (Field field : Sonic2SpecialStageSnapshot.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            differences.addAll(RewindSnapshotDiff.diffKey(path + "." + field.getName(),
                    field.get(a.managerSnapshot()), field.get(b.managerSnapshot())));
        }
        assertTrue(differences.isEmpty(), () -> String.join("\n", differences));
    }

    private void display(int mapping, boolean flipped) throws Exception {
        set(animator, "nativeMappingFrame", mapping);
        set(animator, "nativeOrientationFlipped", flipped);
    }

    private void layout(byte... segments) throws Exception {
        animator.initializeWithMockLayout();
        set(animator, "stageLayout", segments);
        set(animator, "layoutLength", segments.length);
        set(animator, "currentSegmentType", segments[0] & 0x7F);
        set(animator, "currentSegmentFlipped", segments[0] < 0);
        animator.setSpeedFactor(12);
        animator.tickVintTimer();
    }

    private void assertOffsets(int x, int y) {
        assertEquals(x, manager.getGeometryOffsetX());
        assertEquals(y, manager.getGeometryOffsetY());
    }

    private static void assertPosition(List<Sonic2SpecialStageSlotPool.RenderState> states, int slot, int x, int y) {
        var state = states.stream().filter(s -> s.identity().slot() == slot).findFirst().orElseThrow();
        assertEquals(x, state.x(), "slot " + slot + " X");
        assertEquals(y, state.y(), "slot " + slot + " Y");
    }

    private static void invoke(Object owner, String name) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(owner);
    }

    private static Object get(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
