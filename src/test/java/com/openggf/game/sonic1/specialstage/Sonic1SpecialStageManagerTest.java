package com.openggf.game.sonic1.specialstage;

import com.openggf.control.InputActionMasks;
import com.openggf.graphics.WaterShaderProgram;
import com.openggf.physics.TrigLookupTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlas;
import com.openggf.level.Palette;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.openggf.game.sonic1.constants.Sonic1Constants.SS_BLOCK_SIZE_PX;
import static com.openggf.game.sonic1.constants.Sonic1Constants.SS_JUMP_FORCE;
import static com.openggf.game.sonic1.constants.Sonic1Constants.SS_LAYOUT_STRIDE;

@RequiresRom(SonicGame.SONIC_1)
public class Sonic1SpecialStageManagerTest {
    private GraphicsManager graphicsManager;
    private Sonic1SpecialStageManager manager;

    @BeforeEach
    public void setUp() {
        GraphicsManager.getInstance().resetState();
        graphicsManager = GraphicsManager.getInstance();
        graphicsManager.initHeadless();
        manager = new Sonic1SpecialStageManager();
    }

    @AfterEach
    public void tearDown() {
        if (manager != null) {
            manager.reset();
        }
        if (graphicsManager != null) {
            graphicsManager.cleanup();
        }
        GraphicsManager.getInstance().resetState();
    }

    @Test
    public void testInitializeLoadsZonePatternBases() throws Exception {
        manager.initialize(0);
        assertTrue(manager.isInitialized(), "Special stage manager should initialize");

        Field rendererField = Sonic1SpecialStageManager.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        Sonic1SpecialStageRenderer renderer = (Sonic1SpecialStageRenderer) rendererField.get(manager);
        assertNotNull(renderer, "Renderer should be created during initialization");

        Field zoneBasesField = Sonic1SpecialStageRenderer.class.getDeclaredField("zonePatternBases");
        zoneBasesField.setAccessible(true);
        int[] zoneBases = (int[]) zoneBasesField.get(renderer);
        assertNotNull(zoneBases, "Zone pattern bases should be set");
        assertTrue(zoneBases.length == 6, "Should have 6 zone pattern bases");

        int previousBase = -1;
        for (int i = 0; i < zoneBases.length; i++) {
            int base = zoneBases[i];
            assertTrue(base > 0, "Zone pattern base should be positive for zone " + (i + 1));
            assertTrue(base > previousBase, "Zone pattern bases should increase monotonically");
            previousBase = base;

            PatternAtlas.Entry entry = graphicsManager.getPatternAtlasEntry(base);
            assertNotNull(entry, "Pattern atlas entry missing for zone " + (i + 1) + " base " + base);
        }
    }

    @Test
    public void testInitialDrawRendersStageBlocks() throws Exception {
        manager.initialize(0);
        manager.draw();

        Field rendererField = Sonic1SpecialStageManager.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        Sonic1SpecialStageRenderer renderer = (Sonic1SpecialStageRenderer) rendererField.get(manager);
        assertNotNull(renderer, "Renderer should be created during initialization");

        assertTrue(renderer.getLastRenderedBlocks() > 0, "Special stage should render at least one block on first draw");
        assertTrue(renderer.getLastValidBlockCells() > 0, "Special stage should detect valid block cells on first draw");
    }

    @Test
    public void testUpdateDoesNotRunAwayFromSpawnImmediately() throws Exception {
        manager.initialize(0);
        for (int i = 0; i < 120; i++) {
            manager.update();
        }
        manager.draw();

        Field rendererField = Sonic1SpecialStageManager.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        Sonic1SpecialStageRenderer renderer = (Sonic1SpecialStageRenderer) rendererField.get(manager);
        assertNotNull(renderer, "Renderer should be created during initialization");
        assertTrue(renderer.getLastRenderedBlocks() > 0, "Special stage should still render blocks after updates");
    }

    @Test
    public void testBackdropColorUsesResolvedSpecialPalette() throws Exception {
        manager.initialize(0);
        Palette.Color backdrop = manager.getBackdropColor();
        assertNotNull(backdrop, "Backdrop color should be available after initialization");
        assertEquals(0, backdrop.r & 0xFF, "Backdrop red should match S1 special-stage palette");
        assertEquals(0, backdrop.g & 0xFF, "Backdrop green should match S1 special-stage palette");
        assertEquals(73, backdrop.b & 0xFF, "Backdrop blue should match S1 special-stage palette");
    }

