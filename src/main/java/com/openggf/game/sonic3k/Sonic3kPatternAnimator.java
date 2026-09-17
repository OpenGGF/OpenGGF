package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameServices;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.animation.ChannelContext;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.CnzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.IczZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AniPlcParser;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.data.compression.KosinskiReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Animates S3K zone tiles using the ROM's AniPLC script format plus the direct
 * HCZ background DMA updates that sit alongside AniPLC in the original engine.
 */
class Sonic3kPatternAnimator implements AnimatedPatternManager,
        com.openggf.game.rewind.RewindSnapshottable<com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot> {
    private static final Logger LOG = Logger.getLogger(Sonic3kPatternAnimator.class.getName());

    /** Scalar rewind blob: 1 flag byte plus 15 ints. */
    private static final int SCALAR_BLOB_BYTES = 61;

    private static final int HCZ1_WATERLINE_VISIBLE = 0x60;
    private static final int HCZ1_EQUILIBRIUM_Y = 0x610;

    private static final int[] HCZ2_DEFORM_INDEX = {
            3, 0x0A, 0x14, 0x1E, 0x2C,
            2, 0x0C, 0x16, 0x20,
            5, 0x00, 0x08, 0x0E, 0x18, 0x22, 0x2A,
            3, 0x02, 0x10, 0x1A, 0x24,
            1, 0x12, 0x1C,
            1, 0x06, 0x28,
            1, 0x04, 0x26,
            0xFF
    };
    private static final int[] HCZ2_SMALL_BG_LINE_WORD_COUNTS = {
            0x40, 0x00, 0x30, 0x10, 0x20, 0x20, 0x10, 0x30
    };
    private static final int[] HCZ2_ART2_WORD_COUNTS = {
            0x80, 0x00, 0x60, 0x20, 0x40, 0x40, 0x20, 0x60
    };
    private static final int[] HCZ2_ART3_WORD_COUNTS = {
            0x100, 0x00, 0xC0, 0x40, 0x80, 0x80, 0x40, 0xC0
    };
    private static final int[] HCZ2_ART4_WORD_COUNTS = {
            0x300, 0x00, 0x2A0, 0x60, 0x240, 0xC0, 0x1E0, 0x120,
            0x180, 0x180, 0x120, 0x1E0, 0xC0, 0x240, 0x60, 0x2A0
    };
    private static final int PACHINKO_BG_DEST_TILE = 0x0E9;
    private static final int PACHINKO_BG_DMA_BYTES = 0x780;
    private static final int PACHINKO_LOW_SOURCE_TILE = 0x080;
    private static final int PACHINKO_LOW_SOURCE_BYTES = 0x5000;
    private static final int PACHINKO_HIGH_SOURCE_TILE = 0x300;
    private static final int PACHINKO_HIGH_SOURCE_BYTES = 0x0C00;
    // Obj_CNZHoverFan maps its blade piece to this AniPLC destination.
    private static final int CNZ_HOVER_FAN_BLADE_DEST_TILE = 0x304;
    private static final int CNZ_HOVER_FAN_BLADE_TILE_COUNT = 4;
    private static final int[] PACHINKO_COPY_OFFSETS = {
            0x180, 0x120, 0x0C0, 0x060, 0x400, 0x3A0, 0x340, 0x2E0, 0x680, 0x620, 0x5C0, 0x560
    };
    private static final int[] PACHINKO_MIX_OFFSETS = {
            0x400, 0x3A0, 0x340, 0x2E0, 0x680, 0x620, 0x5C0, 0x560, 0x180, 0x120, 0x0C0, 0x060
    };
    private static final int[] PACHINKO_PHASE_PARAMS = {
            0x50, 0x05, 0xA0, 0x0A,
            0x00, 0x00, 0x00, 0x00,
            0xA0, 0x0A, 0x50, 0x05
    };
    private static final int[] CNZ_DMA_WORD_COUNTS = {
            0x200, 0x000,
            0x1C0, 0x040,
            0x180, 0x080,
            0x140, 0x0C0,
            0x100, 0x100,
            0x0C0, 0x140,
            0x080, 0x180,
            0x040, 0x1C0
    };
    private static final int[] MHZ_BG1_DMA_WORD_COUNTS = {
            0x080, 0x000,
            0x060, 0x020,
            0x040, 0x040,
            0x020, 0x060
    };
    private static final int[] MHZ_BG2_DMA_WORD_COUNTS = {
            0x200, 0x000,
            0x1C0, 0x040,
            0x180, 0x080,
            0x140, 0x0C0,
            0x100, 0x100,
            0x0C0, 0x140,
            0x080, 0x180,
            0x040, 0x1C0
    };
    private static final int[] ICZ_HORIZONTAL_DMA_WORD_COUNTS = {
            0x100, 0x000,
            0x0C0, 0x040,
            0x080, 0x080,
            0x040, 0x0C0
    };
    private static final int[] LBZ1_SCROLL_WORD_COUNTS = {
            0x140, 0x000,
            0x0F0, 0x050,
            0x0A0, 0x0A0,
            0x050, 0x0F0
    };
    private static final int ANIPLC_LRZ1_ADDR = 0x028A6A;
    /** {@code AniPLC_LRZ2} (sonic3k.asm:56022); {@code Offs_AniFunc} pairs it with {@code $901}. */
    private static final int ANIPLC_LRZ2_ADDR = 0x028A84;
    private static final int ART_UNC_ANI_SOZ1_BG_ADDR = 0x0BD9C0;
    private static final int ART_UNC_ANI_SOZ1_BG_SIZE = 0x0C00;
    private static final int ART_UNC_ANI_SOZ1_BG2_ADDR = 0x0BE5C0;
    private static final int ART_UNC_ANI_SOZ1_BG2_SIZE = 0x1800;
    private static final int[] SOZ1_SPLIT_WORD_COUNTS = {
            0x0C0, 0x000,
            0x090, 0x030,
            0x060, 0x060,
            0x030, 0x090
    };

    /** {@code ArtUnc_AniLRZ__BG} (sonic3k.lst: $C0300): eight $480-byte background frames. */
    private static final int ART_UNC_ANI_LRZ_BG_ADDR = 0x0C0300;
    private static final int ART_UNC_ANI_LRZ_BG_SIZE = 0x2400;
    /** {@code ArtUnc_AniLRZ__BG2} (sonic3k.lst: $C2700): eight $180-byte background frames. */
    private static final int ART_UNC_ANI_LRZ_BG2_ADDR = 0x0C2700;
    private static final int ART_UNC_ANI_LRZ_BG2_SIZE = 0x0C00;
    /**
     * {@code word_2834C} (sonic3k.asm:55107-55119): six (first, second) word-count pairs for
     * {@code loc_282D0}'s channel 0. The pair rotates one $480-byte frame by {@code band * $C0}
     * bytes, so the two counts always sum to $240 words.
     */
    private static final int[] LRZ_BG1_SPLIT_WORD_COUNTS = {
            0x240, 0x000,
            0x1E0, 0x060,
            0x180, 0x0C0,
            0x120, 0x120,
            0x0C0, 0x180,
            0x060, 0x1E0
    };
    /**
     * {@code word_283D2} (sonic3k.asm:55177-55184): four pairs for {@code loc_28364}'s channel 1,
     * rotating one $180-byte frame by {@code band * $60} bytes.
     */
    private static final int[] LRZ_BG2_SPLIT_WORD_COUNTS = {
            0x0C0, 0x000,
            0x090, 0x030,
            0x060, 0x060,
            0x030, 0x090
    };
    /** {@code AnimateTiles_LRZ1}/{@code AnimateTiles_LRZ2} {@code d4} = {@code tiles_to_bytes($320)}. */
    private static final int LRZ_BG1_DEST_TILE = 0x320;
    /** The same routines' {@code d6} = {@code tiles_to_bytes($344)}. */
    private static final int LRZ_BG2_DEST_TILE = 0x344;
    /** {@code $344} plus channel 1's $180 bytes, the last tile either channel writes. */
    private static final int LRZ_ANIMATED_TILE_CAPACITY = 0x350;

    private final AnimatedTileChannelGraph graph;
    private final Level level;
    private final int zoneIndex;
    private final int actIndex;
    private final boolean isSkipIntro;
    private final List<AniPlcScriptState> scripts;
    private final Sonic3kAniPlcPublicationQueue aniPlcPublications = new Sonic3kAniPlcPublicationQueue();

    private final Pattern[] firstTreePatterns;
    private boolean firstTreeApplied;

    private final byte[] hczWaterlineScrollData;
    private final byte[] hcz1DynamicBlockData;
    private final byte[] hcz1WaterlineBelow1Data;
    private final byte[] hcz1WaterlineAbove1Data;
    private final Pattern[] hcz1WaterlineBelow1Patterns;
    private final Pattern[] hcz1UpperBg1Patterns;
    private final Pattern[] hcz1WaterlineAbove1Patterns;
    private final Pattern[] hcz1LowerBg1Patterns;
    private final byte[] hcz1WaterlineBelow2Data;
    private final byte[] hcz1WaterlineAbove2Data;
    private final Pattern[] hcz1WaterlineBelow2Patterns;
    private final Pattern[] hcz1UpperBg2Patterns;
    private final Pattern[] hcz1WaterlineAbove2Patterns;
    private final Pattern[] hcz1LowerBg2Patterns;
    private final byte[] hcz2SmallBgLineData;
    private final byte[] hcz2Art2Data;
    private final byte[] hcz2Art3Data;
    private final byte[] hcz2Art4Data;
    private final Pattern[] hcz2SmallBgLinePatterns;
    private final Pattern[] hcz2Art2Patterns;
    private final Pattern[] hcz2Art3Patterns;
    private final Pattern[] hcz2Art4Patterns;
    private final byte[] cnzBgData;
    private final byte[] mhzBg1Data;
    private final byte[] mhzBg2Data;
    private final byte[] iczArt1Data;
    private final byte[] iczArt2Data;
    private final byte[] iczArt3Data;
    private final byte[] iczArt4Data;
    private final byte[] iczArt5Data;
    private byte[] lbzSharedData;
    private byte[] lrzBg1Data;
    private byte[] lrzBg2Data;
    private byte[] lbz1ScrollData;
    private byte[] lbz1ScrollCapData;
    private byte[] lbz2ScrollData;
    private byte[] lbz2WaterlineBelowData;
    private byte[] lbz2LowerBgData;
    private byte[] lbz2WaterlineAboveData;
    private byte[] lbz2UpperBgData;
    private byte[] lbz2WaterlineBelowSourceData;
    private byte[] lbz2WaterlineAboveSourceData;
    private byte[] lbzWaterlineScrollData;
    private final byte[] soz1BgData;
    private final byte[] soz1Bg2Data;
    private final byte[] soz2BgData;
    private final byte[] pachinkoScratch;
    private final byte[] pachinkoLowSource;
    private final byte[] pachinkoHighSource;

    // Reused per-call scratch for raw tile decode/compose. The animator is
    // per-zone-lifetime, so these survive across delta-gated update bursts and
    // remove the per-tile Pattern/byte[] allocations from the dynamic-art path.
    // The compose buffers are fully overwritten before use on every call.
    // Note: scratch retains last-frame data between delta-gated updates —
    // harmless, since every consuming site overwrites before reading.
    // Note: beginPatternAtlasBatch is not re-entrant; do not call the apply*
    // methods from within an already-active atlas batch (no such path today).
    private final Pattern rawTileScratchPattern = new Pattern();
    private final byte[] rawTileScratchBytes = new byte[Pattern.PATTERN_SIZE_IN_ROM];
    private final byte[] hcz1StripComposeScratch = new byte[0x300];
    private final byte[] lbz2WaterlineComposeScratch = new byte[0x200];
    // Derived deformation values only; excluded from rewind state just like tile scratch.
    private final int[] hcz2HScrollScratch = new int[24];

    private int lastHcz1WaterlineDelta = Integer.MIN_VALUE;
    private int lastHcz2SmallBgLineValue = Integer.MIN_VALUE;
    private int lastHcz2Art2Value = Integer.MIN_VALUE;
    private int lastHcz2Art3Value = Integer.MIN_VALUE;
    private int lastHcz2Art4Value = Integer.MIN_VALUE;
    private int lastMhzBg1Phase = Integer.MIN_VALUE;
    private int lastMhzBg2Phase = Integer.MIN_VALUE;
    /**
     * {@code Anim_Counters+1} / {@code Anim_Counters+3} for {@code loc_282D0}.
     * {@code Animate_Init} (sonic3k.asm:56411-56414, 56458-56461) writes {@code -1} to both for
     * {@code $900} and {@code $1600} and leaves them at their cleared 0 for {@code $901}, so a
     * direct or star-post {@code $901} load whose first phase is 0 skips its first upload.
     */
    private int lastLrzBg1Phase;
    private int lastLrzBg2Phase;
    private int pachinkoPhase;
    private int pachinkoSourceOffset;
    private int pachinkoStripeOffset;
    private int lbzRegularScriptCount;
    private int frameCounter;

    // Gumball bonus stage: direct DMA of uncompressed art based on BG scroll
    // ROM: AnimateTiles_Gumball (sonic3k.asm:55266)
    // ROM Add_To_DMA_Queue params:
    //   d1 = source address (ArtUnc_AniGumball)
    //   d2 = VRAM BYTE destination = tiles_to_bytes($054) = 0xA80 → VRAM tile $54
    //   d3 = $40 words = 128 bytes = 4 tiles
    // Source data is 256 bytes; max byte offset read = 31*4 + 128 = 252 < 256, safely within bounds.
    private static final int GUMBALL_DEST_TILE = 0x54;
    private static final int GUMBALL_SOURCE_SIZE = 0x100;                       // 256 bytes source data
    private static final int GUMBALL_TILE_COUNT = 4;                             // 4 tiles ($40 words = 128 bytes)
    private static final int GUMBALL_DMA_SIZE = GUMBALL_TILE_COUNT * Pattern.PATTERN_SIZE_IN_ROM;
    private final byte[] gumballAniData;
    private int lastGumballIndex = -1;
    private int gumballFrameCounter;

    Sonic3kPatternAnimator(RomByteReader reader, Level level,
                           int zoneIndex, int actIndex, boolean isSkipIntro) {
        this.graph = GameServices.animatedTileChannelGraph();
        this.level = level;
        this.zoneIndex = zoneIndex;
        this.actIndex = actIndex;
        this.isSkipIntro = isSkipIntro;

        // Animate_Init seeds Anim_Counters+1/+3 with -1 for $900 and $1600 only; every other
        // load leaves the cleared 0 (sonic3k.asm:56411-56414, 56458-56461).
        int seed = seedsLrzAnimationCounters(zoneIndex, actIndex) ? 0xFF : 0;
        this.lastLrzBg1Phase = seed;
        this.lastLrzBg2Phase = seed;

        int aniPlcAddr = resolveAniPlcAddr(zoneIndex, actIndex);
        if (aniPlcAddr < 0) {
            this.scripts = List.of();
            this.firstTreePatterns = null;
            this.hczWaterlineScrollData = null;
            this.hcz1DynamicBlockData = null;
            this.hcz1WaterlineBelow1Data = null;
            this.hcz1WaterlineAbove1Data = null;
            this.hcz1WaterlineBelow1Patterns = null;
            this.hcz1UpperBg1Patterns = null;
            this.hcz1WaterlineAbove1Patterns = null;
            this.hcz1LowerBg1Patterns = null;
            this.hcz1WaterlineBelow2Data = null;
            this.hcz1WaterlineAbove2Data = null;
            this.hcz1WaterlineBelow2Patterns = null;
            this.hcz1UpperBg2Patterns = null;
            this.hcz1WaterlineAbove2Patterns = null;
            this.hcz1LowerBg2Patterns = null;
            this.hcz2SmallBgLineData = null;
            this.hcz2Art2Data = null;
            this.hcz2Art3Data = null;
            this.hcz2Art4Data = null;
            this.hcz2SmallBgLinePatterns = null;
            this.hcz2Art2Patterns = null;
            this.hcz2Art3Patterns = null;
            this.hcz2Art4Patterns = null;
            this.cnzBgData = null;
            this.mhzBg1Data = null;
            this.mhzBg2Data = null;
            this.iczArt1Data = null;
            this.iczArt2Data = null;
            this.iczArt3Data = null;
            this.iczArt4Data = null;
            this.iczArt5Data = null;
            this.soz1BgData = null;
            this.soz1Bg2Data = null;
            this.soz2BgData = null;
            this.pachinkoScratch = null;
            this.pachinkoLowSource = null;
            this.pachinkoHighSource = null;
            // Gumball uses direct DMA, not AniPLC — still needs data loaded
            if (zoneIndex == 0x13) {
                this.gumballAniData = loadRawBytes(reader,
                        Sonic3kConstants.GUMBALL_ANI_TILES_ADDR, GUMBALL_SOURCE_SIZE);
            } else {
                this.gumballAniData = null;
            }
            installGraphChannels();
            return;
        }

        // Offs_AniPLC names LRZ1 for both SOZ acts, but AnimateTiles_SOZ1/2
        // return directly: neither invokes AnimateTiles_DoAniPLC. Loading that
        // unused list would replace static SOZ art at $350-$357 with lava flames.
        List<AniPlcScriptState> parsedScripts = zoneIndex == 0x08
                ? List.of() : AniPlcParser.parseScripts(reader, aniPlcAddr);
        this.lbzRegularScriptCount = parsedScripts.size();
        if (zoneIndex == 0x06 && actIndex == 0) {
            List<AniPlcScriptState> specScripts = AniPlcParser.parseScripts(reader,
                    Sonic3kConstants.ANIPLC_LBZ_SPEC_ADDR);
            List<AniPlcScriptState> combinedScripts =
                    new ArrayList<>(parsedScripts.size() + specScripts.size());
            combinedScripts.addAll(parsedScripts);
            combinedScripts.addAll(specScripts);
            this.scripts = List.copyOf(combinedScripts);
        } else {
            this.scripts = parsedScripts;
        }
        AniPlcParser.ensurePatternCapacity(scripts, level);
        for (AniPlcScriptState script : scripts) {
            // Capture original presented bytes even before the first submission.
            aniPlcPublications.registerDestination(script.destinationTileIndex(), script.tilesPerFrame());
        }

        if (zoneIndex == 0 && actIndex == 1) {
            int firstTreeEnd = Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_DEST_TILE
                    + Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE / Pattern.PATTERN_SIZE_IN_ROM;
            level.ensurePatternCapacity(firstTreeEnd);
        }
        ensureHczPatternCapacity();
        ensurePachinkoPatternCapacity();
        ensureIczPatternCapacity();
        ensureLbzPatternCapacity();
        ensureMhzPatternCapacity();

        boolean isAiz1Intro = zoneIndex == 0 && actIndex == 0 && !isSkipIntro;
        if (!isAiz1Intro) {
            AniPlcParser.primeScripts(scripts, level, GameServices.graphics());
        }

        this.firstTreePatterns = zoneIndex == 0 && actIndex == 1
                ? loadUncompressedPatterns(reader,
                Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_ADDR,
                Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE)
                : null;
        this.firstTreeApplied = false;

        if (zoneIndex == 1 && actIndex == 0) {
            this.hczWaterlineScrollData = loadRawBytes(reader,
                    Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_ADDR,
                    Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_SIZE);
            this.hcz1DynamicBlockData = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR,
                    0x0C00);
            this.hcz1WaterlineBelow1Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineAbove1Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineBelow1Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1UpperBg1Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_UPPER_BG1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineAbove1Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1LowerBg1Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_LOWER_BG1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineBelow2Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineAbove2Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineBelow2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1UpperBg2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_UPPER_BG2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineAbove2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1LowerBg2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_LOWER_BG2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz2SmallBgLineData = null;
            this.hcz2Art2Data = null;
            this.hcz2Art3Data = null;
            this.hcz2Art4Data = null;
            this.hcz2SmallBgLinePatterns = null;
            this.hcz2Art2Patterns = null;
            this.hcz2Art3Patterns = null;
            this.hcz2Art4Patterns = null;
            this.cnzBgData = null;
            this.mhzBg1Data = null;
            this.mhzBg2Data = null;
            // HCZ1 starts with the lower repair strips resident before the
            // waterline-specific recomposition path kicks in.
            applyPatternsToLevel(this.hcz1LowerBg1Patterns, 0x2F4);
            applyPatternsToLevel(this.hcz1LowerBg2Patterns, 0x300);
        } else if (zoneIndex == 1 && actIndex == 1) {
            this.hczWaterlineScrollData = null;
            this.hcz1DynamicBlockData = null;
            this.hcz1WaterlineBelow1Data = null;
            this.hcz1WaterlineAbove1Data = null;
            this.hcz1WaterlineBelow1Patterns = null;
            this.hcz1UpperBg1Patterns = null;
            this.hcz1WaterlineAbove1Patterns = null;
            this.hcz1LowerBg1Patterns = null;
            this.hcz1WaterlineBelow2Data = null;
            this.hcz1WaterlineAbove2Data = null;
            this.hcz1WaterlineBelow2Patterns = null;
            this.hcz1UpperBg2Patterns = null;
            this.hcz1WaterlineAbove2Patterns = null;
            this.hcz1LowerBg2Patterns = null;
            this.hcz2SmallBgLineData = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_SMALL_BG_LINE_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_SMALL_BG_LINE_SIZE);
            this.hcz2Art2Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_2_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_2_SIZE);
            this.hcz2Art3Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_3_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_3_SIZE);
            this.hcz2Art4Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_4_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_4_SIZE);
            this.hcz2SmallBgLinePatterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_SMALL_BG_LINE_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_SMALL_BG_LINE_SIZE);
            this.hcz2Art2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_2_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_2_SIZE);
            this.hcz2Art3Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_3_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_3_SIZE);
            this.hcz2Art4Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_4_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_4_SIZE);
            this.cnzBgData = null;
            this.mhzBg1Data = null;
            this.mhzBg2Data = null;
        } else if (zoneIndex == 0x03) {
            this.hczWaterlineScrollData = null;
            this.hcz1DynamicBlockData = null;
            this.hcz1WaterlineBelow1Data = null;
            this.hcz1WaterlineAbove1Data = null;
            this.hcz1WaterlineBelow1Patterns = null;
            this.hcz1UpperBg1Patterns = null;
            this.hcz1WaterlineAbove1Patterns = null;
            this.hcz1LowerBg1Patterns = null;
            this.hcz1WaterlineBelow2Data = null;
            this.hcz1WaterlineAbove2Data = null;
            this.hcz1WaterlineBelow2Patterns = null;
            this.hcz1UpperBg2Patterns = null;
            this.hcz1WaterlineAbove2Patterns = null;
            this.hcz1LowerBg2Patterns = null;
            this.hcz2SmallBgLineData = null;
            this.hcz2Art2Data = null;
            this.hcz2Art3Data = null;
            this.hcz2Art4Data = null;
            this.hcz2SmallBgLinePatterns = null;
            this.hcz2Art2Patterns = null;
            this.hcz2Art3Patterns = null;
            this.hcz2Art4Patterns = null;
            this.cnzBgData = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_CNZ_6_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_CNZ_6_SIZE);
            this.mhzBg1Data = null;
            this.mhzBg2Data = null;
        } else if (zoneIndex == 0x07) {
            this.hczWaterlineScrollData = null;
            this.hcz1DynamicBlockData = null;
            this.hcz1WaterlineBelow1Data = null;
            this.hcz1WaterlineAbove1Data = null;
            this.hcz1WaterlineBelow1Patterns = null;
            this.hcz1UpperBg1Patterns = null;
            this.hcz1WaterlineAbove1Patterns = null;
            this.hcz1LowerBg1Patterns = null;
            this.hcz1WaterlineBelow2Data = null;
            this.hcz1WaterlineAbove2Data = null;
            this.hcz1WaterlineBelow2Patterns = null;
            this.hcz1UpperBg2Patterns = null;
            this.hcz1WaterlineAbove2Patterns = null;
            this.hcz1LowerBg2Patterns = null;
            this.hcz2SmallBgLineData = null;
            this.hcz2Art2Data = null;
            this.hcz2Art3Data = null;
            this.hcz2Art4Data = null;
            this.hcz2SmallBgLinePatterns = null;
            this.hcz2Art2Patterns = null;
            this.hcz2Art3Patterns = null;
            this.hcz2Art4Patterns = null;
            this.cnzBgData = null;
            this.mhzBg1Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_MHZ_BG_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_MHZ_BG_SIZE);
            this.mhzBg2Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_MHZ_BG2_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_MHZ_BG2_SIZE);
        } else {
            this.hczWaterlineScrollData = null;
            this.hcz1DynamicBlockData = null;
            this.hcz1WaterlineBelow1Data = null;
            this.hcz1WaterlineAbove1Data = null;
            this.hcz1WaterlineBelow1Patterns = null;
            this.hcz1UpperBg1Patterns = null;
            this.hcz1WaterlineAbove1Patterns = null;
            this.hcz1LowerBg1Patterns = null;
            this.hcz1WaterlineBelow2Data = null;
            this.hcz1WaterlineAbove2Data = null;
            this.hcz1WaterlineBelow2Patterns = null;
            this.hcz1UpperBg2Patterns = null;
            this.hcz1WaterlineAbove2Patterns = null;
            this.hcz1LowerBg2Patterns = null;
            this.hcz2SmallBgLineData = null;
            this.hcz2Art2Data = null;
            this.hcz2Art3Data = null;
            this.hcz2Art4Data = null;
            this.hcz2SmallBgLinePatterns = null;
            this.hcz2Art2Patterns = null;
            this.hcz2Art3Patterns = null;
            this.hcz2Art4Patterns = null;
            this.cnzBgData = null;
            this.mhzBg1Data = null;
            this.mhzBg2Data = null;
        }

        if (zoneIndex == 0x05) {
            this.iczArt1Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_1_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_1_SIZE);
            this.iczArt2Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_2_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_2_SIZE);
            this.iczArt3Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_3_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_3_SIZE);
            this.iczArt4Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_4_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_4_SIZE);
            this.iczArt5Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_5_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_ICZ_5_SIZE);
        } else {
            this.iczArt1Data = null;
            this.iczArt2Data = null;
            this.iczArt3Data = null;
            this.iczArt4Data = null;
            this.iczArt5Data = null;
        }

        loadLbzRawArt(reader);
        loadLrzRawArt(reader);
        bootstrapLbz2WaterlinePhase();

        if (zoneIndex == 0x08 && actIndex == 0) {
            this.soz1BgData = loadRawBytes(reader, ART_UNC_ANI_SOZ1_BG_ADDR, ART_UNC_ANI_SOZ1_BG_SIZE);
            this.soz1Bg2Data = loadRawBytes(reader, ART_UNC_ANI_SOZ1_BG2_ADDR, ART_UNC_ANI_SOZ1_BG2_SIZE);
        } else {
            this.soz1BgData = null;
            this.soz1Bg2Data = null;
        }

        this.soz2BgData = zoneIndex == 0x08 && actIndex == 1
                ? loadRawBytes(reader, Sonic3kConstants.ART_UNC_ANI_SOZ2_BG_ADDR,
                        Sonic3kConstants.ART_UNC_ANI_SOZ2_BG_SIZE) : null;

        if (zoneIndex == 0x14) {
            byte[] lowSource = loadKosinskiBytes(reader,
                    Sonic3kConstants.ART_KOS_PACHINKO_BG1_ADDR,
                    Sonic3kConstants.ART_KOS_PACHINKO_BG1_SIZE,
                    PACHINKO_LOW_SOURCE_BYTES);
            byte[] highSource = buildPachinkoHighSource(loadKosinskiBytes(reader,
                    Sonic3kConstants.ART_KOS_PACHINKO_BG2_ADDR,
                    Sonic3kConstants.ART_KOS_PACHINKO_BG2_SIZE,
                    0x600));
            if (lowSource != null && highSource != null) {
                this.pachinkoScratch = new byte[PACHINKO_BG_DMA_BYTES];
                this.pachinkoLowSource = lowSource;
                this.pachinkoHighSource = highSource;
                this.pachinkoPhase = 0;
                this.pachinkoSourceOffset = 0;
                this.pachinkoStripeOffset = 0;
            } else {
                this.pachinkoScratch = null;
                this.pachinkoLowSource = null;
                this.pachinkoHighSource = null;
            }
        } else {
            this.pachinkoScratch = null;
            this.pachinkoLowSource = null;
            this.pachinkoHighSource = null;
        }

        // Gumball bonus stage animated tiles (zone 0x13)
        if (zoneIndex == 0x13) {
            this.gumballAniData = loadRawBytes(reader,
                    Sonic3kConstants.GUMBALL_ANI_TILES_ADDR, GUMBALL_SOURCE_SIZE);
        } else {
            this.gumballAniData = null;
        }
        installGraphChannels();
    }

    @Override
    public void update() {
        // Zone 0x13 (Gumball bonus stage) has no AniPLC scripts but still requires
        // animated tile updates. Run its updater before the early return.
        if (zoneIndex == 0x13) {
            updateGumball();
            return;
        }
        if (zoneIndex == 0x14) {
            updatePachinko();
            runAllScripts();
            return;
        }
        if (!graph.channels().isEmpty()) {
            graph.update(new ChannelContext(
                    graph, null, level, GameServices.zoneRuntimeState(), zoneIndex, actIndex, frameCounter++));
            return;
        }
        if (scripts.isEmpty()) {
            return;
        }

        switch (zoneIndex) {
            case 0 -> {
                if (actIndex == 0) {
                    updateAiz1();
                } else {
                    updateAiz2();
                }
            }
            default -> runAllScripts();
        }
    }

    /**
     * Runs a pattern-only setup or replay-prelude pass while preserving counters
     * already represented by a zone runtime's initialization. Production calls
     * this after initial Process_Sprites (loc_6468); replay uses it only for an
     * additional omitted handoff iteration. All animated-tile channels except the MHZ mushroom-cap position
     * counter recompute their transfer from current camera/frame state, so the
     * warmup pass reproduces the ROM's DMA output. The mushroom-cap channel is
     * the one stateful accumulator: it advances {@code Anim_Counters+$F}
     * (AnimateTiles_MHZ, sonic3k.asm:54901-54908). That counter is already seeded
     * to its ROM {@code LevelLoop} frame-0 value by the pre-loop
     * {@code Animate_Tiles} pass (loc_6468, sonic3k.asm:7853-7855). The pattern
     * pass must not repeat that separately represented counter effect.
     * Advancing the accumulator here would double-count that setup
     * pass and leave the caps two bob-steps ahead. Preserve the counter across
     * the warmup so caps read {@code Anim_Counters+$F} at the ROM frame-0 phase.
     */
    public void updatePatternsPreservingPreseededCounters() {
        MhzZoneRuntimeState mhz = currentMhzState();
        int preservedMushroomCapCounter = mhz != null ? mhz.mushroomCapPositionCounter() : -1;
        update();
        if (mhz != null) {
            mhz.publishMushroomCapPositionCounter(preservedMushroomCapCounter);
        }
    }

    private void updateAiz1() {
        if (isAizBossActive()) {
            return;
        }
        if (isSkipIntro || AizPlaneIntroInstance.isMainLevelPhaseActive()) {
            runAllScripts();
        }
    }

    private void updateAiz2() {
        if (isAizBossActive()) {
            return;
        }

        int cameraX = getCameraX();
        if (cameraX >= 0x1C0) {
            runAllScripts();
            firstTreeApplied = false;
            return;
        }

        if (!scripts.isEmpty()) {
            tickScript(scripts.get(0));
        }
        if (!firstTreeApplied && firstTreePatterns != null) {
            applyPatternsToLevel(firstTreePatterns, Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_DEST_TILE);
            firstTreeApplied = true;
        }
    }

    private void runAllScripts() {
        for (AniPlcScriptState script : scripts) {
            tickScript(script);
        }
    }

    private boolean isAizBossActive() {
        try {
            AizZoneRuntimeState aizState = GameServices.hasRuntime()
                    ? S3kRuntimeStates.currentAiz(GameServices.zoneRuntimeRegistry()).orElse(null)
                    : null;
            return aizState != null && aizState.isBossFlagActive();
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.isAizBossActive: " + e.getMessage());
        }
        return false;
    }

    private boolean isGenericBossActive() {
        try {
            if (GameServices.module().getLevelEventProvider() instanceof AbstractLevelEventManager manager) {
                return manager.isBossActive();
            }
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.isGenericBossActive: " + e.getMessage());
        }
        return false;
    }

    boolean shouldRunScriptChannels() {
        return !scripts.isEmpty();
    }

    boolean shouldRunMgzScriptChannels() {
        return !scripts.isEmpty() && !isGenericBossActive();
    }

    boolean shouldRunHcz1CustomChannels() {
        return hczWaterlineScrollData != null
                && hcz1DynamicBlockData != null
                && hcz1WaterlineBelow1Patterns != null
                && hcz1LowerBg1Patterns != null
                && hcz1WaterlineBelow2Patterns != null
                && hcz1LowerBg2Patterns != null
                && hcz1UpperBg1Patterns != null
                && hcz1WaterlineAbove1Patterns != null
                && hcz1UpperBg2Patterns != null
                && hcz1WaterlineAbove2Patterns != null;
    }

    boolean shouldRunHcz2CustomChannels() {
        return hcz2SmallBgLineData != null
                && hcz2Art2Data != null
                && hcz2Art3Data != null
                && hcz2Art4Data != null;
    }

    boolean shouldRunSoz2CustomChannels() { return soz2BgData != null; }

    void updateSoz2TorchesForGraph() {
        if (!GameServices.hasRuntime()) return;
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElse(null);
        if (state == null || state.actIndex() != 1) return;
        int frame = state.lighting().tickTorch();
        if (frame >= 0) applyRawPatternSliceToLevel(soz2BgData, frame * 0xC0, 0xC0, 0x330);
    }

    boolean shouldRunSoz1CustomChannels() {
        return soz1BgData != null && soz1Bg2Data != null;
    }

    /**
     * CNZ custom DMA can run whenever the direct background art source is
     * present. The graph caches the phase, so this guard does not need to
     * inspect transient animation state.
     */
    boolean shouldRunCnzCustomChannels() {
        return cnzBgData != null;
    }

    boolean shouldRunMhzBackgroundLayer1Channel() {
        return mhzBg1Data != null;
    }

    boolean shouldRunMhzBackgroundLayer2Channel() {
        return mhzBg2Data != null;
    }

    boolean shouldRunMhzMushroomCapCounterChannel() {
        return currentMhzState() != null;
    }

    boolean shouldRunIczHorizontalCustomChannels() {
        return iczArt1Data != null;
    }

    boolean shouldRunIczAct1VerticalCustomChannels() {
        return actIndex == 0
                && iczArt2Data != null
                && iczArt3Data != null
                && iczArt4Data != null
                && iczArt5Data != null;
    }

    boolean shouldRunLbzSharedChannel() {
        return lbzSharedData != null
                && !(actIndex == 1 && isLbz2RideAnimatedTileGateActive());
    }

    boolean shouldRunLbz1CustomChannels() {
        return actIndex == 0
                && lbz1ScrollData != null
                && lbz1ScrollCapData != null;
    }

    boolean shouldRunLbz1AlarmScriptChannel(AniPlcScriptState script) {
        return isLbzAlarmAnimationActive() || script.getFrameIndex() != 1;
    }

    boolean shouldRunLbz2ScrollChannel() {
        return actIndex == 1 && lbz2ScrollData != null && !isLbz2RideAnimatedTileGateActive();
    }

    boolean shouldRunLbz2WaterlineChannel() {
        return actIndex == 1
                && lbz2WaterlineBelowData != null
                && lbz2LowerBgData != null
                && lbz2WaterlineAboveData != null
                && lbz2UpperBgData != null
                && lbzWaterlineScrollData != null;
    }

    boolean shouldRunLbz2RideTriggerChannel() {
        return actIndex == 1 && GameServices.hasRuntime();
    }

    void tickScript(AniPlcScriptState script) {
        // AnimateTiles_DoAniPLC changes counters now, but Add_To_DMA_Queue
        // publishes its ROM payload only in the following VInt Process_DMA_Queue.
        script.tickSubmission(tileId -> aniPlcPublications.submit(
                script.destinationTileIndex(), script.copyFramePayload(tileId)));
    }

    void publishAniPlcAtVBlank() {
        aniPlcPublications.publish(this::publishAniPlcPayload);
    }

    private void publishAniPlcPayload(int destination, byte[] payload) {
        GraphicsManager graphics = GameServices.graphics();
        for (int tile = 0; tile < payload.length / 32; tile++) {
            Pattern pattern = level.getPattern(destination + tile);
            pattern.fromSegaFormat(java.util.Arrays.copyOfRange(payload, tile * 32, (tile + 1) * 32));
            if (graphics != null && graphics.isGlInitialized()) graphics.updatePatternTexture(pattern, destination + tile);
        }
        Sonic3kPlcLoader.refreshAffectedRenderers(List.of(new Sonic3kPlcLoader.TileRange(
                destination, payload.length / 32)), GameServices.level());
    }

    private byte[] presentedAniPlcPattern(int tile) {
        Pattern pattern = level.getPattern(tile);
        byte[] packed = new byte[32];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x += 2)
            packed[y * 4 + x / 2] = (byte) ((pattern.getPixel(x, y) & 15) << 4
                    | pattern.getPixel(x + 1, y) & 15);
        return packed;
    }

    boolean requiresObjectRendererRefresh(AniPlcScriptState script) {
        return zoneIndex == Sonic3kZoneIds.ZONE_CNZ
                && script.destinationTileIndex() == CNZ_HOVER_FAN_BLADE_DEST_TILE
                && script.tilesPerFrame() == CNZ_HOVER_FAN_BLADE_TILE_COUNT;
    }

    void updateHcz1BackgroundStripsForGraph() {
        updateHcz1BackgroundStrips();
    }

    void updateHcz2BackgroundStripsForGraph() {
        updateHcz2BackgroundStrips();
    }

    void updateSoz1BackgroundTilesForGraph() {
        updateSoz1BackgroundTiles();
    }

    /**
     * Graph entry point for the direct-DMA half of AnimateTiles_CNZ.
     *
     * <p>The shared graph owns the invalidation policy, while this method keeps
     * the ROM-shaped transfer math in one place for direct comparison against
     * {@code AnimateTiles_CNZ}.
     */
    void updateCnzBackgroundTilesForGraph() {
        updateCnzBackgroundTiles();
    }

    void updateMhzBackgroundLayer1ForGraph() {
        updateMhzBackgroundLayer1();
    }

    void updateMhzBackgroundLayer2ForGraph() {
        updateMhzBackgroundLayer2();
    }

    void advanceMhzMushroomCapPositionCounterForGraph() {
        MhzZoneRuntimeState state = currentMhzState();
        if (state != null) {
            state.advanceMushroomCapPositionCounter();
        }
    }

    void updateIczHorizontalTilesForGraph() {
        updateIczHorizontalTiles();
    }

    void updateIczAct1VerticalTilesForGraph() {
        updateIczAct1VerticalTiles();
    }

    void updateLbzSharedTilesForGraph(int channelFrameCounter) {
        if (lbzSharedData == null) {
            return;
        }
        int phase = computeLbzSharedPhase(channelFrameCounter);
        applyRawPatternSliceToLevel(lbzSharedData, ror16(phase, 7), 0x200, 0x160);
    }

    void updateLbz1ScrollTilesForGraph() {
        updateLbz1ScrollTiles();
    }

    int computeLbz1AlarmScriptPhase(AniPlcScriptState script, int channelFrameCounter) {
        if (isLbzAlarmAnimationActive()) {
            return channelFrameCounter;
        }
        return script.getFrameIndex() == 1 ? -1 : 0;
    }

    void updateLbz1AlarmScriptForGraph(AniPlcScriptState script) {
        if (!isLbzAlarmAnimationActive()) {
            script.restoreCounters(0, 0);
        }
        tickScript(script);
    }

    void updateLbz2ScrollTilesForGraph() {
        updateLbz2ScrollTiles();
    }

    void updateLbz2WaterlineTilesForGraph() {
        updateLbz2WaterlineTiles();
    }

    void consumeLbz2RideTriggerForGraph() {
        isLbz2RideAnimatedTileGateActive();
    }

    private void updateHcz1BackgroundStrips() {
        int waterlineDelta = computeHcz1WaterlineDelta();
        if (waterlineDelta == lastHcz1WaterlineDelta) {
            return;
        }
        lastHcz1WaterlineDelta = waterlineDelta;

        if (waterlineDelta == 0) {
            applyPatternsToLevel(hcz1LowerBg1Patterns, 0x2F4);
            applyPatternsToLevel(hcz1LowerBg2Patterns, 0x300);
            return;
        }

        if (waterlineDelta < 0) {
            if (waterlineDelta > -HCZ1_WATERLINE_VISIBLE) {
                applyDynamicHcz1Strip(0x000, (waterlineDelta + HCZ1_WATERLINE_VISIBLE) * 0x60, 0x2DC);
            } else {
                applyPatternsToLevel(hcz1WaterlineBelow1Patterns, 0x2DC);
                applyPatternsToLevel(hcz1WaterlineBelow2Patterns, 0x2E8);
            }
            applyPatternsToLevel(hcz1LowerBg1Patterns, 0x2F4);
            applyPatternsToLevel(hcz1LowerBg2Patterns, 0x300);
            return;
        }

        applyPatternsToLevel(hcz1UpperBg1Patterns, 0x2DC);
        applyPatternsToLevel(hcz1UpperBg2Patterns, 0x2E8);
        if (waterlineDelta < HCZ1_WATERLINE_VISIBLE) {
            applyDynamicHcz1Strip(0x300, (HCZ1_WATERLINE_VISIBLE - waterlineDelta) * 0x60, 0x2F4);
        } else {
            applyPatternsToLevel(hcz1WaterlineAbove1Patterns, 0x2F4);
            applyPatternsToLevel(hcz1WaterlineAbove2Patterns, 0x300);
        }
    }

    private void updateHcz2BackgroundStrips() {
        int[] hScroll = buildHcz2HScrollValues(getCameraX());
        int eventsBg12 = hScroll[3] - hScroll[9];
        int eventsBg14 = hScroll[2] - hScroll[9];

        int smallBgValue = eventsBg12 & 0x1F;
        if (smallBgValue != lastHcz2SmallBgLineValue) {
            lastHcz2SmallBgLineValue = smallBgValue;
            applyHcz2DmaSection(hcz2SmallBgLineData, smallBgValue, 0x2D2,
                    (smallBgValue & 7) << 7,
                    (smallBgValue & 7) << 7,
                    (smallBgValue & 0x18) << 2,
                    HCZ2_SMALL_BG_LINE_WORD_COUNTS);
        }

        int art2Value = eventsBg12 & 0x1F;
        if (art2Value != lastHcz2Art2Value) {
            lastHcz2Art2Value = art2Value;
            applyHcz2DmaSection(hcz2Art2Data, art2Value, 0x2D6,
                    (art2Value & 7) << 8,
                    (art2Value & 7) << 8,
                    (art2Value & 0x18) << 3,
                    HCZ2_ART2_WORD_COUNTS);
        }

        int art3Value = eventsBg14 & 0x1F;
        if (art3Value != lastHcz2Art3Value) {
            lastHcz2Art3Value = art3Value;
            int baseOffset = ror16(art3Value & 7, 7);
            applyHcz2DmaSection(hcz2Art3Data, art3Value, 0x2DE,
                    baseOffset,
                    baseOffset,
                    (art3Value & 0x18) << 4,
                    HCZ2_ART3_WORD_COUNTS);
        }

        int art4Value = eventsBg14 & 0x3F;
        if (art4Value != lastHcz2Art4Value) {
            lastHcz2Art4Value = art4Value;
            applyHcz2Art4DmaSection(art4Value);
        }
    }

    private void applyHcz2DmaSection(byte[] sourceData, int rawValue, int destTile,
                                     int baseOffset, int secondChunkOffset, int bankOffset,
                                     int[] wordCounts) {
        if (sourceData == null) {
            return;
        }

        int pairIndex = (rawValue & 0x18) >> 2;
        int wordCount1 = wordCounts[pairIndex];
        int wordCount2 = wordCounts[pairIndex + 1];
        int destTile2 = destTile + ((wordCount1 << 1) / Pattern.PATTERN_SIZE_IN_ROM);

        applyRawPatternSliceToLevel(sourceData, baseOffset + bankOffset, wordCount1 << 1, destTile);
        if (wordCount2 != 0) {
            applyRawPatternSliceToLevel(sourceData, secondChunkOffset, wordCount2 << 1, destTile2);
        }
    }

    private void applyHcz2Art4DmaSection(int rawValue) {
        if (hcz2Art4Data == null) {
            return;
        }

        int d1 = negateWord(rawValue);
        int d2 = d1;
        d1 &= 7;
        d1 = ror16(d1, 7);
        int d0 = d1;
        d0 = word(d0 + d0);
        d1 = word(d1 + d0);
        int sourceOffset2 = d1;
        d2 = word(~d2);
        d2 &= 0x38;
        d0 = d2;
        d2 = word(d2 << 3);
        d1 = word(d1 + d2);
        d2 = word(d2 + d2);
        d1 = word(d1 + d2);

        int pairIndex = d0 >> 2;
        int wordCount1 = HCZ2_ART4_WORD_COUNTS[pairIndex];
        int wordCount2 = HCZ2_ART4_WORD_COUNTS[pairIndex + 1];
        int destTile2 = 0x2EE + ((wordCount1 << 1) / Pattern.PATTERN_SIZE_IN_ROM);

        applyRawPatternSliceToLevel(hcz2Art4Data, d1, wordCount1 << 1, 0x2EE);
        if (wordCount2 != 0) {
            applyRawPatternSliceToLevel(hcz2Art4Data, sourceOffset2, wordCount2 << 1, destTile2);
        }
    }

    private void applyDynamicHcz1Strip(int sourceBaseOffset, int tableOffset, int destTile) {
        if (hczWaterlineScrollData == null || hcz1DynamicBlockData == null) {
            return;
        }

        byte[] composed = hcz1StripComposeScratch;
        for (int i = 0; i < 0x60; i++) {
            int lookupIndex = tableOffset + i;
            if (lookupIndex < 0 || lookupIndex >= hczWaterlineScrollData.length) {
                return;
            }
            int byteIndex = hczWaterlineScrollData[lookupIndex] & 0xFF;
            int sourceByteOffset = byteIndex << 2;
            int sourceIndexA = sourceBaseOffset + sourceByteOffset;
            int sourceIndexB = sourceBaseOffset + 0x600 + sourceByteOffset;
            if (sourceIndexA + 4 > hcz1DynamicBlockData.length
                    || sourceIndexB + 4 > hcz1DynamicBlockData.length) {
                return;
            }
            System.arraycopy(hcz1DynamicBlockData, sourceIndexA, composed, i << 2, 4);
            System.arraycopy(hcz1DynamicBlockData, sourceIndexB, composed, 0x180 + (i << 2), 4);
        }
        applyRawPatternBytesToLevel(composed, destTile);
    }

    private void applyRawPatternBytesToLevel(byte[] data, int destTile) {
        applyRawPatternBytesToLevel(data, 0, data.length, destTile);
    }

    /**
     * Decodes {@code length} bytes of Sega-format tile data starting at
     * {@code offset} directly into the level patterns, batching the atlas
     * uploads (dirty-slot path: one texture bind for the whole burst) and
     * reusing the per-instance scratch pattern/byte buffers instead of
     * allocating per tile.
     */
    private void applyRawPatternBytesToLevel(byte[] data, int offset, int length, int destTile) {
        if (length <= 0 || (length % Pattern.PATTERN_SIZE_IN_ROM) != 0
                || offset < 0 || offset + length > data.length) {
            return;
        }

        int tileCount = length / Pattern.PATTERN_SIZE_IN_ROM;
        int maxPatterns = level.getPatternCount();
        GraphicsManager graphicsManager = GameServices.graphics();
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        graphicsManager.beginPatternAtlasBatch();
        try {
            for (int i = 0; i < tileCount; i++) {
                int destIndex = destTile + i;
                if (destIndex >= maxPatterns) {
                    break;
                }
                System.arraycopy(data, offset + i * Pattern.PATTERN_SIZE_IN_ROM,
                        rawTileScratchBytes, 0, Pattern.PATTERN_SIZE_IN_ROM);
                rawTileScratchPattern.fromSegaFormat(rawTileScratchBytes);
                Pattern dest = level.getPattern(destIndex);
                dest.copyFrom(rawTileScratchPattern);
                if (canUpdateTextures) {
                    graphicsManager.updatePatternTexture(dest, destIndex);
                }
            }
        } finally {
            graphicsManager.endPatternAtlasBatch();
        }
    }

    private void applyPatternSliceToLevel(Pattern[] sourcePatterns, int sourceByteOffset,
                                          int byteLength, int destTile) {
        if (sourcePatterns == null || byteLength <= 0) {
            return;
        }
        if ((sourceByteOffset % Pattern.PATTERN_SIZE_IN_ROM) != 0
                || (byteLength % Pattern.PATTERN_SIZE_IN_ROM) != 0) {
            return;
        }

        int sourceTileOffset = sourceByteOffset / Pattern.PATTERN_SIZE_IN_ROM;
        int tileCount = byteLength / Pattern.PATTERN_SIZE_IN_ROM;
        int maxPatterns = level.getPatternCount();
        GraphicsManager graphicsManager = GameServices.graphics();
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        graphicsManager.beginPatternAtlasBatch();
        try {
            for (int i = 0; i < tileCount; i++) {
                int sourceIndex = sourceTileOffset + i;
                int destIndex = destTile + i;
                if (sourceIndex >= sourcePatterns.length || destIndex >= maxPatterns) {
                    break;
                }
                Pattern dest = level.getPattern(destIndex);
                dest.copyFrom(sourcePatterns[sourceIndex]);
                if (canUpdateTextures) {
                    graphicsManager.updatePatternTexture(dest, destIndex);
                }
            }
        } finally {
            graphicsManager.endPatternAtlasBatch();
        }
    }

    private void applyRawPatternSliceToLevel(byte[] sourceData, int sourceByteOffset,
                                             int byteLength, int destTile) {
        if (sourceData == null || byteLength <= 0) {
            return;
        }
        if ((sourceByteOffset % Pattern.PATTERN_SIZE_IN_ROM) != 0) {
            return;
        }
        applyRawPatternBytesToLevel(sourceData, sourceByteOffset, byteLength, destTile);
    }

    private void applyRawPatternSliceAllowingRowOffset(byte[] sourceData, int sourceByteOffset,
                                                       int byteLength, int destTile) {
        if (sourceData == null || byteLength <= 0) {
            return;
        }
        applyRawPatternBytesToLevel(sourceData, sourceByteOffset, byteLength, destTile);
    }

    private void applyPatternsToLevel(Pattern[] sourcePatterns, int destTile) {
        if (sourcePatterns == null || sourcePatterns.length == 0) {
            return;
        }

        int maxPatterns = level.getPatternCount();
        GraphicsManager graphicsManager = GameServices.graphics();
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        graphicsManager.beginPatternAtlasBatch();
        try {
            for (int i = 0; i < sourcePatterns.length; i++) {
                int destIndex = destTile + i;
                if (destIndex >= maxPatterns) {
                    break;
                }
                Pattern dest = level.getPattern(destIndex);
                dest.copyFrom(sourcePatterns[i]);
                if (canUpdateTextures) {
                    graphicsManager.updatePatternTexture(dest, destIndex);
                }
            }
        } finally {
            graphicsManager.endPatternAtlasBatch();
        }
    }

    int computeSoz1Phase() {
        if (isSoz1BossArenaPhaseLocked()) {
            return 0;
        }
        // sub_55D56 uses the current camera. Animation may precede the
        // presentation pass, so do not read ParallaxManager's cached BG copy.
        return com.openggf.game.sonic3k.scroll.SwScrlSoz.desertTilePhase(getCameraX());
    }

    // sub_55E4C writes equal Events_bg+$10 and Camera_X_pos_BG_copy
    // throughout the arena background routine, fixing AnimateTiles_SOZ1 at zero.
    private boolean isSoz1BossArenaPhaseLocked() {
        return zoneIndex == 8 && actIndex == 0
                && com.openggf.game.sonic3k.runtime.S3kRuntimeStates
                        .currentSoz(GameServices.zoneRuntimeRegistry())
                        .map(state -> state.events().backgroundRoutine() != 0).orElse(false);
    }

    /**
     * ROM: AnimateTiles_ICZ horizontal phase formula:
     * {@code (Events_bg+$10 - Camera_X_pos_BG_copy) & $1F}.
     */
    int computeIczHorizontalPhase() {
        int bgCameraX = resolveIczBgCameraX();
        int eventsBg10 = resolveIczEventsBg10(bgCameraX);
        return (eventsBg10 - bgCameraX) & 0x1F;
    }

    /**
     * Packs the four ICZ1 vertical direct-DMA phases into one graph phase key.
     */
    int computeIczAct1VerticalCompositePhase() {
        int bgCameraY = resolveIczAct1BgCameraY();
        int eventsBg12 = asrWordValue(bgCameraY, 1);
        int phase2 = (-(bgCameraY - eventsBg12)) & 0x3F;
        int phase3 = (-(bgCameraY - asrWordValue(eventsBg12, 1)
                - asrWordValue(asrWordValue(eventsBg12, 1), 1))) & 0x1F;
        int phase4 = (-(bgCameraY - asrWordValue(eventsBg12, 1))) & 0x0F;
        int phase5 = (-(bgCameraY - asrWordValue(eventsBg12, 2))) & 0x07;
        return phase2 | (phase3 << 6) | (phase4 << 11) | (phase5 << 15);
    }

    private int resolveIczBgCameraX() {
        int cameraX = getCameraX();
        IczZoneRuntimeState state = currentIczState();
        if (state != null) {
            return state.iczBgCameraX(cameraX, getCameraY());
        }
        if (actIndex == 0) {
            int x = cameraX & 0xFFFF;
            if (x < 0x3940) {
                return 0x1880;
            }
            return asrWordValue(cameraX, 1) - 0x1D80;
        }

        int cameraY = getCameraY();
        if (!isIczAct2Indoor(cameraX, cameraY)) {
            return 0;
        }
        int d0 = ((short) cameraX) << 16;
        d0 >>= 1;
        int d1 = d0 >> 3;
        for (int i = 0; i < 4; i++) {
            d0 -= d1;
        }
        return (short) (d0 >> 16);
    }

    private int resolveIczEventsBg10(int bgCameraX) {
        IczZoneRuntimeState state = currentIczState();
        if (state != null) {
            return state.iczEventsBg10(getCameraX(), getCameraY());
        }
        if (actIndex == 0) {
            int cameraX = getCameraX() & 0xFFFF;
            if (cameraX < 0x3940) {
                return 0;
            }
            return asrWordValue(bgCameraX, 1);
        }

        int cameraX = getCameraX();
        int cameraY = getCameraY();
        if (!isIczAct2Indoor(cameraX, cameraY)) {
            return 0;
        }
        int d0 = ((short) cameraX) << 16;
        d0 >>= 1;
        int d1 = d0 >> 3;
        for (int i = 0; i < 5; i++) {
            d0 -= d1;
        }
        return (short) (d0 >> 16);
    }

    private int resolveIczAct1BgCameraY() {
        int cameraY = getCameraY();
        int cameraX = getCameraX() & 0xFFFF;
        IczZoneRuntimeState state = currentIczState();
        if (state != null) {
            return state.iczAct1BgCameraY(cameraX, cameraY);
        }
        if (cameraX < 0x3940) {
            return asrWordValue(cameraY, 7);
        }
        return asrWordValue(cameraY, 1);
    }

    private boolean isIczAct2Indoor(int cameraX, int cameraY) {
        int x = cameraX & 0xFFFF;
        int y = cameraY & 0xFFFF;
        if (x >= 0x1900 && x < 0x1B80) {
            return true;
        }
        return x >= 0x1000 && x < 0x3600 && y >= 0x720;
    }

    /**
     * ROM: AnimateTiles_ICZ direct horizontal DMA path. It copies one or two
     * split slices from ArtUnc_AniICZ__1 into tiles $10E-$11D.
     */
    private void updateIczHorizontalTiles() {
        if (iczArt1Data == null) {
            return;
        }

        int phase = computeIczHorizontalPhase();
        int baseOffset = ror16(phase & 7, 7);
        int splitBits = phase & 0x18;
        int primarySourceOffset = baseOffset + (splitBits << 4);
        int pairIndex = splitBits >> 2;
        int firstWordCount = ICZ_HORIZONTAL_DMA_WORD_COUNTS[pairIndex];
        int secondWordCount = ICZ_HORIZONTAL_DMA_WORD_COUNTS[pairIndex + 1];

        applyRawPatternSliceToLevel(iczArt1Data, primarySourceOffset, firstWordCount << 1, 0x10E);
        if (secondWordCount != 0) {
            int secondDestTile = 0x10E + ((firstWordCount << 1) / Pattern.PATTERN_SIZE_IN_ROM);
            applyRawPatternSliceToLevel(iczArt1Data, baseOffset, secondWordCount << 1, secondDestTile);
        }
    }

    /**
     * ROM: AnimateTiles_ICZ act-1 vertical DMA path. These row-offset copies
     * upload the indoor BG pieces at tiles $122-$130.
     */
    private void updateIczAct1VerticalTiles() {
        if (!shouldRunIczAct1VerticalCustomChannels()) {
            return;
        }

        int bgCameraY = resolveIczAct1BgCameraY();
        int eventsBg12 = asrWordValue(bgCameraY, 1);

        int phase2 = (-(bgCameraY - eventsBg12)) & 0x3F;
        applyRawPatternSliceAllowingRowOffset(iczArt2Data, phase2 << 2, 0x100, 0x122);

        int halfEvents = asrWordValue(eventsBg12, 1);
        int quarterEvents = asrWordValue(halfEvents, 1);
        int phase3 = (-(bgCameraY - halfEvents - quarterEvents)) & 0x1F;
        applyRawPatternSliceAllowingRowOffset(iczArt3Data, phase3 << 2, 0x080, 0x12A);

        int phase4 = (-(bgCameraY - halfEvents)) & 0x0F;
        applyRawPatternSliceAllowingRowOffset(iczArt4Data, phase4 << 2, 0x040, 0x12E);

        int phase5 = (-(bgCameraY - asrWordValue(eventsBg12, 2))) & 0x07;
        applyRawPatternSliceAllowingRowOffset(iczArt5Data, phase5 << 2, 0x020, 0x130);
    }

    /**
     * ROM: AnimateTiles_CNZ phase formula.
     *
     * <p>CNZ derives its direct-DMA phase from the same shared-state edge that
     * Task 3 published through the runtime adapter:
     * {@code (Events_bg+$10 - Camera_X_pos_BG_copy) & $3F}. This keeps the
     * animated tiles tied to the actual deform math instead of a free-running
     * local counter.
     */
    int computeCnzPhase() {
        if (!GameServices.hasRuntime()) {
            return 0;
        }
        CnzZoneRuntimeState state = GameServices.zoneRuntimeRegistry()
                .currentAs(CnzZoneRuntimeState.class)
                .orElse(null);
        if (state == null) {
            return 0;
        }
        return (state.deformPhaseBgX() - state.publishedBgCameraX()) & 0x3F;
    }

    int computeLbzSharedPhase(int channelFrameCounter) {
        return (channelFrameCounter >>> 2) & 0x0F;
    }

    private boolean isLbzAlarmAnimationActive() {
        LbzZoneRuntimeState state = currentLbzState();
        return state != null && state.isAlarmAnimationActive();
    }

    private boolean isLbz2RideAnimatedTileGateActive() {
        LbzZoneRuntimeState state = currentLbzState();
        return state != null && state.isLbz2RideAnimatedTileGateActive();
    }

    private LbzZoneRuntimeState currentLbzState() {
        if (!GameServices.hasRuntime()) {
            return null;
        }
        return S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElse(null);
    }

    int computeLbz1ScrollPhase() {
        int cameraX = getCameraX();
        int eventsBg10 = asrWordValue(cameraX, 5) + 0x0A;
        int bgCameraX = asrWordValue(cameraX, 4) + 0x0A;
        return (eventsBg10 - bgCameraX) & 0x1F;
    }

    int computeLbz2ScrollPhase() {
        LbzZoneRuntimeState state = currentLbzState();
        if (state != null) {
            return state.lbz2ScrollArtPhase();
        }
        int cameraX = getCameraX();
        int bgCameraX = resolveLbzBgCameraX(asrWordValue(cameraX, 1));
        int eventsBg12 = asrWordValue(cameraX, 1) - asrWordValue(cameraX, 4);
        return (eventsBg12 - bgCameraX) & 0x0F;
    }

    int computeLbz2WaterlinePhase() {
        return computeLbz2WaterlineDelta() & 0xFFFF;
    }

    private int resolveLbzBgCameraX(int fallback) {
        try {
            int bgCameraX = GameServices.parallax().getBgCameraX();
            if (bgCameraX != Integer.MIN_VALUE) {
                return bgCameraX;
            }
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.resolveLbzBgCameraX: " + e.getMessage());
        }
        return fallback;
    }

    private void updateLbz1ScrollTiles() {
        if (!shouldRunLbz1CustomChannels()) {
            return;
        }

        int phase = computeLbz1ScrollPhase();
        int lowPhase = phase & 7;
        int baseOffset = (lowPhase << 7) + (lowPhase << 9);
        int secondOffset = baseOffset;
        int splitBits = phase & 0x18;
        int primarySourceOffset = baseOffset + (splitBits << 2) + (splitBits << 4);
        int pairIndex = splitBits >> 2;
        int firstWordCount = LBZ1_SCROLL_WORD_COUNTS[pairIndex];
        int secondWordCount = LBZ1_SCROLL_WORD_COUNTS[pairIndex + 1];

        int destTile = 0x350;
        applyRawPatternSliceToLevel(lbz1ScrollData, primarySourceOffset, firstWordCount << 1, destTile);
        destTile += (firstWordCount << 1) / Pattern.PATTERN_SIZE_IN_ROM;
        if (secondWordCount != 0) {
            applyRawPatternSliceToLevel(lbz1ScrollData, secondOffset, secondWordCount << 1, destTile);
            destTile += (secondWordCount << 1) / Pattern.PATTERN_SIZE_IN_ROM;
        }
        applyRawPatternSliceToLevel(lbz1ScrollCapData, lowPhase << 5, 0x20, destTile);
    }

    private void updateLbz2ScrollTiles() {
        if (lbz2ScrollData == null) {
            return;
        }
        int phase = computeLbz2ScrollPhase();
        applyRawPatternSliceToLevel(lbz2ScrollData, phase << 6, 0x40, 0x2E3);
    }

    private void updateLbz2WaterlineTiles() {
        if (!shouldRunLbz2WaterlineChannel()) {
            return;
        }

        int delta = computeLbz2WaterlineDelta();
        if (delta == 0) {
            applyRawPatternSliceToLevel(lbz2LowerBgData, 0, 0x200, 0x2C3);
            applyRawPatternSliceToLevel(lbz2UpperBgData, 0, 0x200, 0x2D3);
            return;
        }

        if (delta < 0) {
            if (delta > -0x40) {
                applyLbz2DynamicWaterline(lbz2WaterlineBelowSourceData, (delta + 0x40) << 6, 0x2C3);
            } else {
                applyRawPatternSliceToLevel(lbz2WaterlineBelowData, 0, 0x200, 0x2C3);
            }
            applyRawPatternSliceToLevel(lbz2UpperBgData, 0, 0x200, 0x2D3);
            return;
        }

        applyRawPatternSliceToLevel(lbz2LowerBgData, 0, 0x200, 0x2C3);
        if (delta < 0x40) {
            applyLbz2DynamicWaterline(lbz2WaterlineAboveSourceData, (-delta + 0x40) << 6, 0x2D3);
        } else {
            applyRawPatternSliceToLevel(lbz2WaterlineAboveData, 0, 0x200, 0x2D3);
        }
    }

    private void applyLbz2DynamicWaterline(byte[] sourceData, int tableOffset, int destTile) {
        if (sourceData == null || lbzWaterlineScrollData == null
                || tableOffset < 0 || tableOffset + 0x40 > lbzWaterlineScrollData.length) {
            return;
        }

        byte[] composed = lbz2WaterlineComposeScratch;
        for (int i = 0; i < 0x40; i++) {
            int sourceByteOffset = (lbzWaterlineScrollData[tableOffset + i] & 0xFF) << 2;
            int sourceIndexA = sourceByteOffset;
            int sourceIndexB = 0x100 + sourceByteOffset;
            if (sourceIndexA + 4 > sourceData.length || sourceIndexB + 4 > sourceData.length) {
                return;
            }
            System.arraycopy(sourceData, sourceIndexA, composed, i << 2, 4);
            System.arraycopy(sourceData, sourceIndexB, composed, 0x100 + (i << 2), 4);
        }
        applyRawPatternBytesToLevel(composed, destTile);
    }

    private int computeLbz2WaterlineDelta() {
        LbzZoneRuntimeState state = currentLbzState();
        if (state != null) {
            return (short) state.lbz2WaterlinePhase();
        }
        int shake = 0;
        try {
            shake = GameServices.parallax().getShakeOffsetY();
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.computeLbz2WaterlineDelta: " + e.getMessage());
        }

        return computeLbz2WaterlineDeltaFromCamera(shake);
    }

    private int computeLbz2WaterlineDeltaFromCamera(int shake) {
        int relativeY = (short) (getCameraY() - shake - 0x5F0);
        int bgYFixed = (((short) relativeY) << 16) >> 1;
        int step = bgYFixed >> 3;
        bgYFixed -= step;
        bgYFixed -= step >> 2;
        int bgYWithoutBase = (short) (bgYFixed >> 16);
        return (short) (bgYWithoutBase - relativeY);
    }

    /**
     * ROM: {@code AnimateTiles_MHZ} first custom background phase.
     *
     * <p>The ROM reads {@code Events_bg+$12} and subtracts
     * {@code Camera_X_pos_BG_copy}, then masks with {@code $1F}. MHZ deform
     * publishes those values through {@link MhzZoneRuntimeState}.
     */
    int computeMhzBackgroundLayer1Phase() {
        MhzZoneRuntimeState state = currentMhzState();
        return state == null ? 0 : state.backgroundLayer1Phase();
    }

    /**
     * ROM: {@code AnimateTiles_MHZ} second custom background phase.
     *
     * <p>The ROM reads {@code Events_bg+$10} and subtracts
     * {@code Camera_X_pos_BG_copy}, then masks with {@code $3F}.
     */
    int computeMhzBackgroundLayer2Phase() {
        MhzZoneRuntimeState state = currentMhzState();
        return state == null ? 0 : state.backgroundLayer2Phase();
    }

    /**
     * ROM: {@code loc_282D0} channel 0 phase.
     *
     * <p>{@code moveq #0,d0 / move.w (Events_bg+$12),d0 / sub.w (Camera_X_pos_BG_copy),d0 /
     * subq.w #1,d0 / divu.w #$30,d0 / swap d0} (sonic3k.asm:55056-55062). The subtraction is a
     * word operation on a zero-extended long, so a negative difference wraps to a large unsigned
     * value before the division: this is an unsigned remainder of the 16-bit difference, not a
     * signed modulo, and {@code $10000 mod $30} is not 0.
     */
    int computeLrzBackgroundLayer1Phase() {
        LrzZoneRuntimeState state = currentLrzState();
        if (state == null) {
            return 0;
        }
        int difference = (state.animationPhaseX1() - state.backgroundCameraX() - 1) & 0xFFFF;
        return difference % 0x30;
    }

    /**
     * ROM: {@code loc_28364} channel 1 phase, {@code (Events_bg+$10 - Camera_X_pos_BG_copy) & $1F}
     * (sonic3k.asm:55121-55126). No {@code -1} and no division.
     */
    int computeLrzBackgroundLayer2Phase() {
        LrzZoneRuntimeState state = currentLrzState();
        if (state == null) {
            return 0;
        }
        return (state.animationPhaseX0() - state.backgroundCameraX()) & 0x1F;
    }

    private LrzZoneRuntimeState currentLrzState() {
        if (!GameServices.hasRuntime()) {
            return null;
        }
        return S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElse(null);
    }

    private MhzZoneRuntimeState currentMhzState() {
        if (!GameServices.hasRuntime()) {
            return null;
        }
        return S3kRuntimeStates.currentMhz(GameServices.zoneRuntimeRegistry())
                .orElse(null);
    }

    private IczZoneRuntimeState currentIczState() {
        if (!GameServices.hasRuntime()) {
            return null;
        }
        return S3kRuntimeStates.currentIcz(GameServices.zoneRuntimeRegistry())
                .orElse(null);
    }

    private void updateMhzBackgroundLayer1() {
        if (mhzBg1Data == null) {
            return;
        }

        int phase = computeMhzBackgroundLayer1Phase();
        if (phase == lastMhzBg1Phase) {
            return;
        }
        lastMhzBg1Phase = phase;

        int baseSourceOffset = (phase & 7) << 8;
        int bandBits = phase & 0x18;
        int primarySourceOffset = baseSourceOffset + (bandBits << 3);
        int pairIndex = bandBits >> 2;
        applySplitRawPatternDma(mhzBg1Data, primarySourceOffset, baseSourceOffset,
                MHZ_BG1_DMA_WORD_COUNTS[pairIndex], MHZ_BG1_DMA_WORD_COUNTS[pairIndex + 1], 0x1B8);
    }

    private void updateMhzBackgroundLayer2() {
        if (mhzBg2Data == null) {
            return;
        }

        int phase = computeMhzBackgroundLayer2Phase();
        if (phase == lastMhzBg2Phase) {
            return;
        }
        lastMhzBg2Phase = phase;

        int baseSourceOffset = ror16(phase & 7, 6);
        int bandBits = phase & 0x38;
        int primarySourceOffset = baseSourceOffset + (bandBits << 4);
        int pairIndex = bandBits >> 2;
        applySplitRawPatternDma(mhzBg2Data, primarySourceOffset, baseSourceOffset,
                MHZ_BG2_DMA_WORD_COUNTS[pairIndex], MHZ_BG2_DMA_WORD_COUNTS[pairIndex + 1], 0x1D5);
    }

    /**
     * ROM: {@code loc_282D0} channel 0 (sonic3k.asm:55055-55095).
     *
     * <p>{@code phase & 7} picks one $480-byte frame; {@code phase & $38} rotates the read start
     * within it by {@code band * $C0} bytes and selects the matching {@code word_2834C} pair, so
     * the two transfers wrap the frame around the $24-tile destination.
     */
    private void updateLrzBackgroundLayer1() {
        if (lrzBg1Data == null) {
            return;
        }
        int phase = computeLrzBackgroundLayer1Phase();
        if (phase == lastLrzBg1Phase) {
            return;
        }
        lastLrzBg1Phase = phase;

        int frameSourceOffset = (phase & 7) * 0x480;
        int bandBits = phase & 0x38;
        int primarySourceOffset = frameSourceOffset + bandBits * 24;
        int pairIndex = bandBits >> 2;
        applySplitRawPatternDma(lrzBg1Data, primarySourceOffset, frameSourceOffset,
                LRZ_BG1_SPLIT_WORD_COUNTS[pairIndex], LRZ_BG1_SPLIT_WORD_COUNTS[pairIndex + 1],
                LRZ_BG1_DEST_TILE);
    }

    /**
     * ROM: {@code loc_28364} channel 1 (sonic3k.asm:55121-55167), the same shape over
     * $180-byte frames rotated by {@code band * $60} bytes.
     */
    private void updateLrzBackgroundLayer2() {
        if (lrzBg2Data == null) {
            return;
        }
        int phase = computeLrzBackgroundLayer2Phase();
        if (phase == lastLrzBg2Phase) {
            return;
        }
        lastLrzBg2Phase = phase;

        int frameSourceOffset = (phase & 7) * 0x180;
        int bandBits = phase & 0x18;
        int primarySourceOffset = frameSourceOffset + bandBits * 12;
        int pairIndex = bandBits >> 2;
        applySplitRawPatternDma(lrzBg2Data, primarySourceOffset, frameSourceOffset,
                LRZ_BG2_SPLIT_WORD_COUNTS[pairIndex], LRZ_BG2_SPLIT_WORD_COUNTS[pairIndex + 1],
                LRZ_BG2_DEST_TILE);
    }

    void updateLrzBackgroundLayer1ForGraph() {
        updateLrzBackgroundLayer1();
    }

    void updateLrzBackgroundLayer2ForGraph() {
        updateLrzBackgroundLayer2();
    }

    boolean shouldRunLrzBackgroundLayer1Channel() {
        return lrzBg1Data != null;
    }

    /**
     * {@code loc_2833C} returns before channel 1 when {@code Current_zone} is {@code $16}
     * (sonic3k.asm:55096-55099), so the boss act runs channel 0 only.
     */
    boolean shouldRunLrzBackgroundLayer2Channel() {
        return lrzBg2Data != null && zoneIndex != Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ;
    }

    private void applySplitRawPatternDma(byte[] sourceData, int primarySourceOffset,
                                         int secondSourceOffset, int firstWordCount,
                                         int secondWordCount, int destTile) {
        int firstByteCount = firstWordCount << 1;
        applyRawPatternSliceToLevel(sourceData, primarySourceOffset, firstByteCount, destTile);
        if (secondWordCount != 0) {
            int secondDestTile = destTile + (firstByteCount / Pattern.PATTERN_SIZE_IN_ROM);
            applyRawPatternSliceToLevel(sourceData, secondSourceOffset, secondWordCount << 1, secondDestTile);
        }
    }

    /**
     * ROM: AnimateTiles_CNZ direct background DMA path.
     *
     * <p>The ROM uses {@code phase & 7} to choose the base 1 KB source bank,
     * {@code phase & $38} to select one of eight split layouts from
     * {@code word_27C9C}, and then DMA-copies one or two word-count segments
     * from {@code ArtUnc_AniCNZ__6} into VRAM tile {@code $308+}. The engine
     * mirrors that by copying raw pattern slices into the level pattern buffer
     * so the graph can own CNZ's direct-DMA destination range.
     */
    private void updateCnzBackgroundTiles() {
        if (cnzBgData == null) {
            return;
        }

        int phase = computeCnzPhase();
        int baseSourceOffset = ror16(phase & 7, 6);
        int bandBits = phase & 0x38;
        int primarySourceOffset = baseSourceOffset + (bandBits << 4);
        int pairIndex = bandBits >> 2;
        int firstWordCount = CNZ_DMA_WORD_COUNTS[pairIndex];
        int secondWordCount = CNZ_DMA_WORD_COUNTS[pairIndex + 1];

        applyRawPatternSliceToLevel(cnzBgData, primarySourceOffset, firstWordCount << 1, 0x308);
        if (secondWordCount != 0) {
            int secondDestTile = 0x308 + ((firstWordCount << 1) / Pattern.PATTERN_SIZE_IN_ROM);
            applyRawPatternSliceToLevel(cnzBgData, baseSourceOffset, secondWordCount << 1, secondDestTile);
        }
    }

    private void updateSoz1BackgroundTiles() {
        if (soz1BgData == null || soz1Bg2Data == null) {
            return;
        }

        int phase = computeSoz1Phase();
        int lowPhase = phase & 7;
        int splitBits = phase & 0x18;
        int baseOffset = lowPhase * 0x180;
        int mainOffset = baseOffset + (splitBits * 12);
        int splitIndex = splitBits >> 2;
        int firstWordCount = SOZ1_SPLIT_WORD_COUNTS[splitIndex];
        int secondWordCount = SOZ1_SPLIT_WORD_COUNTS[splitIndex + 1];

        applyRawPatternSliceToLevel(soz1BgData, mainOffset, firstWordCount << 1, 0x330);
        if (secondWordCount != 0) {
            int secondDestTile = 0x330 + ((firstWordCount << 1) / Pattern.PATTERN_SIZE_IN_ROM);
            applyRawPatternSliceToLevel(soz1BgData, baseOffset, secondWordCount << 1, secondDestTile);
        }

        applyRawPatternSliceToLevel(soz1Bg2Data, phase * 0x0C0, 0x0C0, 0x33C);
    }

    private void ensureHczPatternCapacity() {
        if (zoneIndex == 1 && actIndex == 0) {
            level.ensurePatternCapacity(0x300 + (Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE
                    / Pattern.PATTERN_SIZE_IN_ROM));
        } else if (zoneIndex == 1 && actIndex == 1) {
            level.ensurePatternCapacity(0x2EE + (Sonic3kConstants.ART_UNC_HCZ2_4_SIZE
                    / Pattern.PATTERN_SIZE_IN_ROM));
        }
    }

    private void ensurePachinkoPatternCapacity() {
        if (zoneIndex == 0x14) {
            level.ensurePatternCapacity(PACHINKO_BG_DEST_TILE
                    + (PACHINKO_BG_DMA_BYTES / Pattern.PATTERN_SIZE_IN_ROM));
        }
    }

    private void ensureIczPatternCapacity() {
        if (zoneIndex == 0x05) {
            level.ensurePatternCapacity(0x131);
        }
    }

    private void ensureLbzPatternCapacity() {
        if (zoneIndex == 0x06) {
            level.ensurePatternCapacity(0x36D);
        }
    }

    private void ensureMhzPatternCapacity() {
        if (zoneIndex == 0x07) {
            level.ensurePatternCapacity(0x1F5);
        }
    }

    /**
     * {@code Animate_Init}: {@code $900} (sonic3k.asm:56411-56414) and {@code $1600}
     * (56458-56461) write {@code -1} to {@code Anim_Counters+1} and {@code +3}. {@code $901} is
     * absent from that list, so it runs with the counters the level load cleared.
     */
    private static boolean seedsLrzAnimationCounters(int zoneIndex, int actIndex) {
        return (zoneIndex == Sonic3kZoneIds.ZONE_LRZ && actIndex == 0)
                || (zoneIndex == Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ && actIndex == 0);
    }

    /**
     * {@code AnimateTiles_LRZ1}/{@code LRZ2} source art. Both playable acts read the same two
     * uncompressed blocks; the boss act reuses them at different destination tiles.
     */
    private void loadLrzRawArt(RomByteReader reader) {
        if (zoneIndex != Sonic3kZoneIds.ZONE_LRZ) {
            return;
        }
        lrzBg1Data = loadRawBytes(reader, ART_UNC_ANI_LRZ_BG_ADDR, ART_UNC_ANI_LRZ_BG_SIZE);
        lrzBg2Data = loadRawBytes(reader, ART_UNC_ANI_LRZ_BG2_ADDR, ART_UNC_ANI_LRZ_BG2_SIZE);
        level.ensurePatternCapacity(LRZ_ANIMATED_TILE_CAPACITY);
    }

    private void loadLbzRawArt(RomByteReader reader) {
        if (zoneIndex != 0x06) {
            return;
        }

        lbzSharedData = loadRawBytes(reader,
                Sonic3kConstants.ART_UNC_ANI_LBZ_SHARED_ADDR,
                Sonic3kConstants.ART_UNC_ANI_LBZ_SHARED_SIZE);
        if (actIndex == 0) {
            lbz1ScrollData = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_LBZ1_1_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_LBZ1_1_SIZE);
            lbz1ScrollCapData = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_ANI_LBZ1_2_ADDR,
                    Sonic3kConstants.ART_UNC_ANI_LBZ1_2_SIZE);
            return;
        }

        lbz2ScrollData = loadRawBytes(reader,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_2_ADDR,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_2_SIZE);
        lbz2WaterlineBelowData = loadRawBytes(reader,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_WATERLINE_BELOW_ADDR,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_WATERLINE_BELOW_SIZE);
        lbz2LowerBgData = loadRawBytes(reader,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_LOWER_BG_ADDR,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_LOWER_BG_SIZE);
        lbz2WaterlineAboveData = loadRawBytes(reader,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_WATERLINE_ABOVE_ADDR,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_WATERLINE_ABOVE_SIZE);
        lbz2UpperBgData = loadRawBytes(reader,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_UPPER_BG_ADDR,
                Sonic3kConstants.ART_UNC_ANI_LBZ2_UPPER_BG_SIZE);
        lbz2WaterlineBelowSourceData = concatenateLbz2WaterlineSource(lbz2WaterlineBelowData, lbz2LowerBgData);
        lbz2WaterlineAboveSourceData = concatenateLbz2WaterlineSource(lbz2WaterlineAboveData, lbz2UpperBgData);
        lbzWaterlineScrollData = loadRawBytes(reader,
                Sonic3kConstants.LBZ_WATERLINE_SCROLL_DATA_ADDR,
                Sonic3kConstants.LBZ_WATERLINE_SCROLL_DATA_SIZE);
    }

    private static byte[] concatenateLbz2WaterlineSource(byte[] waterlineData, byte[] adjacentBgData) {
        if (waterlineData == null || adjacentBgData == null) {
            return null;
        }
        byte[] source = new byte[waterlineData.length + adjacentBgData.length];
        System.arraycopy(waterlineData, 0, source, 0, waterlineData.length);
        System.arraycopy(adjacentBgData, 0, source, waterlineData.length, adjacentBgData.length);
        return source;
    }

    private void bootstrapLbz2WaterlinePhase() {
        if (zoneIndex != Sonic3kZoneIds.ZONE_LBZ || actIndex != 1) {
            return;
        }
        LbzZoneRuntimeState state = currentLbzState();
        if (state == null
                || state.lbz2WaterlinePhase() != 0
                || state.lbz2ScrollArtPhaseSource() != 0
                || state.publishedBgCameraX() != 0) {
            return;
        }
        int waterlinePhase = computeLbz2WaterlineDeltaFromCamera(0);
        state.publishLbz2DeformOutputs(waterlinePhase, 0, 0);
    }

    private byte[] loadRawBytes(RomByteReader reader, int addr, int size) {
        if (addr + size > reader.size()) {
            return null;
        }
        return reader.slice(addr, size);
    }

    private Pattern[] loadUncompressedPatterns(RomByteReader reader, int addr, int size) {
        if (addr + size > reader.size()) {
            return null;
        }

        byte[] data = reader.slice(addr, size);
        Pattern[] patterns = new Pattern[size / Pattern.PATTERN_SIZE_IN_ROM];
        for (int i = 0; i < patterns.length; i++) {
            Pattern pattern = new Pattern();
            byte[] tileData = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(data, i * Pattern.PATTERN_SIZE_IN_ROM, tileData, 0,
                    Pattern.PATTERN_SIZE_IN_ROM);
            pattern.fromSegaFormat(tileData);
            patterns[i] = pattern;
        }
        return patterns;
    }

    int computeHcz1WaterlineDelta() {
        int delta = (short) (getCameraY() - HCZ1_EQUILIBRIUM_Y);
        int quarterDelta = delta >> 2;
        return (short) (quarterDelta - delta);
    }

    int computeHcz2CompositePhase() {
        int[] hScroll = buildHcz2HScrollValues(getCameraX());
        int eventsBg12 = hScroll[3] - hScroll[9];
        int eventsBg14 = hScroll[2] - hScroll[9];
        return ((eventsBg12 & 0x3F) << 8) | (eventsBg14 & 0x3F);
    }

    private int[] buildHcz2HScrollValues(int cameraX) {
        int[] hScroll = hcz2HScrollScratch;
        int d0 = ((short) cameraX) << 16;
        d0 >>= 1;
        int d1 = d0 >> 3;

        int pos = 0;
        while (pos < HCZ2_DEFORM_INDEX.length) {
            int count = HCZ2_DEFORM_INDEX[pos++];
            if ((count & 0x80) != 0) {
                break;
            }
            int value = (short) (d0 >> 16);
            for (int i = 0; i <= count; i++) {
                int byteOffset = HCZ2_DEFORM_INDEX[pos++];
                hScroll[byteOffset >> 1] = value;
            }
            d0 -= d1;
        }
        return hScroll;
    }

    private int getCameraX() {
        try {
            return GameServices.camera().getX();
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.getCameraX: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Gumball bonus stage animated tiles.
     * ROM: AnimateTiles_Gumball (sonic3k.asm:55266).
     * <p>
     * Computes a scroll index from (Events_bg+$10 - Camera_Y_BG) &amp; $1F,
     * uses it as a byte offset into ArtUnc_AniGumball, then DMA copies
     * 4 tiles ($40 words = 128 bytes) to VRAM tile $54. Creates a vertical
     * scrolling effect in the background tile art.
     */
    private void updateGumball() {
        if (gumballAniData == null) {
            return;
        }
        // ROM: d1 = (Events_bg+$10) - Camera_Y_pos_BG_copy
        // For the bonus stage, Events_bg+$10 is the BG event Y offset.
        // The camera is locked in the gumball bonus stage, so we drive the
        // animation from a per-frame counter instead of camera Y. This
        // produces constant vertical scrolling of the BG tile art.
        gumballFrameCounter = (gumballFrameCounter + 1) & 0x1F;
        int index = gumballFrameCounter;

        if (index == lastGumballIndex) {
            return; // No change — skip DMA
        }
        lastGumballIndex = index;

        // ROM: d1 = d1 * 4; source = ArtUnc_AniGumball + d1
        // Each scroll step shifts the source offset by 4 bytes = 1 tile row (8 pixels wide,
        // 4bpp = 4 bytes/row). Source data is 256 bytes and wraps cyclically to create a
        // scrolling stripes animation. We read `i * PATTERN_SIZE_IN_ROM` into the data,
        // wrapping on overflow so the tile pattern loops.
        int sourceByteOffset = index * 4;

        // Copy 4 tiles from source offset to level patterns at tile 0x54, wrapping the
        // source data on overflow.
        level.ensurePatternCapacity(GUMBALL_DEST_TILE + GUMBALL_TILE_COUNT);
        GraphicsManager graphicsManager = GameServices.graphics();
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        byte[] tileData = rawTileScratchBytes;
        graphicsManager.beginPatternAtlasBatch();
        try {
            for (int i = 0; i < GUMBALL_TILE_COUNT; i++) {
                int baseOffset = (sourceByteOffset + i * Pattern.PATTERN_SIZE_IN_ROM) % GUMBALL_SOURCE_SIZE;
                int destIndex = GUMBALL_DEST_TILE + i;
                if (destIndex >= level.getPatternCount()) {
                    break;
                }
                // Read one tile worth of bytes with wrap-around on source data.
                for (int b = 0; b < Pattern.PATTERN_SIZE_IN_ROM; b++) {
                    tileData[b] = gumballAniData[(baseOffset + b) % GUMBALL_SOURCE_SIZE];
                }
                Pattern dest = level.getPattern(destIndex);
                dest.fromSegaFormat(tileData);
                if (canUpdateTextures) {
                    graphicsManager.updatePatternTexture(dest, destIndex);
                }
            }
        } finally {
            graphicsManager.endPatternAtlasBatch();
        }
    }

    private void updatePachinko() {
        if (pachinkoScratch == null || pachinkoLowSource == null || pachinkoHighSource == null) {
            return;
        }

        if (pachinkoPhase == 0) {
            rebuildPachinkoScratch();
            pachinkoPhase = 1;
        } else {
            applyPachinkoBlendPhase(pachinkoPhase);
            pachinkoPhase++;
            if (pachinkoPhase >= 4) {
                pachinkoPhase = 0;
                pachinkoSourceOffset += 0x280;
                if (pachinkoSourceOffset >= PACHINKO_LOW_SOURCE_BYTES) {
                    pachinkoSourceOffset = 0;
                }
                applyRawPatternSliceToLevel(pachinkoScratch, 0, PACHINKO_BG_DMA_BYTES,
                        PACHINKO_BG_DEST_TILE);
            }
        }
    }

    private void rebuildPachinkoScratch() {
        int sourceOffset = pachinkoStripeOffset & 0x7F;
        pachinkoStripeOffset = (pachinkoStripeOffset - 4) & 0x7F;

        int destPos = 0;
        int sourcePos = sourceOffset;
        for (int group = 0; group < 3; group++) {
            for (int row = 0; row < 4; row++) {
                System.arraycopy(pachinkoHighSource, sourcePos, pachinkoScratch, destPos, 0x80);
                destPos += 0x80;
                sourcePos += 0x100;
            }
            destPos += 0x80;
        }

        int writePos = 0x200;
        int offsetIndex = 0;
        for (int group = 0; group < 3; group++) {
            for (int row = 0; row < 4; row++) {
                int a1 = PACHINKO_COPY_OFFSETS[offsetIndex];
                int a2 = PACHINKO_MIX_OFFSETS[offsetIndex];
                int maskA = 0xFFFFFFFF;
                int maskB = 0x00000000;
                for (int column = 0; column < 8; column++) {
                    int mixed = (readLongBE(pachinkoScratch, a1) & maskA)
                            | (readLongBE(pachinkoScratch, a2) & maskB);
                    writeLongBE(pachinkoScratch, writePos, mixed);
                    a1 += 4;
                    a2 += 4;
                    writePos += 4;
                    maskA <<= 4;
                    maskB = (maskB << 4) | 0x0F;
                }
                offsetIndex++;
            }
            writePos += 0x200;
        }
    }

    private void applyPachinkoBlendPhase(int phase) {
        int phaseIndex = phase - 1;
        if (phaseIndex < 0 || phaseIndex > 2) {
            return;
        }

        int writePos = phaseIndex * 0x280;
        int paramPos = phaseIndex * 4;
        int addHigh = PACHINKO_PHASE_PARAMS[paramPos];
        int addLow = PACHINKO_PHASE_PARAMS[paramPos + 1];
        int subHigh = PACHINKO_PHASE_PARAMS[paramPos + 2];
        int subLow = PACHINKO_PHASE_PARAMS[paramPos + 3];
        int readPos = pachinkoSourceOffset;

        for (int i = 0; i < 0x200; i++) {
            int source = pachinkoLowSource[readPos++] & 0xFF;
            if (source != 0) {
                int high = source & 0xF0;
                if (high != 0) {
                    pachinkoScratch[writePos] = (byte) ((pachinkoScratch[writePos] & 0x0F)
                            | ((high + addHigh) & 0xF0));
                }
                int low = source & 0x0F;
                if (low != 0) {
                    pachinkoScratch[writePos] = (byte) ((pachinkoScratch[writePos] & 0xF0)
                            | ((low + addLow) & 0x0F));
                }
            }
            writePos++;
        }

        for (int i = 0; i < 0x80; i++) {
            int source = pachinkoLowSource[readPos++] & 0xFF;
            if (source != 0) {
                int high = source & 0xF0;
                if (high != 0) {
                    int value = ((source & 0x80) == 0)
                            ? ((high + addHigh) & 0xF0)
                            : ((high - subHigh) & 0xF0);
                    pachinkoScratch[writePos] = (byte) ((pachinkoScratch[writePos] & 0x0F) | value);
                }
                int low = source & 0x0F;
                if (low != 0) {
                    int value = ((low & 0x08) == 0)
                            ? ((low + addLow) & 0x0F)
                            : ((low - subLow) & 0x0F);
                    pachinkoScratch[writePos] = (byte) ((pachinkoScratch[writePos] & 0xF0) | value);
                }
            }
            writePos++;
        }
    }

    private int getCameraY() {
        try {
            return GameServices.camera().getY();
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.getCameraY: " + e.getMessage());
            return 0;
        }
    }

    private static int resolveAniPlcAddr(int zoneIndex, int actIndex) {
        return switch (zoneIndex) {
            case 0 -> actIndex == 0
                    ? Sonic3kConstants.ANIPLC_AIZ1_ADDR
                    : Sonic3kConstants.ANIPLC_AIZ2_ADDR;
            case 1 -> actIndex == 0
                    ? Sonic3kConstants.ANIPLC_HCZ1_ADDR
                    : Sonic3kConstants.ANIPLC_HCZ2_ADDR;
            case 2 -> Sonic3kConstants.ANIPLC_MGZ_ADDR;
            case 0x03 -> Sonic3kConstants.ANIPLC_CNZ_ADDR;
            case 0x04 -> actIndex == 0
                    ? Sonic3kConstants.ANIPLC_FBZ1_ADDR
                    : Sonic3kConstants.ANIPLC_FBZ2_ADDR;
            case 0x05 -> Sonic3kConstants.ANIPLC_ICZ_ADDR;
            case 0x06 -> actIndex == 0
                    ? Sonic3kConstants.ANIPLC_LBZ1_ADDR
                    : Sonic3kConstants.ANIPLC_LBZ2_ADDR;
            case 0x07 -> Sonic3kConstants.ANIPLC_MHZ_ADDR;
            // Offs_AniFunc pairs (AnimateTiles, AniPLC) per act: both Sandopolis acts and LRZ1
            // take AniPLC_LRZ1, LRZ2 takes AniPLC_LRZ2 (sonic3k.asm:53873-53880).
            case 0x08 -> ANIPLC_LRZ1_ADDR;
            case Sonic3kZoneIds.ZONE_LRZ -> actIndex == 0 ? ANIPLC_LRZ1_ADDR : ANIPLC_LRZ2_ADDR;
            case 0x14 -> Sonic3kConstants.ANIPLC_PACHINKO_ADDR;
            // Offs_AniFunc pairs for $1601 (Hidden Palace) and $1701 (sanctuary) are
            // AnimateTiles_DoAniPLC / AniPLC_HPZ; $1600 and $1700 have no AniPLC
            // script (sonic3k.asm AnimateTiles table, entries 44-47).
            case 0x16, 0x17 -> actIndex == 1 ? Sonic3kConstants.ANIPLC_HPZ_ADDR : -1;
            default -> -1;
        };
    }

    private void installGraphChannels() {
        if (zoneIndex == 2) {
            graph.install(S3kAnimatedTileChannels.buildMgzChannels(this, scripts));
            return;
        }
        if (zoneIndex == 1) {
            graph.install(S3kAnimatedTileChannels.buildHczChannels(this, scripts, actIndex));
            return;
        }
        if (zoneIndex == 0x08) {
            graph.install(S3kAnimatedTileChannels.buildSozChannels(this, actIndex));
            return;
        }
        if (zoneIndex == 0x03) {
            graph.install(S3kAnimatedTileChannels.buildCnzChannels(this, scripts));
            return;
        }
        if (zoneIndex == 0x04) {
            graph.install(S3kAnimatedTileChannels.buildFbzChannels(this, scripts));
            return;
        }
        if (zoneIndex == 0x06) {
            graph.install(S3kAnimatedTileChannels.buildLbzChannels(this, scripts, actIndex, lbzRegularScriptCount));
            return;
        }
        if (zoneIndex == 0x07) {
            graph.install(S3kAnimatedTileChannels.buildMhzChannels(this, scripts));
            return;
        }
        if (zoneIndex == 0x05) {
            graph.install(S3kAnimatedTileChannels.buildIczChannels(this, scripts, actIndex));
            return;
        }
        if (zoneIndex == Sonic3kZoneIds.ZONE_LRZ) {
            graph.install(S3kAnimatedTileChannels.buildLrzChannels(this, scripts));
            return;
        }
        graph.install(List.of());
    }

    /** Package-level deterministic inspection used by ROM AniPLC conformance tests. */
    List<AniPlcScriptState> scriptsForTesting() {
        return scripts;
    }

    private static int word(int value) {
        return value & 0xFFFF;
    }

    private static int asrWordValue(int value, int bits) {
        return (short) value >> bits;
    }

    private static int negateWord(int value) {
        return word(-value);
    }

    private static int ror16(int value, int bits) {
        int masked = value & 0xFFFF;
        int shift = bits & 15;
        return ((masked >>> shift) | (masked << (16 - shift))) & 0xFFFF;
    }

    private static int readLongBE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static void writeLongBE(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >>> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    private byte[] loadKosinskiBytes(RomByteReader reader, int addr, int compressedSize,
                                     int minimumDecompressedSize) {
        if (addr < 0 || addr + compressedSize > reader.size()) {
            return null;
        }
        byte[] compressed = reader.slice(addr, compressedSize);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed)) {
            byte[] decompressed = KosinskiReader.decompress(Channels.newChannel(bais), false);
            if (decompressed.length < minimumDecompressedSize) {
                LOG.warning(() -> String.format(
                        "Pachinko Kosinski resource too short at 0x%X: expected >= 0x%X bytes, got 0x%X",
                        addr, minimumDecompressedSize, decompressed.length));
                return null;
            }
            return decompressed;
        } catch (IOException e) {
            LOG.warning(() -> String.format(
                    "Failed to decompress Pachinko Kosinski resource at 0x%X: %s", addr, e.getMessage()));
            return null;
        }
    }

    private byte[] buildPachinkoHighSource(byte[] source) {
        if (source == null || source.length < 0x600) {
            return null;
        }
        byte[] expanded = new byte[PACHINKO_HIGH_SOURCE_BYTES];
        int sourcePos = 0;
        int destPos = 0;
        for (int row = 0; row < 0x0C; row++) {
            for (int i = 0; i < 0x80; i += 4) {
                System.arraycopy(source, sourcePos, expanded, destPos + 0x80 + i, 4);
                System.arraycopy(source, sourcePos, expanded, destPos + i, 4);
                sourcePos += 4;
            }
            destPos += 0x100;
        }
        return expanded;
    }

    // --- RewindSnapshottable<PatternAnimatorSnapshot> ---

    @Override
    public String key() {
        return "pattern-animator";
    }

    @Override
    public com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot capture() {
        // AniPLC script counters
        com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot.ScriptCounter[] sc =
                new com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot.ScriptCounter[scripts.size()];
        for (int i = 0; i < scripts.size(); i++) {
            AniPlcScriptState s = scripts.get(i);
            sc[i] = new com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot.ScriptCounter(
                    s.getTimer(), s.getFrameIndex());
        }
        // Scalar state packed into extra blob (53 bytes: 1 bool + 13 ints)
        byte[] publicationState = aniPlcPublications.capture(this::presentedAniPlcPattern);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(SCALAR_BLOB_BYTES + publicationState.length);
        buf.put((byte) (firstTreeApplied ? 1 : 0));
        buf.putInt(lastHcz1WaterlineDelta);
        buf.putInt(lastHcz2SmallBgLineValue);
        buf.putInt(lastHcz2Art2Value);
        buf.putInt(lastHcz2Art3Value);
        buf.putInt(lastHcz2Art4Value);
        buf.putInt(lastMhzBg1Phase);
        buf.putInt(lastMhzBg2Phase);
        buf.putInt(pachinkoPhase);
        buf.putInt(pachinkoSourceOffset);
        buf.putInt(pachinkoStripeOffset);
        buf.putInt(frameCounter);
        buf.putInt(lastGumballIndex);
        buf.putInt(gumballFrameCounter);
        buf.putInt(lastLrzBg1Phase);
        buf.putInt(lastLrzBg2Phase);
        buf.put(publicationState);
        return new com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot(
                sc,
                new com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot.HandlerCounter[0],
                buf.array());
    }

    @Override
    public void restore(com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot snap) {
        // Restore AniPLC script counters
        com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot.ScriptCounter[] sc = snap.scriptCounters();
        int restoreCount = Math.min(sc.length, scripts.size());
        for (int i = 0; i < restoreCount; i++) {
            scripts.get(i).restoreCounters(sc[i].timer(), sc[i].frameIndex());
        }
        // Restore scalar state from extra blob
        byte[] extra = snap.extra();
        if (extra != null && extra.length >= 45) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(extra);
            firstTreeApplied     = buf.get() != 0;
            lastHcz1WaterlineDelta   = buf.getInt();
            lastHcz2SmallBgLineValue = buf.getInt();
            lastHcz2Art2Value        = buf.getInt();
            lastHcz2Art3Value        = buf.getInt();
            lastHcz2Art4Value        = buf.getInt();
            if (extra.length >= 53) {
                lastMhzBg1Phase       = buf.getInt();
                lastMhzBg2Phase       = buf.getInt();
            }
            pachinkoPhase            = buf.getInt();
            pachinkoSourceOffset     = buf.getInt();
            pachinkoStripeOffset     = buf.getInt();
            frameCounter             = buf.getInt();
            lastGumballIndex         = buf.getInt();
            gumballFrameCounter      = buf.getInt();
            int scalarEnd = 53;
            if (extra.length >= SCALAR_BLOB_BYTES) {
                lastLrzBg1Phase   = buf.getInt();
                lastLrzBg2Phase   = buf.getInt();
                scalarEnd = SCALAR_BLOB_BYTES;
            }
            if (extra.length > scalarEnd) {
                aniPlcPublications.restore(
                        java.util.Arrays.copyOfRange(extra, scalarEnd, extra.length),
                        this::publishAniPlcPayload);
            } else {
                aniPlcPublications.clear();
            }
        }
    }
}
