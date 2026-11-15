/**
 * Shared object contracts and their live runtime owners.
 *
 * <p>Choose the owner by the behaviour being changed:
 * <ul>
 *   <li>An ordinary object correction starts in its concrete
 *       {@code game.sonic1}, {@code game.sonic2} or {@code game.sonic3k} routine
 *       and production-entity test. Shared lifetime policy starts at
 *       {@link com.openggf.level.objects.ObjectInstance#getOutOfRangeReferenceX()},
 *       {@link com.openggf.level.objects.ObjectInstance#isPersistent()} and
 *       {@link com.openggf.level.objects.ObjectInstance#checksOutOfRangeAfterRoutine()}.</li>
 *   <li>{@link com.openggf.level.objects.ObjectManager} is the existing runtime
 *       entry point for admission, execution and removal. Object implementations
 *       receive it through injected {@link com.openggf.level.objects.ObjectServices}.</li>
 *   <li>{@link com.openggf.level.objects.ObjectPopulation} owns live membership,
 *       slots, child reservations and stable identities. Rendering and rewind use
 *       this population; they do not maintain competing object inventories.</li>
 *   <li>{@link com.openggf.level.objects.ObjectRewindLifecycle} owns population
 *       reconstruction. Start there for adoption, deferred children and restore
 *       ordering; implement an object's creation hook in
 *       {@link com.openggf.level.objects.RewindRecreatable}.</li>
 *   <li>{@link com.openggf.level.objects.SolidContactMemory} is the package-private owner of rides,
 *       object-side standing/pushing bits and frame markers. The immutable saved
 *       form is {@link com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.SolidContactState}.
 *       {@link com.openggf.level.objects.ObjectSolidContactController} owns solid
 *       geometry and the gameplay decisions that read or change that state.</li>
 *   <li>{@link com.openggf.level.objects.ObjectRangeOps} owns the unsigned
 *       chunk-X calculation. Its native bound is 640; viewport-aware callers
 *       choose 128 + width + 192 (848 at width 528). The calling routine or
 *       manager still owns the reference coordinate, predicate and check phase.</li>
 * </ul>
 *
 * <p>Placement candidates, live-object retirement and prior-render visibility
 * are different boundaries. Leaving the load candidate window does not remove
 * a live object. The manager checks lifetime during execution; S1's slot pass
 * selects pre/post-routine timing, while the non-counter pass checks afterward.
 * An object's persistence hook can veto that check. A ROM render-flag read
 * observes its retained render state, not the lifetime range calculation.
 *
 * <p>Object-side bits and player-side riding flags have different lifetimes.
 * Deleting a solid expires its object bits without releasing the player's ride.
 * During ordinary graph rewind, publish reconstructed identities before restoring
 * reference fields, then settle derived state after those actors' fields are
 * available. Deferred player effects complete later through player refresh and
 * {@code ObjectRewindLifecycle.completePendingPlayerBoundObject}; they are not
 * available as required graph references during the ordinary restore pass.
 * The owning classes document the detailed lifecycle and callback boundaries.
 *
 * <p>Focused boundary checks live beside these owners under
 * {@code src/test/java/com/openggf/level/objects}: start with
 * {@code TestObjectSolidContactController}, {@code TestObjectManagerRewindSnapshot},
 * {@code TestShieldRewindPendingRestore} or
 * {@code TestObjectManagerDynamicChainRewindRestore}. The contributor testing
 * guide describes selection and the broader checks required for delivery.
 * Lifetime work starts with {@code TestBuzzBomberLifecycle} for the real moving
 * object and native/widescreen manager boundary, then
 * {@code TestObjectRangeHelpers}, {@code TestAbstractObjectInstanceRange} or
 * {@code TestObjectManagerCounterBasedDynamicUnload} when the shared contract changes.
 */
package com.openggf.level.objects;
