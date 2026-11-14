package com.openggf.game.soniccd.timeattack;

import com.openggf.game.soniccd.backupram.CdSaveRecord;

/**
 * The Time Attack record arithmetic of {@code Time Attack/Main.asm}. Times are the level's {@code time}
 * long, {@code 00 mm ss ff}, with frames; the menu shows hundredths.
 */
public final class CdTimeAttackRecords {
    private CdTimeAttackRecords() { }

    /**
     * The record insertion at the menu's start (Time Attack/Main.asm:284-350): a time strictly faster than a rank
     * ({@code cmp.l (a2),d0; bge}) moves the slower ones down and takes the place, with the default
     * initials. Returns the rank won, 1 to 3, or 0.
     */
    public static int insert(CdSaveRecord record, int row, int time, int initials) {
        if (time >= record.time(row, 2)) return 0;                        // cmp.l (a2),d0; bge
        record.setTime(row, 2, record.time(row, 1));                       // the third place takes the second
        record.setInitials(row, 2, record.initials(row, 1));
        if (time >= record.time(row, 1)) return place(record, row, 2, time, initials);
        record.setTime(row, 1, record.time(row, 0));                       // the second takes the first
        record.setInitials(row, 1, record.initials(row, 0));
        if (time >= record.time(row, 0)) return place(record, row, 1, time, initials);
        return place(record, row, 0, time, initials);
    }

    private static int place(CdSaveRecord record, int row, int rank, int time, int initials) {
        record.setTime(row, rank, time);
        record.setInitials(row, rank, initials);
        return rank + 1;
    }

    /** NormalizeTimeFrames (Time Attack/Main.asm:1504-1518): frames to hundredths, {@code ff * 100 / 60}. */
    public static int normalize(int time) {
        int frames = time & 0xFF;
        return (time & 0xFFFFFF00) | ((frames * 100 / 60) & 0xFF);
    }

    /** AddTimeValue (Time Attack/Main.asm:1521-1559): byte-wise add with carries at 100 hundredths and 60 seconds. */
    public static int add(int total, int delta) {
        int cs = (delta & 0xFF) + (total & 0xFF);
        int sec = ((total >> 8) & 0xFF) + cs / 100;
        cs %= 100;
        sec = (sec & 0xFF) + ((delta >> 8) & 0xFF);
        int min = ((total >> 16) & 0xFF) + sec / 60;
        sec %= 60;
        min = (min + ((delta >> 16) & 0xFF)) & 0xFF;
        return (total & 0xFF000000) | (min << 16) | (sec << 8) | cs;
    }

    /**
     * The title-menu unlocks PrepareTimeAttackRecords grants once all zones are open (Time Attack/Main.asm:1445-1465):
     * total below 25'46"12 sets bit 2, below 30'21"05 bit 1, below 37'27"57 bit 3 ({@code cmpi.l; bge}).
     */
    public static int unlocks(int titleFlags, int total) {
        int flags = titleFlags;
        if (total < 0x192E0C) flags |= 0x04;
        if (total < 0x1E1505) flags |= 0x02;
        if (total < 0x251B39) flags |= 0x08;
        return flags & 0xFF;
    }
}
