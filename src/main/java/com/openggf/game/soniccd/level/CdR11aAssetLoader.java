package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.util.Objects;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;

/** First bounded production disc consumer. No Rom, module, engine singleton, JSON or source-asset fallback. */
public final class CdR11aAssetLoader {
    private CdR11aAssetLoader() { }

    /**
     * Binds variant, stage, catalogue slot, observed complete-content profile, MMD bytes and domain
     * before any asset decode. Current supported media layouts are the three existing captured
     * retail content records. This adds no pressing, BIOS, stock admission or gameplay certification.
     */
    public static CdR11aAssets load(CdProgramAssets source, CdReleaseVariant variant, CdStageKey stage) throws IOException {
        CdR11aProfile profile = CdR11aProfile.forStage(variant, stage);
        Objects.requireNonNull(source, "source");
        if (source.header().observedVariant().orElse(null) != variant) {
            throw new IOException("Requested variant differs from supplied disc headers");
        }
        var observedRelease = CdRetailReleases.find(source.identity())
                .orElseThrow(() -> new IOException("R11A assets require an existing known whole-disc content profile"));
        if (observedRelease.variant() != variant) {
            throw new IOException("Whole-disc content differs from requested R11A variant");
        }
        var catalogue = source.catalogue().orElseThrow(() -> new IOException("Missing release-bound CD catalogue"));
        var payload = catalogue.stage(stage);
        if (payload.file() != profile.file() || !payload.stage().equals(profile.stage())) {
            throw new IOException("Catalogue stage/file does not match the R11A profile");
        }
        CdMmdImage image = source.mmd(payload.file());
        profile.verifyProgram(payload.file(), profile.domain(), image);

        var blocks = decode(profile, image, LEVEL_BLOCKS, 6432, 0x2000, 0);
        // NemesisReader peeks eight bits per run. The final tile code needs the
        // one *actual adjacent MMD byte* at $3ED74. It is read-ahead, not an
        // extension of Art_LevelTiles' source blob or a native decoder-read claim.
        // r11a_asset_check.py independently verifies payload/read-ahead cursors and equal output.
        var tiles = decode(profile, image, ART_LEVEL_TILES, 25600, 25600, 1);
        var terrain = CdR11aTerrain.decode(blocks.bytes().copy(), tiles.bytes().copy(), read(profile, image, LEVEL_CHUNKS),
                read(profile, image, LAYOUT_FG), read(profile, image, LAYOUT_BG), read(profile, image, LEVEL_COLLISION),
                read(profile, image, COL_ANGLE_MAP), read(profile, image, COL_HEIGHT_MAP), read(profile, image, COL_WIDTH_MAP));
        var loadPlan = CdR11aLoadPlan.decode(profile, image);
        int objects = profile.range(OBJECT_LAYOUTS).fileOffset();
        int objectBytes = 12 + profile.range(OBJECT_PLACEMENT_RECORDS).length() + 6;
        var placementRange = new CdR11aProfile.FileRange(profile.file(), profile.domain(), objects, objectBytes);
        var placements = CdObjectPlacements.decodeLayout(placementRange.read(image).copy());
        var sonicArt = new CdR11aProfile.FileRange(profile.file(), profile.domain(), profile.range(ART_SONIC).fileOffset(), 69472);
        var sonic = CdSonicVisualAssets.decode(read(profile, image, MAP_SPR_SONIC), read(profile, image, DPLC_SONIC),
                sonicArt.read(image).copy());
        var shield = CdShieldArt.decode(profile, image);
        var identity = new CdR11aAssets.Identity(variant, stage, payload.file(), profile.domain(),
                profile.expectedMmdSha256(), source.identity().normalizedSha256());
        return new CdR11aAssets(profile, identity, image, blocks, tiles, terrain, loadPlan, placements, sonic, shield);
    }

    private static CdR11aAssets.NemesisRead decode(CdR11aProfile profile, CdMmdImage image, CdR11aAsset asset,
                                                    int output, int capacity, int actualLookaheadBytes) throws IOException {
        var blob = profile.range(asset);
        var input = new CdR11aProfile.FileRange(profile.file(), profile.domain(), blob.fileOffset(), blob.length() + actualLookaheadBytes);
        var result = CdNemesisAsset.decode(input.read(image).copy(), output, capacity);
        return new CdR11aAssets.NemesisRead(asset, blob, input, result.consumedInputBytes(), result.bytes());
    }
    private static byte[] read(CdR11aProfile profile, CdMmdImage image, CdR11aAsset asset) {
        return profile.range(asset).read(image).copy();
    }
}
