package com.openggf.level;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.BiFunction;

/** Immutable decoded-placement transformations. Registration and trusted ownership stay in ModContext. */
@com.openggf.game.ModApi
public final class LevelPatch {
    private record Operation(Predicate<ObjectSpawn> select, Function<ObjectSpawn, ObjectSpawn> edit,
                             String bindingOwner, String bindingKey) {
        ObjectSpawn apply(ObjectSpawn spawn) {
            if (edit != null) return edit.apply(spawn);
            if (spawn.ownerModId()!=null) throw new IllegalArgumentException("Cannot rebind another mod's placement");
            return new ObjectSpawn(spawn.x(),spawn.y(),spawn.objectId(),spawn.subtype(),spawn.renderFlags(),
                    spawn.respawnTracked(),spawn.rawYWord(),spawn.layoutIndex(),bindingOwner,bindingKey);
        }
    }
    private final List<Operation> operations;
    private final Integer zoneIndex;
    private final Predicate<List<ObjectSpawn>> inventoryCondition;
    private final boolean nativePlacements;
    private final BiFunction<List<RingSpawn>,List<ObjectSpawn>,List<RingSpawn>> ringRetention;
    private final String admittedOwner;
    private final Set<String> admittedObjectKeys;
    private LevelPatch(List<Operation> operations,Integer zoneIndex,
            Predicate<List<ObjectSpawn>> inventoryCondition,boolean nativePlacements,
            BiFunction<List<RingSpawn>,List<ObjectSpawn>,List<RingSpawn>> ringRetention,String admittedOwner,
            Set<String> admittedObjectKeys) {
        this.operations=List.copyOf(operations);
        this.zoneIndex=zoneIndex;
        this.inventoryCondition=inventoryCondition;
        this.nativePlacements=nativePlacements;
        this.ringRetention=ringRetention;
        this.admittedOwner=admittedOwner;
        this.admittedObjectKeys=Set.copyOf(admittedObjectKeys);
    }
    private LevelPatch withOperations(List<Operation> operations) {
        return new LevelPatch(operations,zoneIndex,inventoryCondition,nativePlacements,ringRetention,admittedOwner,admittedObjectKeys);
    }
    public static LevelPatch empty() { return new LevelPatch(List.of(),null,null,false,null,null,Set.of()); }
    public Selection select(Predicate<ObjectSpawn> predicate) { return new Selection(this, Objects.requireNonNull(predicate)); }

    /** Restricts decoded admission to one ROM zone; other zones invoke no transformation callbacks. */
    public LevelPatch forZone(int zoneIndex) {
        if (zoneIndex<0) throw new IllegalArgumentException("Zone index must be non-negative");
        return new LevelPatch(operations,zoneIndex,inventoryCondition,nativePlacements,ringRetention,admittedOwner,admittedObjectKeys);
    }
    /** Reads the immutable original object inventory before applying any transformation. */
    public LevelPatch whenInventory(Predicate<List<ObjectSpawn>> condition) {
        return new LevelPatch(operations,zoneIndex,Objects.requireNonNull(condition),nativePlacements,ringRetention,admittedOwner,admittedObjectKeys);
    }
    /**
     * Opts engine decoded admission into factory bindings and ring retention on the same native level.
     * Direct {@link #apply(Level)} keeps returning a separate {@link MutableLevel} snapshot.
     */
    public LevelPatch nativePlacements() {
        return new LevelPatch(operations,zoneIndex,inventoryCondition,true,ringRetention,admittedOwner,admittedObjectKeys);
    }
    /** Keeps an ordered subset of the exact original ring records; neither coordinates nor identities may change. */
    public LevelPatch retainRings(BiFunction<List<RingSpawn>,List<ObjectSpawn>,List<RingSpawn>> retention) {
        return new LevelPatch(operations,zoneIndex,inventoryCondition,nativePlacements,Objects.requireNonNull(retention),admittedOwner,admittedObjectKeys);
    }

