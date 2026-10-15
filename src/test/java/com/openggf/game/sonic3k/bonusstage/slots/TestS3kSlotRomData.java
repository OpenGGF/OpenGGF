package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Alternate resource bytes must reach real layout/reel/prize consumers. */
class TestS3kSlotRomData {
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    private static byte[] image() {
        byte[] image=new byte[0x1E4484];
        System.arraycopy(S3kSlotRomTestData.image(),0x599BE,image,0x599BE,0xC0);
        return image;
    }
    private static void word(byte[] image, int address, int value) {
        image[address] = (byte) (value >>> 8); image[address + 1] = (byte) value;
    }

    @Test void declaredRomBankOwnsStartAndBothMutableLayoutCopies() {
        byte[] bytes = image();
        word(bytes, 0x1E4080, 0x1234); word(bytes, 0x1E4082, 0x5678);
        bytes[0x1E4084] = 5; bytes[0x1E4483] = 7;
        var bank = S3kSlotResources.fromRom(RomByteReader.fromBytes(bytes));
        var buffers = S3kSlotRenderBuffers.fromRomData(bank);
        assertEquals(0x1234, bank.playerStartX()); assertEquals(0x5678, bank.playerStartY());
        assertEquals(5, buffers.layout()[0]); assertEquals(7, buffers.layout()[1023]);
        assertEquals(5, buffers.expandedLayout()[0x20 * 0x80 + 0x20]);
        assertEquals(7, buffers.expandedLayout()[0x3F * 0x80 + 0x3F]);
        buffers.layout()[0] = 99;
        assertEquals(5, bank.layout()[0]);
    }

    @Test void actualPrizeAndTargetConsumersUseDeclaredSignedWordsAndRows() {
        byte[] bytes = image(); word(bytes, 0x4C8A4 + 6, 321);
        word(bytes, 0x4C8A4 + 8, -123);
        bytes[0x4C8B4] = 1; bytes[0x4C8B4 + 3] = (byte) 0xFF;
        var bank = S3kSlotResources.fromRom(RomByteReader.fromBytes(bytes));
        assertEquals(321, S3kSlotPrizeCalculator.calculate(bank, 3, (byte) 0x33));
        assertEquals(-123, S3kSlotPrizeCalculator.calculate(bank, 4, (byte) 0x44));
        assertEquals(0, S3kSlotRewardResolver.pickFixedRow(bank, 0));
        assertEquals(-1, S3kSlotRewardResolver.pickFixedRow(bank, 1));
    }

    @Test void actualReelAndCellAnimationConsumersReadDeclaredBanks() {
        byte[] bytes = image();
        for (int reel = 0; reel < 3; reel++)
            for (int cell = 0; cell < 8; cell++) bytes[0x4C8CC + reel * 8 + cell] = (byte) (reel + 4);
        bytes[0x1E4084] = 1; bytes[0x4B688] = 0x12;
        var bank = S3kSlotResources.fromRom(RomByteReader.fromBytes(bytes));
        var state = S3kSlotStageState.bootstrap(bank);
        new S3kSlotOptionCycleSystem().tick(state, 0, new com.openggf.game.GameRng(com.openggf.game.GameRng.Flavour.S3K));
        assertArrayEquals(new int[] {4, 5, 6}, state.optionCycleDisplaySymbols());
        assertArrayEquals(new int[] {4, 5, 6}, S3kSlotMachineDisplayState.fromState(state, 0, 0).faces());
        var buffers = S3kSlotRenderBuffers.fromRomData(bank);
        assertTrue(buffers.startSlotWallAnimationAt(0, 2)); buffers.tickTransientAnimations();
        assertEquals(0x12, buffers.layout()[0]);
        buffers.tickTransientAnimations(); buffers.tickTransientAnimations();
        assertEquals(2, buffers.layout()[0], "the first zero terminator restores the permanent cell");
        byte[] layout = bank.layout();
        bytes[0x4B5EC] = 0x19;
        var changed = S3kSlotResources.fromRom(RomByteReader.fromBytes(bytes));
        assertTrue(new S3kSlotLayoutAnimator(changed).queueRingSparkle(layout, 0));
        assertEquals(0x19, layout[0]);
    }

    @Test void missingOrMalformedRequiredBanksFailBeforeUse() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> S3kSlotResources.fromRom(RomByteReader.fromBytes(new byte[0x1E4084])));
        byte[] bytes = image(); java.util.Arrays.fill(bytes, 0x4B688, 0x4B688 + 25, (byte) 1);
        assertThrows(IllegalArgumentException.class,
                () -> S3kSlotResources.fromRom(RomByteReader.fromBytes(bytes)));
    }

    @RequiresRom(SonicGame.SONIC_3K)
    @Test void verifiedLockedOnRomSuppliesOriginalLayoutReelsAndSignedPrizes() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var bank = S3kSlotResources.fromRom(RomByteReader.fromRom(GameServices.rom().getRom()));
        assertArrayEquals(S3kSlotRomTestData.resources().backgroundDeformHeights(),bank.backgroundDeformHeights());
        assertEquals(S3kSlotRomTestData.resources().backgroundBands(),bank.backgroundBands());
        assertEquals(0x460, bank.playerStartX()); assertEquals(0x360, bank.playerStartY());
        assertArrayEquals(new short[] {100, 30, 20, 25, -1, 10, 8, 200}, bank.rewardValues());
        assertArrayEquals(new byte[] {0, 1, 2, 5, 4, 6, 5, 3}, bank.reelSequenceA());
        assertEquals("e5fbad9116c0febbfb792fad5ed0445291c1f49ebfd2bf04b81c187cb32dfe2d",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bank.layout())));
    }
}
