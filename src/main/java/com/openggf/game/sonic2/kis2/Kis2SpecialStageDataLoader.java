package com.openggf.game.sonic2.kis2;

import com.openggf.game.sonic2.objects.Sonic2SpecialStageResultsMappings;
import com.openggf.game.sonic2.objects.Sonic2SpecialStageResultsMappings.ResultsPiece;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageDataLoader;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStagePalette;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSpriteMappings.SpriteFrame;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSpriteMappings.SpritePiece;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.util.PatternDecompressor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * KiS2 special-stage assets from the lock-on chip. Addresses verified against
 * the bit-perfect gameRevision=3, fixBugs=0 assembly and the supplied chip.
 * The inherited S2 loader owns unchanged track, object, sound and sidekick data.
 */
public final class Kis2SpecialStageDataLoader extends Sonic2SpecialStageDataLoader {
    static final int PLAYER_ART = 0x33B3F0; // ArtNem_SpecialSonicAndTails
    static final int PLAYER_ART_SIZE = 6892;
    static final int PLAYER_MAPPINGS = 0x32D410; // Obj09_MapUnc_34212
    static final int PLAYER_DPLC = 0x32D728; // Obj09_MapRUnc_345FA
    static final int MAIN_PALETTE = 0x302CBE; // Pal_SS
    static final int RING_REQUIREMENTS = 0x3071D2; // SpecialStage_RingReq_Alone
    private static final int FRAME_COUNT = 18;

    private final LockOnAddressSpace addressSpace;
    private Pattern[] playerPatterns;
    private SpriteFrame[] frames;
    private List<SpriteDplcFrame> knucklesPlans;

    public Kis2SpecialStageDataLoader(Rom sonic2Rom, LockOnAddressSpace addressSpace) {
        super(sonic2Rom);
        if (!addressSpace.hasChip()) {
            throw new IllegalArgumentException("KiS2 special stages require the chip window");
        }
        this.addressSpace = addressSpace;
    }

    @Override
    public byte[] getRingRequirementsSolo() {
        return bytes(RING_REQUIREMENTS, 28);
    }

    /** KiS2 removes SpecialStage_RingReq_Team; configured sidekicks share the solo target. */
    @Override
    public byte[] getRingRequirementsTeam() {
        return getRingRequirementsSolo();
    }

    @Override
    public Palette[] getPalettes(int stageIndex) {
        Palette[] palettes = super.getPalettes(stageIndex);
        byte[] main = bytes(MAIN_PALETTE, 96);
        for (int line = 0; line < 3; line++) {
            for (int color = 0; color < 16; color++) {
                int offset = line * 32 + color * 2;
                int word = (main[offset] & 0xFF) << 8 | (main[offset + 1] & 0xFF);
                palettes[line].setColor(color, Sonic2SpecialStagePalette.genesisColorToPaletteColor(word));
            }
        }
        return palettes;
    }

    /** Obj61_Init moves bombs to line 2 because Knuckles occupies line 1. */
    @Override
    public int getBombPaletteLine() { return 2; }

    /** Obj5E_MapUnc_7070 frame 0 removes the SONIC name and keeps RINGS. */
    @Override
    public boolean showMainPlayerHudName() { return false; }

    @Override
    public Pattern[] getPlayerArtPatterns() throws IOException {
        if (playerPatterns == null) {
            Pattern[] stock = super.getPlayerArtPatterns();
            Pattern[] knuckles;
            try (var channel = Channels.newChannel(new ByteArrayInputStream(bytes(PLAYER_ART, PLAYER_ART_SIZE)))) {
                knuckles = PatternDecompressor.fromBytes(NemesisReader.decompress(channel));
            }
            // Preserve stock Tails source indices for the optional configured sidekick.
            playerPatterns = Arrays.copyOf(stock, stock.length + knuckles.length);
            System.arraycopy(knuckles, 0, playerPatterns, stock.length, knuckles.length);
        }
        return playerPatterns;
    }

    @Override
    public PlayerDplcPlans getPlayerDplcPlans() throws IOException {
        decodeFrames();
        PlayerDplcPlans stock = super.getPlayerDplcPlans();
        return new PlayerDplcPlans(knucklesPlans, stock.tails(), stock.tailsTails());
    }

    @Override
    public SpriteFrame getMainPlayerFrame(int frame) {
        try {
            decodeFrames();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load KiS2 special-stage mappings", e);
        }
        return frames[Math.max(0, Math.min(frame, FRAME_COUNT - 1))];
    }