    /** Takes one snapshot and publishes the final placements once; source geometry is preserved. */
    public MutableLevel apply(Level source) {
        MutableLevel result=MutableLevel.snapshot(Objects.requireNonNull(source));
        List<ObjectSpawn> originalObjects=List.copyOf(result.getObjects());
        if (!matchesZone(source) || !matchesInventory(originalObjects)) return result;
        List<RingSpawn> originalRings=List.copyOf(result.getRings());
        List<ObjectSpawn> objects=applyToObjects(originalObjects);
        List<RingSpawn> rings=retainedRings(originalRings,originalObjects);
        result.replaceObjectSpawnsPersisted(objects);
        if (ringRetention!=null) result.replaceRingSpawnsPersisted(rings);
        return result;
    }

    boolean usesNativePlacements() { return nativePlacements; }
    boolean retainsRings() { return ringRetention!=null; }
    boolean matchesZone(Level source) { return zoneIndex==null || zoneIndex==source.getZoneIndex(); }
    private boolean matchesInventory(List<ObjectSpawn> originalObjects) {
        return inventoryCondition==null || inventoryCondition.test(originalObjects);
    }

    Level applyNativeDecoded(AbstractLevel source) {
        if (admittedOwner==null) throw new IllegalStateException("Native placements require ModContext decoded patch admission");
        for (Operation operation:operations) {
            if (operation.edit()!=null)
                throw new IllegalArgumentException("Native placement admission supports factory bindings only");
            if (!admittedOwner.equals(operation.bindingOwner()) || !admittedObjectKeys.contains(operation.bindingKey()))
                throw new IllegalArgumentException("Native placement binding needs a verified owner factory");
        }
        List<ObjectSpawn> originalObjects=List.copyOf(source.getObjects());
        List<RingSpawn> originalRings=List.copyOf(source.getRings());
        if (!matchesInventory(originalObjects)) return source;
        List<ObjectSpawn> objects=applyToObjects(originalObjects);
        List<RingSpawn> rings=retainedRings(originalRings,originalObjects);
        source.installDecodedPlacements(objects,rings);
        return source;
    }

    private List<RingSpawn> retainedRings(List<RingSpawn> originalRings,List<ObjectSpawn> originalObjects) {
        if (ringRetention==null) return originalRings;
        Set<Integer> identities=new HashSet<>();
        for (RingSpawn ring:originalRings) if (ring.placementId()>=0 && !identities.add(ring.placementId()))
            throw new IllegalArgumentException("Duplicate ring placement identity: "+ring.placementId());
        List<RingSpawn> retained=List.copyOf(Objects.requireNonNull(
                ringRetention.apply(originalRings,originalObjects),"Ring retention result"));
        int originalIndex=0;
        for (RingSpawn ring:retained) {
            while (originalIndex<originalRings.size() && originalRings.get(originalIndex)!=ring) originalIndex++;
            if (originalIndex==originalRings.size())
                throw new IllegalArgumentException("Ring retention must keep unique original records in their original order");
            originalIndex++;
        }
        return retained;
    }
    public List<ObjectSpawn> applyToObjects(List<ObjectSpawn> placements) {
        for (Operation operation:operations) if (operation.edit()==null && operation.bindingOwner()==null)
            throw new IllegalStateException("Local object bindings require ModContext.decodedLevelPatch registration");
        List<ObjectSpawn> result=List.copyOf(placements);
        validateIdentities(result);
        for (Operation operation : operations) {
            List<ObjectSpawn> edited=new ArrayList<>(result.size());
            for (ObjectSpawn spawn : result) {
                ObjectSpawn replacement=operation.select().test(spawn) ? operation.apply(spawn) : spawn;
                if (replacement != null) {
                    if (spawn.layoutIndex()!=replacement.layoutIndex()) throw new IllegalArgumentException("Patch must preserve placement identity");
                    edited.add(replacement);
                }
            }
            result=List.copyOf(edited);
        }
        validateIdentities(result);
        return result;
    }

