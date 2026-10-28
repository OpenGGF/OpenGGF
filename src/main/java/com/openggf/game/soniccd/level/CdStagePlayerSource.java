package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.Objects;

/**
 * Read-only stage/player format binding over an existing aggregate. Native pin1ecc5f3a:
 * Sonic/Main.asm:146 selects MapSpr_Sonic; the actual authenticated R11A profile and
 * R11B asset descriptors bind its program-local address. No source bytes are read here.
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
}
