package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.util.PatternDecompressor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

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
 */
public final class Kis2PlayerArt {

    private static final int NEMESIS_READ_SIZE = 8192;

    private final RomByteReader sk;
    private SpriteArtSet cachedKnuckles;
    private Palette cachedPalette;
    private Pattern[] cachedLifeIcon;

    public Kis2PlayerArt(RomByteReader sk) {
        this.sk = sk;
    }

    /** Knuckles' sprite set, converted to S2 palette indices. */
    public SpriteArtSet loadKnuckles() throws IOException {
        if (cachedKnuckles != null) {
            return cachedKnuckles;
        }
        Pattern[] tiles = S3kSpriteDataLoader.loadArtTiles(sk,
                Kis2Constants.ART_UNC_KNUCKLES, Kis2Constants.ART_UNC_KNUCKLES_SIZE);
        convertToSonic2Layout(tiles);
        List<SpriteMappingFrame> mappingFrames =
                S3kSpriteDataLoader.loadMappingFrames(sk, Kis2Constants.MAP_UNC_KNUCKLES);
        List<SpriteDplcFrame> dplcFrames =
                S3kSpriteDataLoader.loadDplcFrames(sk, Kis2Constants.MAP_RUNC_KNUCKLES);
        SpriteAnimationSet animationSet = S3kSpriteDataLoader.loadAnimationSet(sk,
                Kis2Constants.KNUCKLES_ANIM_DATA, Kis2Constants.KNUCKLES_ANIM_SCRIPT_COUNT);
        int bankSize = S3kSpriteDataLoader.resolveBankSize(dplcFrames, mappingFrames);
        cachedKnuckles = new SpriteArtSet(
                tiles,
                mappingFrames,
                dplcFrames,
                0,                               // palette line 0 (Obj01_Init art_tile)
                Sonic2Constants.ART_TILE_SONIC,  // ArtTile_ArtUnc_Sonic ($780): Knuckles takes Sonic's slot
                1,
                bankSize,
                animationProfile(),
                animationSet);
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
                .setIdleAnimId(Sonic3kAnimationIds.WAIT)
                .setWalkAnimId(Sonic3kAnimationIds.WALK)
                .setRunAnimId(Sonic3kAnimationIds.RUN)
                .setRunFramesUseWalkAnimationId(true)
                .setRollAnimId(Sonic3kAnimationIds.ROLL)
                .setRoll2AnimId(Sonic3kAnimationIds.ROLL2)
                .setPushAnimId(Sonic3kAnimationIds.PUSH)
                .setPushUsesWalkSpecialHandler(true)
                .setPushDelayShift(8)            // KiS2 SAnim_Push: lsr.w #8,d2
                .setDuckAnimId(Sonic3kAnimationIds.DUCK)
                .setLookUpAnimId(Sonic3kAnimationIds.LOOK_UP)
                .setSpindashAnimId(Sonic3kAnimationIds.SPINDASH)
                .setSpringAnimId(Sonic3kAnimationIds.SPRING)
                .setDeathAnimId(Sonic3kAnimationIds.DEATH)
                .setDrownAnimId(Sonic3kAnimationIds.DROWN)
                .setHurtAnimId(Sonic3kAnimationIds.HURT)
                .setSkidAnimId(Sonic3kAnimationIds.SKID)
                .setAirAnimId(Sonic3kAnimationIds.WALK)
                .setBalanceAnimId(Sonic3kAnimationIds.BALANCE)
                .setWalkSpeedThreshold(0x40)
                .setRunSpeedThreshold(0x600)     // SAnim_WalkRun cmpi.w #$600,d2
                .setFallbackFrame(0)
                .setAnglePreAdjust(true)         // stock S2 SAnim_WalkRun subq.b #1,d0
                .setWalkRunPublishesFrameBeforeTimerAdvance(true)
                .setDoubleWalkRunAnimationSpeedWhenSliding(true)
                .setTumbleFrameBase(0x31);       // KiS2 SAnim_Tumble addi.b #$31,d0
    }
}
