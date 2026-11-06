package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.util.Objects;

/**
 * Read-only stage/player format binding over an existing aggregate. Native pin1ecc5f3a:
 * Sonic/Main.asm:146 selects MapSpr_Sonic; the actual authenticated R11A profile and
 * R11B/R12A/R12B/R13C/R13D asset descriptors bind its program-local address. No source bytes are read here.
 *
 * <p>Aggregate factories own media authentication. Consistent constructed package
 * fixtures remain data, not authenticated media. Reference relations below confer
 * no execution, invocation, source lease, Frame admission or exact-once authority.
 * This binding borrows immutable products without decoding, opening banks, retaining
 * a mutable Level, or creating art, input, clock, phase, callback or capture owners.</p>
 */
public final class CdStagePlayerSource {
    private final CdR11aAssets present;
    private final CdR11bAssets past;
    private final CdR12aTerrainAssets act2Terrain;
    private final CdR12aPlayerAssets act2Player;
    private final CdR12bTerrainAssets act2PastTerrain;
    private final CdR12bPlayerAssets act2PastPlayer;
    private final CdR13FutureAssets act3Future;
    private final CdStageTerrain terrain;
    private final CdR11aPlayerAssets player;
    private final CdSonicVisualAssets sonic;
    private final int mappingAddress;
    private final CdReleaseVariant variant;
    private final CdStageKey stage;
    private final int ordinaryPeriod;

    private CdStagePlayerSource(CdR11aAssets present, CdR11bAssets past) {
        if ((present == null) == (past == null)) {
            throw new IllegalArgumentException("Exactly one explicit stage aggregate is required");
        }
        this.present = present;
        this.past = past;
        this.act2Terrain = null;
        this.act2Player = null;
        this.act2PastTerrain = null;
        this.act2PastPlayer = null;
        this.act3Future = null;
        CdStageTerrain.Shape expectedShape;
        if (present != null) {
            var identity = Objects.requireNonNull(present.identity(), "Present identity");
            var profile = Objects.requireNonNull(present.profile(), "Present profile");
            variant = Objects.requireNonNull(identity.variant(), "Present variant");
            stage = Objects.requireNonNull(identity.stage(), "Present stage");
            if (identity.file() != CdNativeFile.R11A || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || identity.file() != profile.file() || identity.domain() != profile.domain()
                    || variant != profile.variant() || !stage.equals(profile.stage())
                    || !stage.equals(CdStagePayload.R11A.stage())) {
                throw new IllegalArgumentException("Present profile, identity and native stage differ");
            }
            terrain = Objects.requireNonNull(present.terrain(), "Present terrain").stageTerrain();
            player = Objects.requireNonNull(present.player(), "Present player data");
            sonic = Objects.requireNonNull(present.sonic(), "Present Sonic visuals");
            mappingAddress = profile.range(CdR11aAsset.MAP_SPR_SONIC).nativeAddress();
            ordinaryPeriod = 1;
            expectedShape = CdStageTerrain.Shape.PPZ1_PRESENT;
        } else {
            var identity = Objects.requireNonNull(past.identity(), "Past identity");
            variant = Objects.requireNonNull(identity.variant(), "Past variant");
            stage = Objects.requireNonNull(identity.stage(), "Past stage");
            if (variant != CdReleaseVariant.USA || identity.file() != CdNativeFile.R11B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !stage.equals(CdStagePayload.R11B.stage())) {
                throw new IllegalArgumentException("Past identity and native stage differ");
            }
            terrain = Objects.requireNonNull(past.terrain(), "Past terrain");
            player = Objects.requireNonNull(past.player(), "Past player data");
            sonic = Objects.requireNonNull(past.sonic(), "Past Sonic visuals");
            mappingAddress = CdR11bAssets.Asset.MAP_SPR_SONIC.range().nativeAddress();
            ordinaryPeriod = 0;
            expectedShape = CdStageTerrain.Shape.PPZ1_PAST;
        }
        if (terrain.shape() != expectedShape) {
            throw new IllegalArgumentException("Stage identity and explicit native terrain format differ");
        }
        if (player.sonic() != sonic) {
            throw new IllegalArgumentException("Player and Sonic visuals must be the same borrowed product");
        }
    }

