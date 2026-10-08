package com.openggf.mods.code;

import com.openggf.game.CharacterDefinition;
import com.openggf.game.ModKeySyntax;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.runtime.OwnerBoundCallbacks;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/** Internal expert-registry publication. The host supplies the registration owner and inherited graph. */
public final class OwnedCharacterRegistry {
    private static final ReferenceQueue<Object> RELEASED = new ReferenceQueue<>();
    private static final Map<IdentityReference,String> PROVENANCE = new HashMap<>();
    // One publication admits at most maxCollectionEntries definitions. This also bounds
    // retained live callback provenance; weak identities release old application graphs.
    private static final int PROVENANCE_LIMIT = ModInputLimits.production().maxCollectionEntries() * 12;
    private OwnedCharacterRegistry() { }

    public static PlayableCharacterRegistry bind(String verifiedOwner, PlayableCharacterRegistry registry,
            PlayableCharacterRegistry inherited, ModFaultBoundary boundary) {
        ClassLoader caller=com.openggf.util.EngineCallerAccess
                .callerOutside(OwnedCharacterRegistry.class).getClassLoader();
        if (caller!=OwnedCharacterRegistry.class.getClassLoader())
            throw new SecurityException("Character registry ownership belongs to engine publication");
        String owner=ModKeySyntax.requireManifestId(verifiedOwner);
        Objects.requireNonNull(boundary,"boundary");
        return boundary.call(owner,()-> {
            Objects.requireNonNull(registry,"Playable character registry returned null");
            if (registry==inherited) return registry;
            int limit=ModInputLimits.production().maxCollectionEntries();
            if (registry.definitions().size()>limit)
                throw new IllegalArgumentException("Too many playable character definitions");
            var inheritedDefinitions=inherited==null ? Map.of() : inherited.definitions();
            synchronized (PROVENANCE) {
                drainReleased();
                // Validate the whole publication before allocating any callback wrappers.
                for (var entry:registry.definitions().entrySet()) {
                    if (inheritedDefinitions.get(entry.getKey())==entry.getValue()) continue;
                    if (!entry.getKey().isBuiltin() && !entry.getKey().ownerModId().orElseThrow().equals(owner))
                        throw new IllegalArgumentException("Fresh character key belongs to another mod owner");
                    requireCompatible(owner,entry.getValue());
                    for (Object callback:callbacks(entry.getValue())) requireCompatible(owner,callback);
                }
                var callbacks=new OwnerBoundCallbacks(owner,boundary,limit,Map.of());
                PlayableCharacterRegistry result=PlayableCharacterRegistry.empty();
                Set<Object> published=Collections.newSetFromMap(new IdentityHashMap<>());
                for (var entry:registry.definitions().entrySet()) {
                    CharacterDefinition definition=entry.getValue();
                    if (inheritedDefinitions.get(entry.getKey())!=definition) {
                        CharacterDefinition mapped=OwnerAwareCharacterDefinition.wrap(owner,entry.getKey(),definition,boundary,callbacks);
                        published.add(definition);published.add(mapped);published.addAll(callbacks(mapped));
                        // Engine-native defaults may be shared across unrelated modules. Raw
                        // creator callbacks are tracked so a copied record cannot retag them.
                        for (Object callback:callbacks(definition))
                            if (callback.getClass().getClassLoader()!=OwnedCharacterRegistry.class.getClassLoader()) published.add(callback);
                        definition=mapped;
                    }
                    result=result.register(entry.getKey(),definition);
                }
                if (published.isEmpty()) return registry;
                long fresh=published.stream().filter(value->!PROVENANCE.containsKey(new IdentityReference(value,null))).count();
                if (PROVENANCE.size()+fresh>PROVENANCE_LIMIT)
                    throw new IllegalArgumentException("Live character callback provenance limit exceeded");
                for (Object value:published) PROVENANCE.put(new IdentityReference(value,RELEASED),owner);
                return result;
            }
        });
    }

    private static List<Object> callbacks(CharacterDefinition definition) {
        var result=new ArrayList<Object>(4);
        result.add(definition.spriteFactory());result.add(definition.respawnStrategyFactory());result.add(definition.artSupplier());
        if (definition.paletteSupplier()!=null) result.add(definition.paletteSupplier());
        return result;
    }
    private static void requireCompatible(String owner,Object value) {
        String earlier=PROVENANCE.get(new IdentityReference(value,null));
        if (earlier!=null && !earlier.equals(owner))
            throw new IllegalArgumentException("Fresh character callbacks are already owned by another mod");
    }
    private static void drainReleased() {
        IdentityReference released;
        while ((released=(IdentityReference)RELEASED.poll())!=null) PROVENANCE.remove(released);
    }
    private static final class IdentityReference extends WeakReference<Object> {
        private final int hash;
        IdentityReference(Object value,ReferenceQueue<Object> queue) { super(value,queue);hash=System.identityHashCode(value); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this==other || other instanceof IdentityReference reference && get()!=null && get()==reference.get();
        }
    }
}