    LevelPatch bindOwnership(String owner, Set<String> registeredObjectKeys) {
        com.openggf.game.ModKeySyntax.requireManifestId(owner);
        Objects.requireNonNull(registeredObjectKeys,"registeredObjectKeys");
        List<Operation> bound=new ArrayList<>(operations.size());
        for (Operation operation:operations) {
            if (operation.edit()!=null) { bound.add(operation); continue; }
            if (operation.bindingOwner()!=null && !owner.equals(operation.bindingOwner()))
                throw new IllegalArgumentException("Object binding belongs to another mod owner");
            String key=operation.bindingOwner()==null ? owner+":"+operation.bindingKey() : operation.bindingKey();
            if (!registeredObjectKeys.contains(key))
                throw new IllegalArgumentException("Object binding needs a registered owner factory: "+key);
            bound.add(new Operation(operation.select(),null,owner,key));
        }
        return new LevelPatch(bound,zoneIndex,inventoryCondition,nativePlacements,ringRetention,owner,registeredObjectKeys);
    }
    private static void validateIdentities(List<ObjectSpawn> placements) {
        Set<Integer> identities=new HashSet<>();
        for (ObjectSpawn spawn : placements) if (spawn.layoutIndex() >= 0 && !identities.add(spawn.layoutIndex()))
            throw new IllegalArgumentException("Duplicate placement identity: " + spawn.layoutIndex());
    }
    @com.openggf.game.ModApi
    public static final class Selection {
        private final LevelPatch patch;
        private final Predicate<ObjectSpawn> predicate;
        private Selection(LevelPatch patch, Predicate<ObjectSpawn> predicate) { this.patch=patch; this.predicate=predicate; }
        private LevelPatch append(Function<ObjectSpawn, ObjectSpawn> edit) {
            List<Operation> operations=new ArrayList<>(patch.operations);
            operations.add(new Operation(predicate,edit,null,null)); return patch.withOperations(operations);
        }
        public LevelPatch remove() { return append(spawn -> null); }
        /** Replaces gameplay fields, retaining the placement slot and existing owner/key pair. */
        public LevelPatch replace(Function<ObjectSpawn, ObjectSpawn> replacement) {
            Objects.requireNonNull(replacement);
            return append(spawn -> {
                ObjectSpawn edited=Objects.requireNonNull(replacement.apply(spawn), "Use remove for deletion");
                if (!Objects.equals(spawn.ownerModId(),edited.ownerModId()) || !Objects.equals(spawn.objectKey(),edited.objectKey()))
                    throw new IllegalArgumentException("Replacement must preserve object ownership; use bind for a native placement");
                return edited;
            });
        }
        public LevelPatch move(int dx, int dy) {
            return replace(spawn -> spawn.withPosition(Math.addExact(spawn.x(),dx),Math.addExact(spawn.y(),dy)));
        }
        /** Uses a local registered factory; ModContext supplies its verified owner during admission. */
        public LevelPatch bind(String localObjectKey) {
            return appendBinding(null,com.openggf.game.ModKeySyntax.requireLocalName(localObjectKey));
        }
        /** Explicitly redirects native placements to an already registered namespaced factory. */
        public LevelPatch bind(String owner, String objectKey) {
            com.openggf.game.ModKeySyntax.requireManifestId(owner);
            com.openggf.game.ModKeySyntax.requireDisplayKey(objectKey);
            if (!objectKey.startsWith(owner+":")) throw new IllegalArgumentException("Object key must match owner");
            return appendBinding(owner,objectKey);
        }
        private LevelPatch appendBinding(String owner,String key) {
            List<Operation> operations=new ArrayList<>(patch.operations);
            operations.add(new Operation(predicate,null,owner,key)); return patch.withOperations(operations);
        }
    }
}
