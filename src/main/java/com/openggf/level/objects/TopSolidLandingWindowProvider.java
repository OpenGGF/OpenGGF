package com.openggf.level.objects;

/**
 * A helper's direct positive, feet-relative window for a new top-solid landing.
 *
 * <p>The exclusive upper overlap limit belongs to the native helper, not the
 * player's collision model or the object's body height. Surface geometry and
 * continued riding remain with their existing owners. Implementations return
 * null only when the ordinary classifier should retain authority.
 */
public interface TopSolidLandingWindowProvider {
    Integer getDirectTopLandingOverlapLimit();
}
