package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;

/** Canonical board oracle moved from production in the 2026-10-15 campaign task. */
public final class S3kSlotLayoutFixture {
    public static final short START_X = 0x0460;
    public static final short START_Y = 0x0360;
    private static final String[] SLOT_BONUS_LAYOUT_ROWS = {
            "00000000161000000000000044444000",
            "00000007000111000011170010001000",
            "00000070000000111100007110001000",
            "41151100000000000000000000005000",
            "40000000000000000000000000001000",
            "40000000000000000000000000001000",
            "40000000000050000005000000000700",
            "41100000070007000070007000000070",
            "00100000700000000000000700000001",
            "00700007000800000000800070000006",
            "07000000008800000000880000000001",
            "01000000088807000070888000000010",
            "01000050000005000050000005000010",
            "01000007000750000005700070000010",
            "00100000000000000000000000000100",
            "00100000000000000000000000000100",
            "00100000000000000000000000000100",
            "00100000000000000000000000000100",
            "01000007000750000005700070000010",
            "01000050000005000050000005000010",
            "01000000088807000070888000000010",
            "10000000008800000000880000000070",
            "60000007000800000000800070000700",
            "10000000700000000000000700000100",
            "07000000070007000070007000000114",
            "00700000000050000005000000000004",
            "00010000000000000000000000000004",
            "00010000000000000000000000000004",
            "00050000000000000000000000115114",
            "00010001170000111100000007000000",
            "00010001007111000011100070000000",
            "00044444000000000000016100000000"
    };

    private S3kSlotLayoutFixture() { }

    public static byte[] layout() {
        byte[] layout = new byte[32 * 32];
        int index = 0;
        for (String row : SLOT_BONUS_LAYOUT_ROWS) {
            for (int i = 0; i < row.length(); i++) layout[index++] = (byte) Character.digit(row.charAt(i), 16);
        }
        return layout;
    }

    static S3kSlotLayoutData data() { return new S3kSlotLayoutData(START_X, START_Y, layout()); }
    public static S3kSlotRenderBuffers buffers() { return S3kSlotRenderBuffers.fromLayout(data()); }
    public static byte[] expandedLayout() { return S3kSlotRomData.buildExpandedLayoutBuffer(layout()); }
    public static void bootstrap(S3kSlotBonusStageRuntime runtime) { runtime.bootstrap(data()); }

    /** Explicit ROM-free coordinator input; no production fallback or catalogue entry. */
    public static Rom rom() {
        byte[] bytes = new byte[0x1E4080 + 4 + 1024];
        bytes[0x1E4080] = (byte) (START_X >>> 8);
        bytes[0x1E4081] = (byte) START_X;
        bytes[0x1E4082] = (byte) (START_Y >>> 8);
        bytes[0x1E4083] = (byte) START_Y;
        System.arraycopy(layout(), 0, bytes, 0x1E4084, 1024);
        return Rom.fromReader(new RomByteReader(bytes), "explicit Slots component fixture");
    }
}
