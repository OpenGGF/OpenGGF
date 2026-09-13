package com.openggf.game.sonic2.kis2;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.RomByteReader;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.util.PatternDecompressor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;

/**
 * Knuckles' player art for the lock-on patch, read from the logical S&amp;K ROM
 * ({@code docs/kis2/BRANCH_DIFFS.md} §Art, mappings, DPLC and the boot-time
 * conversion).
 *
 * <p>KiS2 keeps S3K's {@code ArtUnc_Knuckles}, {@code MapUnc_Knuckles} and
 * {@code MapRUnc_Knuckles} and converts every DPLC'd tile through
 * {@code ArtConvTable} ({@code LoadSonicDynPLC_Part2}) so the S3K palette
 * indices land on Sonic 2's line-0 layout. The engine converts the whole tile
 * set once at load and draws with the S2-layout line
 * ({@code Pal_KnuxEndPose}).
 *
 * <p>The S&amp;K-format tiles, mappings, DPLCs and animation scripts come through
 * the S&amp;K donor surface ({@code CrossGameDonorProvider.createPlayerArtProvider})
 * so this package never depends on the S3K game package; the lock-on program
 * reads exactly those S&amp;K tables ({@code ArtUnc_Knuckles},
 * {@code MapUnc_Knuckles}, {@code MapRUnc_Knuckles}, {@code AniKnuckles}).
 */
public final class Kis2PlayerArt {

    private static final int NEMESIS_READ_SIZE = 8192;

    private final RomByteReader sk;
    private final PlayerSpriteArtProvider skKnucklesArt;
    private SpriteArtSet cachedKnuckles;
    private Palette cachedPalette;
    private Pattern[] cachedLifeIcon;

    /**
     * @param sk            the logical S&amp;K ROM
     * @param skKnucklesArt the S&amp;K donor's player-art provider over {@code sk},
     *                      answering {@code "knuckles"} with the S&amp;K-format set
     */
    public Kis2PlayerArt(RomByteReader sk, PlayerSpriteArtProvider skKnucklesArt) {
        this.sk = Objects.requireNonNull(sk, "sk");
        this.skKnucklesArt = Objects.requireNonNull(skKnucklesArt, "skKnucklesArt");
    }