    private CdStagePlayerSource(CdR12aTerrainAssets terrainAssets, CdR12aPlayerAssets playerAssets) {
        present = null; past = null; act2Terrain = terrainAssets; act2Player = playerAssets;
        act2PastTerrain = null; act2PastPlayer = null;
        act3Future = null;
        var terrainIdentity = Objects.requireNonNull(terrainAssets.identity(), "Act2 terrain identity");
        var playerIdentity = Objects.requireNonNull(playerAssets.identity(), "Act2 player identity");
        var terrainProfile = Objects.requireNonNull(terrainAssets.profile(), "Act2 terrain profile");
        var playerProfile = Objects.requireNonNull(playerAssets.profile(), "Act2 player profile");
        variant = Objects.requireNonNull(terrainIdentity.variant(), "Act2 variant");
        stage = Objects.requireNonNull(terrainIdentity.stage(), "Act2 stage");
        if (variant != CdReleaseVariant.USA || !stage.equals(CdStagePayload.R12A.stage())
                || terrainIdentity.file() != CdNativeFile.R12A || terrainIdentity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !terrainIdentity.equals(playerIdentity)
                || variant != terrainProfile.variant() || variant != playerProfile.variant()
                || !stage.equals(terrainProfile.stage()) || !stage.equals(playerProfile.stage())
                || terrainIdentity.file() != terrainProfile.file() || terrainIdentity.file() != playerProfile.file()
                || terrainIdentity.domain() != terrainProfile.domain() || terrainIdentity.domain() != playerProfile.domain()
                || !terrainIdentity.mmdSha256().equals(terrainProfile.expectedMmdSha256())
                || !terrainIdentity.mmdSha256().equals(playerProfile.expectedMmdSha256())
                || !terrainProfile.range(CdR12aProfile.Asset.MAP_SPR_SONIC).equals(playerProfile.range(CdR12aProfile.Asset.MAP_SPR_SONIC))) {
            throw new IllegalArgumentException("Act2 terrain/player original source, profiles or disc lineage differ");
        }
        terrain = Objects.requireNonNull(terrainAssets.terrain(), "Act2 terrain");
        player = Objects.requireNonNull(playerAssets.player(), "Act2 player data");
        sonic = Objects.requireNonNull(playerAssets.sonic(), "Act2 Sonic visuals");
        if (terrain.shape() != CdStageTerrain.Shape.PPZ2_PRESENT)
            throw new IllegalArgumentException("Act2 identity and explicit native terrain format differ");
        if (player.sonic() != sonic)
            throw new IllegalArgumentException("Player and Sonic visuals must be the same borrowed product");
        mappingAddress = playerProfile.range(CdR12aProfile.Asset.MAP_SPR_SONIC).nativeAddress();
        ordinaryPeriod = 1;
    }

