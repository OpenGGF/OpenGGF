package slaytherobotnik.scene;

/**
 * Animation scripts and tables the event pictures read straight from the ROM, by their
 * sonic3k.lst addresses, so the pictures run the game's own data.
 */
final class RomTables {
    /** AniRaw_SSEntryRing: the Giant Ring forming (frames 0-7), then turning $A, 9, 8, $B. */
    static final int ANIRAW_SS_ENTRY_RING = 0x619C2;
    /** AniRaw_SSEntryFlash: the entry flash, 0-3 then 3-0 mirrored, then $F4. */
    static final int ANIRAW_SS_ENTRY_FLASH = 0x619D4;
    /** Ani_Monitor: the eleven monitor animations (subtypes 0-9 and the break). */
    static final int ANI_MONITOR = 0x1DB3A;
    /** Ani_MHZMushroomCap: resting, then the squash-and-spring bounce. */
    static final int ANI_MHZ_MUSHROOM_CAP = 0x3E1DA;
    /** MHZMushroomCap_Positions: the caps' sway, an (x, y) byte pair per step of Anim_Counters+$F. */
    static final int MHZ_MUSHROOM_CAP_POSITIONS = 0x3E106;
    /** AniRaw_BloominatorAttack: (frame, delay) pairs for Animate_RawMultiDelay, ending $F4. */
    static final int ANIRAW_BLOOMINATOR_ATTACK = 0x86E42;
    /** SineTable: GetSineCosine's 256 steps plus a quarter, signed words scaled by 256. */
    static final int SINE_TABLE = 0x1D64;

    private RomTables() {
    }

    /** {@code length} bytes at {@code address}, or null without the ROM. */
    static byte[] read(Shell shell, int address, int length) {
        if (!shell.art.hasRom()) {
            return null;
        }
        try {
            return shell.art.rom().read(address, length);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** GetSineCosine's table as {@link SpilledRings} wants it, or null without the ROM. */
    static int[] sine(Shell shell) {
        byte[] bytes = read(shell, SINE_TABLE, 0x140 * 2);
        if (bytes == null) {
            return null;
        }
        int[] table = new int[0x140];
        for (int i = 0; i < table.length; i++) {
            table[i] = (short) (((bytes[i * 2] & 0xFF) << 8) | (bytes[i * 2 + 1] & 0xFF));
        }
        return table;
    }
}
