package com.openggf.game.mutators;

import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.patch.ResolutionResult;

/** Engine-owned contribution that may host a mutator catalogue after the whole patch stack resolves. */
public interface MutatorHostPatch extends GamePatch {
    boolean contributesMutators();

    ResolutionResult hostMutators(ResolutionResult.Resolved resolved, GameplayLaunchRequest request,
                                  PatchContext context);
}