    private CdStagePlayerSource(CdR12bTerrainAssets terrainAssets, CdR12bPlayerAssets playerAssets) {
        present = null; past = null; act2Terrain = null; act2Player = null;
        act2PastTerrain = terrainAssets; act2PastPlayer = playerAssets;
        act3Future = null;
        var terrainIdentity = Objects.requireNonNull(terrainAssets.identity(), "Act2 Past terrain identity");
        var playerIdentity = Objects.requireNonNull(playerAssets.identity(), "Act2 Past player identity");
        var terrainProfile = Objects.requireNonNull(terrainAssets.profile(), "Act2 Past terrain profile");
        var playerProfile = Objects.requireNonNull(playerAssets.profile(), "Act2 Past player profile");
        variant = Objects.requireNonNull(terrainIdentity.variant(), "Act2 Past variant");
        stage = Objects.requireNonNull(terrainIdentity.stage(), "Act2 Past stage");
        if (variant != CdReleaseVariant.USA || !stage.equals(CdStagePayload.R12B.stage())
                || terrainIdentity.file() != CdNativeFile.R12B || terrainIdentity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !terrainIdentity.equals(playerIdentity)
                || variant != terrainProfile.variant() || variant != playerProfile.variant()
                || !stage.equals(terrainProfile.stage()) || !stage.equals(playerProfile.stage())
                || terrainIdentity.file() != terrainProfile.file() || terrainIdentity.file() != playerProfile.file()
                || terrainIdentity.domain() != terrainProfile.domain() || terrainIdentity.domain() != playerProfile.domain()
                || !terrainIdentity.mmdSha256().equals(terrainProfile.expectedMmdSha256())
                || !terrainIdentity.mmdSha256().equals(playerProfile.expectedMmdSha256())
                || !CdR12bPlayerAssets.Asset.SPEC_CHUNKS.range().equals(terrainProfile.range(CdR12bProfile.Asset.SPEC_CHUNKS))
                || !CdR12bPlayerAssets.Asset.SPEC_CHUNKS.range().equals(playerProfile.range(CdR12bProfile.Asset.SPEC_CHUNKS))) {
            throw new IllegalArgumentException("Act2 Past terrain/player original source, profiles or disc lineage differ");
        }
        terrain = Objects.requireNonNull(terrainAssets.terrain(), "Act2 Past terrain");
        player = Objects.requireNonNull(playerAssets.player(), "Act2 Past player data");
        sonic = Objects.requireNonNull(playerAssets.sonic(), "Act2 Past Sonic visuals");
        if (terrain.shape() != CdStageTerrain.Shape.PPZ2_PAST)
            throw new IllegalArgumentException("Act2 Past identity and explicit native terrain format differ");
        if (player.sonic() != sonic)
            throw new IllegalArgumentException("Player and Sonic visuals must be the same borrowed product");
        mappingAddress = CdR12bPlayerAssets.Asset.MAP_SPR_SONIC.nativeAddress();
        ordinaryPeriod = 0;
    }

    // BEGIN original Act3 Future immutable data relation.
    private CdStagePlayerSource(CdR13FutureAssets assets) {
        present = null; past = null; act2Terrain = null; act2Player = null;
        act2PastTerrain = null; act2PastPlayer = null; act3Future = assets;
        var identity = Objects.requireNonNull(assets.identity(), "Act3 Future identity");
        variant = Objects.requireNonNull(identity.variant(), "Act3 Future variant");
        stage = Objects.requireNonNull(identity.stage(), "Act3 Future stage");
        boolean good = stage.equals(CdStagePayload.R13C.stage()) && identity.file() == CdNativeFile.R13C;
        boolean bad = stage.equals(CdStagePayload.R13D.stage()) && identity.file() == CdNativeFile.R13D;
        String expectedHash = good ? "3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f"
                : "27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356";
        // Identity consistency only. Aggregate factories retain all actual media authentication.
        if (variant != CdReleaseVariant.USA || (!good && !bad)
                || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !expectedHash.equals(identity.mmdSha256())
                || !CdRetailReleases.USA.normalizedSha256().equals(identity.observedWholeDiscSha256())) {
            throw new IllegalArgumentException("Act3 Future original source identity and stage differ");
        }
        terrain = Objects.requireNonNull(assets.terrain(), "Act3 Future terrain");
        player = Objects.requireNonNull(assets.player(), "Act3 Future player data");
        sonic = Objects.requireNonNull(assets.sonic(), "Act3 Future Sonic visuals");
        if (terrain.shape() != (good ? CdStageTerrain.Shape.PPZ3_GOOD_FUTURE : CdStageTerrain.Shape.PPZ3_BAD_FUTURE)) {
            throw new IllegalArgumentException("Act3 Future identity and native terrain format differ");
        }
        if (player.sonic() != sonic || sonic.sourceFormat() != CdSonicVisualAssets.SourceFormat.FRAMES_167) {
            throw new IllegalArgumentException("Act3 Future player and Sonic visuals must be the same 167-frame product");
        }
        var mapping = Objects.requireNonNull(assets.range(CdR13FutureAssets.Asset.MAP_SPR_SONIC), "Act3 Future mapping range");
        if (mapping.file() != identity.file() || mapping.domain() != identity.domain()
                || mapping.fileOffset() != CdR13FutureAssets.Asset.MAP_SPR_SONIC.fileOffset(identity.file())
                || mapping.length() != CdR13FutureAssets.Asset.MAP_SPR_SONIC.length(identity.file())) {
            throw new IllegalArgumentException("Act3 Future mapping range and original source differ");
        }
        mappingAddress = mapping.nativeAddress();
        ordinaryPeriod = 2;
    }

