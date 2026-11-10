package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.level.CdR11aAssets;
import java.util.Objects;
/** Retained reader/placement relation, not a second source or invocation authority. */
final class CdOriginalPresentObjectSource {
    static void require(CdObjectPlacementRuntime placement,CdR11aAssets source){
        Objects.requireNonNull(placement);Objects.requireNonNull(source);
        if(placement.placements()!=source.placements()||!placement.sourceIdentity().equals(java.util.Optional.of(source.identity())))
            throw new IllegalArgumentException("Original Present body requires its actual reader-backed placement/source");
    }
    private CdOriginalPresentObjectSource(){}
}
