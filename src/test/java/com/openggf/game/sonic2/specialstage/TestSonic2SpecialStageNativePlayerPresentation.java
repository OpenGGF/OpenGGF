package com.openggf.game.sonic2.specialstage;

import com.openggf.data.Rom;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independent short checks for fixed native owners, without running a stage route. */
class TestSonic2SpecialStageNativePlayerPresentation {
    @Test
    void normalPlayersUseFixedSlotsAndBodyCoordinatesWithoutAdvancingGameplay() throws Exception {
        Fixture f = fixture(true, true, false);
        set(f.sonic(), "xPos", 101);
        set(f.sonic(), "yPos", 145);
        set(f.sonic(), "mappingFrame", 7);
        set(f.sonic(), "renderXFlip", true);
        set(f.sonic(), "ssDplcTimer", 2);
        set(f.tails(), "tailsTailsFrameDuration", 2);
        set(f.tails(), "tailsTailsMappingFrame", 12);
        Sonic2SpecialStageSnapshot.PlayerSnapshot before = f.sonic().captureRewindSnapshot();

        f.update();

        assertEquals(List.of(0, 1, 5, 6, 7), f.presentation().capture().stream()
                .map(s -> s.identity().slot()).toList());
        assertState(f.state(0), 101, 145, 7, true, false, true);
        assertState(f.state(5), 101, 169, 0, false, false, true);
        assertEquals(4, f.state(5).identity().priorityBucket());
        assertEquals(2, f.state(1).identity().priorityBucket());
        assertEquals(1, f.state(7).identity().priorityBucket());
        assertEquals(Sonic2SpecialStageSlotPool.ArtRole.SONIC, f.state(0).artRole());
        assertEquals(Sonic2SpecialStageSlotPool.ArtRole.SHADOW, f.state(5).artRole());
        assertEquals(12, f.state(7).mappingFrame());
        Sonic2SpecialStageSnapshot.PlayerSnapshot after = f.sonic().captureRewindSnapshot();
        assertEquals(before.angle(), after.angle());
        assertEquals(before.ssXPos(), after.ssXPos());
        assertEquals(before.ssYPos(), after.ssYPos());
        assertEquals(before.ssZPos(), after.ssZPos());
        assertEquals(before.ssDplcTimer(), after.ssDplcTimer());
        assertArrayEquals(before.ctrlRecordBuf(), after.ctrlRecordBuf());
    }

    @Test
    void tailsAloneStillUsesSidekickShadowAndTailSlots() {
        Fixture f = fixture(false, true, true);
        f.update();
        assertEquals(List.of(1, 6, 7), f.presentation().capture().stream()
                .map(s -> s.identity().slot()).toList());
        assertEquals(0xB040, f.state(1).identity().address());
        assertEquals(3, f.state(1).identity().priorityBucket());
        assertEquals(2, f.state(7).identity().priorityBucket());
        assertEquals(f.state(1).identity().handle(), f.state(6).identity().parent());
        assertEquals(Sonic2SpecialStageSlotPool.ArtRole.TAILS, f.state(1).artRole());
        f.pool().clear();
        f.update();
        assertEquals(List.of(), f.presentation().capture());
    }

    @Test
    void normalShadowUsesEveryNativeOctantIncludingNegativeSideFramesAndReflection() throws Exception {
        Fixture f = fixture(true, false, false);
        set(f.sonic(), "xPos", 100);
        set(f.sonic(), "yPos", 70);
        // angle, resulting X/Y/frame/flip. Values follow word_3417A and the
        // separate negative-angle reflection around offsetY54.
        int[][] expected = {
                {0x00, 120, 70, 2, 1}, {0x20, 120, 90, 1, 1},
                {0x40, 100, 94, 0, 0}, {0x60, 80, 90, 1, 0},
                {0x80, 80, 38, 5, 0}, {0xA0, 80, 58, 7, 0},
                {0xC0, 100, 62, 9, 0}, {0xE0, 120, 58, 7, 1},
                {0xF0, 120, 38, 5, 1}
        };
        for (int[] row : expected) {
            set(f.sonic(), "angle", row[0]);
            f.update();
            assertState(f.state(5), row[1], row[2], row[3], row[4] != 0, false, true);
        }
    }

    @Test
    void jumpShadowUsesNativeRatioLookupInsteadOfBodyAngleOrPosition() throws Exception {
        Fixture f = fixture(true, false, false);
        set(f.sonic(), "routine", Sonic2SpecialStagePlayer.RoutineState.JUMPING);
        set(f.sonic(), "ssZPos", 110);
        set(f.sonic(), "xPos", 999);
        set(f.sonic(), "yPos", -999);
        set(f.sonic(), "angle", 0x99);
        // abs(55)*128/110=64; byte_33D32[64]=$55. Quadrant signs
        // select $2B/$55/$D5/$AB; signed shifts preserve their asymmetry.
        int[][] expected = {
                {55, 1, 191, 169, 1, 1}, {-55, 1, 64, 169, 1, 0},
                {55, -1, 191, 170, 7, 1}, {-55, -1, 64, 170, 7, 0}
        };
        for (int[] row : expected) {
            set(f.sonic(), "ssXPos", row[0]);
            set(f.sonic(), "ssYPos", row[1]);
            f.update();
            assertState(f.state(5), row[2], row[3], row[4], row[5] != 0, false, true);
            assertEquals(999, f.state(0).x());
            assertEquals(-999, f.state(0).y());
            assertEquals(0x99, f.sonic().getAngle());
        }
    }

