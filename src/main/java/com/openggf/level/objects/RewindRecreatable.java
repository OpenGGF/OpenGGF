package com.openggf.level.objects;

/**
 * Uniform recreate hook for dynamic objects that cannot be reconstructed from an
 * {@link ObjectSpawn} alone via the game registry's factory.
 *
 * <p>Dynamic entries first try construction-child adoption in ObjectRewindLifecycle.
 * An owned helper records the actual parent; adoption waits for that captured
 * parent's identity and matches class/creator/subtype within its family. A child
 * produced only by its parent's constructor can be adoption-only, but routine or
 * independent admissions need an explicit recreate hook. The coverage analyzer
 * recognizes hooks, not proof of constructor-only admission: adoption-only classes
 * still need an honest coverage disposition rather than a marker hiding the gap.
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
 *   <li>Captured object-reference fields resolve from the compact blob later.
 *       Final/structural links excluded by reference policy must instead be established
 *       by construction/adoption or a class-specific recreation contract.</li>
 *   <li>Scalar fields set in the constructor may use placeholder values (e.g. 0); the
 *       standard scalar-restore pass applied after recreate will overwrite them with the
 *       exact captured values.</li>
 *   <li>The generic recreate dispatcher installs construction services around this hook,
 *       including nested constructors. Spawn-only classes use SpawnRewindRecreatable;
 *       the old construction-context interface is a compatibility alias.</li>
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
