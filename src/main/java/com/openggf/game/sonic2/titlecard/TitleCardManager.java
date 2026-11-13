package com.openggf.game.sonic2.titlecard;

import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.sonic2.resources.Sonic2RuntimePlcPublisher;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;

import com.openggf.game.titlecard.TitleCardMappings;
import com.openggf.game.titlecard.TitleCardLoopTail;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.SessionManager;

import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.ZoneKey;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.ZoneRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.game.titlecard.TitleCardSpriteRenderer;
import com.openggf.level.Pattern;
import com.openggf.util.PatternDecompressor;

import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the Sonic 2 title card display.
 *
 * <p>The title card appears:
 * <ul>
 *   <li>When a level first loads</li>
 *   <li>After the player loses a life and respawns</li>
 *   <li>When returning from a special stage</li>
 * </ul>
 *
 * <p>State machine:
 * <pre>
 * SLIDE_IN → DISPLAY → SLIDE_OUT → COMPLETE
 * </pre>
 */
public class TitleCardManager implements TitleCardProvider, TitleCardLoopTail,
        com.openggf.game.internal.LockedTitleCardLevelStepGate,
        RewindSnapshottable<TitleCardManager.Snapshot> {
    private static final Logger LOGGER = Logger.getLogger(TitleCardManager.class.getName());
    public static final String REWIND_KEY = "s2-title-card";

    private static TitleCardManager instance;

    /*
     * The leave sequence's length is fixed by the Obj34 leave routines, not by
     * how far this engine's overlay elements happen to have travelled. ROM
     * order (docs/s2disasm/s2.asm:4913-5066):
     *
     *   Level_TtlCard's scroll-in wait loop (:4914-4925) runs before
     *   InitPlayers (:4945), so the players do not exist for it at all.
     *   :5003-5006 then run ObjectsManager / RingsManager /
     *   SpecialCNZBumpers / RunObjects once -- one player object pass with no
     *   WaitForVint of its own. :5056-5058 arm the leave flags
     *   (TitleCard_ZoneName titlecard_leaveflag = -1, TitleCard_Left routine
     *   $E, titlecard_location $A) and :5060-5066 loop WaitForVint /
     *   RunObjects / BuildSprites / RunPLC_RAM until TitleCard_Background is
     *   unloaded.
     *
     * That loop is exactly 25 iterations:
     *   Obj34_LeftPartOut (:27518-27540) steps titlecard_location
     *   $A -> 6 -> 2 -> 0 (the -2 clamp) -> -4 and, on the pass that reads a
     *   negative location, hands TitleCard_Bottom routine $10 and deletes
     *   itself: 5 passes.
     *   Obj34_BottomPartOut (:27542-27551) starts at location 0 and adds 4
     *   per pass, deleting itself and handing TitleCard_Background routine
     *   $12 on the pass that reads $28: 11 passes.
     *   Obj34_BackgroundOutInit/Out (:27587-27604) sets location $F0 and
     *   subtracts $20 per pass, deleting itself on the pass that computes
     *   -$30: 9 passes.
     *
     * So the players run 1 + 25 = 26 object passes between InitPlayers and
     * Level_MainLoop (:5091).
     */
    private static final int LEAVE_PRELOOP_PASSES = 1;
    /** The title-card VBlank {@code Level_TtlCard} waits once more after its loop (s2.asm:4914-4926). */
    private static final int POST_ARRIVAL_VBLANKS = 1;

    /** Native game width (320-pixel frame everything is authored for). */
    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    /**
     * Returns the configured viewport width in game pixels.
     * At native 320 equals SCREEN_WIDTH exactly (xOffset == 0 - byte-identical).
     */
    private int viewportWidth() {
        try {
            int w = GameServices.graphics().getProjectionWidth();
            return w > 0 ? w : SCREEN_WIDTH;
        } catch (Exception ignored) {
            return SCREEN_WIDTH;
        }
    }

    /**
     * Horizontal offset to centre the 320-wide title-card composition in the
     * configured viewport. Zero at native 320 - byte-identical.
     */
    private int xOffset() {
        return (viewportWidth() - SCREEN_WIDTH) / 2;
    }

    /** Pattern base ID for title card art (high ID to avoid conflicts) */
    private static final int PATTERN_BASE = PatternAtlasRange.TITLE_CARDS.base();

    /**
     * VRAM layout for title card art:
     * - $580-$58F: E, N, O, Z letters (first 16 tiles of ArtNem_TitleCard)
     * - $590-$5AF: Act numbers and misc (next ~32 tiles)
     * - $5B0-$5D7: "SONIC THE HEDGEHOG" bar (~40 tiles)
     * - $5D4-$5D7: Red stripes (~4 tiles, overlaps with above)
     * - $5DE-$63F: Zone name letters from ArtNem_TitleCard2 (~98 tiles)
     *
     * Total span: $580 to ~$640 = ~192 tiles
     */
    private static final int VRAM_BASE = 0x580;
    private static final int VRAM_END = 0x700;  // Conservative upper bound
    private static final int VRAM_TITLE_CARD2_START = 0x5DE;  // Where TitleCard2 letters start

    /**
     * Charset lookup for title card letters.
     * Maps letter (A-Z) to tile offset in ArtNem_TitleCard2.
     * E, N, O, Z are -1 because they exist in ArtNem_TitleCard (base).
     *
     * From s2.asm charset definitions:
     * charset 'A',0
     * charset 'B',"\4\8\xC\4\x10\x14\x18\x1C\x1E\x22\x26\x2A\4\4\x30\x34\x38\x3C\x40\x44\x48\x4C\x52\x56\4"
     */
    private static final int[] LETTER_OFFSETS = {
            0,    // A = 0x00
            4,    // B = 0x04
            8,    // C = 0x08
            12,   // D = 0x0C
            -1,   // E = in ArtNem_TitleCard (skip)
            16,   // F = 0x10
            20,   // G = 0x14
            24,   // H = 0x18
            28,   // I = 0x1C
            30,   // J = 0x1E
            34,   // K = 0x22
            38,   // L = 0x26
            42,   // M = 0x2A
            -1,   // N = in ArtNem_TitleCard (skip)
            -1,   // O = in ArtNem_TitleCard (skip)
            48,   // P = 0x30
            52,   // Q = 0x34
            56,   // R = 0x38
            60,   // S = 0x3C
            64,   // T = 0x40
            68,   // U = 0x44
            72,   // V = 0x48
            76,   // W = 0x4C
            82,   // X = 0x52
            86,   // Y = 0x56
            -1    // Z = in ArtNem_TitleCard (skip)
    };

    /**
     * Charset lookup for letter sizes (tile count).
     * From s2.asm: charset 'a',"\4\4\4\4\4\4\4\4\2\4\4\4\6\4\4\4\4\4\4\4\4\4\6\4\4"
     */
    private static final int[] LETTER_SIZES = {
            4,    // A
            4,    // B
            4,    // C
            4,    // D
            4,    // E
            4,    // F
            4,    // G
            4,    // H
            2,    // I (narrow)
            4,    // J
            4,    // K
            4,    // L
            6,    // M (wide)
            4,    // N
            4,    // O
            4,    // P
            4,    // Q
            4,    // R
            4,    // S
            4,    // T
            4,    // U
            4,    // V
            6,    // W (wide)
            4,    // X
            4,    // Y
            4     // Z
    };

    /**
     * Zone names for title card letter loading.
     * These match the internal zone order (0-10).
     */
    private static final String[] ZONE_NAMES = {
            "EMERALD HILL",     // 0 - EHZ
            "CHEMICAL PLANT",   // 1 - CPZ
            "AQUATIC RUIN",     // 2 - ARZ
            "CASINO NIGHT",     // 3 - CNZ
            "HILL TOP",         // 4 - HTZ
            "MYSTIC CAVE",      // 5 - MCZ
            "OIL OCEAN",        // 6 - OOZ
            "METROPOLIS",       // 7 - MTZ
            "SKY CHASE",        // 8 - SCZ
            "WING FORTRESS",    // 9 - WFZ
            "DEATH EGG"         // 10 - DEZ
    };

    // Current state
    private TitleCardState state = TitleCardState.COMPLETE;
    private int stateTimer = 0;
    private int frameCounter = 0;

    /**
     * LoadZoneTiles DMA chunk size: the {@code $1000}-byte granule its
     * {@code rol.w #4,d7 / andi.w #$F,d7} chunk-count extraction is defined against
     * (docs/s2disasm/s2.asm:6485-6486).
     */
    private static final int ZONE_TILE_DMA_CHUNK_BYTES = 0x1000;

    /** Remaining LoadZoneTiles {@code dbf} iterations, one VBlank each (s2.asm:6519). */
    private int zoneTileUploadFramesLeft = 0;

    // Current zone/act
    private int currentZone = 0;
    private int currentAct = 0;

    // Sole Obj34 state; projections do not retain animation positions.
    Obj34 obj34;
    private boolean presentationOmitted;

    // Unified VRAM-aligned pattern array
    // Index = VRAM address - VRAM_BASE
    private Pattern[] combinedPatterns;

    // Raw ArtNem_TitleCard2 patterns (full alphabet, stays in "RAM")
    private Pattern[] titleCard2RawPatterns;

    // Base ArtNem_TitleCard patterns
    private Pattern[] titleCardBasePatterns;

    private boolean artLoaded = false;
    private boolean artCached = false;
    private boolean exitPlcsQueued;
    private int lastLoadedZone = -1;  // Track which zone's letters we've loaded

    /**
     * Object passes dispatched since {@code Level:} armed the title-card leave
     * flags, or {@code 0} while the card is still in its pre-{@code InitPlayers}
     * phases. Pass 1 is the {@code jsr (RunObjects).l} at
     * docs/s2disasm/s2.asm:5006; passes 2..26 are the 25 iterations of the
     * leave loop at docs/s2disasm/s2.asm:5060-5066.
     */
    private int leavePass;

    /** Whether the gameplay-phase Obj34_WaitAndGoAway tail is running. */
    private boolean exitTailActive;
    /** Immutable live-title state used by the gameplay rewind registry. */
    public record Snapshot(
            TitleCardState state,
            int stateTimer,
            int frameCounter,
            int currentZone,
            int currentAct,
            int zoneTileUploadFramesLeft,
            boolean artLoaded,
            boolean artCached,
            boolean exitPlcsQueued,
            int lastLoadedZone,
            int leavePass,
            boolean exitTailActive,
            boolean presentationOmitted,
            Pattern[] combinedPatterns,
            Pattern[] titleCard2RawPatterns,
            Pattern[] titleCardBasePatterns,
            Obj34.Snapshot obj34) {
        public Snapshot {
            Objects.requireNonNull(state, "state");
            combinedPatterns = copy(combinedPatterns);
            titleCard2RawPatterns = copy(titleCard2RawPatterns);
            titleCardBasePatterns = copy(titleCardBasePatterns);

        }

        @Override
        public Pattern[] combinedPatterns() {
            return copy(combinedPatterns);
        }

        @Override
        public Pattern[] titleCard2RawPatterns() {
            return copy(titleCard2RawPatterns);
        }

        @Override
        public Pattern[] titleCardBasePatterns() {
            return copy(titleCardBasePatterns);
        }

        private static Pattern[] copy(Pattern[] source) {
            return source == null ? null : source.clone();
        }
    }

    private final java.util.function.Supplier<ZoneRegistry> zoneRegistry;
    private final java.util.function.BooleanSupplier skipModCards;

    public TitleCardManager() {
        this(() -> GameServices.module().getZoneRegistry(),
                () -> GameServices.configuration().getBoolean(SonicConfiguration.SKIP_MOD_ZONE_TITLE_CARDS));
    }

    TitleCardManager(java.util.function.Supplier<ZoneRegistry> zoneRegistry,
                     java.util.function.BooleanSupplier skipModCards) {
        this.zoneRegistry = Objects.requireNonNull(zoneRegistry, "zoneRegistry");
        this.skipModCards = Objects.requireNonNull(skipModCards, "skipModCards");
    }

    @Override
    public String key() {
        return REWIND_KEY;
    }

    @Override
    public Snapshot capture() {
        return new Snapshot(
                state, stateTimer, frameCounter, currentZone, currentAct,
                zoneTileUploadFramesLeft,
                artLoaded, artCached, exitPlcsQueued, lastLoadedZone,
                leavePass, exitTailActive, presentationOmitted,
                combinedPatterns, titleCard2RawPatterns, titleCardBasePatterns,
                obj34 == null ? null : obj34.capture());
    }

    @Override
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        state = snapshot.state();
        stateTimer = snapshot.stateTimer();
        frameCounter = snapshot.frameCounter();
        currentZone = snapshot.currentZone();
        currentAct = snapshot.currentAct();
        zoneTileUploadFramesLeft = snapshot.zoneTileUploadFramesLeft();
        artLoaded = snapshot.artLoaded();
        artCached = snapshot.artCached();
        exitPlcsQueued = snapshot.exitPlcsQueued();
        lastLoadedZone = snapshot.lastLoadedZone();
        leavePass = snapshot.leavePass();
        exitTailActive = snapshot.exitTailActive();
        presentationOmitted = snapshot.presentationOmitted();
        combinedPatterns = snapshot.combinedPatterns();
        titleCard2RawPatterns = snapshot.titleCard2RawPatterns();
        titleCardBasePatterns = snapshot.titleCardBasePatterns();

        obj34 = snapshot.obj34() == null ? null : new Obj34();
        if (obj34 != null) obj34.restore(snapshot.obj34());
    }

    @Override
    public void resetForMissingSnapshot() {
        reset();
    }

    public static synchronized TitleCardManager getInstance() {
        if (instance == null) {
            instance = new TitleCardManager();
        }
        return instance;
    }

    /**
     * Initializes the title card for a zone/act.
     *
     * @param zoneIndex Zone index (0-10)
     * @param actIndex  Act index (0-2)
     */
    public void initialize(int zoneIndex, int actIndex) {
        this.currentZone = zoneIndex;
        this.currentAct = actIndex;
        this.exitPlcsQueued = false;
        this.exitTailActive = false;
        this.leavePass = 0;
        this.state = TitleCardState.SLIDE_IN;
        this.stateTimer = 0;
        this.frameCounter = 0;
        this.zoneTileUploadFramesLeft = 0;

        if (isSkippedModZone(zoneIndex)) {
            obj34 = null;
            state = TitleCardState.COMPLETE;
            LOGGER.info("Skipping unsupported title card for mod zone " + zoneIndex);
            return;
        }

        // Load base art if needed
        if (!artLoaded) {
            loadArt();
        }

        // Load zone-specific letters if zone changed
        if (lastLoadedZone != zoneIndex) {
            loadZoneLetters(zoneIndex);
            lastLoadedZone = zoneIndex;
            // Force GPU cache refresh when letters change
            artCached = false;
        }

        // Reset art cache to ensure fresh GPU upload
        artCached = false;

        presentationOmitted = false;
        createElements();
    }

    private void createElements() {
        obj34 = new Obj34();
        var session = SessionManager.getCurrentWorldSession();
        var module = session == null ? null : session.getGameModule();
        boolean showAct = !TitleCardMappings.isSingleActZone(currentZone)
                && (module == null || module.showsTitleCardActNumber(currentZone, currentAct));
        // The native Competition world owns the two-player lifecycle capability.
        // Read it only at initialization; rewind retains the mode with Obj34.
        boolean twoPlayer = module != null && module.getGameService(
                com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle.class) != null;
        obj34.init(nativeZoneFrame(), showAct ? TitleCardMappings.getActFrame(currentAct) : -1, twoPlayer);
    }

    /** Current_Zone byte, MapUnc_TitleCards zone order (s2.asm:28560-28577). */
    private int nativeZoneFrame() {
        return switch (currentZone) {
            case 1 -> 13; case 2 -> 15; case 3 -> 12; case 4 -> 7;
            case 5 -> 11; case 6 -> 10; case 7 -> currentAct==2 ? 5 : 4;
            case 8 -> 16; case 9 -> 6; case 10 -> 14; default -> 0;
        };
    }

    /**
     * Loads title card art from ROM.
     *
     * The title card uses art from two Nemesis-compressed sources:
     * - ArtNem_TitleCard: E, N, O, Z letters, numbers, "SONIC THE HEDGEHOG" bar, stripes
     * - ArtNem_TitleCard2: Full alphabet for zone names (kept in "RAM" for letter lookup)
     */
    private void loadArt() {
        try {
            RomManager romManager = GameServices.rom();
            if (!romManager.isRomAvailable()) {
                LOGGER.warning("ROM not available for title card art loading");
                return;
            }
            Rom rom = romManager.getRom();

            // Load ArtNem_TitleCard - base title card art (E,N,O,Z, numbers, bar, stripes)
            titleCardBasePatterns = PatternDecompressor.nemesis(rom,
                    Sonic2Constants.ART_NEM_TITLE_CARD_ADDR, "TitleCard");
            LOGGER.info("Loaded " + (titleCardBasePatterns != null ? titleCardBasePatterns.length : 0) +
                    " title card base patterns");

            // Load ArtNem_TitleCard2 - zone name alphabet (keep in "RAM")
            titleCard2RawPatterns = PatternDecompressor.nemesis(rom,
                    Sonic2Constants.ART_NEM_TITLE_CARD2_ADDR, "TitleCard2");
            LOGGER.info("Loaded " + (titleCard2RawPatterns != null ? titleCard2RawPatterns.length : 0) +
                    " title card 2 alphabet patterns");

            // Null safety
            if (titleCardBasePatterns == null) titleCardBasePatterns = new Pattern[0];
            if (titleCard2RawPatterns == null) titleCard2RawPatterns = new Pattern[0];

            // Create unified VRAM-aligned pattern array
            // Array index = VRAM address - VRAM_BASE
            int totalSize = VRAM_END - VRAM_BASE;
            combinedPatterns = new Pattern[totalSize];
            Pattern emptyPattern = new Pattern();
            Arrays.fill(combinedPatterns, emptyPattern);

            // Copy ArtNem_TitleCard to VRAM $580 (index 0)
            // This contains E, N, O, Z letters, act numbers, "SONIC THE HEDGEHOG" bar, red stripes
            for (int i = 0; i < titleCardBasePatterns.length && i < totalSize; i++) {
                if (titleCardBasePatterns[i] != null) {
                    combinedPatterns[i] = titleCardBasePatterns[i];
                }
            }
            LOGGER.info("Copied " + titleCardBasePatterns.length + " TitleCard patterns to VRAM $580+");

            // Note: Zone-specific letters will be copied in loadZoneLetters()
            // when initialize() is called with a zone index

            artLoaded = true;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load title card art", e);
            artLoaded = false;
        }
    }

    /**
     * Loads zone-specific letters from ArtNem_TitleCard2 into VRAM.
     *
     * The original game copies only the letters needed for each zone name
     * from ArtNem_TitleCard2 (in RAM) to VRAM starting at $5DE.
     * Letters E, N, O, Z are skipped because they already exist in ArtNem_TitleCard.
     *
     * @param zoneIndex The zone index (0-10)
     */
    private void loadZoneLetters(int zoneIndex) {
        if (titleCard2RawPatterns == null || combinedPatterns == null) {
            LOGGER.warning("Cannot load zone letters - art not loaded");
            return;
        }

        String zoneName = registryZoneName(zoneIndex);

        // Track which letters we've already copied (like the original's 'used' bitmap)
        boolean[] usedLetters = new boolean[26];
        // E, N, O, Z are pre-marked as "used" since they're in ArtNem_TitleCard
        usedLetters['E' - 'A'] = true;
        usedLetters['N' - 'A'] = true;
        usedLetters['O' - 'A'] = true;
        usedLetters['Z' - 'A'] = true;

        // VRAM destination starts at $5DE
        int vramDest = VRAM_TITLE_CARD2_START - VRAM_BASE;

        LOGGER.info("Loading letters for zone: " + zoneName);

        // Process each character in the zone name
        for (char c : zoneName.toCharArray()) {
            if (c == ' ') continue;  // Skip spaces
            if (c < 'A' || c > 'Z') continue;  // Skip non-letters

            int letterIndex = c - 'A';

            // Skip if we've already copied this letter
            if (usedLetters[letterIndex]) continue;
            usedLetters[letterIndex] = true;

            // Get source offset and size from charset tables
            int srcOffset = LETTER_OFFSETS[letterIndex];
            int tileCount = LETTER_SIZES[letterIndex];

            // Letters in ArtNem_TitleCard have -1 offset (shouldn't happen due to usedLetters check)
            if (srcOffset < 0) continue;

            // Copy tiles from "RAM" (titleCard2RawPatterns) to "VRAM" (combinedPatterns)
            for (int i = 0; i < tileCount; i++) {
                int srcIndex = srcOffset + i;
                int destIndex = vramDest + i;

                if (srcIndex < titleCard2RawPatterns.length &&
                    destIndex < combinedPatterns.length &&
                    titleCard2RawPatterns[srcIndex] != null) {
                    combinedPatterns[destIndex] = titleCard2RawPatterns[srcIndex];
                }
            }

            LOGGER.fine("Copied letter " + c + " (" + tileCount + " tiles) from offset " +
                       srcOffset + " to VRAM $" + Integer.toHexString(VRAM_BASE + vramDest));

            // Advance VRAM destination
            vramDest += tileCount;
        }

        LOGGER.info("Loaded " + (vramDest - (VRAM_TITLE_CARD2_START - VRAM_BASE)) +
                   " tiles for zone " + zoneName);
    }

    private boolean isSkippedModZone(int zoneIndex) {
        try {
            ZoneKey key = zoneRegistry.get().zoneKey(zoneIndex);
            return shouldSkipTitleCard(key, skipModCards.getAsBoolean());
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static boolean shouldSkipTitleCard(ZoneKey key, boolean configuredSkip) {
        return configuredSkip && key instanceof ZoneKey.Mod;
    }

    String registryZoneName(int zoneIndex) {
        try {
            String name = zoneRegistry.get().getZoneName(zoneIndex);
            if (name != null && !name.isBlank()) return name.toUpperCase(java.util.Locale.ROOT);
        } catch (RuntimeException unavailable) {
            // Bootstrap/tests without an installed module retain the stock fallback below.
        }
        return zoneIndex >= 0 && zoneIndex < ZONE_NAMES.length ? ZONE_NAMES[zoneIndex] : ZONE_NAMES[0];
    }


    /**
     * Ensures art is cached to GPU.
     */
    private void ensureArtCached() {
        if (artCached || !artLoaded || combinedPatterns == null) {
            return;
        }

        GraphicsManager graphicsManager = GameServices.graphics();
        if (graphicsManager == null) {
            return;
        }

        // Cache all patterns in the VRAM-aligned array
        int cachedCount = 0;
        for (int i = 0; i < combinedPatterns.length; i++) {
            if (combinedPatterns[i] != null) {
                graphicsManager.cachePatternTexture(combinedPatterns[i], PATTERN_BASE + i);
                cachedCount++;
            }
        }
        LOGGER.info("Cached " + cachedCount + " title card patterns to GPU");

        artCached = true;
    }

    /**
     * Updates the title card state machine.
     * Call this once per frame while in TITLE_CARD mode.
     */
    public void update() {
        frameCounter++;
        stateTimer++;
        if (obj34 != null) obj34.consumeFills(); // Vint_TitleCard, before RunObjects.
        if (exitTailActive || state == TitleCardState.TEXT_WAIT || state == TitleCardState.TEXT_EXIT) {
            if (obj34 != null && obj34.tick()) queueExitPlcs();
            if (obj34 == null || Arrays.stream(obj34.parts).noneMatch(part -> part.alive)) {
                exitTailActive=false;state=TitleCardState.COMPLETE;
            } else if (obj34.parts[Obj34.NAME].duration==0) state=TitleCardState.TEXT_EXIT;
            return;
        }
        if (leavePass > 0) {
            leavePass++;
            if (obj34 != null && !obj34.parts[Obj34.BACKGROUND].alive) {
                enterTextWait();
                return;
            }
        }
        switch (state) {
            case SLIDE_IN, DISPLAY -> {
                if (obj34 != null) obj34.tick();
                if (obj34 == null || obj34.parts[Obj34.NAME].x==obj34.parts[Obj34.NAME].target) {
                    if (!plcQueueBusy()) enterZoneTileUpload();
                    else state=TitleCardState.DISPLAY;
                }
            }
            case ZONE_TILE_UPLOAD -> updateZoneTileUpload();
            case EXIT_LEFT_SWOOSH, EXIT_BOTTOM_BAR, EXIT_BACKGROUND -> {
                if (obj34 != null) {
                    obj34.tick();
                    if (!obj34.parts[Obj34.BOTTOM].alive) {
                        if (state != TitleCardState.EXIT_BACKGROUND) stateTimer = 0;
                        state = TitleCardState.EXIT_BACKGROUND;
                    } else if (!obj34.parts[Obj34.LEFT].alive) {
                        state = TitleCardState.EXIT_BOTTOM_BAR;
                    }
                }
            }
            default -> { }
        }
    }

    /**
     * Leaves {@code Level_TtlCard} the way the ROM does. Once the re-loop test
     * passes, the routine waits for one more title-card VBlank and draws the
     * HUD base ({@code Level_TtlCard}, docs/s2disasm/s2.asm:4914-4927) before
     * the level-size, scroll and zone-tile loads that follow (s2.asm:4929-4938).
     * That wait is a VBlank of its own, ahead of the upload's chunk VBlanks, so
     * the hold counts it first.
     *
     * <p>Without it the whole leave sequence ran one VBlank early: in the
     * complete-emerald run's admission census every level entry records one
     * more admitted arrival row than the engine's slide-in plus PLC-drain
     * iterations (EHZ 53, CPZ 62, ARZ 46, CNZ 61), while the upload span after
     * the decompression lag matches {@link #zoneTileUploadVBlanks()} exactly.
     */
    private void enterZoneTileUpload() {
        state = TitleCardState.ZONE_TILE_UPLOAD;
        stateTimer = 0;
        zoneTileUploadFramesLeft = POST_ARRIVAL_VBLANKS + zoneTileUploadVBlanks();
        LOGGER.fine("Title card entered ZONE_TILE_UPLOAD state at frame " + frameCounter
                + " for " + zoneTileUploadFramesLeft + " VBlank(s)");
    }

    /**
     * VBlanks LoadZoneTiles spends uploading the zone's 8x8 art
     * (docs/s2disasm/s2.asm:6471-6524).
     *
     * <p>The routine Kosinski-decodes the {@code LevelArtPointers} 8x8 stream into
     * Chunk_Table and takes the resulting write pointer as the byte size
     * ({@code move.w a1,d3}, :6483). It then extracts the DMA chunk count with
     * {@code rol.w #4,d7 / andi.w #$F,d7} (:6485-6486) -- bits 15-12 of that size, which
     * is {@code size / $1000} -- and runs a {@code dbf d7,-} loop (:6506-6523) whose body
     * contains {@code bsr.w WaitForVint} (:6519). {@code dbf} executes the body
     * {@code d7 + 1} times, so the cost is {@code size / $1000 + 1} VBlanks: one per
     * {@code $1000}-byte DMA chunk plus one for the leading partial chunk.
     *
     * <p>Every term here is the ROM's. The size is the engine's own decompressed art
     * length ({@link Sonic2Level#getZoneTileArtByteSize()}), decoded from the same stream
     * at the same address, so the resulting per-zone frame counts are a consequence of the
     * zone's art rather than a table.
     */
    private int zoneTileUploadVBlanks() {
        if (!(GameServices.levelOrNull() instanceof com.openggf.level.LevelManager lm)) {
            return 0;
        }
        if (!(lm.getCurrentLevel() instanceof com.openggf.game.sonic2.Sonic2Level level)) {
            return 0;
        }
        int byteSize = level.getZoneTileArtByteSize();
        if (byteSize <= 0) {
            return 0;
        }
        return (byteSize / ZONE_TILE_DMA_CHUNK_BYTES) + 1;
    }

    /**
     * The post-arrival VBlank, then LoadZoneTiles' zone-art upload hold, one
     * VBlank per DMA chunk.
     */
    private void updateZoneTileUpload() {
        if (zoneTileUploadFramesLeft > 0) {
            zoneTileUploadFramesLeft--;
        }
        if (zoneTileUploadFramesLeft <= 0) {
            enterLeftSwooshExit();
        }
    }

    private void enterLeftSwooshExit() {
        state = TitleCardState.EXIT_LEFT_SWOOSH;
        stateTimer = 0;
        // This frame is the s2.asm:5003-5006 pass: InitPlayers has run and
        // RunObjects dispatches the players once, but the leave flags at
        // :5056-5058 are only armed after it, so no leave piece moves yet.
        leavePass = LEAVE_PRELOOP_PASSES;
        if (obj34 == null) { createElements(); obj34.settle(); }
        // The load's leading object pass finishes arrival before leave is armed.
        obj34.tick();
        obj34.armLeave();
    }

    /** Models {@code tst.l (Plc_Buffer).w} (docs/s2disasm/s2.asm:4923). */
    private boolean plcQueueBusy() {
        if (SessionManager.getCurrentWorldSession() == null) {
            return false;
        }
        Sonic2PlcService plcService =
                GameServices.module().getGameService(Sonic2PlcService.class);
        return plcService != null && plcService.isBusy();
    }

    private void enterTextWait() {
        state = TitleCardState.TEXT_WAIT;
        stateTimer = 0;
        leavePass = 0;
        // s2.asm:5066-5080 writes routine $16 and anim_frame_duration $2D to
        // the surviving pieces here, after the leave loop and before
        // Level_MainLoop, whether or not the card was displayed.
        if (obj34 != null) obj34.armTail();
        LOGGER.fine("Title card entered TEXT_WAIT state at frame " + frameCounter);
    }

    @Override
    public void completeLockedIteration() {
        // Level's leave loop tests TitleCard_Background after RunObjects and
        // RunPLC_RAM, then clears Control_Locked and enters Level_StartGame
        // without another WaitForVint (s2.asm:5060-5080; KiS2:5374-5405).
        // Keep all 26 player/object passes, including this final locked pass.
        if (leavePass > 0 && obj34 != null && !obj34.parts[Obj34.BACKGROUND].alive) {
            enterTextWait();
        }
    }

    /**
     * Native assembly already owns art and players. Enter the existing locked
     * leave state without replaying slide-in or its zone-tile upload waits.
     */
    public void beginOmittedPresentationLeave(int zoneIndex, int actIndex) {
        reset();
        currentZone = zoneIndex;
        currentAct = actIndex;
        presentationOmitted = true;
        createElements();
        obj34.settle();
        enterLeftSwooshExit();
    }

    /** Competition owns Level_TtlCard's waits; the campaign state machine is unchanged.
     * Level_TtlCard (s2.asm:4914-4927), Obj34_Init (s2.asm:27328-27375).
     */
    public void beginCompetitionArrival(int zoneIndex, int actIndex) {
        reset();
        currentZone = zoneIndex;
        currentAct = actIndex;
        createElements();
        state = TitleCardState.SLIDE_IN;
    }

    public void stepCompetitionArrival() {
        obj34.consumeFills();
        obj34.tick();
    }

    public boolean competitionArrivalReady() {
        var name = obj34.parts[Obj34.NAME];
        return name.routine != 0 && name.x == name.target && !plcQueueBusy();
    }

    public void beginCompetitionLeave() {
        enterLeftSwooshExit();
    }

    /** Obj34_BackgroundOutInit owns one DeformBgLayer call before its first out pass. */
    public boolean isFirstBackgroundLeavePass() {
        return obj34 != null && obj34.parts[Obj34.BACKGROUND].routine == 18;
    }

    /**
     * Arms the gameplay-phase title-card tail for a presentation that was
     * omitted.
     *
     * <p>{@code Level_TtlCard} leaves its locked loop as soon as the zone-name
     * piece has reached {@code titlecard_x_target} and {@code Plc_Buffer} is
     * empty (s2.asm:4914-4925), but the pieces themselves are still live: just
     * before the main level loop the game rewrites their routine to {@code $16}
     * with {@code anim_frame_duration = $2D} (s2.asm:5066-5080), so
     * {@code Obj34_WaitAndGoAway} runs on ordinary gameplay frames. Omitting the
     * presentation must still run that tail, because the frame the zone-name
     * piece leaves is where the game loads the standard-water and per-zone
     * animal art (s2.asm:27605-27637).
     *
     * <p>Only the zone-name piece publishes art: {@code
     * Obj34_LoadStandardWaterAndAnimalArt} gates the two {@code LoadPLC} calls
     * on {@code cmpa.w #TitleCard_ZoneName,a0} (s2.asm:27630), so the other
     * pieces only delete themselves.
     */
    @Override
    public void beginOmittedPresentationExitTail(int zoneIndex, int actIndex) {
        // Animal_PLCTable is indexed by Current_Zone (s2.asm:27639-27642), so
        // the tail needs the zone the omitted card would have shown.
        currentZone = zoneIndex;
        currentAct = actIndex;
        exitTailActive = true;
        exitPlcsQueued = false;
        presentationOmitted = true;
        createElements();
        obj34.settleAfterLeave();
        stateTimer=0;
        state=TitleCardState.TEXT_WAIT;
    }

    private void queueExitPlcs() {
        if (exitPlcsQueued) {
            return;
        }
        try {
            Sonic2PlcService plcService = GameServices.module().getGameService(Sonic2PlcService.class);
            if (plcService != null) {
                Sonic2PlcService.Operation[] transaction = {
                        Sonic2PlcService.appendOperation(Sonic2Constants.PLC_STD_WATER),
                        Sonic2PlcService.appendOperation(animalPlcForZone(currentZone))};
                if (GameServices.module().getObjectArtProvider() instanceof Sonic2ObjectArtProvider artProvider
                        && GameServices.levelOrNull() != null) {
                    Sonic2RuntimePlcPublisher.transact(artProvider, plcService,
                            GameServices.levelOrNull()::refreshObjectArtPatterns, transaction);
                } else {
                    plcService.transact(transaction);
                }
                exitPlcsQueued = true;
            }
        } catch (Exception e) {
            // The presentation renderer also runs without a gameplay module in focused tests.
            LOGGER.log(Level.FINE, "Title card exit PLCs not queued", e);
        }
    }

    private static int animalPlcForZone(int zone) {
        return switch (zone) {
            case 0 -> Sonic2Constants.PLC_ANIMALS_EHZ;
            case 1 -> Sonic2Constants.PLC_ANIMALS_CPZ;
            case 2 -> Sonic2Constants.PLC_ANIMALS_ARZ;
            case 3 -> Sonic2Constants.PLC_ANIMALS_CNZ;
            case 4, 7, 9 -> Sonic2Constants.PLC_ANIMALS_HTZ_MTZ_WFZ;
            case 5 -> Sonic2Constants.PLC_ANIMALS_MCZ;
            case 6 -> Sonic2Constants.PLC_ANIMALS_OOZ;
            case 8 -> Sonic2Constants.PLC_ANIMALS_SCZ;
            case 10 -> Sonic2Constants.PLC_ANIMALS_DEZ;
            default -> Sonic2Constants.PLC_ANIMALS_EHZ;
        };
    }

    /**
     * Renders the title card.
     * Call this from Engine.draw() when in TITLE_CARD mode.
     */
    /** Pure projection from the ROM's zone-ordered mapping table to shared art mappings. */
    private static int overlayNameFrame(int nativeFrame) {
        return switch (nativeFrame) {
            case 4, 5 -> TitleCardMappings.FRAME_MTZ;
            case 6 -> TitleCardMappings.FRAME_WFZ;
            case 7 -> TitleCardMappings.FRAME_HTZ;
            case 8 -> TitleCardMappings.FRAME_HPZ;
            case 10 -> TitleCardMappings.FRAME_OOZ;
            case 11 -> TitleCardMappings.FRAME_MCZ;
            case 12 -> TitleCardMappings.FRAME_CNZ;
            case 13 -> TitleCardMappings.FRAME_CPZ;
            case 14 -> TitleCardMappings.FRAME_DEZ;
            case 15 -> TitleCardMappings.FRAME_ARZ;
            case 16 -> TitleCardMappings.FRAME_SCZ;
            default -> TitleCardMappings.FRAME_EHZ;
        };
    }

    public void draw() {
        if (presentationOmitted || obj34 == null) return;
        ensureArtCached();
        GraphicsManager graphicsManager = GameServices.graphics();
        if (graphicsManager == null || combinedPatterns == null) return;
        if (state == TitleCardState.SLIDE_IN) {
            graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.RECTI,
                    -1, 0f, 0f, 0f, 0, 0, viewportWidth(), SCREEN_HEIGHT));
        }
        graphicsManager.beginPatternBatch();
        for (int y=0;y<28;y++) for(int x=0;x<40;x++) {
            int word=obj34.planeWord(0,x,y);
            if(word==0) continue;
            int id=PATTERN_BASE+(word&0x7ff)-VRAM_BASE;
            graphicsManager.renderPatternWithId(id,
                    new com.openggf.level.PatternDesc((word&0xf800)|(id&0x7ff)),
                    x*8+xOffset(),y*8);
        }
        // Native sprite priority is slot order: name, ZONE, act, bottom, left.
        // Reverse submission keeps earlier native sprites in front where they overlap.
        for(int i=obj34.publishedParts.size()-1;i>=0;i--) {
            Obj34.PartState part=obj34.publishedParts.get(i);
            if(i==Obj34.BACKGROUND || !part.alive() || !part.displayed()) continue;
            for(TitleCardMappings.SpritePiece piece:TitleCardMappings.getFrame(i==Obj34.NAME ? overlayNameFrame(part.frame()) : part.frame())) {
                TitleCardSpriteRenderer.renderSpritePiece(graphicsManager,piece,
                        part.x()-128+xOffset(),part.y()-128,VRAM_BASE,PATTERN_BASE,combinedPatterns.length);
            }
        }
        graphicsManager.flushPatternBatch();
    }

    /**
     * Returns true if the title card animation is fully complete.
     * Use this to determine when to stop drawing the title card.
     */
    public boolean isComplete() {
        return state == TitleCardState.COMPLETE;
    }

    /**
     * Returns true if player control should be released.
     * From disassembly lines 5073-5078: control is unlocked and the title card
     * game mode flag is cleared at the START of TEXT_WAIT, not when the title
     * card is complete. This allows the player to move while the text is still
     * visible on screen.
     */
    public boolean shouldReleaseControl() {
        return state == TitleCardState.TEXT_WAIT ||
               state == TitleCardState.TEXT_EXIT ||
               state == TitleCardState.COMPLETE;
    }

    /**
     * Returns true if the title card overlay should still be drawn.
     * The overlay remains visible during TEXT_WAIT and TEXT_EXIT phases,
     * even though player control has been released and the game mode has
     * returned to LEVEL. This creates the effect of the text floating over
     * the level while the player can already move.
     */
    public boolean isOverlayActive() {
        return state == TitleCardState.TEXT_WAIT ||
               state == TitleCardState.TEXT_EXIT;
    }

    /**
     * Gets the current state.
     */
    public TitleCardState getState() {
        return state;
    }

    /**
     * Resets the manager state.
     */
    public void reset() {
        state = TitleCardState.COMPLETE;
        stateTimer = 0;
        frameCounter = 0;
        zoneTileUploadFramesLeft = 0;
        exitPlcsQueued = false;
        exitTailActive = false;
        leavePass = 0;
        obj34 = null;
        presentationOmitted = false;
    }

    /**
     * Player physics runs only for the object passes the ROM dispatches with
     * the player objects created.
     *
     * <p>{@code InitPlayers} is at docs/s2disasm/s2.asm:4945, after the
     * {@code Level_TtlCard} scroll-in wait loop at :4914-4925, so Sonic and
     * Tails do not exist for the card's slide-in and hold at all. They exist
     * for the single {@code RunObjects} at :5006 and for the 25 iterations of
     * the leave loop at :5060-5066 -- 26 passes, tracked by {@link #leavePass}.
     */
    @Override
    public boolean shouldRunPlayerPhysics() {
        return leavePass > 0;
    }

    /**
     * The level's placed objects have exactly the same ROM lifetime as the
     * players, and for the same reason: {@code Level:} calls
     * {@code ObjectsManager} at docs/s2disasm/s2.asm:5003 -- one instruction
     * before the {@code InitPlayers} window cited above -- so the object RAM
     * the {@code Level_TtlCard} scroll-in loop at :4914-4925 dispatches holds
     * only the Obj34 title-card pieces, never a level object. The level
     * objects therefore run for the single {@code RunObjects} at :5006 and the
     * 25 leave-loop iterations at :5060-5066, and for nothing before them.
     *
     * <p>Without this the engine aged every placed object by the full length
     * of the card's slide-in and hold. That is invisible on a fresh headless
     * load, whose card is only the leave window, but a re-entry card (a star
     * post restart, or a special-stage return inside a run) is far longer: the
     * CPZ act 2 special-stage return ran 91 object passes where the ROM runs
     * 26, drifting the ObjA7 Grabber cluster (x_vel {@code #-$40} per pass,
     * s2.asm:76662-76668) about 17 pixels and putting a badnik in the player's
     * path that the ROM never places there.
     *
     * @see #shouldRunPlayerPhysics()
     */
    @Override
    public boolean shouldRunLevelObjectsDuringLockedPhase() {
        return leavePass > 0;
    }

    /**
     * The leave sequence's object passes skip the level's scroll/event step
     * except on the iteration whose object pass reaches
     * {@code Obj34_BackgroundOutInit} (docs/s2disasm/s2.asm:27587-27596), which
     * calls {@code DeformBgLayer}. The pass after the load and the other leave
     * iterations only run objects, sprites and the pattern load queue
     * (s2.asm:5003-5007, 5060-5066). The scroll call before the load
     * (s2.asm:4932) belongs to the level-load camera setup, and the slot-machine
     * routine it reaches is cleared again by {@code Level_FromCheckpoint}
     * (s2.asm:4980-4983).
     *
     * <p>{@code DeformBgLayer} is where CNZ's slot machine runs: its event
     * dispatch at {@code loc_C4D0} (s2.asm:15174-15175) reaches
     * {@code LevEvents_CNZ} (s2.asm:21511-21512). {@code SlotMachine_Routine1}
     * (s2.asm:59438-59460) seeds the reels from the low byte of
     * {@code Vint_runcount}, so the iteration chosen here decides the first
     * reels a CNZ act shows.
     *
     * <p>Within that iteration the ROM calls {@code DeformBgLayer} from the
     * background piece's slot, after the players and before the dynamic level
     * objects; the shared level step runs it after the object pass, so this
     * iteration's level objects read the camera from before its scroll.
     */
    @Override
    public boolean runsLevelScrollAndEventsThisLockedPass() {
        return leavePass > 0 && obj34 != null
                && obj34.parts[Obj34.BACKGROUND].routine == 0x14
                && obj34.parts[Obj34.BACKGROUND].location == 0xF0 - 0x20;
    }

    /**
     * ROM {@code RunObjects} extends its slot count to {@code LevelOnly_Object_RAM}
     * only when {@code Game_Mode} is exactly {@code GameModeID_Level}
     * (docs/s2disasm/s2.asm:29812-29818); {@code GameModeFlag_TitleCard} is set for
     * the whole pre-{@code Level_MainLoop} window (set s2.asm:4758, cleared
     * s2.asm:5087). So the 26 pre-main-loop passes above run Sonic and Tails --
     * both inside {@code Object_RAM} -- but never Obj05 Tails' tails or the
     * dust/shield/bubble/star slots that follow {@code Object_RAM_End}
     * (docs/s2disasm/s2.constants.asm:1145-1176).
     */
    @Override
    public boolean shouldRunLevelOnlyFixedSlotsDuringLockedPhase() {
        return false;
    }


    /**
     * Gets the current zone index.
     */
    public int getCurrentZone() {
        return currentZone;
    }

    /**
     * Gets the current act index.
     */
    public int getCurrentAct() {
        return currentAct;
    }
}
