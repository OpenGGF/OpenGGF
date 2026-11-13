package com.openggf.game.internal;

import com.openggf.game.*;
import com.openggf.sprites.animation.*;
import java.util.*;

/** ROM scripts plus semantic phase addresses, including implicit movement banks. */
public record PoweredAnimationSource(SpriteAnimationSet scripts,
        Map<Integer, AnimationFamilies.Binding> families, int walkStride, int runStride,
        int alternateMask, int alternateStep, int alternateCeiling) {
    public PoweredAnimationSource { families = Map.copyOf(families); }

    /** Deduplicate timing repetitions without losing their positions in the ROM script. */
    public Map<Integer, String> poses(int animation) {
        var binding = families.get(animation);
        var script = scripts.getScript(animation);
        if (binding == null || binding.family() == null || script == null) return Map.of();
        var unique = new ArrayList<Integer>();
        for (int f : script.frames()) if (!unique.contains(f)) unique.add(f);
        Collections.sort(unique);
        var result = new HashMap<Integer, String>();
        String family = "family-" + binding.family().name().toLowerCase(Locale.ROOT).replace('_', '-');
        int stride = binding.family() == CanonicalAnimation.WALK ? walkStride
                : binding.family() == CanonicalAnimation.RUN ? runStride : 0;
        for (int phase = 0; phase < unique.size(); phase++) {
            int frame = unique.get(phase);
            for (int slope = 0; slope < (stride == 0 ? 1 : 4); slope++) {
                String suffix = (slope == 0 ? "" : List.of("", "up45-", "steep-", "inverted-").get(slope)) + phase;
                result.put(frame + slope * stride, family + "/" + suffix);
                if (alternateStep != 0 && binding.family() == CanonicalAnimation.WALK)
                    result.put(frame + slope * stride + alternateStep, family + "/" + suffix);
            }
        }
        // Only the six host-overridden scripts replace phase correspondence.
        // Shared scripts retain the host's semantic phases, including repeated timers.
        if ((binding.animation() == null || !Set.of(CanonicalAnimation.WALK, CanonicalAnimation.RUN, CanonicalAnimation.PUSH,
                CanonicalAnimation.WAIT, CanonicalAnimation.BALANCE, CanonicalAnimation.DUCK).contains(binding.animation()))) {
            result.clear();
            for (int step = 0; step < script.frames().size(); step++)
                result.put(script.frames().get(step), binding.phaseKey(step));
        }
        return Map.copyOf(result);
    }
}
