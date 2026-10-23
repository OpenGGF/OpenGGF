package com.openggf.game.sonic2.audio;

import com.openggf.audio.NativeMailboxMusicRoutes;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.data.compression.SaxmanDecompressor;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestSonic2NativeMailboxMusicRoutes {
    private final Sonic2AudioProfile profile = new Sonic2AudioProfile();

    @Test
    void translatesRetailRequestsWithoutChangingExistingEngineMusicIds() {
        NativeMailboxMusicRoutes routes = assertInstanceOf(NativeMailboxMusicRoutes.class, profile);
        assertEquals(0x89, Sonic2Music.OPTIONS.id);
        assertEquals(Sonic2Music.OPTIONS.id, routes.musicForNativeRequest(0x91));
        assertEquals(Sonic2Music.CASINO_NIGHT.id, routes.musicForNativeRequest(0x89));
        assertEquals(Sonic2Music.EMERALD_HILL_2P.id, routes.musicForNativeRequest(0x8C));
        assertEquals(Sonic2Music.EXTRA_LIFE.id, routes.musicForNativeRequest(0x98));
        assertEquals(Sonic2Music.CONTINUE.id, routes.musicForNativeRequest(0x9C));
        assertEquals(Sonic2Music.GAME_OVER.id, routes.musicForNativeRequest(0x9B));
        assertEquals(Sonic2Music.GOT_EMERALD.id, routes.musicForNativeRequest(0x9D));
        assertEquals(Sonic2Music.CREDITS.id, routes.musicForNativeRequest(0x9E));
        assertEquals(Sonic2Music.UNDERWATER.id, routes.musicForNativeRequest(0x9F));
    }

    @Test
    void leavesSilenceSfxCommandsAndOutOfRangeValuesToNativeDispatch() {
        for (int request = 0; request <= 0xFF; request++) {
            if (request < 0x81 || request >= 0xA0) {
                assertEquals(-1, profile.musicForNativeRequest(request),
                        "Raw request $" + Integer.toHexString(request)
                                + " must not alias an engine music id");
            }
        }
        // Engine music ids $B8/$BA/$BD/$DC are SFX at the retail mailbox boundary.
        assertEquals(-1, profile.musicForNativeRequest(-1));
        assertEquals(-1, profile.musicForNativeRequest(0x191));
        assertEquals(-1, profile.musicForNativeRequest(Integer.MIN_VALUE));
        assertEquals(-1, profile.musicForNativeRequest(Integer.MAX_VALUE));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void everyRouteReachesThePointerSelectedByTheDecompressedRetailPlaylist() throws Exception {
        Rom rom = RomManager.getInstance().getRom();
        // DecompressSoundDriver (s2.asm:91469-91473) supplies a raw Saxman payload and
        // patched compressed size, rather than the two-byte music-stream size header.
        assertEquals(0x4DFA, rom.read16BitAddr(0xEC04A)); // lea Snd_Driver(pc),a6
        assertEquals(0x3E3C, rom.read16BitAddr(0xEC04E)); // move.w #compressedSize,d7
        int payloadAddress = 0xEC04C + (short) rom.read16BitAddr(0xEC04C);
        int payloadLength = rom.read16BitAddr(0xEC050);
        byte[] driver = new SaxmanDecompressor().decompressRaw(
                rom.readBytes(payloadAddress, payloadLength), payloadLength);
        assertEquals(4872, driver.length);

        // Follow zBGMLoad's actual SUB $81 and LD HL,zMasterPlaylist operands.
        assertEquals(0xD6, driver[0x794] & 0xFF);
        int firstMusicRequest = driver[0x795] & 0xFF;
        assertEquals(0x81, firstMusicRequest);
        assertEquals(0x21, driver[0x7A1] & 0xFF);
        int playlistAddress = wordLe(driver, 0x7A2);
        assertEquals(0x11F5, playlistAddress);

        Sonic2SmpsLoader loader = new Sonic2SmpsLoader(rom);
        for (int index = 0; index < 31; index++) {
            int metadata = driver[playlistAddress + index] & 0xFF;
            int bankAddress = (metadata & 0x80) != 0 ? 0xF8000 : 0xF0000;
            int pointerAddress = bankAddress + 2 * (metadata & 0x1F);
            byte[] pointer = rom.readBytes(pointerAddress, 2);
            int expectedMusicAddress = bankAddress + (wordLe(pointer, 0) & 0x7FFF);
            int request = firstMusicRequest + index;
            assertEquals(expectedMusicAddress,
                    loader.findMusicOffset(profile.musicForNativeRequest(request)),
                    "Native request $" + Integer.toHexString(request)
                            + " must reach the retail bank/pointer entry");
        }

        // MenuScreen_Options (s2.asm:12078-12079) submits raw $91, not engine $89.
        assertEquals(0x103C, rom.read16BitAddr(0x9028));
        assertEquals(0x0091, rom.read16BitAddr(0x902A));
    }

    private static int wordLe(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }
}
