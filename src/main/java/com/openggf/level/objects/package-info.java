/**
 * Shared object contracts and their live runtime owners.
 *
 * <p>Choose the owner by the behaviour being changed:
 * <ul>
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
 *   <li>{@link com.openggf.level.objects.contact.SolidContactState} owns rides,
 *       object-side standing/pushing bits, frame markers and their saved form.
 *       {@link com.openggf.level.objects.ObjectSolidContactController} owns solid
 *       geometry and the gameplay decisions that read or change that state.</li>
 * </ul>
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
 */
package com.openggf.level.objects;