    @Test
    public void testSonicAnimationFrameAdvancesDuringStage() throws Exception {
        manager.initialize(0);

        Field frameField = Sonic1SpecialStageManager.class.getDeclaredField("sonicSpriteFrame");
        frameField.setAccessible(true);

        Set<Integer> seenFrames = new HashSet<>();
        for (int i = 0; i < 180; i++) {
            manager.update();
            seenFrames.add(frameField.getInt(manager));
        }

        assertTrue(seenFrames.size() > 1, "Sonic special-stage roll animation should advance through multiple frames");
    }

    @Test
    public void testSpecialStagePaletteCycleMutatesPaletteEntries() throws Exception {
        manager.initialize(0);

        Field palettesField = Sonic1SpecialStageManager.class.getDeclaredField("ssPalettes");
        palettesField.setAccessible(true);
        Palette[] palettes = (Palette[]) palettesField.get(manager);
        assertNotNull(palettes, "Special-stage palettes should be loaded");
        assertTrue(palettes.length == 4, "Special-stage palette lines should be present");

        Set<String> observedColors = new HashSet<>();
        for (int i = 0; i < 120; i++) {
            manager.update();
            Palette.Color c = palettes[2].getColor(7); // v_palette+$4E cycle target
            observedColors.add((c.r & 0xFF) + "," + (c.g & 0xFF) + "," + (c.b & 0xFF));
        }

        assertTrue(observedColors.size() > 1, "Special-stage palette cycle should change cycled colors over time");
    }

    @Test
    public void testAnimCountersMatchRomStartupPhaseAfterFirstUpdate() throws Exception {
        manager.initialize(0);
        // Skip the ROM's 44-VBlank-tick pre-physics hold (PaletteWhiteOut +
        // instant setup + PaletteWhiteIn, see
        // Sonic1SpecialStageManager.SS_STARTUP_HOLD_TICKS) so this update()
        // lands on Obj09's real first ExecuteObjects tick, matching what this
        // test asserts ("ROM parity" on "first tick").
        manager.advanceToEntryPresentation();
        manager.update();

        Field ringAnimFrameField = Sonic1SpecialStageManager.class.getDeclaredField("ringAnimFrame");
        Field wallVramAnimFrameField = Sonic1SpecialStageManager.class.getDeclaredField("wallVramAnimFrame");
        Field ani2FrameField = Sonic1SpecialStageManager.class.getDeclaredField("ani2Frame");
        Field ani3FrameField = Sonic1SpecialStageManager.class.getDeclaredField("ani3Frame");
        ringAnimFrameField.setAccessible(true);
        wallVramAnimFrameField.setAccessible(true);
        ani2FrameField.setAccessible(true);
        ani3FrameField.setAccessible(true);

        assertEquals(1, ringAnimFrameField.getInt(manager), "ani1 should advance to frame 1 on first tick (ROM parity)");
        assertEquals(7, wallVramAnimFrameField.getInt(manager), "ani0 should wrap to frame 7 on first tick (ROM parity)");
        assertEquals(1, ani2FrameField.getInt(manager), "ani2 should advance to frame 1 on first tick (ROM parity)");
        assertEquals(1, ani3FrameField.getInt(manager), "ani3 should advance to frame 1 on first tick (ROM parity)");
    }

    @Test
    public void testDrawForcesSpecialStageShaderState() throws Exception {
        manager.initialize(0);

        graphicsManager.setUseWaterShader(true);
        graphicsManager.setUseSpritePriorityShader(true);
        graphicsManager.setCurrentSpriteHighPriority(true);
        graphicsManager.setWaterEnabled(true);
        graphicsManager.setUseUnderwaterPaletteForBackground(true);

        manager.draw();

        assertTrue(!(graphicsManager.getShaderProgram() instanceof WaterShaderProgram), "S1 special stage draw should disable water shader");
        assertTrue(!graphicsManager.isUseSpritePriorityShader(), "S1 special stage draw should disable sprite priority mode");
        assertTrue(!graphicsManager.getCurrentSpriteHighPriority(), "S1 special stage draw should clear sprite high-priority state");
        assertTrue(!graphicsManager.isWaterEnabled(), "S1 special stage draw should clear water-enabled state");
        assertTrue(!graphicsManager.isUseUnderwaterPaletteForBackground(), "S1 special stage draw should disable underwater background palette mode");
    }

