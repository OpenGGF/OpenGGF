package com.openggf.game.sonic2;

import com.openggf.data.RetailRomRevision;

/** Immutable, module-owned retail behavior selection; UNKNOWN retains compatibility defaults. */
public record Sonic2RevisionProfile(RetailRomRevision revision)
        implements com.openggf.game.internal.RingCollectionPolicy {
    public Sonic2RevisionProfile {
        java.util.Objects.requireNonNull(revision, "revision");
        if (revision != RetailRomRevision.UNKNOWN
                && !revision.belongsTo(com.openggf.data.RomIdentity.S2)) {
            throw new IllegalArgumentException("A Sonic 2 profile requires a Sonic 2 retail revision");
        }
    }
    public static final Sonic2RevisionProfile COMPATIBLE =
            new Sonic2RevisionProfile(RetailRomRevision.UNKNOWN);

    public boolean isRev00() { return revision == RetailRomRevision.S2_REV00; }

    /** REV01 adds routine >=4 admission checks to Obj66 and Obj85. */
    public boolean rejectsHurtBumperAndLauncher() { return !isRev00(); }

    /** Obj74 and Obj66 call DisplaySprite in native placement mode only in REV00. */
    public boolean displaysNativeDebugGeometry() { return isRev00(); }

    @Override
    public Result collect(int held, int extraLifeFlags, int amount) {
        return Sonic2RingAwards.ordinary(this, held, extraLifeFlags, amount);
    }
}
