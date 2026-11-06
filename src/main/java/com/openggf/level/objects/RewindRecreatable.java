package com.openggf.level.objects;

/**
 * Uniform recreate hook for dynamic objects that cannot be reconstructed from an
 * {@link ObjectSpawn} alone via the game registry's factory.
 *
 * <p>Implementing classes appear in the dynamic-object list at capture time (i.e.
 * runtime/routine-spawned dynamics, not construction-spawned children which are
 * adopted from their reconstructed parent). Their recreation during a rewind restore is
 * delegated to this method by
 * {@link ObjectRewindDynamicCodecs#genericRecreate(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry, DynamicObjectRecreateContext)},
 * which supplies the generic reconstruction path.
 *
 * <p>{@link ObjectRewindLifecycle} reconstructs the normal actor graph and
 * registers its captured identities, adopting constructor children and retrying
 * entries that wait for parents. It then restores graph fields/references and
 * runs settling hooks. Native player-bound effects can defer to the later player
 * refresh hook: they receive the existing scalar-only handoff there, do not join
 * the earlier restore table, and cannot be required reference targets of its
 * field/settling pass. Missing optional refs stay absent; later completion does
 * no automatic fixup. Implementors supply reconstruction, not normal field
 * restoration or settling. {@link ObjectManager#rewindSnapshottable()} coordinates
 * the surrounding world clocks, placement and contact owners.
 *
 * <p><strong>Contract for implementors:</strong>
 * <ul>
 *   <li>The returned instance must be of the same concrete class as the captured object.</li>
 *   <li>Object-reference fields (e.g. parent back-references) must <em>not</em> be
 *       wired here — they are resolved from the compact blob after recreate returns.</li>
 *   <li>Scalar fields set in the constructor may use placeholder values (e.g. 0); the
 *       standard scalar-restore pass applied after recreate will overwrite them with the
 *       exact captured values.</li>
 *   <li>The implementation must not call {@code services()} during its constructor (the
 *       injection ThreadLocal may not be set); use {@link RewindRecreateContext#objectServices()}
 *       if runtime context is needed during reconstruction.</li>
 * </ul>
 *
 * <p><strong>ArchUnit constraint:</strong> This interface lives in {@code level.objects}
 * (shared, no game dependency). Implementing classes in game-specific packages
 * ({@code game.sonic1}, {@code game.sonic2}, {@code game.sonic3k}) may freely
 * reference game-specific types in their {@code recreateForRewind} body —
 * the dependency flows game→shared, which is permitted.
 */
@com.openggf.game.ModApi
public interface RewindRecreatable {

    /**
     * Recreates this object's instance from its captured spawn and state during a
     * rewind restore. Called by
     * {@link ObjectRewindDynamicCodecs#genericRecreate} when this class is identified
     * as the captured class.
     *
     * <p>Object-reference fields for normally reconstructed actors are not set here;
     * they resolve by id in the following field pass. Deferred native player effects
     * follow the separate scalar-only refresh contract above.
     *
     * @param ctx restore-time context carrying the captured {@link ObjectSpawn},
     *            the compact scalar state blob, and {@link ObjectServices}
     * @return the recreated instance, or {@code null} for an unsupported entry or
     *         an effect deferred under its owning native refresh policy
     */
    AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx);
}
