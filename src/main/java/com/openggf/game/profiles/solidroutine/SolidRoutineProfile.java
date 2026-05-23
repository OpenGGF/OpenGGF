package com.openggf.game.profiles.solidroutine;

import com.openggf.level.objects.SolidObjectProvider;

import java.util.Objects;

public record SolidRoutineProfile(
        SolidRoutineKind kind,
        boolean topSolidOnly,
        boolean monitorSolidity,
        int monitorVerticalOffset,
        boolean inclusiveRightEdge,
        boolean stickyContactBuffer,
        boolean usesPlatformLandingSnap,
        boolean usesCollisionHalfWidthForTopLanding,
        boolean usesGroundHalfHeightForTopSolidContact,
        boolean bypassesOffscreenSolidGate,
        boolean allowsObjectControlledSolidContacts,
        boolean forceAirOnRideExit,
        boolean dropOnFloor,
        boolean carriesAirborneRiderAfterExitPlatform) {

    public SolidRoutineProfile {
        Objects.requireNonNull(kind, "kind");
    }

    public static SolidRoutineProfile fromProvider(SolidObjectProvider provider) {
        Objects.requireNonNull(provider, "provider");
        boolean topSolidOnly = provider.isTopSolidOnly();
        boolean monitorSolidity = provider.hasMonitorSolidity();
        return new SolidRoutineProfile(
                kindFor(topSolidOnly, monitorSolidity),
                topSolidOnly,
                monitorSolidity,
                provider.getMonitorSolidObjectVerticalOffset(),
                provider.usesInclusiveRightEdge(),
                provider.usesStickyContactBuffer(),
                provider.usesPlatformObjectLandingSnap(),
                provider.usesCollisionHalfWidthForTopLanding(),
                provider.usesGroundHalfHeightForTopSolidContact(),
                provider.bypassesOffscreenSolidGate(),
                provider.allowsObjectControlledSolidContacts(),
                provider.forceAirOnRideExit(),
                provider.dropOnFloor(),
                provider.carriesAirborneRiderAfterExitPlatform());
    }

    public static SolidRoutineProfile fullSolid(boolean stickyContactBuffer) {
        return fullSolid(stickyContactBuffer, false, false);
    }

    public static SolidRoutineProfile fullSolid(
            boolean stickyContactBuffer,
            boolean inclusiveRightEdge,
            boolean bypassesOffscreenSolidGate) {
        return new SolidRoutineProfile(
                SolidRoutineKind.FULL_SOLID,
                false,
                false,
                0,
                inclusiveRightEdge,
                stickyContactBuffer,
                true,
                false,
                false,
                bypassesOffscreenSolidGate,
                false,
                true,
                false,
                false);
    }

    public static SolidRoutineProfile topSolid(boolean stickyContactBuffer) {
        return new SolidRoutineProfile(
                SolidRoutineKind.TOP_SOLID_ONLY,
                true,
                false,
                0,
                false,
                stickyContactBuffer,
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                false);
    }

    public static SolidRoutineProfile monitorSolid(int verticalOffset, boolean stickyContactBuffer) {
        return new SolidRoutineProfile(
                SolidRoutineKind.MONITOR_SOLID,
                false,
                true,
                verticalOffset,
                false,
                stickyContactBuffer,
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                false);
    }

    public static SolidRoutineAdapter adapt(SolidObjectProvider provider) {
        return new SolidRoutineAdapter(
                Objects.requireNonNull(provider, "provider"),
                fromProvider(provider));
    }

    private static SolidRoutineKind kindFor(boolean topSolidOnly, boolean monitorSolidity) {
        if (monitorSolidity) {
            return SolidRoutineKind.MONITOR_SOLID;
        }
        return topSolidOnly ? SolidRoutineKind.TOP_SOLID_ONLY : SolidRoutineKind.FULL_SOLID;
    }
}
