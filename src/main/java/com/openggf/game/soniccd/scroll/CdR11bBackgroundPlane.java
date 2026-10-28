package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStageTerrain;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.CopiedCameras;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.FlagWords;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.LiveCameras;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Result;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State;

import java.util.Objects;

/**
 * Pure finite R11B Plane B binding over the existing shared name-table algorithm and value types.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Palmtree Panic/Scroll (Act 1 Past).asm:
 * DrawLevelBG1:700-803, InitLevelDrawBG:899-914, BGCameraSectIDs/BGCameraSects:934-947,
 * DrawBGBlockRow:961-984; common Block Draw.asm:GetBlockDataAbsXY:202-235/DrawBlock:117-179.
 * The supplied 34-byte selector view is never padded to Present's 130 bytes. Initial selection
 * masks $1F0; incremental rows/columns retain their distinct signed/unsigned source rules.
 * No new captured state, clock, camera step, current-art service, GPU write or live stage admission.
 */
public final class CdR11bBackgroundPlane {
    private final CdR11aBackgroundPlane plane;

    private CdR11bBackgroundPlane(CdR11aBackgroundPlane plane) {
        this.plane = Objects.requireNonNull(plane, "plane");
    }

    /** Reuses the actual authenticated asset aggregate; never opens media or a disassembly. */
    public static CdR11bBackgroundPlane bind(CdR11bAssets assets) {
        Objects.requireNonNull(assets, "assets");
        return fromViews(assets.terrain(), assets.view(CdR11bAssets.Asset.BG_CAMERA_SECT_IDS),
                assets.view(CdR11bAssets.Asset.BG_CAMERA_SECTS));
    }

    /** Named source/shape checks only; constructed views confer no media or execution authority. */
    public static CdR11bBackgroundPlane fromViews(CdStageTerrain terrain, CdR11bAssets.View ids,
                                                 CdR11bAssets.View pointers) {
        return new CdR11bBackgroundPlane(CdR11aBackgroundPlane.forPastViews(terrain, ids, pointers));
    }

    /** Explicit unauthenticated component-fixture seam, with immutable existing terrain/view borrows. */
    public static CdR11bBackgroundPlane fromTables(CdStageTerrain terrain, CdAssetBytes ids, CdAssetBytes pointers) {
        return new CdR11bBackgroundPlane(CdR11aBackgroundPlane.forPastTables(terrain, ids, pointers));
    }

    public Result initialize(State before, LiveCameras live, FlagWords flags) {
        return plane.initialize(before, live, flags);
    }

    public Result incremental(State before, LiveCameras live, CopiedCameras copied, FlagWords flags) {
        return plane.incremental(before, live, copied, flags);
    }
}
