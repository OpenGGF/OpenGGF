package com.openggf.game.sonic3k.specialstage;

import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The layout bank of a Blue Sphere stage is chosen by {@code SK_special_stage_flag}, not by the
 * stage ordinal. In {@code sub_85B0} (skdisasm 044fa46 sonic3k.asm:10845) the locked-on game
 * ({@code SK_alone_flag} clear) takes {@code SStageLayoutPtrs} (the Sonic 3 layouts, relocated by
 * {@code LockOnROM_Start} at {@code loc_8630}) unless {@code SK_special_stage_flag} is set, in which
 * case {@code loc_85E4} takes {@code SSLayoutOffs_RAM}: the Sonic &amp; Knuckles set Kos-decompressed
 * from {@code SSCompressedLayoutPtrs}. {@code Current_special_stage & 7} indexes either bank.
 *
 * <p>The only gameplay writer of a nonzero flag is the HPZ pedestal ({@code loc_90926},
 * sonic3k.asm:197836); the giant ring ({@code loc_61892}) writes zero. The same flag also selects the
 * Super Emerald reward counter ({@code loc_9CCE}), cell ({@code $D}) and art, which is why the
 * engine's reward kind carries it. The oracle reads the ROM's own pointer tables, never the engine's
 * layout constants.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kSpecialStageLayoutBank {
    /** {@code SStageLayoutPtrs}: eight Sonic 3 ROM-relative longs (sonic3k.lst). */
    private static final long S_STAGE_LAYOUT_PTRS = 0x25E2D8;
    /** {@code LockOnROM_Start}: base of the locked-on Sonic 3 image. */
    private static final long LOCK_ON_ROM_START = 0x200000;
    /** {@code SSCompressedLayoutPtrs}: first entry is {@code SSLayoutData1_Kos} (sonic3k.lst). */
    private static final long SS_COMPRESSED_LAYOUT_PTRS = 0x1E4078;
    private static final int LAYOUT_SIZE = 0x408;

    @BeforeEach
    void bootGraphics() {
        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void clear() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest(name = "stage {0} {1}")
    @CsvSource({
            "0,CHAOS_EMERALD", "1,CHAOS_EMERALD", "2,CHAOS_EMERALD", "3,CHAOS_EMERALD",
            "4,CHAOS_EMERALD", "5,CHAOS_EMERALD", "6,CHAOS_EMERALD", "7,CHAOS_EMERALD",
            "0,SUPER_EMERALD", "1,SUPER_EMERALD", "2,SUPER_EMERALD", "3,SUPER_EMERALD",
            "4,SUPER_EMERALD", "5,SUPER_EMERALD", "6,SUPER_EMERALD", "7,SUPER_EMERALD"})
    void rewardKindSelectsTheRomLayoutBankAndTheOrdinalSelectsTheStage(
            int stage, EmeraldRewardKind rewardKind) throws Exception {
        byte[] expected = romLayout(stage, rewardKind == EmeraldRewardKind.SUPER_EMERALD);
        var manager = new Sonic3kSpecialStageManager();
        manager.initialize(stage, rewardKind);

        byte[] cells = new byte[0x400];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = (byte) manager.getGrid().getCell(i & 0x1F, i >> 5);
        }
        byte[] expectedCells = java.util.Arrays.copyOf(expected, 0x400);
        assertArrayEquals(expectedCells, cells, "loaded grid is the ROM layout of the selected bank");
        // sub_85B0's trailer: Special_stage_angle, X, Y and rings left follow the grid.
        assertEquals(word(expected, 0x400) >>> 8, manager.getPlayer().getAngle(), "Special_stage_angle");
        assertEquals(word(expected, 0x402), manager.getPlayer().getXPos(), "Special_stage_X_pos");
        assertEquals(word(expected, 0x404), manager.getPlayer().getYPos(), "Special_stage_Y_pos");
        assertEquals(word(expected, 0x406), manager.getRingsLeft(), "Special_stage_rings_left");
        // The ordinal and reward are untouched by the bank choice.
        assertEquals(stage, manager.getCurrentStage());
        assertEquals(rewardKind == EmeraldRewardKind.SUPER_EMERALD, manager.isSuperEmeraldMode());
    }

    /** The bank the ROM's own pointer tables resolve for this ordinal. */
    public static byte[] romLayout(int stage, boolean skSpecialStageFlag) throws Exception {
        var rom = GameServices.rom().getRom();
        int ordinal = stage & 7;
        if (!skSpecialStageFlag) {
            long address = LOCK_ON_ROM_START
                    + (rom.read32BitAddr(S_STAGE_LAYOUT_PTRS + 4L * ordinal) & 0xFFFFFFFFL);
            return rom.readBytes(address, LAYOUT_SIZE);
        }
        long compressed = rom.read32BitAddr(SS_COMPRESSED_LAYOUT_PTRS) & 0xFFFFFFFFL;
        byte[] packed = rom.readBytes(compressed, (int) Math.min(0x4000, rom.getSize() - compressed));
        byte[] set;
        try (var channel = Channels.newChannel(new ByteArrayInputStream(packed))) {
            set = KosinskiReader.decompress(channel);
        }
        // SSLayoutOffs_RAM: RAM_start + n*$408.
        return java.util.Arrays.copyOfRange(set, ordinal * LAYOUT_SIZE, (ordinal + 1) * LAYOUT_SIZE);
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