    /** Knuckles' sprite set, converted to S2 palette indices. */
    public SpriteArtSet loadKnuckles() throws IOException {
        if (cachedKnuckles != null) {
            return cachedKnuckles;
        }
        SpriteArtSet sk3Format = skKnucklesArt.loadPlayerSpriteArt("knuckles");
        if (sk3Format == null) {
            throw new IOException("S&K donor art provider returned no Knuckles art");
        }
        // Copy before converting: the donor provider caches its tile array.
        Pattern[] tiles = new Pattern[sk3Format.artTiles().length];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = new Pattern();
            tiles[i].copyFrom(sk3Format.artTiles()[i]);
        }
        convertToSonic2Layout(tiles);
        cachedKnuckles = new SpriteArtSet(
                tiles,
                sk3Format.mappingFrames(),
                sk3Format.dplcFrames(),
                0,                               // palette line 0 (Obj01_Init art_tile)
                Sonic2Constants.ART_TILE_SONIC,  // ArtTile_ArtUnc_Sonic ($780): Knuckles takes Sonic's slot
                1,
                sk3Format.bankSize(),
                animationProfile(),
                sk3Format.animationSet());
        return cachedKnuckles;
    }

    /** {@code Pal_KnuxEndPose}: the S2-layout Knuckles line the converted art expects. */
    public Palette loadKnucklesPalette() {
        if (cachedPalette == null) {
            Palette palette = new Palette();
            palette.fromSegaFormat(sk.slice(Kis2Constants.PAL_KNUCKLES_S2_LAYOUT, Palette.PALETTE_SIZE_IN_ROM));
            cachedPalette = palette;
        }
        return cachedPalette.deepCopy();
    }

    /**
     * Tier-one HUD life icon and 1-up monitor face: the S&amp;K
     * {@code ArtNem_KnucklesLifeIcon} converted like the player art. KiS2's
     * own "Knuckles lives counter.nem" lives on the chip.
     */
    public Pattern[] loadLifeIcon() throws IOException {
        if (cachedLifeIcon == null) {
            int available = Math.min(NEMESIS_READ_SIZE, sk.size() - Kis2Constants.ART_NEM_KNUCKLES_LIFE_ICON);
            byte[] compressed = sk.slice(Kis2Constants.ART_NEM_KNUCKLES_LIFE_ICON, available);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                Pattern[] tiles = PatternDecompressor.fromBytes(NemesisReader.decompress(channel));
                convertToSonic2Layout(tiles);
                cachedLifeIcon = tiles;
            }
        }
        return cachedLifeIcon.clone();
    }

    /** {@code KPLC_ConvertArtFromS3K}: remap every pixel through {@code ArtConvTable}. */
    static void convertToSonic2Layout(Pattern[] tiles) {
        for (Pattern tile : tiles) {
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    int index = tile.getPixel(x, y) & 0x0F;
                    tile.setPixel(x, y, (byte) Kis2Constants.ART_CONV_TABLE[index]);
                }
            }
        }
    }

    /**
     * Sonic 2's animation code with Knuckles' table: the KiS2 {@code SonicAniData}
     * is the S&amp;K {@code AniKnuckles} table in S2's id slots (BRANCH_DIFFS.md
     * §Animation). {@code SAnim_Push} shifts by 8, {@code SAnim_Tumble} bases
     * at {@code $31}, {@code Obj01_MdNormal_Checks} (blink/get-up) and every
     * Super branch are removed, and stock S2 {@code SAnim_WalkRun} (angle
     * pre-adjust, doubled speed while sliding) is retained.
     */
    private static SpriteAnimationProfile animationProfile() {
        return new ScriptedVelocityAnimationProfile()
                .setIdleAnimId(Kis2Constants.ANIM_WAIT)
                .setWalkAnimId(Kis2Constants.ANIM_WALK)
                .setRunAnimId(Kis2Constants.ANIM_RUN)
                .setRunFramesUseWalkAnimationId(true)
                .setRollAnimId(Kis2Constants.ANIM_ROLL)
                .setRoll2AnimId(Kis2Constants.ANIM_ROLL2)
                .setPushAnimId(Kis2Constants.ANIM_PUSH)
                .setPushUsesWalkSpecialHandler(true)
                .setPushDelayShift(8)            // KiS2 SAnim_Push: lsr.w #8,d2
                .setDuckAnimId(Kis2Constants.ANIM_DUCK)
                .setLookUpAnimId(Kis2Constants.ANIM_LOOK_UP)
                .setSpindashAnimId(Kis2Constants.ANIM_SPINDASH)
                .setSpringAnimId(Kis2Constants.ANIM_SPRING)
                .setDeathAnimId(Kis2Constants.ANIM_DEATH)
                .setDrownAnimId(Kis2Constants.ANIM_DROWN)
                .setHurtAnimId(Kis2Constants.ANIM_HURT)
                .setSkidAnimId(Kis2Constants.ANIM_STOP)
                .setAirAnimId(Kis2Constants.ANIM_WALK)
                .setBalanceAnimId(Kis2Constants.ANIM_BALANCE)
                .setWalkSpeedThreshold(0x40)
                .setRunSpeedThreshold(0x600)     // SAnim_WalkRun cmpi.w #$600,d2
                .setFallbackFrame(0)
                .setAnglePreAdjust(true)         // stock S2 SAnim_WalkRun subq.b #1,d0
                .setWalkRunPublishesFrameBeforeTimerAdvance(true)
                .setDoubleWalkRunAnimationSpeedWhenSliding(true)
                .setTumbleFrameBase(0x31);       // KiS2 SAnim_Tumble addi.b #$31,d0
    }
}
