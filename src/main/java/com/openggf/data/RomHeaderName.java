package com.openggf.data;

import java.util.List;
import java.util.Locale;

/**
 * Game family named by a Mega Drive ROM header title, as read at offset
 * {@code 0x120} (domestic) or {@code 0x150} (international) of a cartridge
 * header. Lock-on dumps carry a second header at {@code 0x200000}.
 *
 * <p>Classification is a lookup table over normalised titles; the order of
 * the rows matters because the Sonic 1 title is a prefix of the sequels.
 */
public enum RomHeaderName {
    S1,
    S2,
    S3,
    SK,
    UNKNOWN;

    /** Offset of the domestic title inside a cartridge header. */
    public static final int DOMESTIC_NAME_OFFSET = 0x120;
    /** Offset of the international title inside a cartridge header. */
    public static final int INTERNATIONAL_NAME_OFFSET = 0x150;
    /** Length of each title field. */
    public static final int NAME_LENGTH = 48;

    private record Row(RomHeaderName name, String needle) {
    }

    private static final List<Row> TABLE = List.of(
            new Row(S2, "SONIC THE HEDGEHOG 2"),
            new Row(S3, "SONIC THE HEDGEHOG 3"),
            new Row(SK, "SONIC & KNUCKLES"),
            new Row(SK, "SONIC AND KNUCKLES"),
            new Row(S1, "SONIC THE HEDGEHOG"));

    /** Classifies a raw header title; whitespace runs and case are ignored. */
    public static RomHeaderName of(String rawTitle) {
        if (rawTitle == null) {
            return UNKNOWN;
        }
        String normalized = rawTitle.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        for (Row row : TABLE) {
            if (normalized.contains(row.needle())) {
                return row.name();
            }
        }
        return UNKNOWN;
    }

    /** Classifies whichever of the two title fields is recognised first. */
    public static RomHeaderName of(String domesticTitle, String internationalTitle) {
        RomHeaderName domestic = of(domesticTitle);
        return domestic != UNKNOWN ? domestic : of(internationalTitle);
    }
}
