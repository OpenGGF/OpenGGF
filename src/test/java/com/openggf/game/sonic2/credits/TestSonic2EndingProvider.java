package com.openggf.game.sonic2.credits;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.EndingPhase;
import com.openggf.game.EndingProvider;
import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Sonic2EndingProvider} state machine and phase mapping.
 * <p>
 * These tests exercise the provider's public API without requiring ROM,
 * OpenGL, or a running engine. They verify:
 * <ul>
 *   <li>Initial slide and logo-flash state before initialize()</li>
 *   <li>S2-specific demo defaults (all no-ops)</li>
 *   <li>GameModule integration</li>
 * </ul>
 */
public class TestSonic2EndingProvider {

    // ========================================================================
    // Initial state (before initialize)
    // ========================================================================

    @Test
    public void testInitialSlideIsZero() {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        assertEquals(0, provider.getCurrentSlide(), "Initial slide should be 0");
    }

    // ========================================================================
    // S2 does not use demo playback defaults
    // ========================================================================

    @Test
    public void testNoDemoLoadRequest() {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        assertFalse(provider.hasDemoLoadRequest(), "S2 ending should never have demo load requests");
    }

    @Test
    public void testNoTextReturnRequest() {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        assertFalse(provider.hasTextReturnRequest(), "S2 ending should never have text return requests");
    }

    @Test
    public void testNoLamppostState() {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        assertNull(provider.getDemoLamppostState(), "S2 ending should never return lamppost state");
    }

    @Test
    public void testScrollNotFrozen() {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        assertFalse(provider.isScrollFrozen(), "S2 ending should not freeze scroll by default");
    }

    @Test
    public void testDemoInputMaskIsZero() {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        assertEquals(0, provider.getDemoInputMask(), "S2 ending demo input mask should be 0");
    }

    @Test
    public void testDemoCoordinatesAreZero() {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        assertEquals(0, provider.getDemoZone());
        assertEquals(0, provider.getDemoAct());
        assertEquals(0, provider.getDemoStartX());
        assertEquals(0, provider.getDemoStartY());
    }

    // ========================================================================
    // GameModule integration
    // ========================================================================

    @Test
    public void testSonic2GameModuleCreatesNewProviderPerCall() {
        Sonic2GameModule module = new Sonic2GameModule();
        EndingProvider first = module.getEndingProvider();
        EndingProvider second = module.getEndingProvider();
        assertNotSame(first, second, "Each getEndingProvider() call should create a new instance");
    }

    // ========================================================================
    // Logo flash manager accessor
    // ========================================================================

    @Test
    public void testLogoFlashManagerNullBeforePostCredits() {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        assertNull(provider.getLogoFlashManager(), "Logo flash manager should be null before POST_CREDITS phase");
    }