    /** Borrow original C/D immutable data only; no programme, Frame, FULL or invocation authority. */
    public static CdStagePlayerSource fromAct3Future(CdR13FutureAssets assets) {
        return new CdStagePlayerSource(Objects.requireNonNull(assets, "Act3 Future assets"));
    }

    /** Exact immutable aggregate relation only; equal metadata does not imply ownership or execution. */
    public boolean ownsAct3Future(CdR13FutureAssets assets) {
        return act3Future != null && act3Future == assets;
    }
    // END original Act3 Future immutable data relation.

    /** Original R12B data relation only. It does not select a destination or admit a Frame/load/World. */
    public static CdStagePlayerSource fromAct2Past(CdR12bTerrainAssets terrain, CdR12bPlayerAssets player) {
        return new CdStagePlayerSource(Objects.requireNonNull(terrain, "Act2 Past terrain assets"),
                Objects.requireNonNull(player, "Act2 Past player assets"));
    }

    /**
     * Explicit original R12A data relation. Equal source identities include complete-file and
     * observed-disc lineage; the factories retain authentication responsibility. Independent
     * immutable aggregates may be paired, without implying a private source lease or programme join.
     */
    public static CdStagePlayerSource fromAct2(CdR12aTerrainAssets terrain, CdR12aPlayerAssets player) {
        return new CdStagePlayerSource(Objects.requireNonNull(terrain, "Act2 terrain assets"),
                Objects.requireNonNull(player, "Act2 player assets"));
    }

    /** Present format/identity consistency only; opens no optional bank or media source. */
    public static CdStagePlayerSource fromPresent(CdR11aAssets assets) {
        return new CdStagePlayerSource(Objects.requireNonNull(assets, "Present assets"), null);
    }

    /** Actual Past aggregate data remains separate from the typed Present facade. */
    public static CdStagePlayerSource fromPast(CdR11bAssets assets) {
        return new CdStagePlayerSource(null, Objects.requireNonNull(assets, "Past assets"));
    }

    public CdStageTerrain terrain() { return terrain; }
    public CdR11aPlayerAssets player() { return player; }
    public CdSonicVisualAssets sonic() { return sonic; }
    public int mappingAddress() { return mappingAddress; }
    public CdReleaseVariant variant() { return variant; }
    public CdStageKey stage() { return stage; }

    /** Source's ordinary period only; never a replacement for the actual raw/marked Frame gate. */
    public int ordinaryPeriod() { return ordinaryPeriod; }

    public boolean ownsPresent(CdR11aAssets assets) { return present != null && present == assets; }
    public boolean ownsPast(CdR11bAssets assets) { return past != null && past == assets; }
    /** Exact selected immutable pair only; equal metadata or one matching aggregate is insufficient. */
    public boolean ownsAct2(CdR12aTerrainAssets terrain, CdR12aPlayerAssets player) {
        return act2Terrain != null && act2Player != null && act2Terrain == terrain && act2Player == player;
    }
    /** Exact selected R12B immutable pair; R12A and act1 Past ownership remain distinct. */
    public boolean ownsAct2Past(CdR12bTerrainAssets terrain, CdR12bPlayerAssets player) {
        return act2PastTerrain != null && act2PastPlayer != null && act2PastTerrain == terrain && act2PastPlayer == player;
    }
}
