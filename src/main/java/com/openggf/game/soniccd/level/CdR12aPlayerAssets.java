package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.util.Objects;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;

/**
 * Detached actual-USA R12A player byte prerequisites, not a player, current art bank or playable act.
 * Pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Sonic/Main.asm:135-150,797-831,2645-2662,
 * 2951-3049,3066-3089; Functions (Misc).asm:69-101,143-150,175-230;
 * Scroll (Act 2 Present).asm:131-147; Data (Act 2 Present).asm:116-126.
 * Bytes exclusively come from the authenticated original MMD, never the native source tree.
 */
public final class CdR12aPlayerAssets {
    // The terrain factory's immutable original-file identity describes the same source; it does not
    // cause a second terrain decode, borrow another act's profile or admit a runtime owner.
    private final CdR12aTerrainAssets.Identity identity;
    private final CdR12aProfile profile;
    private final CdMmdImage image;
    private final CdSonicVisualAssets sonic;
    private final CdR11aPlayerAssets player;

    private CdR12aPlayerAssets(CdR12aTerrainAssets.Identity identity, CdR12aProfile profile, CdMmdImage image,
                              CdSonicVisualAssets sonic, CdR11aPlayerAssets player) {
        this.identity = identity; this.profile = profile; this.image = image; this.sonic = sonic; this.player = player;
    }

    /** Borrows the actual catalogue/CUE owner; retains immutable detached values without closing its caller. */
    public static CdR12aPlayerAssets fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs, "actual CD program owner");
        if (programs.header().observedVariant().orElse(null) != CdReleaseVariant.USA)
            throw new IOException("R12A player assets require original USA media");
        var release = CdRetailReleases.find(programs.identity())
                .orElseThrow(() -> new IOException("R12A player assets require a known complete-disc content profile"));
        if (release.variant() != CdReleaseVariant.USA) throw new IOException("R12A player source release differs");
        var payload = programs.catalogue().orElseThrow(() -> new IOException("R12A source catalogue unavailable"))
                .stage(CdStagePayload.R12A.stage());
        var profile = CdR12aProfile.forStage(CdReleaseVariant.USA, payload.stage());
        if (payload.file() != profile.file()) throw new IOException("R12A catalogue stage/file differs");
        var image = programs.mmd(payload.file()); profile.verifyProgram(payload.file(), profile.domain(), image);
        // ObjSonic_Init actually selects230F60, not the identical legacy copy at21F200.
        // Full69472 source bytes include the actual final00 that frames30/31 read.
        var sonic = CdSonicVisualAssets.decode(read(profile, image, MAP_SPR_SONIC), read(profile, image, DPLC_SONIC),
                read(profile, image, ART_SONIC));
        // The existing R11a-named class is a byte-format decoder, not an act/source fallback.
        // Native valid animation IDs reach only the first60 of80 MiniAnims bytes; CalcAngle
        // reaches quotient256 but not the separate trailing00 at index257.
        var player = CdR11aPlayerAssets.decode(read(profile, image, ANI_SONIC),
                profile.range(SONIC_MINI_ANIMATIONS).read(image).slice(0, 60).copy(), read(profile, image, SONIC_RAMP_CHUNKS),
                read(profile, image, SPEC_CHUNKS), read(profile, image, SINE_TABLE),
                profile.range(ARC_TAN_TABLE).read(image).slice(0, 257).copy(), sonic);
        return new CdR12aPlayerAssets(new CdR12aTerrainAssets.Identity(profile.variant(), profile.stage(), profile.file(), profile.domain(),
                profile.expectedMmdSha256(), programs.identity().normalizedSha256()), profile, image, sonic, player);
    }

    public CdR12aTerrainAssets.Identity identity() { return identity; }
    public CdR12aProfile profile() { return profile; }
    public CdSonicVisualAssets sonic() { return sonic; }
    public CdR11aPlayerAssets player() { return player; }
    public CdR12aProfile.FileRange sonicArtReadRange() { return profile.range(ART_SONIC); }
    public CdAssetBytes view(CdR12aProfile.Asset asset) { return profile.range(asset).read(image); }
    private static byte[] read(CdR12aProfile profile, CdMmdImage image, CdR12aProfile.Asset asset) {
        return profile.range(asset).read(image).copy();
    }
}
