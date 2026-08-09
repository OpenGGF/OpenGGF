package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2RomData;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
class TestLbzFinalBoss2RomData {

    @Test
    void exactLockedOnRangesMatchReviewedHashesAndFrameCounts() throws Exception {
        Rom rom = TestEnvironment.currentRom();

        assertSliceHash(rom, Sonic3kConstants.LBZ_FINAL_BOSS_2_CIRCLE_TABLE_ADDR, 64,
                "ede65917bf9e68f1b084e1d0844f6f5c321daa7c");
        assertSliceHash(rom, Sonic3kConstants.LBZ_FINAL_BOSS_2_CIRCLE_TABLE_2_ADDR, 64,
                "0f9e0656a4f242d32caed881e29eca1b408cc83e");
        assertSliceHash(rom, Sonic3kConstants.MAP_LBZ_FINAL_BOSS_2_ADDR, 0x15C,
                "2d8d99437204300961db7e431d91c8e077cd2360");
        assertSliceHash(rom, Sonic3kConstants.ART_KOSM_LBZ_FINAL_BOSS_2_ADDR, 0x1122,
                "77a45958379d955bfc216966f6f1f0fb887c66e5");
        assertSliceHash(rom, Sonic3kConstants.PAL_LBZ_FINAL_BOSS_2_ADDR, 32,
                "9352e917efeba50717353089423f8b0f24894d79");
        assertSliceHash(rom, Sonic3kConstants.MAP_EGG_ROBO_HEAD_ADDR, 0x28,
                "6b66fa56d221f51f70f85049ef05240798567a7f");
        assertSliceHash(rom, Sonic3kConstants.ART_KOSM_EGG_ROBO_HEAD_ADDR, 0x1E2,
                "12479274979954cf89bac77b8fc1b9337f9013bb");
    }

    @Test
    void gameplayTablesAreReadFromRomWithNativeSignedness() throws Exception {
        LbzFinalBoss2RomData data = new LbzFinalBoss2RomData(
                RomByteReader.fromRom(TestEnvironment.currentRom()));

        assertEquals(0, data.circleOffset(0));
        assertEquals(0x28, data.circleOffset(63));
        assertEquals(0, data.circleOffset2(0));
        assertEquals(0x14, data.circleOffset2(63));
        assertArrayEquals(new int[]{0x60, 0x10, 0xA0, 0x80}, data.motionWords(0));
        assertArrayEquals(new int[]{0, 0x200, -0x200, 0}, data.motionWords(1));
        assertArrayEquals(new int[]{9, 7, 4, 5, 6, 5, 4, 0xFC},
                data.segmentAnimation(0));
        assertArrayEquals(new int[]{9, 0xB, 8, 9, 0xA, 9, 8, 0xFC},
                data.segmentAnimation(1));
        assertArrayEquals(new int[]{4, 7, 8, 12, 13, 14}, data.flashPaletteIndices());
        assertArrayEquals(new int[]{0x008, 0x00A, 0x004, 0x644, 0x422, 0x000},
                data.flashPaletteWords(false));
        assertArrayEquals(new int[]{0x888, 0x666, 0xAAA, 0xAAA, 0xEEE, 0xEEE},
                data.flashPaletteWords(true));
        assertArrayEquals(new int[]{0x4310, 0x03ED}, data.escapeExplosionPosition(0));
        assertArrayEquals(new int[]{0x44F0, 0x0390}, data.escapeExplosionPosition(15));
        assertArrayEquals(new int[]{0x0F, 0, 1, 0xFC}, data.eggRoboHeadAnimation());
        assertArrayEquals(new int[]{0x05, 0, 1, 0xFC}, data.robotnikHeadAnimation());
        assertEquals(0, data.escapeMinimumY(),
                "word_72FEA[0] is copied to _unkFAB0 for Knuckles' LBZ route");
        assertArrayEquals(new int[]{0, -0x24}, data.childOffset(
                Sonic3kConstants.BOSS_EXPLOSION_HITBOX_CHILD_TABLE_ADDR, 0));
        assertArrayEquals(new int[]{-4, -4}, data.childOffset(
                Sonic3kConstants.BOSS_EXPLOSION_HITBOX_CHILD_TABLE_ADDR, 6));
    }

    @Test
    void rawAnimationAndTimedShakeScriptsComeFromLockedOnRom() throws Exception {
        LbzFinalBoss2RomData data = new LbzFinalBoss2RomData(
                RomByteReader.fromRom(TestEnvironment.currentRom()));

        assertArrayEquals(new int[]{0x0F, 0, 1, 0xFC}, data.eggRoboHeadAnimation());
        assertArrayEquals(new int[]{
                        0, 0, 0, 1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 4, 0xF4
                }, data.bossExplosionAnimation());
        assertArrayEquals(new int[]{
                        1, -1, 1, -1, 2, -2, 2, -2, 3, -3,
                        3, -3, 4, -4, 4, -4, 5, -5, 5, -5
                }, data.timedScreenShakeOffsets());
        assertEquals(-5, data.timedScreenShakeOffset(19));
    }

    private static void assertSliceHash(Rom rom, int address, int size, String expected)
            throws Exception {
        byte[] bytes = rom.readBytes(address, size);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        assertEquals(expected, actual);
    }
}
