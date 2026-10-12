package com.openggf.level.objects;

/** Optional creator projection over the existing owner-bounded two-phase reconstruction path. */
@com.openggf.game.ModApi
public interface ModRewindRecreatable extends RewindRecreatable {
    AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context);
    @Override default AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return recreateForRewind(new ObjectReconstructionContext(context));
    }
}
