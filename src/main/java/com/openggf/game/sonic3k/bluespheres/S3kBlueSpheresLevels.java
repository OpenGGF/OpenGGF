package com.openggf.game.sonic3k.bluespheres;

/**
 * Blue Spheres level numbers, stage seeds and level codes.
 *
 * <p>Every routine here is a pure translation of S&amp;K integer arithmetic.
 * A level number is the 27-bit value the ROM keeps in
 * {@code Blue_spheres_current_level}. A stage seed is the four layout-piece
 * bytes in {@code Blue_spheres_current_stage}. A level code is the 12-digit
 * number the title menu shows and accepts.
 */
public final class S3kBlueSpheresLevels {
    /** {@code andi.l #$7FFFFFF}: the level number is 27 bits wide. */
    public static final int LEVEL_MASK = 0x7FFFFFF;
    /** Digits in a level code ({@code moveq #$C-1,d6} at loc_4D240). */
    public static final int CODE_DIGITS = 12;

    /** Offset added to levels flagged as single-stage progress (loc_4D36A). */
    private static final int PROGRESS_OFFSET = 0x7654321;
    /** Offset added before the code bits are scrambled (loc_4D39A). */
    private static final int CODE_OFFSET = 0x1234567;
    private static final int CODE_XOR = 0xAAAAAAAA;
    private static final int FLAG_XOR = 0x55;

    private S3kBlueSpheresLevels() {
    }

    /**
     * The four layout pieces for {@code level}: piece {@code k} is
     * {@code ((2k+1) * level + k) mod (128 - k)}, except that piece 0 is the
     * low seven bits. ROM: loc_4D4FC (sonic3k.asm:101266-101321), repeated in
     * sub_4CD18. The two-step {@code divu} pairs compute a full 32-bit
     * remainder.
     */
    public static int[] stageSeed(int level) {
        long l = level & 0xFFFFFFFFL;
        return new int[] {
                (int) (l & 0x7F),
                (int) (((3 * l + 1) & 0xFFFFFFFFL) % 0x7F),
                (int) (((5 * l + 2) & 0xFFFFFFFFL) % 0x7E),
                (int) (((7 * l + 3) & 0xFFFFFFFFL) % 0x7D)
        };
    }

    /**
     * Advances a stage seed by one level: {@code addi.l #$01030507} and the
     * three wrap checks. ROM: BlueSpheresResults, loc_4DBFC-loc_4DC0A
     * (sonic3k.asm:101686-101714). No byte can carry into its neighbour
     * because every piece is below {@code $80} before the add.
     */
    public static int[] advanceSeed(int[] seed) {
        int s0 = (seed[0] + 1) & 0x7F;
        int s1 = seed[1] + 3;
        int s2 = seed[2] + 5;
        int s3 = seed[3] + 7;
        if (s1 >= 0x7F) s1 -= 0x7F;
        if (s2 >= 0x7E) s2 -= 0x7E;
        if (s3 >= 0x7D) s3 -= 0x7D;
        return new int[] { s0, s1, s2, s3 };
    }

    /**
     * The 28-bit stage number the title shows in single-stage progress:
     * the four seven-bit pieces packed high to low. ROM: loc_4D0B0
     * (sonic3k.asm:100789-100801).
     */
    public static int packedStageNumber(int[] seed) {
        return ((seed[0] & 0x7F) << 21) | ((seed[1] & 0x7F) << 14)
                | ((seed[2] & 0x7F) << 7) | (seed[3] & 0x7F);
    }

    /**
     * The 12-digit code for {@code level}. ROM: loc_4D36A-loc_4D3CE
     * (sonic3k.asm:101124-101155), then loc_4D240 for the digits.
     *
     * @param progressFlag {@code Blue_spheres_progress_flag} is non-zero
     */
    public static long encodeCode(int level, boolean progressFlag) {
        int stat = (level + (progressFlag ? PROGRESS_OFFSET : 0)) & LEVEL_MASK;
        int check = checksum(stat);
        int high = ((check & 0x3F) | (progressFlag ? 0x40 : 0)) ^ FLAG_XOR;
        int low = (level + CODE_OFFSET) & LEVEL_MASK;
        low |= ((check << 5) & 0xF800) << 16;
        low = Integer.rotateRight(low, 6) ^ CODE_XOR;
        return ((long) (high & 0xFFFF) << 32) | (low & 0xFFFFFFFFL);
    }

    /** A code accepted by {@link #decodeCode}. */
    public record DecodedCode(int level, boolean progressFlag) {
    }

    /**
     * Checks and decodes a 12-digit code. Returns {@code null} when the
     * checksum does not match, which the ROM answers with {@code sfx_Error}.
     * ROM: sub_4D488 (sonic3k.asm:101231-101334).
     */
    public static DecodedCode decodeCode(long code) {
        int high = (int) ((code >>> 32) & 0xFFFF);
        int low = (int) code;
        high ^= FLAG_XOR;
        int flags = high;
        int check = high & 0x3F;
        low = Integer.rotateLeft(low ^ CODE_XOR, 6);
        int expected = ((((low >>> 16) & 0xF800) >>> 5) | check) & 0x7FF;
        low -= CODE_OFFSET;
        boolean progress = (flags & 0x40) != 0;
        if (progress) low += PROGRESS_OFFSET;
        int stat = low & LEVEL_MASK;
        if ((checksum(stat) & 0x7FF) != expected) return null;
        int level = progress ? (stat - PROGRESS_OFFSET) & LEVEL_MASK : stat;
        return new DecodedCode(level, progress);
    }

    /** Splits a code into its 12 displayed digits, most significant first. */
    public static int[] codeDigits(long code) {
        int[] digits = new int[CODE_DIGITS];
        long value = code;
        for (int i = CODE_DIGITS - 1; i >= 0; i--) {
            digits[i] = (int) (value % 10);
            value /= 10;
        }
        return digits;
    }

    /**
     * Rebuilds the 48-bit value from 12 digits. ROM: loc_4D268 adds each
     * digit's place value with {@code add.l}/{@code addx.w}.
     */
    public static long codeFromDigits(int[] digits) {
        long value = 0;
        for (int digit : digits) value = value * 10 + digit;
        return value & 0xFFFF_FFFF_FFFFL;
    }

    /**
     * ROM: sub_4D5A0 (sonic3k.asm:101335-101348) with {@code d6 = 1}: a
     * 16-bit shift-and-xor over the high then low word of {@code stat}.
     */
    static int checksum(int stat) {
        int sum = 0;
        for (int word : new int[] { (stat >>> 16) & 0xFFFF, stat & 0xFFFF }) {
            sum ^= word;
            boolean carry = (sum & 1) != 0;
            sum >>>= 1;
            if (carry) sum ^= 0x8810;
        }
        return sum & 0xFFFF;
    }
}
