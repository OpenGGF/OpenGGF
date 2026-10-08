package com.openggf.game.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Engine bridge that marks only the synthesized backing patch safe to continue past. */
public final class ModPatchPlanAssembler {
    private ModPatchPlanAssembler() { }

    public static List<RegisteredPatch> backingFirst(PatchOwner.Mod owner,
                                                      GamePatch backing,
                                                      List<GamePatch> explicitPatches) {
        return backingFirst(owner, backing, explicitPatches, 0);
    }

    /** Continues an owner's registration order when one scene expands across stock games. */
    public static List<RegisteredPatch> backingFirst(PatchOwner.Mod owner,
                                                      GamePatch backing,
                                                      List<GamePatch> explicitPatches,
                                                      long firstIndex) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(backing, "backing");
        List<GamePatch> explicit = List.copyOf(
                Objects.requireNonNull(explicitPatches, "explicitPatches"));
        List<RegisteredPatch> result = new ArrayList<>(explicit.size() + 1);
        if (firstIndex < 0) throw new IllegalArgumentException("firstIndex must be nonnegative");
        result.add(RegisteredPatch.engineGeneratedDecorator(owner, backing.id(), backing, firstIndex));
        long index = firstIndex + 1;
        for (GamePatch patch : explicit) {
            String id = Objects.requireNonNull(patch, "explicit patch").id();
            String namespaced = id.indexOf(':') >= 0 ? id : owner.modId() + ":" + id;
            result.add(new RegisteredPatch(owner, namespaced, patch, index++));
        }
        return List.copyOf(result);
    }
}