    @ParameterizedTest
    @CsvSource({"NTSC, 50, 399, 9325", "PAL, 60, 325, 7771"})
    void allTwentyOneSlidesReachLogoOnTheNativeRegionBoundary(
            String region, int configuredFps, int displayFrames, int logoEntryFrame) throws Exception {
        SonicConfigurationService config = GameServices.configuration();
        Object previousRegion = config.getConfigValue(SonicConfiguration.REGION);
        Object previousFps = config.getConfigValue(SonicConfiguration.FPS);
        try {
            config.setConfigValue(SonicConfiguration.REGION, region);
            // Region selects the ROM branch, independently of render FPS.
            config.setConfigValue(SonicConfiguration.FPS, configuredFps);
            CreditsProgression progression = new CreditsProgression();

            for (int slide = 0; slide < 21; slide++) {
                assertEquals(slide, progression.provider.getCurrentSlide());
                assertEquals(EndingPhase.CREDITS_TEXT, progression.provider.getCurrentPhase());

                progression.advance(21);
                assertEquals(FadeManager.FadeState.FADING_FROM_BLACK, progression.fade.getState(),
                        "the reveal retains its native fade token for the terminal V-blank");
                progression.advance(1);
                assertEquals(FadeManager.FadeState.NONE, progression.fade.getState());

                // DBF waits through d0=0 before starting the outgoing fade.
                progression.advance(displayFrames - 1);
                assertEquals(FadeManager.FadeState.NONE, progression.fade.getState());
                assertEquals(slide, progression.provider.getCurrentSlide());
                progression.advance(1);
                assertEquals(FadeManager.FadeState.FADING_TO_BLACK, progression.fade.getState());

                progression.advance(21);
                assertEquals(slide, progression.provider.getCurrentSlide(),
                        "slide index advances only after all 22 outgoing fade V-blanks");
                progression.advance(1);
                assertEquals(slide + 1, progression.provider.getCurrentSlide());
            }

            // EndgameCredits performs another 22-frame black fade after its
            // negative pointer-table sentinel, before loading the logo.
            progression.advance(21);
            assertEquals(EndingPhase.CREDITS_TEXT, progression.provider.getCurrentPhase());
            assertNull(progression.provider.getLogoFlashManager());
            progression.advance(1);
            assertEquals(EndingPhase.POST_CREDITS, progression.provider.getCurrentPhase());
            assertEquals(logoEntryFrame, progression.frames);
            assertNull(progression.provider.getLogoFlashManager(),
                    "logo asset initialization belongs to the following provider call");
            assertEquals(43 * 22, progression.services.get(PlcLifecyclePhase.PALETTE_FADE));
            assertEquals(43 * 22, progression.fadePreparations);
            assertEquals(21 * displayFrames, progression.services.get(PlcLifecyclePhase.ENDING));
        } finally {
            config.setConfigValue(SonicConfiguration.REGION, previousRegion);
            config.setConfigValue(SonicConfiguration.FPS, previousFps);
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void nativeCreditsWaitBranchesAndSentinelHaveTheExpectedMachineInstructions() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        // EndgameCredits (s2.asm:13216-13224): immediate waits, Graphics_Flags
        // bit 6, VintID_Ending, WaitForVint and inclusive DBF d0.
        assertArrayEquals(HexFormat.of().parseHex(
                        "303c018e08380006fff86704303c014411fc0018f62a6100937a51c8fff4"),
                rom.readBytes(0x9FF2, 30));
        // Both BSR targets resolve to Pal_FadeToBlack at $246A: before
        // advancing the table index, and after its negative sentinel.
        assertArrayEquals(HexFormat.of().parseHex("61008458"), rom.readBytes(0xA010, 4));
        assertArrayEquals(HexFormat.of().parseHex("6100843e"), rom.readBytes(0xA02A, 4));
        assertArrayEquals(HexFormat.of().parseHex("383c0015"), rom.readBytes(0x23E4, 4));
        assertArrayEquals(HexFormat.of().parseHex("383c0015"), rom.readBytes(0x2470, 4));
        assertArrayEquals(HexFormat.of().parseHex("51ccffee"), rom.readBytes(0x23F8, 4));
        assertArrayEquals(HexFormat.of().parseHex("51ccffee"), rom.readBytes(0x2484, 4));
        byte[] pointers = rom.readBytes(0xB2CA, 22 * 4);
        for (int slide = 0; slide < 21; slide++) {
            assertEquals(0, pointers[slide * 4] & 0x80, "slide pointer must be nonnegative");
        }
        assertEquals(0x80, pointers[21 * 4] & 0x80, "slide 21 is the end sentinel");
    }

    private static final class CreditsProgression {
        private final FadeManager fade = new FadeManager();
        private final Sonic2EndingProvider provider = new Sonic2EndingProvider(fade);
        private final Map<PlcLifecyclePhase, Integer> services = new EnumMap<>(PlcLifecyclePhase.class);
        private final PlcFrameLifecycleCoordinator lifecycle;
        private int frames;
        private int fadePreparations;

        @SuppressWarnings({"rawtypes", "unchecked"})
        private CreditsProgression() throws Exception {
            lifecycle = new PlcFrameLifecycleCoordinator(new PlcLifecycleService() {
                @Override
                public void serviceVBlank(PlcLifecyclePhase phase) {
                    services.merge(phase, 1, Integer::sum);
                }

                @Override
                public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                    return phase == PlcLifecyclePhase.PALETTE_FADE;
                }

                @Override
                public void prepareAfterLoop(PlcLifecyclePhase phase) {
                    fadePreparations++;
                }
            });
            provider.bindNativeFadeLifecycle(lifecycle);
            // Enter immediately after the cutscene/credit-art handoff. The
            // complete production update path drives every subsequent slide;
            // no ROM asset loading or OpenGL is needed for this timer test.
            Field state = Sonic2EndingProvider.class.getDeclaredField("state");
            state.setAccessible(true);
            state.set(provider, Enum.valueOf((Class) state.getType(), "CREDITS_FADE_IN"));
            fade.startFadeFromBlack(lifecycle.beginNativeBlockingFade().wrapCompletion(() -> { }), 1);
        }

        private void advance(int count) {
            for (int frame = 0; frame < count; frame++) {
                lifecycle.runLogicalIteration(fade::update, token -> {
                    token.claim(PlcLifecyclePhase.ENDING);
                    provider.update();
                    return null;
                });
                frames++;
            }
        }
    }
}
