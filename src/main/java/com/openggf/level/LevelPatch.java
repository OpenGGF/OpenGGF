package com.openggf.level;

import com.openggf.level.objects.ObjectSpawn;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Function;

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
    private LevelPatch(List<Operation> operations) { this.operations=List.copyOf(operations); }
    public static LevelPatch empty() { return new LevelPatch(List.of()); }
    public Selection select(Predicate<ObjectSpawn> predicate) { return new Selection(this, Objects.requireNonNull(predicate)); }

    /** Takes one snapshot and publishes the final placements once; source geometry is preserved. */
    public MutableLevel apply(Level source) {
        MutableLevel result=MutableLevel.snapshot(Objects.requireNonNull(source));
        result.replaceObjectSpawnsPersisted(applyToObjects(result.getObjects()));
        return result;
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
        return new LevelPatch(bound);
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
            operations.add(new Operation(predicate,edit,null,null)); return new LevelPatch(operations);
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
            operations.add(new Operation(predicate,null,owner,key)); return new LevelPatch(operations);
        }
    }
}
