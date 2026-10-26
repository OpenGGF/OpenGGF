package com.openggf.game.sonic1.specialstage;

import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S1_SPECIAL_COLLISION, 5121M/7988S: controlled contact regressions, not a
 * reconstruction of that publication's unspecified maze shortcuts.
 * Native oracle: S1 pin 28872a46, _incObj/09 Sonic in Special Stage.asm,
 * SonicSS_FindWall/CheckType, SonicSS_Fall, SonicSS_Jump and SonicSS_ChkBumper.
 * Setup changes only the initial layout and player state. Every interaction,
 * contact selection, jump and movement assertion goes through public update().
 */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1SpecialStageContacts {
    private Sonic1SpecialStageManager manager;
    private byte[] layout;

    @BeforeEach
    void setup() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        manager = new Sonic1SpecialStageManager();
        manager.initialize(0);
        manager.advanceToEntryPresentation();
        manager.update(); // Run Obj09_Main, so it cannot overwrite the test's entry state.
        layout = (byte[]) field("layout").get(manager);
        Arrays.fill(layout, (byte) 0);
        set("ssAngle", 0);
        set("ssRotate", 0);
        set("sonicPosX", 460L << 16); // (20*24 - $14)
        set("sonicPosY", 412L << 16); // (20*24 - $44)
        set("sonicVelX", 0);
        set("sonicVelY", 0);
        set("sonicInertia", 0);
        set("sonicAirborne", true);
    }

    @AfterEach
    void cleanup() {
        if (manager != null) manager.reset();
        GraphicsManager.getInstance().cleanup();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @CsvSource({"1,37,-1267,-1267,true", "37,1,0,0,false",
            "37,40,-1792,0,true", "37,58,-1792,0,true"})
    void lastSolidOfFourCellsOwnsBumperResponse(
            int topRight, int bottomRight, int vx, int vy, boolean airborne) {
        layout[20 * 128 + 21] = (byte) topRight;
        layout[21 * 128 + 21] = (byte) bottomRight;

        manager.update();

        var state = manager.captureComparisonState();
        // CalcAngle(24,24)=$20; native sine/cosine=$B5, muls #-$700/asr #8=-1267.
        // A cardinal (24,0) bumper gives -$700. A later wall suppresses the bounce.
        assertEquals(vx, state.sonicVelX());
        assertEquals(vy, state.sonicVelY());
        assertEquals(airborne, state.sonicAirborne());
        assertEquals((460L << 16) + ((long) vx << 8), state.sonicPosX());
        assertEquals((412L << 16) + ((long) vy << 8), state.sonicPosY());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x10, 0x20, 0x40})
    void sideContactWithoutFloorAllowsFreshJumpOnFollowingUpdate(int button) throws Exception {
        prepareSideContact(1);
        manager.update();
        var contact = manager.captureComparisonState();
        assertFalse(contact.sonicAirborne(), "SonicSS_Fall clears in-air when the X probe alone hits");
        assertEquals(0, contact.sonicVelX());
        assertEquals(0x2A, contact.sonicVelY(), "No floor exists: Y movement must remain free");
        assertEquals(483L << 16, contact.sonicPosX());

        manager.handleInput(button, button);
        manager.update();
        var jump = manager.captureComparisonState();
        assertTrue(jump.sonicAirborne());
        assertEquals(0, jump.sonicVelX());
        assertEquals(-0x656, jump.sonicVelY(), "-$680 impulse then $2A gravity");
        assertEquals(contact.sonicPosY() + ((long) -0x656 << 8), jump.sonicPosY());
    }

    @Test
    void airborneContactFramePressIsNotBufferedAndHeldJumpDoesNotRetrigger() throws Exception {
        prepareSideContact(1);
        manager.handleInput(0x40, 0x40);
        manager.update();
        assertFalse(manager.captureComparisonState().sonicAirborne());
        assertEquals(0x2A, manager.captureComparisonState().sonicVelY());
        // No new handleInput call: held remains set but the consumed edge must be gone.
        manager.update();
        assertEquals(0x54, manager.captureComparisonState().sonicVelY());
        assertTrue(manager.captureComparisonState().sonicAirborne(), "Without another collision the player falls");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 0x28, 0x3A})
    void emptyAndNonSolidCellsCannotCreateTheSideContactJumpWindow(int block) throws Exception {
        prepareSideContact(block);
        manager.update();
        var before = manager.captureComparisonState();
        assertTrue(before.sonicAirborne());
        assertEquals(0x100, before.sonicVelX());
        manager.handleInput(0x40, 0x40);
        manager.update();
        assertEquals(0x54, manager.captureComparisonState().sonicVelY(),
                "A jump edge while airborne cannot replace falling velocity");
    }

    private void prepareSideContact(int block) throws Exception {
        set("sonicPosX", 483L << 16); // One pixel before the X probe crosses to columns 21/22.
        set("sonicVelX", 0x100);
        layout[20 * 128 + 22] = (byte) block;
    }

    private void set(String name, Object value) throws Exception {
        field(name).set(manager, value);
    }

    private static Field field(String name) throws Exception {
        Field field = Sonic1SpecialStageManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
