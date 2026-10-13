package com.openggf.mods.mutators;

import com.openggf.audio.GameSound;
import com.openggf.game.mutators.TransientMutatorPolicySource;
import com.openggf.mods.code.ModFaultBoundary;
import java.util.*;
import java.util.function.Consumer;

/** Engine-owned temporary layer; neither saved preferences nor the user configuration journal are edited. */
public final class TemporaryMutatorState implements TransientMutatorPolicySource {
    private static final int MAX_ACTIVE = 128;
    private final long generation;
    private final Map<String,OwnedMutator> definitions = new LinkedHashMap<>();
    private final ModFaultBoundary faults;
    private final java.util.function.Supplier<Set<String>> persistentKeys;
    private final java.util.function.Supplier<Optional<com.openggf.mods.scene.SceneRomArt>> nativeArt;
    private final Map<Long,Lease> leases = new LinkedHashMap<>();
    private final Map<Long,Completion> completions=new LinkedHashMap<>();
    private long ordinal, clock;
    private boolean retired;

    public TemporaryMutatorState(long generation, List<OwnedMutator> definitions, ModFaultBoundary faults) {
        this(generation,definitions,faults,Set::of);
    }
    public TemporaryMutatorState(long generation,List<OwnedMutator> definitions,ModFaultBoundary faults,
                                  java.util.function.Supplier<Set<String>> persistentKeys) {
        this(generation,definitions,faults,persistentKeys,Optional::empty);
    }
    public TemporaryMutatorState(long generation,List<OwnedMutator> definitions,ModFaultBoundary faults,
            java.util.function.Supplier<Set<String>> persistentKeys,
            java.util.function.Supplier<Optional<com.openggf.mods.scene.SceneRomArt>> nativeArt) {
        requireHost();
        this.persistentKeys=Objects.requireNonNull(persistentKeys);
        this.nativeArt=Objects.requireNonNull(nativeArt);
        if(generation < 1) throw new IllegalArgumentException("Positive world generation required");
        this.generation = generation; this.faults = Objects.requireNonNull(faults);
        definitions.forEach(value -> {
            if(this.definitions.putIfAbsent(value.key(),value)!=null)throw new IllegalArgumentException("Duplicate temporary family");
        });
    }
    public ScopedPort open(String owner, Set<MutatorCapability> supported) { return open(owner,supported,ignored -> { }); }
    public ScopedPort open(String owner, Set<MutatorCapability> supported, Consumer<GameSound> sounds) {
        return open(owner,supported,sounds,true);
    }
    public ScopedPort open(String owner,Set<MutatorCapability> supported,Consumer<GameSound> sounds,boolean activationAllowed) {
        requireHost();
        if(retired) throw new IllegalStateException("Temporary world is retired");
        return new ScopedPort(owner,Set.copyOf(supported),Objects.requireNonNull(sounds),activationAllowed);
    }
    @Override public boolean ownerAvailable(String owner) { return !retired && faults.isOwnerAvailable(owner); }
    public long clock() { return clock; }
    public void advancePresentation() { advancePresentation(false); }
    public void advancePresentation(boolean held) {
        if(retired) return;
        if(!held) clock++;
        prune();
        var next = new LinkedHashMap<Long,Lease>();
        leases.forEach((id,value) -> {
            boolean advances = !held || value.policies().stream().anyMatch(p -> p instanceof GameplayHoldPolicy);
            Lease aged = advances ? value.age() : value;
            if(aged.oneShot() || aged.agePresentations() < aged.duration()) next.put(id,aged);
        });
        leases.clear(); leases.putAll(next);
    }
    public void clear() { leases.clear();completions.clear(); }
    public void retire() { retired = true; clear(); }
    private void prune() {
        leases.values().removeIf(value -> !faults.isOwnerAvailable(value.owner()));
        completions.values().removeIf(value -> !faults.isOwnerAvailable(value.owner()));
    }
    @Override public List<Entry> entries() {
        prune(); var result = new ArrayList<Entry>();
        leases.values().forEach(value -> value.policies().forEach(policy -> result.add(new Entry(value.owner(),
                value.id(),policy,value.agePresentations(),value.remaining(),value.oneShot()))));
        return List.copyOf(result);
    }
    @Override public List<Completion> completions() { prune();return List.copyOf(completions.values()); }
    @Override public boolean acknowledgeCompletion(long id) { prune();return completions.remove(id)!=null; }
    @Override public boolean acknowledge(long id) {
        prune(); var lease = leases.get(id);
        if(lease == null || !lease.oneShot()) return false;
        leases.remove(id); return true;
    }
    @Override public boolean report(long id, String status) {
        prune(); var lease = leases.get(id);
        if(lease == null) return false;
        Objects.requireNonNull(status); if(status.length()>160) throw new IllegalArgumentException("Status exceeds 160 characters");
        leases.put(id,lease.withStatus(status)); return true;
    }
    public Snapshot capture() { prune(); return new Snapshot(generation,clock,List.copyOf(leases.values()),List.copyOf(completions.values())); }
    public void restore(Snapshot snapshot) {
        if(retired || snapshot.generation()!=generation) throw new IllegalArgumentException("Temporary snapshot belongs to a retired/other world");
        for(var lease:snapshot.leases()) if(!faults.isOwnerAvailable(lease.owner()))
            throw new IllegalStateException("Historical temporary policy owner is quarantined");
        for(var completion:snapshot.completions())if(!faults.isOwnerAvailable(completion.owner()))
            throw new IllegalStateException("Historical completion owner is quarantined");
        clock=snapshot.clock();leases.clear();completions.clear();
        snapshot.leases().forEach(value -> leases.put(value.id(),value));
        snapshot.completions().forEach(value -> completions.put(value.activationId(),value));
    }
    public record Snapshot(long generation,long clock,List<Lease> leases,List<Completion> completions) {
        public Snapshot { leases=List.copyOf(leases);completions=List.copyOf(completions); }
    }
    public record Lease(long id,String owner,String localId,Map<String,Object>options,List<MutatorPolicy>policies,
                        long duration,long agePresentations,String status) {
        public Lease { options=Map.copyOf(options); policies=List.copyOf(policies); }
        boolean oneShot() { return duration==0; }
        long remaining() { return oneShot()?0:Math.max(0,duration-agePresentations); }
        Lease age() { return new Lease(id,owner,localId,options,policies,duration,agePresentations+1,status); }
        Lease withStatus(String text) { return new Lease(id,owner,localId,options,policies,duration,agePresentations,text); }
    }
    public final class ScopedPort implements TemporaryMutators,AutoCloseable {
        private final String owner; private final Set<MutatorCapability> supported; private final Consumer<GameSound> sounds;
        private boolean closed;
        private final Thread callbackThread=Thread.currentThread();
        private final boolean activationAllowed;
        private ScopedPort(String owner,Set<MutatorCapability>supported,Consumer<GameSound>sounds,boolean activationAllowed) {
            this.owner=Objects.requireNonNull(owner);this.supported=supported;this.sounds=sounds;this.activationAllowed=activationAllowed;
        }
        private void check() { if(Thread.currentThread()!=callbackThread||closed||retired||!faults.isOwnerAvailable(owner)) throw new IllegalStateException("Temporary callback authority is closed"); }
        @Override public ActivationResult activate(String localId,Map<String,Object>options,long duration) {
            check(); prune();
            if(!activationAllowed)return rejected("Temporary admission requires active ordinary gameplay");
            if(duration<0||duration>3600) return rejected("Duration must be 0..3600 presentations");
            if(leases.size()>=MAX_ACTIVE) return rejected("Temporary activation limit reached");
            OwnedMutator owned=definitions.get(owner+":"+localId);
            String reason=availability(owned);
            if(!reason.isEmpty()) return rejected(reason);
            if(leases.values().stream().anyMatch(value -> value.owner().equals(owner)&&value.localId().equals(localId)))
                return rejected("This family is already active");
            for(var value:leases.values()) if(value.owner().equals(owner)) {
                var other=definitions.get(owner+":"+value.localId()).definition();
                if(owned.definition().conflicts().contains(value.localId())||other.conflicts().contains(localId)) return rejected("Conflicting family is active");
            }
            var persistent=persistentKeys.get();
            for(var key:persistent)if(key.startsWith(owner+":")) {
                var other=definitions.get(key);
                if(other!=null && (owned.definition().conflicts().contains(other.definition().localId())
                        || other.definition().conflicts().contains(localId)))return rejected("Conflicting permanent family is active");
            }
            for(var key:owned.definition().prerequisites()) if(!persistent.contains(owner+":"+key)
                    && leases.values().stream().noneMatch(value -> value.owner().equals(owner)&&value.localId().equals(key)))
                return rejected("Prerequisite is inactive: "+key);
            Map<String,Object> validated;
            try { validated=options(owned.definition(),owned.definition().defaults(),options); }
            catch(IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            var policies=prepare(owned,validated);
            String timing=TemporaryPolicyTiming.rejection(policies,duration);
            if(!timing.isEmpty())return rejected(timing);
            long holdCap=policies.stream().filter(GameplayHoldPolicy.class::isInstance)
                    .map(GameplayHoldPolicy.class::cast).mapToLong(GameplayHoldPolicy::maximumPresentations).min().orElse(3600);
            if(holdCap<3600 && (duration==0||duration>holdCap)) return rejected("Hold requires a bounded duration within its maximum");
            if(ordinal>=0xffff_ffffL) throw new IllegalStateException("Activation ordinal exhausted");
            long id=(generation<<32)|++ordinal;
            leases.put(id,new Lease(id,owner,localId,validated,policies,duration,0,duration==0?"Pending native service":"Active"));
            return new ActivationResult(true,id,"");
        }
        @Override public ActivationResult replace(long id,Map<String,Object>options) {
            check(); prune();
            if(!activationAllowed)return rejected("Temporary replacement requires active ordinary gameplay");
            var old=leases.get(id);
            if(old==null||!old.owner().equals(owner)) return rejected("Activation is unavailable to this owner");
            var owned=definitions.get(owner+":"+old.localId());
            Map<String,Object> validated;
            try { validated=options(owned.definition(),old.options(),options); }
            catch(IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            var policies=validated.equals(old.options())?old.policies():prepare(owned,validated);
            String timing=TemporaryPolicyTiming.rejection(policies,old.duration());
            if(!timing.isEmpty())return rejected(timing);
            long holdCap=policies.stream().filter(GameplayHoldPolicy.class::isInstance)
                    .map(GameplayHoldPolicy.class::cast).mapToLong(GameplayHoldPolicy::maximumPresentations).min().orElse(3600);
            if(holdCap<3600 && (old.duration()==0||old.duration()>holdCap))return rejected("Replacement cannot shorten the declared hold maximum below its retained expiry");
            leases.put(id,new Lease(id,owner,old.localId(),validated,policies,old.duration(),old.agePresentations(),old.status()));
            return new ActivationResult(true,id,"");
        }
        @Override public boolean cancel(long id) { check();var value=leases.get(id);return value!=null&&value.owner().equals(owner)&&leases.remove(id)!=null; }
        @Override public ActivationResult complete(long id,boolean won) {
            check();prune();var lease=leases.get(id);
            if(lease==null || !lease.owner().equals(owner) || lease.oneShot()
                    || lease.policies().stream().noneMatch(GameplayHoldPolicy.class::isInstance)
                    || lease.policies().stream().noneMatch(p -> p instanceof OverlayPolicy overlay && overlay.mode()!=OverlayPolicy.Mode.SIMULATED_CHAT))
                return rejected("Only this owner's active modal sidegame can complete");
            int reward=won?lease.policies().stream().filter(RingEconomyPolicy.class::isInstance).map(RingEconomyPolicy.class::cast)
                    .filter(p -> p.kind()==RingEconomyPolicy.Kind.COMPLETION_REWARD).mapToInt(RingEconomyPolicy::amount).findFirst().orElse(0):0;
            if(reward>0 && completions.size()>=32)return rejected("Native completion queue is full");
            leases.remove(id);
            if(reward>0)completions.put(id,new Completion(owner,id,reward));
            return new ActivationResult(true,id,reward>0?"Reward pending native service":"Completed");
        }
        @Override public List<ActiveActivation> active() {
            check();prune();return leases.values().stream().filter(value -> value.owner().equals(owner))
                    .map(value -> new ActiveActivation(value.id(),value.localId(),value.options(),value.agePresentations(),value.remaining(),value.oneShot(),value.status())).toList();
        }
        @Override public List<Availability> available() {
            check();return definitions.values().stream().filter(value -> value.ownerModId().equals(owner)).map(value -> {
                String reason=availability(value);return new Availability(value.definition().localId(),reason.isEmpty(),reason);
            }).toList();
        }
        @Override public Optional<com.openggf.mods.scene.SceneRomArt> nativeArt() { check();return nativeArt.get(); }
        @Override public void playSfx(GameSound sound) { check();sounds.accept(Objects.requireNonNull(sound)); }
        @Override public void close() { closed=true; }
        private String availability(OwnedMutator owned) {
            if(owned==null) return "Unknown owner-local mutator family";
            var d=owned.definition();
            if(d.enableScope()!=MutatorScope.LIVE||d.disableScope()!=MutatorScope.LIVE||d.options().stream().anyMatch(o -> d.optionScope(o.id())!=MutatorScope.LIVE))
                return "Temporary activation requires LIVE actions and options";
            return supported.containsAll(d.capabilities())?"":"Capabilities are unavailable in this gameplay cell";
        }
        private List<MutatorPolicy> prepare(OwnedMutator owned,Map<String,Object>options) {
            try {
                return faults.call(owner,() -> {
                    var result=List.copyOf(owned.definition().factory().prepare(new MutatorOptions(options)));
                    if(result.isEmpty()||result.size()>MutatorCapability.values().length||result.stream().map(MutatorPolicy::capability).distinct().count()!=result.size()
                            ||!result.stream().map(MutatorPolicy::capability).collect(java.util.stream.Collectors.toSet()).equals(owned.definition().capabilities()))
                        throw new IllegalArgumentException("Temporary policies do not match declared capabilities");
                    return result;
                });
            } catch(ModFaultBoundary.CallbackAborted failure) { leases.values().removeIf(value -> failure.disabledOwners().contains(value.owner()));
                completions.values().removeIf(value -> failure.disabledOwners().contains(value.owner()));throw failure; }
        }
        private ActivationResult rejected(String reason) { return new ActivationResult(false,0,reason); }
    }
    private static void requireHost() {
        Class<?> caller=com.openggf.util.EngineCallerAccess.callerOutside(TemporaryMutatorState.class);
        if(caller.getClassLoader()!=TemporaryMutatorState.class.getClassLoader())
            throw new SecurityException("Temporary activation authority belongs to the host");
    }
    private static Map<String,Object> options(MutatorDefinition definition,Map<String,Object>base,Map<String,Object>edits) {
        var result=new LinkedHashMap<>(base);
        edits.forEach((key,value) -> { definition.option(key).validate(value);result.put(key,value); });
        return Map.copyOf(result);
    }
}
