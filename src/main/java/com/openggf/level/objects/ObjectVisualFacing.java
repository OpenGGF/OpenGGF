package com.openggf.level.objects;

import java.util.Objects;

/**
 * Native logical facing for a complete object performer. This is deliberately
 * independent of mapping-frame artwork: turns, skids and look-backs keep their
 * native art relative to the owning routine's direction.
 */
public final class ObjectVisualFacing {
    private ObjectVisualFacing() { }

    public enum Basis { LEFT, RIGHT, NON_DIRECTIONAL }

    /** Implement at the native owner, reading the same semantic state as its routine. */
    public interface Source {
        Basis nativeFacing();
    }

    /**
     * An explicit native declaration that this owner has no shared horizontal
     * body-facing transform. A directional body must declare its native owner;
     * independent child directions require {@link Container}, not a neutral
     * declaration that silently hides those children.
     */
    public interface NonDirectional extends Source {
        @Override default Basis nativeFacing() { return Basis.NON_DIRECTIONAL; }
    }

    /**
     * Native grouping root without a shared body-facing direction. Its own
     * geometry stays native; separately owned child performers keep their own
     * facing groups and anchors. Every visible child requires an explicit Source.
     */
    public interface Container extends NonDirectional { }

    /**
     * Direction belongs to the selected native mapping/pose (for example a
     * missile's angle frame), rather than a separate horizontal mirror state.
     * Preserve that native pose direction instead of mirroring the whole picture
     * from velocity or from an inferred left/right default.
     */
    public interface PoseOwned extends NonDirectional { }

    /**
     * Optional constructor contract for a newly authored Free spawn. Placement
     * bits are native source data; their relationship to logical direction is
     * not universal, even among S1 badniks.
     */
    public interface PlacementSource extends Source {
        int initialPlacementFlags(Basis facing, int sourceFlags);
    }

    public static Basis resolve(ObjectInstance object) {
        Objects.requireNonNull(object, "native object");
        if (object instanceof Source source) {
            Basis basis = source.nativeFacing();
            if (source instanceof NonDirectional && basis != Basis.NON_DIRECTIONAL) {
                throw new IllegalArgumentException(object.getClass().getName()
                        + " declares ObjectVisualFacing.NonDirectional/Container/PoseOwned but inherits or returns " + basis
                        + "; override nativeFacing() to return NON_DIRECTIONAL or correct the native ownership declaration");
            }
            if (basis != null) return basis;
        }
        throw new IllegalArgumentException("Native logical facing is undeclared for "
                + object.getClass().getName() + "; implement ObjectVisualFacing.Source.nativeFacing()"
                + " at its native owner; use ObjectVisualFacing.NonDirectional only without a shared"
                + " body-facing transform, Container for independent child groups, or PoseOwned for native pose direction");
    }

    /** Does not mutate an existing placement or execute a native routine. */
    public static int initialPlacementFlags(ObjectInstance object, Basis facing, int sourceFlags) {
        Objects.requireNonNull(facing, "requested facing");
        if (facing == Basis.NON_DIRECTIONAL)
            throw new IllegalArgumentException("Initial logical direction requires LEFT or RIGHT");
        if (object instanceof PlacementSource source) {
            return source.initialPlacementFlags(facing, sourceFlags);
        }
        Basis basis = resolve(object);
        if (basis == Basis.NON_DIRECTIONAL) return sourceFlags;
        throw new IllegalArgumentException("Native initial-facing placement flags are undeclared for "
                + object.getClass().getName() + "; implement ObjectVisualFacing.PlacementSource"
                + ".initialPlacementFlags(Basis, int) at its native constructor owner;"
                + " preserve existing placements' source flags");
    }
}