    @Test
    void retailJumpShadowKeepsIntermediateSignedWordOverflowAtTailsRadius128() throws Exception {
        Fixture f = fixture(false, true, false);
        set(f.tails(), "routine", Sonic2SpecialStagePlayer.RoutineState.JUMPING);
        set(f.tails(), "ssZPos", 128);
        set(f.tails(), "ssXPos", 128);
        set(f.tails(), "ssYPos", 1);
        f.update();
        // Lookup endpoint128 -> angle0. MULS256*128 produces $8000;
        // the next MULS.W treats it as -32768, giving -102 before offset.
        assertState(f.state(6), 46, 54, 2, true, false, true);
        set(f.tails(), "ssXPos", -128);
        f.update();
        assertState(f.state(6), 6, 54, 5, false, false, true);
    }

    @Test
    void jumpPastEllipseReadsAdjacentRomInstructionBytesAndKeepsBindingAcrossRewind() throws Exception {
        Fixture f = fixture(false, true, false);
        Rom rom = mock(Rom.class);
        // The shipped REV01 byte immediately after byte_33D32's padding is
        // $30, the start of SSPlayer_DoLevelCollision's MOVE.W instruction.
        when(rom.readByte(0x33DB4L)).thenReturn((byte) 0x30);
        f.presentation().bindRom(rom);
        set(f.tails(), "routine", Sonic2SpecialStagePlayer.RoutineState.JUMPING);
        set(f.tails(), "ssZPos", 128);
        set(f.tails(), "ssXPos", 130);
        set(f.tails(), "ssYPos", 1);
        f.update();
        assertState(f.state(6), 69, 192, 1, false, false, true);
        verify(rom).readByte(0x33DB4L);
        List<Sonic2SpecialStageSlotPool.RenderState> snapshot = f.presentation().capture();
        f.presentation().clear();
        f.presentation().restore(snapshot);
        f.update();
        assertEquals(snapshot, f.presentation().capture());
        verify(rom, times(2)).readByte(0x33DB4L);
    }

    @Test
    void jumpLookupUsesSignedPcWordDisplacementAndRequiresRomWhenOutsideTable() throws Exception {
        Fixture f = fixture(true, false, false);
        Rom rom = mock(Rom.class);
        when(rom.readByte(0x2BD32L)).thenReturn((byte) 0x33);
        set(f.sonic(), "routine", Sonic2SpecialStagePlayer.RoutineState.JUMPING);
        set(f.sonic(), "ssZPos", 1);
        set(f.sonic(), "ssXPos", 256);
        set(f.sonic(), "ssYPos", 1);
        IllegalStateException missing = assertThrows(IllegalStateException.class, f::update);
        assertTrue(missing.getMessage().contains("$2bd32"));
        f.presentation().bindRom(rom);
        f.update();
        // Quotient32768 indexes PC using signed $8000, not unsigned32768.
        verify(rom).readByte(0x2BD32L);
        assertTrue(f.state(5).visible());
    }

    @Test
    void tailsDiagonalCorrectionUsesStatusAfterShadowReflection() throws Exception {
        Fixture f = fixture(false, true, false);
        set(f.tails(), "xPos", 100);
        set(f.tails(), "yPos", 70);
        set(f.tails(), "angle", 0xA0);
        set(f.tails(), "anim", 1);
        set(f.tails(), "renderXFlip", true);
        set(f.tails(), "renderYFlip", true);
        f.update();
        assertState(f.state(6), 84, 54, 7, false, false, true);
        set(f.tails(), "statusXFlip", true);
        f.update();
        assertState(f.state(6), 76, 54, 7, false, false, true);
        set(f.tails(), "statusYFlip", true);
        f.update();
        assertState(f.state(6), 80, 58, 7, false, false, true);
        set(f.tails(), "anim", 0);
        set(f.tails(), "statusYFlip", false);
        f.update();
        assertState(f.state(6), 80, 58, 7, false, false, true);
    }

    @Test
    void bodyAndTailBlinkTogetherWhileFixedShadowsContinueDisplaying() throws Exception {
        Fixture f = fixture(true, true, false);
        set(f.sonic(), "ssDplcTimer", 3);
        set(f.tails(), "ssDplcTimer", 3);
        f.update();
        assertFalse(f.state(0).visible());
        assertFalse(f.state(1).visible());
        assertFalse(f.state(7).visible());
        assertTrue(f.state(5).visible());
        assertTrue(f.state(6).visible());
        assertEquals(3, f.tails().captureRewindSnapshot().ssDplcTimer());
        set(f.tails(), "ssDplcTimer", 2);
        f.update();
        assertTrue(f.state(1).visible());
        assertTrue(f.state(7).visible());
        set(f.tails(), "anim", 3);
        f.update();
        assertTrue(f.state(1).visible());
        assertFalse(f.state(7).visible());
    }