    @Test
    public void testResetClearsSpecialStageShaderState() throws Exception {
        manager.initialize(0);

        graphicsManager.setUseWaterShader(true);
        graphicsManager.setUseSpritePriorityShader(true);
        graphicsManager.setCurrentSpriteHighPriority(true);
        graphicsManager.setWaterEnabled(true);
        graphicsManager.setUseUnderwaterPaletteForBackground(true);

        manager.reset();

        assertTrue(!(graphicsManager.getShaderProgram() instanceof WaterShaderProgram), "S1 special stage reset should disable water shader");
        assertTrue(!graphicsManager.isUseSpritePriorityShader(), "S1 special stage reset should disable sprite priority mode");
        assertTrue(!graphicsManager.getCurrentSpriteHighPriority(), "S1 special stage reset should clear sprite high-priority state");
        assertTrue(!graphicsManager.isWaterEnabled(), "S1 special stage reset should clear water-enabled state");
        assertTrue(!graphicsManager.isUseUnderwaterPaletteForBackground(), "S1 special stage reset should disable underwater background palette mode");
    }

    /**
     * Collecting an emerald must NOT arm the special-stage exit. The ROM's
     * SonicSS_ChkEmerald/SonicSS_GetEmerald path only inserts the color into
     * v_emldlist, increments (v_emeralds).w and queues bgm_Emerald -- it never
     * touches obRoutine(a0) (docs/s1disasm/_incObj/09 Sonic in Special
     * Stage.asm:670-700). Only SonicSS_ChkGOAL's `addq.b #2,obRoutine(a0)` on a
     * GOAL block ($27) advances Obj09 into SonicSS_ExitStage (asm:823-832).
     * (Regression guard: an earlier engine build wrongly set
     * exitTriggered/exitPhase/exitTimer in the emerald branch, freezing Sonic
     * into the exit spin-up the tick after any emerald pickup.)
     */
    @Test
    public void testCollectingEmeraldDoesNotTriggerExitSequence() throws Exception {
        manager.initialize(0);

        Field sonicPosXField = Sonic1SpecialStageManager.class.getDeclaredField("sonicPosX");
        Field sonicPosYField = Sonic1SpecialStageManager.class.getDeclaredField("sonicPosY");
        Field layoutField = Sonic1SpecialStageManager.class.getDeclaredField("layout");
        Field exitTriggeredField = Sonic1SpecialStageManager.class.getDeclaredField("exitTriggered");
        Method checkItemsMethod = Sonic1SpecialStageManager.class.getDeclaredMethod("checkItems");
        sonicPosXField.setAccessible(true);
        sonicPosYField.setAccessible(true);
        layoutField.setAccessible(true);
        exitTriggeredField.setAccessible(true);
        checkItemsMethod.setAccessible(true);

        long sonicPosX = sonicPosXField.getLong(manager);
        long sonicPosY = sonicPosYField.getLong(manager);
        byte[] layout = (byte[]) layoutField.get(manager);

        int posX = (int) (sonicPosX >> 16);
        int posY = (int) (sonicPosY >> 16);
        int gridCol = (posX + 0x20) / SS_BLOCK_SIZE_PX;
        int gridRow = (posY + 0x50) / SS_BLOCK_SIZE_PX;
        int layoutIndex = gridRow * SS_LAYOUT_STRIDE + gridCol;
        layout[layoutIndex] = 0x3B;

        checkItemsMethod.invoke(manager);

        assertTrue(manager.isEmeraldCollected(), "Emerald collection flag should be set");
        assertFalse(exitTriggeredField.getBoolean(manager),
                "Collecting an emerald must not arm the special-stage exit (only a GOAL block does)");
    }

    @Test
    public void testGlassBlockUsesAnimationCooldownBeforeAdvancingState() throws Exception {
        manager.initialize(0);

        Field layoutField = Sonic1SpecialStageManager.class.getDeclaredField("layout");
        Field lastCollisionBlockIdField = Sonic1SpecialStageManager.class.getDeclaredField("lastCollisionBlockId");
        Field lastCollisionRowField = Sonic1SpecialStageManager.class.getDeclaredField("lastCollisionRow");
        Field lastCollisionColField = Sonic1SpecialStageManager.class.getDeclaredField("lastCollisionCol");
        Method processItemInteractionMethod = Sonic1SpecialStageManager.class.getDeclaredMethod("processItemInteraction");
        Method updateItemAnimationsMethod = Sonic1SpecialStageManager.class.getDeclaredMethod("updateItemAnimations");

        layoutField.setAccessible(true);
        lastCollisionBlockIdField.setAccessible(true);
        lastCollisionRowField.setAccessible(true);
        lastCollisionColField.setAccessible(true);
        processItemInteractionMethod.setAccessible(true);
        updateItemAnimationsMethod.setAccessible(true);

        byte[] layout = (byte[]) layoutField.get(manager);
        int row = 0;
        int col = 0;
        int layoutIndex = row * SS_LAYOUT_STRIDE + col;

        layout[layoutIndex] = 0x2D;
        lastCollisionBlockIdField.setInt(manager, 0x2D);
        lastCollisionRowField.setInt(manager, row);
        lastCollisionColField.setInt(manager, col);

        processItemInteractionMethod.invoke(manager);
        assertEquals(0x2D, layout[layoutIndex] & 0xFF, "Glass should not advance immediately before animation tick");

        updateItemAnimationsMethod.invoke(manager);
        assertEquals(0x4B, layout[layoutIndex] & 0xFF, "First animation tick should move glass into transitional state");

        for (int i = 0; i < 32; i++) {
            updateItemAnimationsMethod.invoke(manager);
        }

        assertEquals(0x2E, layout[layoutIndex] & 0xFF, "After one full glass animation, block should advance by one hit state");
    }

