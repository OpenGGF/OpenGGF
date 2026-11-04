package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdR11bLoadPlan.Bounds;
import com.openggf.game.soniccd.level.CdR11bLoadPlan.InitialCentre;
import com.openggf.game.soniccd.level.CdR11bLoadPlan.LevelIndex;
import com.openggf.game.soniccd.level.CdR11bLoadPlan.TaggedPointer;

import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;

/**
 * Detached R12A static terrain prerequisites. Reuses existing immutable native-format value records;
 * no R11B decoder or Past admission is invoked. N1ecc5f3a Load Level Data.asm:11-29,57-98,
 * Scroll (Act 2 Present).asm:21-35,44-45,139-140 and Palette Data (Act 2 Present).asm:7-25.
 * PLC tag bytes remain metadata; no PLC list, queue, current art or player lifecycle is created.
 */
public final class CdR12aLoadPlan {
    public record PaletteBinding(int nativeId, int destinationWord, int longwordCounter,
                                 CdR12aProfile.Asset source, CdAssetBytes bytes) {
        public PaletteBinding {
            Objects.requireNonNull(source); Objects.requireNonNull(bytes);
            boolean sonic = nativeId == 3 && source == PAL_SONIC && destinationWord == 0xFB00 && longwordCounter == 7;
            boolean level = nativeId == 5 && source == PAL_LEVEL && destinationWord == 0xFB20 && longwordCounter == 23;
            if ((!sonic && !level) || bytes.length() != source.length())
                throw new IllegalArgumentException("R12A palette source/count/destination differs");
        }
        public int colorCount() { return bytes.length() / 2; }
    }
    private final LevelIndex index;
    private final InitialCentre centre;
    private final Bounds bounds;
    private final List<PaletteBinding> palettes;
    private final CdAssetBytes palette;
    private CdR12aLoadPlan(LevelIndex index, InitialCentre centre, Bounds bounds, List<PaletteBinding> palettes, byte[] palette) {
        this.index = index; this.centre = centre; this.bounds = bounds; this.palettes = List.copyOf(palettes);
        this.palette = CdAssetBytes.copyOf(palette);
    }
    /** Component decoding is not media authentication; the asset factory separately verifies the whole original file. */
    public static CdR12aLoadPlan decode(CdR12aProfile profile, CdMmdImage image) {
        Objects.requireNonNull(profile); Objects.requireNonNull(image);
        if (!CdR12aProfile.originalShape(image)) throw new IllegalArgumentException("R12A metadata requires its original finite image shape");
        var raw = profile.range(LEVEL_DATA_INDEX).read(image);
        var art = new TaggedPointer(raw.unsignedLong(0)); var blocks = new TaggedPointer(raw.unsignedLong(4));
        if (art.plcId() != 3 || art.nativeAddress() != ART_LEVEL_TILES.nativeAddress()
                || blocks.plcId() != 2 || blocks.nativeAddress() != LEVEL_BLOCKS.nativeAddress()
                || raw.unsignedLong(8) != LEVEL_CHUNKS.nativeAddress() || raw.unsignedWord(14) != 0x0505)
            throw new IllegalArgumentException("R12A index tags, pointers or palette IDs differ");
        if (raw.unsignedWord(12) != 0x0081) throw new UnsupportedOperationException("Unreviewed R12A trailing index word");
        var index = new LevelIndex(art, blocks, LEVEL_CHUNKS.nativeAddress(), raw.slice(12, 2), raw.slice(14, 2));
        var position = profile.range(LEVEL_START_LOC).read(image);
        var centre = new InitialCentre(position.unsignedWord(0), position.unsignedWord(2));
        var size = profile.range(CAM_BOUNDS).read(image);
        var bounds = new Bounds(size.unsignedWord(0), size.unsignedWord(2), size.unsignedWord(4),
                size.unsignedWord(6), size.unsignedWord(8), size.unsignedWord(10));
        var layouts = profile.range(LEVEL_LAYOUTS).read(image); int base = LEVEL_LAYOUTS.fileOffset();
        if (base + (short) layouts.unsignedWord(0) != LAYOUT_FG.fileOffset()
                || base + (short) layouts.unsignedWord(2) != LAYOUT_BG.fileOffset())
            throw new IllegalArgumentException("R12A native layout pointers differ");
        var collision = profile.range(LEVEL_COL_INDEX).read(image);
        for (int i = 0; i < 8; i++) if (collision.unsignedLong(i * 4) != LEVEL_COLLISION.nativeAddress())
            throw new IllegalArgumentException("R12A native collision pointer differs");
        var table = profile.range(PALETTE_TABLE).read(image);
        var sonic = palette(profile, image, table, 3, PAL_SONIC, 0xFB00, 7);
        var level = palette(profile, image, table, 5, PAL_LEVEL, 0xFB20, 23);
        byte[] colors = new byte[128]; System.arraycopy(sonic.bytes().copy(), 0, colors, 0, 32);
        System.arraycopy(level.bytes().copy(), 0, colors, 32, 96);
        return new CdR12aLoadPlan(index, centre, bounds, List.of(sonic, level), colors);
    }
    private static PaletteBinding palette(CdR12aProfile profile, CdMmdImage image, CdAssetBytes table,
                                           int id, CdR12aProfile.Asset source, int destination, int count) {
        int row = id * 8;
        if (table.unsignedLong(row) != source.nativeAddress() || table.unsignedWord(row + 4) != destination
                || table.unsignedWord(row + 6) != count) throw new IllegalArgumentException("R12A palette table binding differs");
        return new PaletteBinding(id, destination, count, source, profile.range(source).read(image));
    }
    public LevelIndex levelIndex() { return index; }
    public InitialCentre initialCentre() { return centre; }
    public Bounds bounds() { return bounds; }
    public List<PaletteBinding> paletteBindings() { return palettes; }
    public CdAssetBytes initialPalette() { return palette; }
    public int colorWord(int index) { Objects.checkIndex(index, 64); return palette.unsignedWord(index * 2); }
}
