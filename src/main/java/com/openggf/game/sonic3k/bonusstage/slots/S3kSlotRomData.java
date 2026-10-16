package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.RomByteReader;
import java.util.Objects;

public final class S3kSlotRomData {
    public static final short SLOT_BONUS_CAGE_CENTER_X = 0x0460;
    public static final short SLOT_BONUS_CAGE_CENTER_Y = 0x0430;
    public static final short SLOT_MACHINE_PANEL_CENTER_OFFSET_X = -0x1F;
    public static final short SLOT_MACHINE_PANEL_CENTER_OFFSET_Y = -0x38;
    public static final int SLOT_MACHINE_DISPLAY_OFFSET_X = -112;
    public static final int SLOT_MACHINE_DISPLAY_OFFSET_Y = -56;
    public static final int TRANSIENT_SLOT_COUNT = 0x20;
    public static final int SLOT_LAYOUT_SIZE = 0x20;
    public static final int SLOT_EXPANDED_STRIDE = 0x80;
    public static final int SLOT_LAYOUT_WORLD_OFFSET = 0x20;

    public static final short[] REWARD_VALUES = {100, 30, 20, 25, -1, 10, 8, 200};
    public static final byte[] TARGET_ROWS = {
            4, 0, 0,
            9, 1, 0x11,
            4, 3, 0x33,
            0x12, 4, 0x44,
            9, 2, 0x22,
            0x0F, 5, 0x55,
            0x0F, 6, 0x66,
            (byte) 0xFF, 0x0F, (byte) 0xFF
    };
    public static final byte[] REEL_SEQUENCE_A = {0, 1, 2, 5, 4, 6, 5, 3};
    public static final byte[] REEL_SEQUENCE_B = {0, 1, 2, 5, 4, 6, 1, 3};
    public static final byte[] REEL_SEQUENCE_C = {0, 1, 2, 5, 4, 6, 3, 5};

    // Transient layout-cell animations (ROM sub_4B592's four handlers,
    // sonic3k.asm:98397-98513). Each *_DELAY below is the literal byte the matching
    // handler reloads its countdown with (`move.b #N,2(a0)`), consumed by the shared
    // `subq.b #1,2(a0) / bpl` idiom -- see TransientAnimationSlot.tick, which models
    // that idiom directly, so these stay the ROM's own numbers rather than a
    // period converted for a different countdown convention.
    //   loc_4B5C2 ring sparkle  : #5, byte_4B5EC = $10,$11,$12,$13,0 (terminator
    //                             leaves the cell empty)
    //   loc_4B5F2 bumper bounce : #1, byte_4B622 = $A,$B,0 -> restores #5
    //   loc_4B626 spike         : #7, byte_4B656 = $C,6,$C,0 -> restores #6
    //   loc_4B65A reel flash    : #1, byte_4B688 = ($D,$E,$F) x8, 0 -> restores the
    //                             advanced reel id stashed in the slot's spare byte
    public static final byte[] RING_SPARKLE_FRAMES = {0x10, 0x11, 0x12, 0x13};
    public static final int RING_SPARKLE_DELAY = 5;
    public static final byte[] BUMPER_BOUNCE_FRAMES = {0x0A, 0x0B};
    public static final int BUMPER_BOUNCE_DELAY = 1;
    public static final byte[] SPIKE_ANIMATION_FRAMES = {0x0C, 0x06, 0x0C};
    public static final int SPIKE_ANIMATION_DELAY = 7;
    public static final byte[] SLOT_WALL_COLOR_FRAMES = {
            0x0D, 0x0E, 0x0F,
            0x0D, 0x0E, 0x0F,
            0x0D, 0x0E, 0x0F,
            0x0D, 0x0E, 0x0F,
            0x0D, 0x0E, 0x0F,
            0x0D, 0x0E, 0x0F,
            0x0D, 0x0E, 0x0F,
            0x0D, 0x0E, 0x0F
    };
    // loc_4B65A reloads with #1 (sonic3k.asm:98502), like the bumper handler. The
    // previous value of 2 was a period-2 compensation for the old countdown
    // convention (`--timer > 0` with an eager first publish); the ROM idiom is now
    // modelled directly in TransientAnimationSlot.tick, so the literal reload is
    // correct on its own and the flash still advances one colour every 2 frames.
    public static final int SLOT_WALL_COLOR_DELAY = 1;

    private S3kSlotRomData() {
    }

    // Locked-on World SlotBonusLayout (sub_4B6AA/loc_4B6D2): two entry
    // words, then 32 rows of 32 bytes. Read the bounded source before mutation.
    private static final int SLOT_BONUS_LAYOUT_ADDRESS = 0x1E4080;
    private static final int SLOT_BONUS_SOURCE_BYTES = 4 + SLOT_LAYOUT_SIZE * SLOT_LAYOUT_SIZE;

    static S3kSlotLayoutData loadLayout(RomByteReader reader) {
        Objects.requireNonNull(reader, "reader");
        RomByteReader source = reader.window(SLOT_BONUS_LAYOUT_ADDRESS, SLOT_BONUS_SOURCE_BYTES);
        return new S3kSlotLayoutData((short) source.readU16BE(0), (short) source.readU16BE(2),
                source.slice(4, SLOT_LAYOUT_SIZE * SLOT_LAYOUT_SIZE));
    }

    static byte[] buildExpandedLayoutBuffer(byte[] layout) {
        Objects.requireNonNull(layout, "layout");
        if (layout.length != SLOT_LAYOUT_SIZE * SLOT_LAYOUT_SIZE) {
            throw new IllegalArgumentException("Slots needs exactly 1024 layout bytes");
        }
        byte[] expandedLayout = new byte[SLOT_EXPANDED_STRIDE * SLOT_EXPANDED_STRIDE];
        for (int row = 0; row < SLOT_LAYOUT_SIZE; row++) {
            int expandedRow = row + SLOT_LAYOUT_WORLD_OFFSET;
            int expandedIndex = expandedRow * SLOT_EXPANDED_STRIDE + SLOT_LAYOUT_WORLD_OFFSET;
            System.arraycopy(layout, row * SLOT_LAYOUT_SIZE, expandedLayout, expandedIndex, SLOT_LAYOUT_SIZE);
        }
        return expandedLayout;
    }

}
