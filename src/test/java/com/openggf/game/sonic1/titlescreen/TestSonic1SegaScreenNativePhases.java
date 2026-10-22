package com.openggf.game.sonic1.titlescreen;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic1.audio.Sonic1SmpsConstants;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.titlescreen.SegaPaletteFade;
import com.openggf.level.Palette;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ROM-owned Pal_Sega1/Pal_Sega2 progression and the manager's real E1 submission.
 * Native stock-core corroboration and boundary scope are recorded in
 * docs/architecture/validation/audio/2026-10-22-s1-s2-native-audio-evidence.md.
 * These are phase-relative checks, not a fitted BK2 power-on frame offset.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1SegaScreenNativePhases {
    @Test
    void activePaletteScanAndItsOwnFadeRunBeforeTheSingleChantRequest() throws Exception {
        AudioManager audio = mock(AudioManager.class);
        try (var services = mockStatic(GameServices.class, CALLS_REAL_METHODS)) {
            services.when(GameServices::audio).thenReturn(audio);
            Sonic1TitleScreenManager manager = new Sonic1TitleScreenManager();
            manager.initialize();
            assertEquals(TitleScreenProvider.State.SEGA_LOGO, manager.getState());
            Sonic1TitleScreenDataLoader loader = manager.getDataLoader();
            Object cycle = field(manager, "segaLogoPaletteCycle");
            InputHandler input = new InputHandler();
            assertEquals(-10, field(cycle, "position"));
            assertEquals(SegaPaletteFade.Mode.NONE, fadeMode(manager));

            // PalLoad selects the active white palette before the first scan.
            assertRomColor(Sonic1Constants.PAL_SEGA_BG_ADDR + 0x22,
                    loader.resolveSegaLogoPaletteLine(1, fadeMode(manager), 0).getColor(1));
            manager.update(input);
            assertEquals(-8, field(cycle, "position"),
                    "the first Sega_WaitPal iteration must advance the scan immediately");
            assertFalse((Boolean) field(cycle, "scanComplete"));
            // Starting at -10 skips five entries of the six-color Pal_Sega1 table.
            assertRomColor(Sonic1Constants.PAL_SEGA_SCAN_ADDR + 10,
                    loader.resolveSegaLogoPaletteLine(1, fadeMode(manager), 0).getColor(1));

            // PalCycle_Sega's +2 positions skip transparent entries at 0/32/64/96;
            // the $64 threshold is reached on its 51st call, starting the built-in fade.
            for (int call = 1; call < 51; call++) manager.update(input);
            assertTrue((Boolean) field(cycle, "scanComplete"));
            assertEquals(-12, field(cycle, "position"));
            assertEquals(4, field(cycle, "fadeDelay"));
            verify(audio, never()).playMusic(Sonic1SmpsConstants.CMD_SEGA);

            // Native PCycSega_FadeIn decrements 4 through -1, then advances by
            // twelve bytes. Check the four actual ROM color sets, not a timer-only pin.
            for (int offset : new int[] {0, 12, 24, 36}) {
                for (int delay = 0; delay < 4; delay++) manager.update(input);
                assertEquals(offset - 12, field(cycle, "position"));
                manager.update(input);
                assertEquals(offset, field(cycle, "position"));
                assertRomColor(Sonic1Constants.PAL_SEGA_FADE_ADDR + offset,
                        loader.resolveSegaLogoPaletteLine(0, fadeMode(manager), 0).getColor(2));
                assertRomColor(Sonic1Constants.PAL_SEGA_FADE_ADDR + offset + 10,
                        loader.resolveSegaLogoPaletteLine(1, fadeMode(manager), 0).getColor(1));
                verify(audio, never()).playMusic(Sonic1SmpsConstants.CMD_SEGA);
            }

            // The fifth fade boundary reaches the exclusive $30 color-table end
            // and returns zero. GM_Sega then submits E1, with no further entry wait.
            for (int delay = 0; delay < 4; delay++) manager.update(input);
            verify(audio, never()).playMusic(Sonic1SmpsConstants.CMD_SEGA);
            manager.update(input);
            verify(audio, times(1)).playMusic(Sonic1SmpsConstants.CMD_SEGA);
            assertEquals(36, field(cycle, "position"));
            assertEquals(SegaPaletteFade.Mode.NONE, fadeMode(manager));
            manager.update(input);
            verify(audio, times(1)).playMusic(Sonic1SmpsConstants.CMD_SEGA);
        }
    }

    @Test
    void resettingAndReinitializingRestoresTheDirectActivePaletteEntry() throws Exception {
        AudioManager audio = mock(AudioManager.class);
        try (var services = mockStatic(GameServices.class, CALLS_REAL_METHODS)) {
            services.when(GameServices::audio).thenReturn(audio);
            Sonic1TitleScreenManager manager = new Sonic1TitleScreenManager();
            InputHandler input = new InputHandler();
            manager.initialize();
            manager.update(input);
            manager.reset();
            verify(audio).stopSegaPcm();
            manager.initialize();

            assertEquals(SegaPaletteFade.Mode.NONE, fadeMode(manager));
            assertEquals(-10, field(field(manager, "segaLogoPaletteCycle"), "position"));
            assertRomColor(Sonic1Constants.PAL_SEGA_BG_ADDR + 0x22,
                    manager.getDataLoader().resolveSegaLogoPaletteLine(1, fadeMode(manager), 0).getColor(1));
            manager.update(input);
            assertEquals(-8, field(field(manager, "segaLogoPaletteCycle"), "position"));
            verify(audio, never()).playMusic(Sonic1SmpsConstants.CMD_SEGA);
        }
    }

    private static void assertRomColor(int address, Palette.Color actual) throws Exception {
        int word = GameServices.rom().getRom().read16BitAddr(address);
        assertEquals((((word >>> 1) & 7) * 255 + 3) / 7, Byte.toUnsignedInt(actual.r));
        assertEquals((((word >>> 5) & 7) * 255 + 3) / 7, Byte.toUnsignedInt(actual.g));
        assertEquals((((word >>> 9) & 7) * 255 + 3) / 7, Byte.toUnsignedInt(actual.b));
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static SegaPaletteFade.Mode fadeMode(Sonic1TitleScreenManager manager) throws Exception {
        Method method = Sonic1TitleScreenManager.class.getDeclaredMethod("segaLogoFadeMode");
        method.setAccessible(true);
        return (SegaPaletteFade.Mode) method.invoke(manager);
    }
}