    @Test
    public void testHeldJumpActionMakesGroundedSonicAirborneWithJumpForceVelocity() throws Exception {
        manager.initialize(0);

        Field ssAngleField = Sonic1SpecialStageManager.class.getDeclaredField("ssAngle");
        Field sonicAirborneField = Sonic1SpecialStageManager.class.getDeclaredField("sonicAirborne");
        Field sonicVelXField = Sonic1SpecialStageManager.class.getDeclaredField("sonicVelX");
        Field sonicVelYField = Sonic1SpecialStageManager.class.getDeclaredField("sonicVelY");
        Field pressedButtonsField = Sonic1SpecialStageManager.class.getDeclaredField("pressedButtons");
        Method processJumpMethod = Sonic1SpecialStageManager.class.getDeclaredMethod("processJump");
        ssAngleField.setAccessible(true);
        sonicAirborneField.setAccessible(true);
        sonicVelXField.setAccessible(true);
        sonicVelYField.setAccessible(true);
        pressedButtonsField.setAccessible(true);
        processJumpMethod.setAccessible(true);

        // Force a known grounded/angle/velocity baseline so this test exercises
        // only Obj09_Jump's own formula, not processFall()'s per-frame gravity
        // and collision probing (which also mutates sonicVelX/Y every update()).
        sonicAirborneField.setBoolean(manager, false);
        ssAngleField.setInt(manager, 0);
        sonicVelXField.setInt(manager, 0);
        sonicVelYField.setInt(manager, 0);

        // GameLoop forwards the logical action-pressed edge through
        // InputActionMasks.toMegaDriveButtonBits(...), i.e. Mega Drive A/B/C
        // button bits (0x40/0x10/0x20), not the AbstractPlayableSprite
        // INPUT_JUMP bit (0x10). A held jump bound to the A action produces
        // 0x40, which must still register as a special-stage jump per ROM
        // SonicSS_Jump's "andi.b #btnABC,d0" (any of A, B, or C).
        int pressedFromActionA = InputActionMasks.toMegaDriveButtonBits(InputActionMasks.ACTION_A);
        manager.handleInput(pressedFromActionA, pressedFromActionA);
        assertEquals(pressedFromActionA, pressedButtonsField.getInt(manager),
                "handleInput should record the pressed edge exactly as received");

        processJumpMethod.invoke(manager);

        assertTrue(sonicAirborneField.getBoolean(manager), "Holding jump on the ground should make Sonic airborne");

        int angle = (-(0 >> 8) & 0xFC) - 0x40;
        int sinVal = TrigLookupTable.sinHex(angle & 0xFF);
        int cosVal = TrigLookupTable.cosHex(angle & 0xFF);
        int expectedVelX = (short) ((cosVal * SS_JUMP_FORCE) >> 8);
        int expectedVelY = (short) ((sinVal * SS_JUMP_FORCE) >> 8);
        assertEquals(expectedVelX, sonicVelXField.getInt(manager), "Jump X velocity should derive from SS_JUMP_FORCE");
        assertEquals(expectedVelY, sonicVelYField.getInt(manager), "Jump Y velocity should derive from SS_JUMP_FORCE");

        // The pressed edge must be consumed exactly once per frame: update()
        // clears pressedButtons at its end (ROM's v_jpadpress2 is a
        // hardware-refreshed once-per-frame edge register), so residual
        // state cannot re-trigger SonicSS_Jump on a later frame without a
        // fresh handleInput() edge.
        manager.update();
        assertEquals(0, pressedButtonsField.getInt(manager),
                "The pressed edge should be cleared after being consumed by a single update()");
    }
}


