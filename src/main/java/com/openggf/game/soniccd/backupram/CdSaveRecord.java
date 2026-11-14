package com.openggf.game.soniccd.backupram;

import java.util.Arrays;

/**
 * The game's record codec: the {@code SONICCD____} file exactly as the game writes it, decoded into named
 * fields and encoded back without touching any byte it does not name. Layout ({@code _Include/Backup
 * RAM.inc:36-55}, sizes from {@code backupram-assets.tsv}): 84 Time Attack times, 84 initials, the default
 * initials, then the main data ({@code svZone} .. {@code svTimeStones}). The file is
 * {@code BRMWRITE}'s 11 blocks of 64 bytes ({@code BuRAMWriteParams}, SPX.asm:289-292), 704 bytes, so the last
 * two bytes of the 706-byte {@code BURAMDATASZ} structure are never stored
 * ({@code WriteTempSaveData}'s {@code BURAMDATASZ/4-1} long copy, Backup RAM/Sub.asm:521-537, agrees).
 *
 * <p>Times are the level's {@code time} long ({@code 00 mm ss cc}); initials are three character codes and
 * a zero. Row {@code n} of the tables is Time Attack selection {@code n+1}: 21 stage rows (zone, act) then
 * seven special-stage rows, three ranks each ({@code TimeAttack_StartRecordSourceBase},
 * Time Attack/Main.asm:286-302).
 */
public final class CdSaveRecord {
    public static final int FILE_BLOCKS = 0x0B;                          // BuRAMWriteParams dc.w $B
    public static final int FILE_BYTES = FILE_BLOCKS * CdBackupRamVolume.BLOCK;
    public static final byte[] FILE_NAME = CdBackupRamFile.name("SONICCD____");   // BuRAMWriteParams (SPX.asm:289-290)
    public static final int ROWS = 7 * 3 + 7;                            // svTmAtkTimes rs.l (7*3*3)+(7*3)
    public static final int RANKS = 3;

    private static final int TIMES = CdBackupRamAssets.layout("svTmAtkTimes");
    private static final int NAMES = CdBackupRamAssets.layout("svTmAtkNames");
    private static final int DEFAULT_NAME = CdBackupRamAssets.layout("svTmAtkDefName");
    private static final int ZONE = CdBackupRamAssets.layout("svZone");
    private static final int TA_UNLOCK = CdBackupRamAssets.layout("svTmAtkUnlock");
    private static final int UNKNOWN = CdBackupRamAssets.layout("svUnknown");
    private static final int GOOD_FUTURES = CdBackupRamAssets.layout("svGoodFutures");
    private static final int TITLE_FLAGS = CdBackupRamAssets.layout("svTitleFlags");
    private static final int SPEC_STAGE = CdBackupRamAssets.layout("svSpecStage");
    private static final int TIME_STONES = CdBackupRamAssets.layout("svTimeStones");

    private final byte[] bytes;

    private CdSaveRecord(byte[] bytes) { this.bytes = bytes; }

    public static CdSaveRecord decode(byte[] file) {
        if (file.length != FILE_BYTES) throw new IllegalArgumentException("The save file is " + FILE_BYTES + " bytes");
        return new CdSaveRecord(file.clone());
    }

    public byte[] encode() { return bytes.clone(); }

    public CdSaveRecord copy() { return new CdSaveRecord(bytes.clone()); }

    public int time(int row, int rank) { return u32(TIMES + 4 * (row * RANKS + rank)); }

    public int initials(int row, int rank) { return u32(NAMES + 4 * (row * RANKS + rank)); }

    public int defaultInitials() { return u32(DEFAULT_NAME); }

    public void setTime(int row, int rank, int value) { put32(TIMES + 4 * (row * RANKS + rank), value); }

    public void setInitials(int row, int rank, int value) { put32(NAMES + 4 * (row * RANKS + rank), value); }

    public void setDefaultInitials(int value) { put32(DEFAULT_NAME, value); }

    /** {@code ReadSaveData} (IPX.asm:1420-1432). */
    public CdSaveVariables variables() {
        return new CdSaveVariables(((bytes[ZONE] & 0xFF) << 8) | (bytes[ZONE + 1] & 0xFF), u8(GOOD_FUTURES),
                u8(TITLE_FLAGS), u8(TA_UNLOCK), u8(UNKNOWN), u8(SPEC_STAGE), u8(TIME_STONES));
    }

    /** {@code WriteSaveData}'s stores, in order (IPX.asm:1452-1460): the word store, then the bytes over it. */
    public void store(CdSaveVariables v) {
        bytes[ZONE] = (byte) (v.savedLevel() >> 8);
        bytes[ZONE + 1] = (byte) v.savedLevel();
        bytes[GOOD_FUTURES] = (byte) v.goodFutures();
        bytes[TITLE_FLAGS] = (byte) v.titleFlags();
        bytes[TA_UNLOCK] = (byte) v.timeAttackUnlock();
        bytes[UNKNOWN] = (byte) v.unknown();
        bytes[SPEC_STAGE] = (byte) v.curSpecStage();
        bytes[TIME_STONES] = (byte) v.timeStones();
    }

    private int u8(int at) { return bytes[at] & 0xFF; }

    private int u32(int at) {
        return ((bytes[at] & 0xFF) << 24) | ((bytes[at + 1] & 0xFF) << 16) | ((bytes[at + 2] & 0xFF) << 8) | (bytes[at + 3] & 0xFF);
    }

    private void put32(int at, int value) {
        bytes[at] = (byte) (value >>> 24);
        bytes[at + 1] = (byte) (value >>> 16);
        bytes[at + 2] = (byte) (value >>> 8);
        bytes[at + 3] = (byte) value;
    }

    @Override public boolean equals(Object o) { return o instanceof CdSaveRecord r && Arrays.equals(bytes, r.bytes); }

    @Override public int hashCode() { return Arrays.hashCode(bytes); }
}
