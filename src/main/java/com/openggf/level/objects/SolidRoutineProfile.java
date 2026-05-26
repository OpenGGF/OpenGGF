package com.openggf.level.objects;

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

    public static SolidRoutineProfile fromProvider(SolidObjectProvider provider) {
        return fromCanonical(com.openggf.game.profiles.solidroutine.SolidRoutineProfile.fromProvider(provider));
    }

    public static SolidRoutineProfile fullSolid(boolean stickyContactBuffer) {
        return fromCanonical(com.openggf.game.profiles.solidroutine.SolidRoutineProfile.fullSolid(stickyContactBuffer));
    }

    public static SolidRoutineProfile fullSolid(
            boolean stickyContactBuffer,
            boolean inclusiveRightEdge,
            boolean bypassesOffscreenSolidGate) {
        return fromCanonical(com.openggf.game.profiles.solidroutine.SolidRoutineProfile.fullSolid(
                stickyContactBuffer,
                inclusiveRightEdge,
                bypassesOffscreenSolidGate));
    }

    public static SolidRoutineProfile topSolid(boolean stickyContactBuffer) {
        return fromCanonical(com.openggf.game.profiles.solidroutine.SolidRoutineProfile.topSolid(stickyContactBuffer));
    }

    public static SolidRoutineProfile monitorSolid(int verticalOffset, boolean stickyContactBuffer) {
        return fromCanonical(com.openggf.game.profiles.solidroutine.SolidRoutineProfile.monitorSolid(
                verticalOffset,
                stickyContactBuffer));
    }

    public static SolidRoutineAdapter adapt(SolidObjectProvider provider) {
        return new SolidRoutineAdapter(
                Objects.requireNonNull(provider, "provider"),
                fromProvider(provider));
    }

    public com.openggf.game.profiles.solidroutine.SolidRoutineProfile toCanonical() {
        return new com.openggf.game.profiles.solidroutine.SolidRoutineProfile(
                kind.toCanonical(),
                topSolidOnly,
                monitorSolidity,
                monitorVerticalOffset,
                inclusiveRightEdge,
                stickyContactBuffer,
                usesPlatformLandingSnap,
                usesCollisionHalfWidthForTopLanding,
                usesGroundHalfHeightForTopSolidContact,
                bypassesOffscreenSolidGate,
                allowsObjectControlledSolidContacts,
                forceAirOnRideExit,
                dropOnFloor,
                carriesAirborneRiderAfterExitPlatform);
    }

    public static SolidRoutineProfile fromCanonical(
            com.openggf.game.profiles.solidroutine.SolidRoutineProfile canonical) {
        Objects.requireNonNull(canonical, "canonical");
        return new SolidRoutineProfile(
                SolidRoutineKind.fromCanonical(canonical.kind()),
                canonical.topSolidOnly(),
                canonical.monitorSolidity(),
                canonical.monitorVerticalOffset(),
                canonical.inclusiveRightEdge(),
                canonical.stickyContactBuffer(),
                canonical.usesPlatformLandingSnap(),
                canonical.usesCollisionHalfWidthForTopLanding(),
                canonical.usesGroundHalfHeightForTopSolidContact(),
                canonical.bypassesOffscreenSolidGate(),
                canonical.allowsObjectControlledSolidContacts(),
                canonical.forceAirOnRideExit(),
                canonical.dropOnFloor(),
                canonical.carriesAirborneRiderAfterExitPlatform());
    }
}