    private void decodeFrames() throws IOException {
        if (frames != null) return;
        LockOnAddressSpace.Read mappingRead = addressSpace.require(PLAYER_MAPPINGS);
        LockOnAddressSpace.Read dplcRead = addressSpace.require(PLAYER_DPLC);
        RomByteReader mappings = mappingRead.reader();
        RomByteReader dplc = dplcRead.reader();
        int mappingBase = mappingRead.localAddress();
        int dplcBase = dplcRead.localAddress();
        int rendererArtBase = super.getPlayerArtPatterns().length;
        List<SpriteDplcFrame> plans = new ArrayList<>(FRAME_COUNT);
        SpriteFrame[] decoded = new SpriteFrame[FRAME_COUNT];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int cue = dplcBase + dplc.readU16BE(dplcBase + frame * 2);
            int cueCount = dplc.readU16BE(cue);
            List<TileLoadRequest> requests = new ArrayList<>(cueCount);
            List<Integer> sourceTiles = new ArrayList<>();
            for (int entry = 0; entry < cueCount; entry++) {
                // LoadSSSonicDynPLC uses standard count/source format in KiS2,
                // unlike stock S2's pre-shifted custom DPLC and section pointers.
                int word = dplc.readU16BE(cue + 2 + entry * 2);
                int count = (word >>> 12) + 1;
                int source = word & 0xFFF;
                requests.add(new TileLoadRequest(source, count));
                for (int tile = 0; tile < count; tile++) sourceTiles.add(source + tile);
            }
            plans.add(new SpriteDplcFrame(List.copyOf(requests)));
            int mapping = mappingBase + mappings.readU16BE(mappingBase + frame * 2);
            int pieceCount = mappings.readU16BE(mapping);
            List<SpritePiece> pieces = new ArrayList<>();
            for (int piece = 0; piece < pieceCount; piece++) {
                int offset = mapping + 2 + piece * 6;
                int y = (byte) mappings.readU8(offset);
                int size = mappings.readU8(offset + 1);
                int tileWord = mappings.readU16BE(offset + 2);
                int x = (short) mappings.readU16BE(offset + 4);
                int width = ((size >>> 2) & 3) + 1;
                int height = (size & 3) + 1;
                int firstTile = tileWord & 0x7FF;
                // Resolve each destination tile independently: a mapping piece
                // may span multiple DPLC requests with noncontiguous sources.
                for (int column = 0; column < width; column++) {
                    for (int row = 0; row < height; row++) {
                        boolean hFlip = (tileWord & 0x800) != 0;
                        boolean vFlip = (tileWord & 0x1000) != 0;
                        int sourceColumn = hFlip ? width - 1 - column : column;
                        int sourceRow = vFlip ? height - 1 - row : row;
                        int source = sourceTiles.get(firstTile + sourceColumn * height + sourceRow);
                        pieces.add(new SpritePiece(x + column * 8, y + row * 8, 1, 1,
                                rendererArtBase + source, hFlip, vFlip));
                    }
                }
            }
            decoded[frame] = new SpriteFrame(pieces.toArray(SpritePiece[]::new));
        }
        knucklesPlans = List.copyOf(plans);
        frames = decoded;
    }

    /** Obj6F mappings retain stock frame IDs, including the new frame $1D name. */
    @Override
    public ResultsPiece[] getResultsFrame(int frame) {
        if (frame < 0 || frame >= 30) return new ResultsPiece[0];
        List<ResultsPiece> pieces = new ArrayList<>();
        int offset = frame == 1 ? 80 : frame == 22 ? 68 : 0;
        readResultsFrame(frame, offset, pieces);
        // Obj6F_Knuckles is a separate object with the same 288-pixel slide.
        // On seven emeralds its origin moves left 44 and the other title left 12.
        if (frame == 1 || frame == 22) readResultsFrame(29, frame == 1 ? 8 : -36, pieces);
        return pieces.toArray(ResultsPiece[]::new);
    }

    private void readResultsFrame(int frame, int xOffset, List<ResultsPiece> pieces) {
        LockOnAddressSpace.Read read = addressSpace.require(0x311D22); // Obj6F_MapUnc_14ED0
        RomByteReader reader = read.reader();
        int base = read.localAddress();
        int record = base + reader.readU16BE(base + frame * 2);
        int count = reader.readU16BE(record);
        for (int i = 0; i < count; i++) {
            int pos = record + 2 + i * 6;
            int size = reader.readU8(pos + 1);
            int word = reader.readU16BE(pos + 2);
            int tile = word & 0x7FF;
            int type;
            int artBase;
            if (tile >= 0x6CA) { type = Sonic2SpecialStageResultsMappings.ART_TYPE_HUD; artBase = 0x6CA; }
            else if (tile >= 0x590) { type = Sonic2SpecialStageResultsMappings.ART_TYPE_RESULTS; artBase = 0x590; }
            else if (tile >= 0x580) { type = Sonic2SpecialStageResultsMappings.ART_TYPE_TITLE_CARD; artBase = 0x580; }
            else if (tile >= 0x520) { type = Sonic2SpecialStageResultsMappings.ART_TYPE_NUMBERS; artBase = 0x520; }
            else { type = Sonic2SpecialStageResultsMappings.ART_TYPE_SS_LETTERS; artBase = 2; }
            pieces.add(new ResultsPiece((short) reader.readU16BE(pos + 4) + xOffset,
                    (byte) reader.readU8(pos), ((size >>> 2) & 3) + 1, (size & 3) + 1,
                    tile - artBase, (word & 0x800) != 0, (word & 0x1000) != 0,
                    (word >>> 13) & 3, type));
        }
    }

    @Override
    public void patchResultsPatterns(Pattern[] patterns, int vramBase, Pattern[] titleCard2) {
        // LoadTitleCardSS reads SpecialStage_ResultsLetters, which appends K.
        // N already resides in the inherited title-card art at VRAM $584.
        byte[] letters = bytes(0x307284, 34);
        int destination = 2 - vramBase;
        for (int i = 0; i < letters.length; i += 2) {
            int source = letters[i] & 0xFF;
            if (source >= 0x80) break;
            int count = letters[i + 1] & 0xFF;
            for (int tile = 0; tile < count; tile++) patterns[destination++] = titleCard2[source + tile];
        }
    }

    private byte[] bytes(int address, int length) {
        LockOnAddressSpace.Read read = addressSpace.require(address);
        return read.reader().slice(read.localAddress(), length);
    }
}
