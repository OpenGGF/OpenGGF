package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectServices;

import java.io.IOException;

/**
 * Doomsday end boss palette writes: the {@code sub_82D72} flash rows, {@code DecColor_Obj},
 * the {@code Pal_DDZ} line 3 reload, the {@code Normal_palette -> Target_palette} copy and the
 * {@code sub_85EB4} / {@code sub_85F2A} whiten and restore steps. Words are 9-bit Mega Drive colours
 * ({@code 0000 BBB0 GGG0 RRR0}); palette line {@code n} of the ROM is engine palette {@code n - 1}.
 */
final class DdzPalette {
    private static final String OWNER = "s3k.ddz.endBoss";

    private DdzPalette() {
    }

    /** {@code sub_82D72} with {@code d0 = row * $18}: twelve colours of palette line 3. */
    static void applyFlashRow(ObjectServices services, int row) {
        try {
            var rom=services.rom();
            for(int i=0;i<12;i++) {
                int destination=rom.read16BitAddr(Sonic3kConstants.PAL_DDZ_BOSS_FLASH_DESTINATIONS_ADDR+i*2);
                int color=(destination-0xFC00)/2;
                S3kPaletteWriteSupport.applyContiguousPatch(services.paletteOwnershipRegistryOrNull(),
                        services.currentLevel(),services.graphicsManager(),OWNER,S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                        color/16,color%16,rom.readBytes(Sonic3kConstants.PAL_DDZ_BOSS_FLASH_COLORS_ADDR+row*24+i*2,2));
            }
        } catch(IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** Sixteen {@code DecColor_Obj} calls with {@code d1 = $E}, {@code d2 = $E0} on one line. */
    static void decreaseLine(ObjectServices services, int line) {
        int[] words = readLine(services, line);
        for (int i = 0; i < words.length; i++) {
            int blue = (words[i] >> 8) & 0xE;
            if (blue != 0) {
                blue -= 2;
            }
            int green = words[i] & 0xE0;
            if (green != 0) {
                green -= 0x20;
            }
            int red = words[i] & 0xE;
            if (red != 0) {
                red -= 2;
            }
            words[i] = (blue << 8) | green | red;
        }
        writeLine(services, line, words);
    }

    /** {@code lea (Pal_DDZ+$20).l,a1 / lea (Normal_palette_line_3).w,a2}: 32 bytes. */
    static void loadDdzLine3(ObjectServices services) {
        try {
            byte[] bytes = services.romReader().slice(Sonic3kConstants.PAL_DDZ_ADDR + 0x20, Palette.PALETTE_SIZE_IN_ROM);
            S3kPaletteWriteSupport.applyContiguousPatch(services.paletteOwnershipRegistryOrNull(),
                    services.currentLevel(), services.graphicsManager(), OWNER,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 2, 0, bytes);
            // The ROM writes Normal_palette_line_3 in RAM before loc_819EA copies Normal_palette to
            // Target_palette. Resolve now so that copy (and the flash fading back to it) sees the reloaded
            // line instead of the three DecColor_Obj passes from the fall.
            var registry = services.paletteOwnershipRegistryOrNull();
            if (registry != null) {
                S3kPaletteWriteSupport.resolvePendingWritesNow(registry, services.currentLevel(),
                        services.graphicsManager());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Pal_DDZ", ex);
        }
    }

    /** {@code Normal_palette -> Target_palette}, all four lines. */
    static void copyNormalToTarget(ObjectServices services) {
        var registry = services.paletteOwnershipRegistryOrNull();
        if (registry == null) {
            return;
        }
        for (int line = 0; line < 4; line++) {
            registry.applyTargetPatch(OWNER, line, 0, toBytes(readLine(services, line)));
        }
    }

    /** Sixty-four {@code sub_85EB4} calls: every colour one step towards white. */
    static void whitenAll(ObjectServices services) {
        for (int line = 0; line < 4; line++) {
            int[] words = readLine(services, line);
            for (int i = 0; i < words.length; i++) {
                int blue = (words[i] >> 8) & 0xE;
                if (blue < 0xE) {
                    blue += 2;
                }
                int green = words[i] & 0xE0;
                if (green < 0xE0) {
                    green += 0x20;
                }
                int red = words[i] & 0xE;
                if (red < 0xE) {
                    red += 2;
                }
                words[i] = (blue << 8) | green | red;
            }
            writeLine(services, line, words);
        }
    }

    /** Sixty-four {@code sub_85F2A} calls: every colour one step down towards {@code Target_palette}. */
    static void fadeTowardsTarget(ObjectServices services) {
        var registry = services.paletteOwnershipRegistryOrNull();
        if (registry == null) {
            return;
        }
        for (int line = 0; line < 4; line++) {
            int[] words = readLine(services, line);
            byte[] target = registry.targetSegaData(line, 0, 16);
            for (int i = 0; i < words.length; i++) {
                int targetWord = ((target[i * 2] & 0xFF) << 8) | (target[i * 2 + 1] & 0xFF);
                int blue = (words[i] >> 8) & 0xE;
                if (blue > ((targetWord >> 8) & 0xE)) {
                    blue -= 2;
                }
                int green = words[i] & 0xE0;
                if (green > (targetWord & 0xE0)) {
                    green -= 0x20;
                }
                int red = words[i] & 0xE;
                if (red > (targetWord & 0xE)) {
                    red -= 2;
                }
                words[i] = (blue << 8) | green | red;
            }
            writeLine(services, line, words);
        }
    }

    static int[] readLine(ObjectServices services, int line) {
        int[] words = new int[16];
        var level = services.currentLevel();
        Palette palette = level == null ? null : level.getPalette(line);
        if (palette == null) {
            return words;
        }
        for (int i = 0; i < 16; i++) {
            Palette.Color colour = palette.getColor(i);
            words[i] = (component(colour.b) << 9) | (component(colour.g) << 5) | (component(colour.r) << 1);
        }
        return words;
    }

    /** Inverse of {@code Palette.Color.fromSegaFormat}'s 0-7 to 0-255 scaling. */
    private static int component(byte value) {
        return ((value & 0xFF) * 7 + 127) / 255;
    }

    private static void writeLine(ObjectServices services, int line, int[] words) {
        S3kPaletteWriteSupport.applyContiguousPatch(services.paletteOwnershipRegistryOrNull(), services.currentLevel(),
                services.graphicsManager(), OWNER, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, line, 0, toBytes(words));
    }

    private static byte[] toBytes(int[] words) {
        byte[] bytes = new byte[words.length * 2];
        for (int i = 0; i < words.length; i++) {
            bytes[i * 2] = (byte) (words[i] >> 8);
            bytes[i * 2 + 1] = (byte) words[i];
        }
        return bytes;
    }
}
