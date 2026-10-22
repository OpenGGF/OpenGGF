package com.openggf.game.sonic3k.titlescreen;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.RandomAccessFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The SEGA logo's palette through {@code Title_Screen}'s fades, as CRAM line 0 per
 * title update. {@code Pal_FadeToBlack} leaves the power-on black (22 VSyncs); then
 * {@code Pal_FadeFromBlack} fills black and, each iteration, VSyncs (the VBlank
 * uploads the palette stepped last time) and runs {@code Pal_FromBlack} toward
 * {@code Pal_TitleSonic1} (sonic3k.asm:4935-5017, 5508-5513). The expected steps
 * are transcribed here from {@code Pal_AddColor}, and the target is read from the
 * ROM file, independently of the manager. Round-2 lane pcm-output, 2026-10-17.
 */
@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(SingletonResetExtension.class)
class TestSonic3kTitleScreenSegaFade {

    private static final int FADE_ITERATIONS = 0x15 + 1;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        GameServices.graphics().initHeadless();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /** {@code Pal_AddColor}: +$200 if not past the target (as a word), else +$20 likewise, else +2. */
    private static int addColour(int current, int target) {
        if (current == target) return current;
        if (current + 0x200 <= target) return current + 0x200;
        if (current + 0x20 <= target) return current + 0x20;
        return current + 2;
    }

    @Test
    void logoPaletteFadesInOneRomStepPerVsyncFromBlack() throws Exception {
        int[] target = new int[16];
        try (var rom = new RandomAccessFile(RomTestUtils.ensureSonic3kRomAvailable(), "r")) {
            rom.seek(Sonic3kConstants.PAL_TITLE_SONIC1_ADDR);
            for (int colour = 0; colour < 16; colour++) target[colour] = rom.readUnsignedShort();
        }
        var manager = new Sonic3kTitleScreenManager();
        var input = new InputHandler();
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
        manager.initialize();
        int[] black = new int[16];
        for (int i = 0; i < FADE_ITERATIONS; i++) {
            manager.update(input);
            input.update();
            assertArrayEquals(black, manager.segaCramForTest(), "Pal_FadeToBlack update " + i);
        }
        int[] normal = new int[16]; // Pal_FillBlack
        for (int i = 0; i < FADE_ITERATIONS; i++) {
            manager.update(input);
            input.update();
            assertArrayEquals(normal, manager.segaCramForTest(),
                    "Pal_FadeFromBlack update " + i + " shows the palette stepped " + i + " times");
            for (int colour = 0; colour < 16; colour++) normal[colour] = addColour(normal[colour], target[colour]);
        }
        manager.update(input); // Wait_SegaS3K's first VSync uploads the 22nd step
        assertArrayEquals(target, manager.segaCramForTest(), "22 steps reach Pal_TitleSonic1");
        assertArrayEquals(target, normal);
    }
}