    @Test
    void tailUsesCopiedBodyFlipsExceptOnItsNativeAnimationAdvance() throws Exception {
        Fixture f = fixture(false, true, false);
        set(f.tails(), "renderXFlip", true);
        set(f.tails(), "renderYFlip", false);
        set(f.tails(), "statusXFlip", false);
        set(f.tails(), "statusYFlip", true);
        set(f.tails(), "tailsTailsFrameDuration", 2);
        f.update();
        assertTrue(f.state(7).xFlip());
        assertFalse(f.state(7).yFlip());
        set(f.tails(), "tailsTailsFrameDuration", 3);
        f.update();
        assertFalse(f.state(7).xFlip());
        assertTrue(f.state(7).yFlip());
    }

    @Test
    void immutableSnapshotAndPlayerRewindReplayProduceTheSameNativePresentation() {
        Fixture f = fixture(true, true, false);
        f.update();
        List<Sonic2SpecialStageSlotPool.RenderState> snapshot = f.presentation().capture();
        Sonic2SpecialStageSnapshot.PlayerSnapshot sonic = f.sonic().captureRewindSnapshot();
        Sonic2SpecialStageSnapshot.PlayerSnapshot tails = f.tails().captureRewindSnapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());

        List<List<Sonic2SpecialStageSlotPool.RenderState>> forward = new ArrayList<>();
        for (int frame = 0; frame < 6; frame++) {
            f.sonic().update(0, 0);
            f.tails().update(0, 0);
            f.update();
            forward.add(f.presentation().capture());
        }
        f.presentation().clear();
        assertEquals(List.of(), f.presentation().capture());
        f.presentation().restore(snapshot);
        assertEquals(snapshot, f.presentation().capture());
        assertEquals(128, snapshot.getFirst().x());
        f.sonic().restoreRewindSnapshot(sonic);
        f.tails().restoreRewindSnapshot(tails);
        for (int frame = 0; frame < 6; frame++) {
            f.sonic().update(0, 0);
            f.tails().update(0, 0);
            f.update();
            assertEquals(forward.get(frame), f.presentation().capture());
        }
    }

    private static Fixture fixture(boolean sonicPresent, boolean tailsPresent, boolean tailsMain) {
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();
        Sonic2SpecialStagePlayer sonic = sonicPresent
                ? player(Sonic2SpecialStagePlayer.PlayerType.SONIC, true, manager) : null;
        Sonic2SpecialStagePlayer tails = tailsPresent
                ? player(Sonic2SpecialStagePlayer.PlayerType.TAILS, tailsMain, manager) : null;
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        if (sonic != null) {
            Sonic2SpecialStageSlotPool.Handle parent = pool.claimFixed(0, SONIC, 0x09,
                    sonic.getPriority(), 0, null);
            pool.claimFixed(5, SONIC_SHADOW, 0x63, 4, 0, parent);
        }
        if (tails != null) {
            Sonic2SpecialStageSlotPool.Handle parent = pool.claimFixed(1, TAILS, 0x10,
                    tails.getPriority(), 0, null);
            pool.claimFixed(6, TAILS_SHADOW, 0x63, 4, 0, parent);
            pool.claimFixed(7, TAILS_TAIL, 0x88, tails.getPriority() - 1, 0, parent);
        }
        return new Fixture(pool, sonic, tails, new Sonic2SpecialStageNativePlayerPresentation());
    }

    private static Sonic2SpecialStagePlayer player(Sonic2SpecialStagePlayer.PlayerType type,
                                                   boolean main, Sonic2SpecialStageManager manager) {
        Sonic2SpecialStagePlayer player = new Sonic2SpecialStagePlayer(type, main, manager);
        player.initializeScalarStateFromRomObjectRoutine();
        player.setSpawned(true);
        return player;
    }

    private record Fixture(Sonic2SpecialStageSlotPool pool, Sonic2SpecialStagePlayer sonic,
                           Sonic2SpecialStagePlayer tails,
                           Sonic2SpecialStageNativePlayerPresentation presentation) {
        void update() {
            presentation.update(pool, sonic, tails, 128, 54);
        }

        Sonic2SpecialStageSlotPool.RenderState state(int slot) {
            return presentation.capture().stream().filter(s -> s.identity().slot() == slot)
                    .findFirst().orElseThrow();
        }
    }

    private static void assertState(Sonic2SpecialStageSlotPool.RenderState state,
                                    int x, int y, int frame, boolean xFlip,
                                    boolean yFlip, boolean visible) {
        assertAll(() -> assertEquals(x, state.x()), () -> assertEquals(y, state.y()),
                () -> assertEquals(frame, state.mappingFrame()), () -> assertEquals(xFlip, state.xFlip()),
                () -> assertEquals(yFlip, state.yFlip()), () -> assertEquals(visible, state.visible()));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
