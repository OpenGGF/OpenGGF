package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestFbzWireCageStationaryRomDplcs {
    private static final int[] OBJ70_PLAYER_FRAMES = {
            0x49, 0x52, 0x53, 0x54,
            0x6C, 0x6D, 0x6E, 0x6F, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77
    };

    @Test
    void everyObj70PlayerFrameHasARealDplcForEveryNativeCharacter() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        RomByteReader reader = RomByteReader.fromRom(rom);

        assertFramesHaveDplcs(reader, Sonic3kConstants.DPLC_SONIC_ADDR, "Sonic");
        assertFramesHaveDplcs(reader, Sonic3kConstants.DPLC_TAILS_ADDR, "Tails");
        assertFramesHaveDplcs(reader, Sonic3kConstants.DPLC_KNUCKLES_ADDR, "Knuckles");
    }

    private static void assertFramesHaveDplcs(RomByteReader reader, int tableAddress, String character) {
        List<SpriteDplcFrame> frames = S3kSpriteDataLoader.loadDplcFrames(reader, tableAddress);
        for (int frame : OBJ70_PLAYER_FRAMES) {
            assertTrue(frame < frames.size(),
                    character + " DPLC table must contain Obj70 mapping frame $" + Integer.toHexString(frame));
            assertFalse(frames.get(frame).requests().isEmpty(),
                    character + " Obj70 mapping frame $" + Integer.toHexString(frame) + " must dirty d6");
        }
    }
}
