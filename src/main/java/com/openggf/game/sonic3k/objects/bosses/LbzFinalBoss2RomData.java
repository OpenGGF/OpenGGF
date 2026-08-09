package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;

import java.util.Objects;

/** ROM-owned lookup data consumed by {@link LbzFinalBoss2Instance}. */
public final class LbzFinalBoss2RomData {
    private static final int TABLE_WORDS = 4;
    private static final int SEGMENT_SCRIPT_BYTES = 8;
    private static final int FLASH_COLORS = 6;
    private static final int ESCAPE_POSITIONS = 16;
    private static final int CHILD_ENTRY_BYTES = 6;
    private static final int BOSS_EXPLOSION_SCRIPT_BYTES = 15;
    private static final int TIMED_SHAKE_BYTES = 20;

    private final RomByteReader reader;

    public LbzFinalBoss2RomData(RomByteReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
        // Fail at construction rather than allowing an object to run with a
        // partial/wrong ROM. Runtime data never falls back to the disassembly.
        reader.slice(Sonic3kConstants.LBZ_FINAL_BOSS_2_CIRCLE_TABLE_ADDR, 64);
        reader.slice(Sonic3kConstants.LBZ_FINAL_BOSS_2_CIRCLE_TABLE_2_ADDR, 64);
        reader.slice(Sonic3kConstants.LBZ_FINAL_BOSS_2_ESCAPE_POSITIONS_ADDR,
                ESCAPE_POSITIONS * 4);
        reader.slice(Sonic3kConstants.PAL_LBZ_FINAL_BOSS_2_ADDR, 32);
        reader.slice(Sonic3kConstants.LBZ_FINAL_BOSS_2_INITIAL_CHILD_TABLE_ADDR, 2 + 4 * CHILD_ENTRY_BYTES);
        reader.slice(Sonic3kConstants.LBZ_FINAL_BOSS_2_ARM_CHILD_TABLE_ADDR, 2 + 4 * CHILD_ENTRY_BYTES);
        reader.slice(Sonic3kConstants.LBZ_FINAL_BOSS_2_DEBRIS_CHILD_TABLE_ADDR, 2 + 5 * CHILD_ENTRY_BYTES);
        reader.slice(Sonic3kConstants.BOSS_EXPLOSION_HITBOX_CHILD_TABLE_ADDR,
                2 + 7 * CHILD_ENTRY_BYTES);
        reader.slice(Sonic3kConstants.LBZ_FINAL_BOSS_KNUX_BOUNDS_ADDR, 2);
        // Big Arm's Child_DrawTouch_Sprite_FlickerMove calls
        // Set_IndexedVelocity with d0=$0C.  The controller, two segment
        // subtypes, and joint therefore reach through byte offset $17.
        reader.slice(Sonic3kConstants.OBJECT_VELOCITY_INDEX_ADDR, 6 * 4);
        reader.slice(Sonic3kConstants.ANI_RAW_ROBOTNIK_HEAD_ADDR, 4);
        reader.slice(Sonic3kConstants.ANI_RAW_EGG_ROBO_HEAD_ADDR, 4);
        reader.slice(Sonic3kConstants.ANI_RAW_BOSS_EXPLOSION_ADDR,
                BOSS_EXPLOSION_SCRIPT_BYTES);
        reader.slice(Sonic3kConstants.SCREEN_SHAKE_ARRAY_ADDR, TIMED_SHAKE_BYTES);
    }

    public int circleOffset(int index) {
        checkIndex(index, 64, "circle index");
        return reader.readU8(Sonic3kConstants.LBZ_FINAL_BOSS_2_CIRCLE_TABLE_ADDR + index);
    }

    public int circleOffset2(int index) {
        checkIndex(index, 64, "circle-2 index");
        return reader.readU8(Sonic3kConstants.LBZ_FINAL_BOSS_2_CIRCLE_TABLE_2_ADDR + index);
    }

    public int[] motionWords(int table) {
        int address = switch (table) {
            case 0 -> Sonic3kConstants.LBZ_FINAL_BOSS_2_MOTION_TABLE_ADDR;
            case 1 -> Sonic3kConstants.LBZ_FINAL_BOSS_2_MOTION_TABLE_2_ADDR;
            default -> throw new IndexOutOfBoundsException("motion table: " + table);
        };
        int[] values = new int[TABLE_WORDS];
        for (int i = 0; i < values.length; i++) {
            values[i] = reader.readS16BE(address + i * 2);
        }
        return values;
    }

    public int[] segmentAnimation(int subtype) {
        int address = switch (subtype) {
            case 0 -> Sonic3kConstants.LBZ_FINAL_BOSS_2_SEGMENT_ANIM_ADDR;
            case 1 -> Sonic3kConstants.LBZ_FINAL_BOSS_2_SEGMENT_ANIM_2_ADDR;
            default -> throw new IndexOutOfBoundsException("segment animation: " + subtype);
        };
        return unsignedBytes(address, SEGMENT_SCRIPT_BYTES);
    }

