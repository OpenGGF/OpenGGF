package com.openggf.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Builds header-shaped byte arrays for classifier and catalogue tests. These
 * are not ROMs: every byte outside the two title fields is a fill value, so
 * nothing here can be mistaken for game data.
 */
final class SyntheticRomImages {
    static final int S1_SIZE = 0x80000;
    static final int S2_SIZE = 0x100000;
    static final int S3_SIZE = 0x200000;
    static final int SK_SIZE = 0x200000;
    static final int LOCK_ON = 0x200000;

    static final String S1_TITLE = "SONIC THE               HEDGEHOG";
    static final String S2_TITLE = "SONIC THE             HEDGEHOG 2";
    static final String S3_TITLE = "SONIC THE             HEDGEHOG 3";
    static final String SK_TITLE = "SONIC & KNUCKLES";

    private SyntheticRomImages() {
    }

    /** An image of {@code size} bytes filled with {@code fill}, titled at 0 and optionally at 0x200000. */
    static byte[] image(int size, byte fill, String titleAt0, String titleAtLockOn) {
        byte[] data = new byte[size];
        Arrays.fill(data, fill);
        stamp(data, 0, titleAt0);
        if (titleAtLockOn != null) {
            stamp(data, LOCK_ON, titleAtLockOn);
        }
        return data;
    }

    static byte[] image(int size, String titleAt0, String titleAtLockOn) {
        return image(size, (byte) 0, titleAt0, titleAtLockOn);
    }

    static PhysicalImage physical(String name, byte[] data) {
        return PhysicalImage.ofBytes(Path.of("/synthetic", name), data);
    }

    private static void stamp(byte[] data, int base, String title) {
        if (title == null) {
            return;
        }
        byte[] text = title.getBytes(StandardCharsets.US_ASCII);
        byte[] field = new byte[RomHeaderName.NAME_LENGTH];
        Arrays.fill(field, (byte) ' ');
        System.arraycopy(text, 0, field, 0, Math.min(text.length, field.length));
        System.arraycopy(field, 0, data, base + RomHeaderName.DOMESTIC_NAME_OFFSET, field.length);
        System.arraycopy(field, 0, data, base + RomHeaderName.INTERNATIONAL_NAME_OFFSET, field.length);
    }
}
