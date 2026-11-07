package com.openggf.game.soniccd.objects;

import java.util.Map;
import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;

/** Borrowed views only. Root must obtain every view from the SAME authenticated programme
 * as the actual placement/World. Construction certifies neither media nor live admission.
 * Missing views fail only on the reached lookup; held animation never looks them up.
 * Mapping root includes both its authentic address and its bounded read hull, including any
 * authentic adjacent bytes required by the existing decoder. This adapter creates no bytes.
 */
public final class CdCczEnemyAssets implements Assets {
    private final Map<Mapping, MappingView> mappings;
    private final Map<Animation, CdObjectAnimation.AnimationTable> animations;
    private final int chainLinks;
    public CdCczEnemyAssets(Map<Mapping, MappingView> mappings,
                           Map<Animation, CdObjectAnimation.AnimationTable> animations, int chainLinks) {
        this.mappings = Map.copyOf(mappings); this.animations = Map.copyOf(animations);
        if (chainLinks < 2 || chainLinks > 4) throw new IllegalArgumentException("Actual programme links 2..4");
        this.chainLinks = chainLinks;
    }
    @Override public MappingView mapping(Mapping which) {
        var v = mappings.get(which);
        if (v == null) throw new UnsupportedOperationException("Reached unbound CC mapping " + which);
        return v;
    }
    @Override public CdObjectAnimation.AnimationTable animation(Animation which) {
        var v = animations.get(which);
        if (v == null) throw new UnsupportedOperationException("Reached unbound CC animation " + which);
        return v;
    }
    @Override public int chainLinkCount() { return chainLinks; }
}