    public int[] flashPaletteIndices() {
        int[] values = new int[FLASH_COLORS];
        for (int i = 0; i < values.length; i++) {
            int ramAddress = reader.readU16BE(
                    Sonic3kConstants.LBZ_FINAL_BOSS_2_FLASH_OFFSETS_ADDR + i * 2);
            values[i] = (ramAddress & 0x1F) >>> 1;
        }
        return values;
    }

    public int[] flashPaletteWords(boolean white) {
        int address = Sonic3kConstants.LBZ_FINAL_BOSS_2_FLASH_WORDS_ADDR
                + (white ? FLASH_COLORS * 2 : 0);
        int[] values = new int[FLASH_COLORS];
        for (int i = 0; i < values.length; i++) {
            values[i] = reader.readU16BE(address + i * 2);
        }
        return values;
    }

    public int[] escapeExplosionPosition(int index) {
        checkIndex(index, ESCAPE_POSITIONS, "escape position");
        int address = Sonic3kConstants.LBZ_FINAL_BOSS_2_ESCAPE_POSITIONS_ADDR + index * 4;
        return new int[]{reader.readU16BE(address), reader.readU16BE(address + 2)};
    }

    public int[] eggRoboHeadAnimation() {
        return unsignedBytes(Sonic3kConstants.ANI_RAW_EGG_ROBO_HEAD_ADDR, 4);
    }

    public int[] robotnikHeadAnimation() {
        return unsignedBytes(Sonic3kConstants.ANI_RAW_ROBOTNIK_HEAD_ADDR, 4);
    }

    public int[] bossExplosionAnimation() {
        return unsignedBytes(Sonic3kConstants.ANI_RAW_BOSS_EXPLOSION_ADDR,
                BOSS_EXPLOSION_SCRIPT_BYTES);
    }

    public int[] timedScreenShakeOffsets() {
        int[] values = new int[TIMED_SHAKE_BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte) reader.readU8(Sonic3kConstants.SCREEN_SHAKE_ARRAY_ADDR + i);
        }
        return values;
    }

    public int timedScreenShakeOffset(int index) {
        checkIndex(index, TIMED_SHAKE_BYTES, "timed screen-shake index");
        return (byte) reader.readU8(Sonic3kConstants.SCREEN_SHAKE_ARRAY_ADDR + index);
    }

    public byte[] paletteLine() {
        return reader.slice(Sonic3kConstants.PAL_LBZ_FINAL_BOSS_2_ADDR, 32);
    }

    /** Signed ChildObjDat x/y bytes, excluding the routine pointer. */
    public int[] childOffset(int tableAddress, int index) {
        int count = reader.readU16BE(tableAddress) + 1;
        checkIndex(index, count, "child index");
        int address = tableAddress + 2 + index * CHILD_ENTRY_BYTES + 4;
        return new int[]{(byte) reader.readU8(address), (byte) reader.readU8(address + 1)};
    }

    /**
     * {@code Set_IndexedVelocity} entry selected by the native even subtype.
     * Obj_VelocityIndex stores signed x/y words in four-byte records.
     */
    public int[] indexedVelocity(int subtype) {
        if ((subtype & 1) != 0) {
            throw new IndexOutOfBoundsException("velocity subtype: " + subtype);
        }
        int index = subtype >>> 1;
        checkIndex(index, 5, "velocity subtype");
        int address = Sonic3kConstants.OBJECT_VELOCITY_INDEX_ADDR + index * 4;
        return new int[]{reader.readS16BE(address), reader.readS16BE(address + 2)};
    }

    /** Signed words selected by a native byte offset into Obj_VelocityIndex. */
    public int[] indexedVelocityAtByteOffset(int byteOffset) {
        if (byteOffset < 0 || byteOffset > 20 || (byteOffset & 1) != 0) {
            throw new IndexOutOfBoundsException("velocity byte offset: " + byteOffset);
        }
        int address = Sonic3kConstants.OBJECT_VELOCITY_INDEX_ADDR + byteOffset;
        return new int[]{reader.readS16BE(address), reader.readS16BE(address + 2)};
    }

    /** Native {@code _unkFAB0} seed selected by Knuckles' first LBZ bounds record. */
    public int escapeMinimumY() {
        return reader.readU16BE(Sonic3kConstants.LBZ_FINAL_BOSS_KNUX_BOUNDS_ADDR);
    }

    private int[] unsignedBytes(int address, int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = reader.readU8(address + i);
        }
        return values;
    }

    private static void checkIndex(int index, int count, String label) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException(label + ": " + index);
        }
    }
}
