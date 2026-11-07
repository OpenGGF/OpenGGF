package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.objects.CdMonitor;
import com.openggf.game.soniccd.objects.CdReservedHud;
import com.openggf.game.soniccd.objects.CdTitleCard;

import java.util.Arrays;
import java.util.Objects;

/**
 * Actual original-USA R31 common startup metadata only, from existing authenticated Asset views.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff R3 shared/Compatibility joins HUD, Monitor
 * and Title sources; r3/title_card_data.asm and sprites/r3/title_card.asm supply actual64/275+EVEN.
 * This does not authenticate handler PCs/Init directories/dispatch, create a Body/World/Frame,
 * decode/read/select art, establish residency, run scripts or manufacture operation authority.
 * The63/82/168 visual bounds are finite mapping-operand hulls only, never available-art claims.
 */
public final class CdR31aObjectAssets {
    private final CdR31aAssets assets;
    private final CdR31aAssets.Identity identity;
    private CdObjectVisualAssets hudVisuals, monitorTime, titleCard;
    private CdReservedHud.Assets reservedHud;
    private CdMonitor.Assets monitor;
    private CdTitleCard.Assets title;

    private CdR31aObjectAssets(CdR31aAssets assets) {
        this.assets = Objects.requireNonNull(assets, "actual original R31A data");
        assets.requireAuthenticated(); // Existing finite whole-source check, no new raw reader.
        identity = Objects.requireNonNull(assets.identity(), "actual R31A identity");
        if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R31A
                || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !identity.stage().equals(CdStagePayload.R31A.stage())) {
            throw new IllegalArgumentException("Only original USA R31A common metadata");
        }
    }

    public static CdR31aObjectAssets fromAssets(CdR31aAssets assets) { return new CdR31aObjectAssets(assets); }
    public boolean ownsAssets(CdR31aAssets assets) { return this.assets == assets; }
    public CdR31aAssets.Identity identity() { return identity; }

    /** Shared native HUD table:4 frames, max operand $3D+2=63, no digit/life art access. */
    public synchronized CdObjectVisualAssets hudVisuals() {
        if (hudVisuals == null) hudVisuals = CdObjectVisualAssets.decodeStatic(
                assets.view(CdR31aAssets.Asset.HUD_MAPPINGS).copy(), 4, 63);
        return hudVisuals;
    }

    /** Complete450-byte/20-frame and136-byte/10-script includes. Max operand $50+2=82. */
    public synchronized CdObjectVisualAssets monitorTime() {
        if (monitorTime == null) monitorTime = CdObjectVisualAssets.decode(
                assets.view(CdR31aAssets.Asset.MONITOR_MAPPINGS).copy(), 20,
                assets.view(CdR31aAssets.Asset.MONITOR_ANIMATIONS).copy(), 10, 82);
        return monitorTime;
    }

    /** R3's genuine276-byte read view retains its real EVEN; Sprite8 requires $A2+6=168. */
    public synchronized CdObjectVisualAssets titleCard() {
        if (titleCard == null) {
            var canonical = assets.view(CdR31aAssets.Asset.TITLE_MAPPINGS);
            var reader = assets.view(CdR31aAssets.Asset.TITLE_MAPPING_READ_VIEW);
            if (!Arrays.equals(canonical.copy(), reader.slice(0, canonical.length()).copy())) {
                throw new IllegalArgumentException("R31 Title canonical/read-view source differs");
            }
            titleCard = CdObjectVisualAssets.decodeStatic(reader.copy(), 9, 168);
        }
        return titleCard;
    }

    /** Ordinary pointer values only. Public record equality grants no reserved-bank ownership. */
    public synchronized CdReservedHud.Assets reservedHudAssets() {
        if (reservedHud == null) reservedHud = new CdReservedHud.Assets(
                assets.range(CdR31aAssets.Asset.HUD_MAPPINGS).nativeAddress(),
                assets.range(CdR31aAssets.Asset.MONITOR_MAPPINGS).nativeAddress());
        return reservedHud;
    }

    /** Reuses the existing strict20/10/450/136 component contract without admitting its caller. */
    public synchronized CdMonitor.Assets monitorAssets() {
        if (monitor == null) monitor = new CdMonitor.Assets(monitorTime(),
                assets.range(CdR31aAssets.Asset.MONITOR_MAPPINGS).nativeAddress());
        return monitor;
    }

    /** Actual64 character bytes and source-local root; native ACT/read/child owners stay external. */
    public synchronized CdTitleCard.Assets titleCardAssets() {
        if (title == null) title = new CdTitleCard.Assets(assets.view(CdR31aAssets.Asset.TITLE_CHARACTERS),
                assets.range(CdR31aAssets.Asset.TITLE_MAPPINGS).nativeAddress());
        return title;
    }
}
