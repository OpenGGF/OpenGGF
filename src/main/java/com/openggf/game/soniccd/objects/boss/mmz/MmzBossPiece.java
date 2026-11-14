package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * State every Metallic Madness boss object shares (objects/r8/boss.asm, amy_rose.asm, boss_door.asm, hotaru.asm,
 * door.asm). The routines name their scratch bytes by offset; these fields name them by use:
 * <ul>
 *   <li>{@link #flags}: the object's handshake byte ({@code obj.var_2c}); its bits differ per object and are named
 *       where they are used.</li>
 *   <li>{@link #step}: {@code obj.var_2d}, the routine's sub-state.</li>
 *   <li>{@link #timer}: the word at {@code obj.var_2a}. The routines address it as a word ({@code addq.w},
 *       {@code addi.w #$230}) and as its two bytes ({@code var_2a} the high byte, {@code var_2b} the low byte);
 *       {@link #timerHigh()}/{@link #timerLow()} are those byte views, so a word write and a byte read agree.</li>
 *   <li>{@link #target}: the word at {@code obj.var_38}, likewise with the high byte {@code var_38} and the low
 *       byte {@code var_39}.</li>
 * </ul>
 */
abstract class MmzBossPiece extends CdBossPart {
    int flags;
    int step;
    int timer;
    int target;

    MmzBossPiece(int id, int x, int y, String name) {
        super(id, x, y, name);
    }

    MmzBossPiece(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    /** An object allocated with ID {@code id} and subtype {@code subtype} ({@code move.b #s,obj.subtype(a1)}). */
    static ObjectSpawn spawnAt(int id, int x, int y, int subtype) {
        return new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, id, subtype & 0xFF, 0, false, 0);
    }

    /** {@code obj.x} / {@code obj.y} of another part (the centre words). */
    final int ox() { return x; }

    final int oy() { return y; }

    final boolean has(int bit) { return (flags & bit) != 0; }

    final void set(int bit) { flags |= bit; }

    /** {@code bclr}: clears the bit and returns whether it was set. */
    final boolean clear(int bit) {
        boolean was = (flags & bit) != 0;
        flags &= ~bit;
        return was;
    }

    // ---- the var_2a and var_38 words and their bytes ---------------------------------------------------------

    final int timerHigh() { return (timer >> 8) & 0xFF; }

    final int timerLow() { return timer & 0xFF; }

    final void setTimerHigh(int value) { timer = ((value & 0xFF) << 8) | (timer & 0xFF); }

    final void setTimerLow(int value) { timer = (timer & 0xFF00) | (value & 0xFF); }

    final int targetHigh() { return (target >> 8) & 0xFF; }

    final int targetLow() { return target & 0xFF; }

    final void setTargetHigh(int value) { target = ((value & 0xFF) << 8) | (target & 0xFF); }

    /** {@code move.w obj.var_38(a0),d0}: the word, signed as {@code cmp.w} reads it. */
    final int targetWord() { return (short) target; }

    // ---- stage file ------------------------------------------------------------------------------------------

    final CdStagePayload payload() {
        var level = cdLevel();
        return level == null ? null : level.payload();
    }

    /** The stage-file offset of a boss table, or -1. */
    final int table(String column) {
        return MmzBossCatalogue.offset(payload(), column);
    }

    final CdStageImage image() { return stageImage(); }

    /** The stage's boss bytes ({@code boss_flags}, {@code boss_started}, {@code boss_door_flag}), or {@code null}. */
    final com.openggf.game.soniccd.zones.mmz.MmzStageSignals signals() {
        return com.openggf.game.soniccd.zones.mmz.MmzStageSignals.of(services().gameModule(), cdLevel());
    }

    /** {@code boss_started}, 0 without a stage. */
    final int bossStarted() {
        var signals = signals();
        return signals == null ? 0 : signals.bossStarted();
    }

    /**
     * {@code move.b #n,boss_started}. {@code bossFight} also drops the player's 56-pixel right extension
     * ({@code PlayerLevelBound}, objects/player.asm:1607), which the shared boundary reads from the screen lock.
     */
    final void setBossStarted(int value) {
        var signals = signals();
        if (signals != null) signals.setBossStarted(value);
        var state = services().gameState();
        if (state != null) state.setScreenLocked((value & 0xFF) != 0);
    }

    // ---- palette RAM -----------------------------------------------------------------------------------------

    /** {@code palette+index*2}: one colour word of the 64-colour palette buffer. */
    final int paletteWord(int index) {
        var level = services().levelManager().getCurrentLevel();
        return PaletteWriteSupport.segaWordFromColor(level.getPalette((index >> 4) & 3).getColor(index & 0xF));
    }

    /** Writes {@code words} to the palette buffer from colour {@code index} on. */
    final void writePaletteWords(int index, int[] words) {
        var level = services().levelManager().getCurrentLevel();
        for (int line = index >> 4; line <= ((index + words.length - 1) >> 4) && line < 4; line++) {
            byte[] bytes = new byte[32];
            var palette = level.getPalette(line);
            for (int c = 0; c < 16; c++) {
                int at = line * 16 + c - index;
                int word = at >= 0 && at < words.length ? words[at]
                        : PaletteWriteSupport.segaWordFromColor(palette.getColor(c));
                bytes[c * 2] = (byte) (word >> 8);
                bytes[c * 2 + 1] = (byte) word;
            }
            services().updatePalette(line, bytes);
        }
    }

    /** {@code SineCosine}: d0 = sine, d1 = cosine of the byte angle. */
    static int sine(int angle) { return com.openggf.physics.TrigLookupTable.sinHex(angle & 0xFF); }

    static int cosine(int angle) { return com.openggf.physics.TrigLookupTable.cosHex(angle & 0xFF); }

    /** True for a routine that ends in {@code CheckObjectDespawn}; boss parts never range-unload otherwise. */
    boolean checksDespawn() { return false; }

    /**
     * CheckObjDespawn2 (Object Spawner.asm:388-399): kept while on screen, else unloaded when the object's 128-pixel
     * column is more than $80+(320+$40)+$80 past the camera's.
     */
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        if (!checksDespawn() || renderFlagOnScreen()) return false;
        int chunk = x & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > 0x80 + (320 + 0x40) + 0x80;
    }

    @Override
    protected String sheetKey() {
        return artProvider() == null ? null : MmzBossArt.key(sheet());
    }

    /** The object's sheet kind ({@link MmzBossArt}). */
    abstract String sheet();
}
