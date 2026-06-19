package com.openggf.level.objects;

import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;

/**
 * Context passed to {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)} during
 * a rewind restore. Exposes the captured spawn, the compact field-state blob, and the
 * restore-time object services.
 *
 * <p>Object-reference fields are <em>not</em> available here — they are resolved from the
 * compact blob (Task 3/5) after recreate returns. Implementations must not attempt
 * to look up sibling objects via this context.
 */
public record RewindRecreateContext(
        ObjectSpawn spawn,
        PerObjectRewindSnapshot state,
        ObjectServices objectServices) {
}
