package com.openggf.game.sonic3k.mutators;

import com.openggf.LevelFrameContext;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.CheckpointState;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.PlayableEntity;
import com.openggf.game.mutators.NativeObjectFieldTarget;
import com.openggf.game.mutators.NativeObjectPolicyPort;
import com.openggf.game.mutators.TransientMutatorPolicyAccess;
import com.openggf.game.mutators.TransientMutatorPolicySource;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.game.sonic3k.objects.badniks.*;
import com.openggf.level.objects.*;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.mods.mutators.*;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.ShieldType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Captured, isolated native encounters and semantic rules belonging to one gameplay world. */
public final class NativeMutatorRuntime implements WorldSessionPolicyState,
        RewindSnapshottable<NativeMutatorRuntime.State>, NativeObjectPolicyPort, com.openggf.game.mutators.NativeCompanionControlSource {
    private static final int MAX_OWNED_ROOTS = 16;
    private static final java.util.concurrent.atomic.AtomicLong GENERATIONS = new java.util.concurrent.atomic.AtomicLong();
    private final long generation = GENERATIONS.incrementAndGet();
    private long assemblyEpoch;
    private boolean retired;
    private final WorldSession world;
    private ObjectServices services;
    private SpriteManager sprites;
    private List<TransientMutatorPolicySource.Entry> entries = List.of();
    private final Set<Long> admitted = new HashSet<>();
    private final List<Owned> owned = new ArrayList<>();
    private final Map<Long, Integer> clocks = new HashMap<>();
    private final Map<Long, Task> tasks = new HashMap<>();
    private final Map<Long,Integer> opportunities = new HashMap<>();
    private final Map<Long,FieldTargets> fieldTargets = new HashMap<>();
    private final Map<ObjectRefId,List<ObjectRefId>> ownedChildren = new HashMap<>();
    // Derived only within one uninterrupted native destruction pass; never a persisted slot claim.
    private final Map<Integer,ObjectRefId> pendingReplacements = new HashMap<>();
    private final Map<ObjectRefId, Integer> projectileAges = new HashMap<>();
    private final Set<ObjectRefId> budded = new HashSet<>();
    private final Set<ObjectRefId> impacts = new HashSet<>();
    private boolean chainDispatch;
    private boolean lastAir;
    private final List<Companion> companions = new ArrayList<>();
    private final List<Ride> rides = new ArrayList<>();
    private final List<CheckpointDestination> encounteredCheckpoints = new ArrayList<>();
    private boolean bossActive;
    private TouchResponseTable touchSizes;
    private com.openggf.mods.scene.SceneImage landmarkThumbnail;
    private Object landmarkSheet;
    private int[] landmarkPalette;
    private boolean landmarkVisible;

    public record Owned(long activationId, String owner, String contentOwner, ObjectRefId identity, int remainingTicks, boolean cancelOnRemoval) { }
    public record Companion(long activationId,String owner,String code,int ticks,boolean cancelOnRemoval,boolean carry,int landingX,int landingY) { }
    public record FieldTargets(List<ObjectRefId> identities,int acquired) {
        public FieldTargets { identities=List.copyOf(identities); }
    }
    public record Ride(long activationId,String owner,ObjectRefId platform,int ticks) { }
    public record Task(int x, int y, int initialRings, int ticks, int progress, boolean complete) { }
    public record CheckpointDestination(int x,int y) { }
    public record State(long generation,long assemblyEpoch,Set<Long> admitted, List<Owned> owned, Map<Long,Integer> clocks,
                        Map<Long,Task> tasks, Map<Long,Integer> opportunities, Map<Long,FieldTargets> fieldTargets, Map<ObjectRefId,List<ObjectRefId>> ownedChildren, Map<ObjectRefId,Integer> projectileAges,
                        Set<ObjectRefId> budded, Set<ObjectRefId> impacts, boolean lastAir, List<Companion> companions,List<Ride> rides,
                        List<CheckpointDestination> encounteredCheckpoints) { }

    public NativeMutatorRuntime(WorldSession world) { this.world = java.util.Objects.requireNonNull(world); }
    @Override public <T> T getService(Class<T> type) {
        return type.isInstance(this) ? type.cast(this) : null;
    }
    @Override public RewindSnapshottable<?> rewindAdapter() { return this; }
    @Override public String key() { return "native-mutator-runtime"; }
    @Override public State capture() {
        return new State(generation,assemblyEpoch,Set.copyOf(admitted), List.copyOf(owned), Map.copyOf(clocks), Map.copyOf(tasks), Map.copyOf(opportunities),Map.copyOf(fieldTargets),Map.copyOf(ownedChildren),
                Map.copyOf(projectileAges), Set.copyOf(budded), Set.copyOf(impacts), lastAir, List.copyOf(companions),List.copyOf(rides),List.copyOf(encounteredCheckpoints));
    }
    @Override public void restore(State state) {
        if(retired) throw new IllegalStateException("Native policy owner belongs to a retired world");
        if(state.generation()!=generation || state.assemblyEpoch()!=assemblyEpoch)
            throw new IllegalArgumentException("Native policy snapshot belongs to another world or assembly");
        reset(); admitted.addAll(state.admitted()); owned.addAll(state.owned()); clocks.putAll(state.clocks());
        tasks.putAll(state.tasks()); opportunities.putAll(state.opportunities()); fieldTargets.putAll(state.fieldTargets()); ownedChildren.putAll(state.ownedChildren()); projectileAges.putAll(state.projectileAges()); budded.addAll(state.budded()); impacts.addAll(state.impacts());
        lastAir = state.lastAir(); companions.addAll(state.companions()); rides.addAll(state.rides()); encounteredCheckpoints.addAll(state.encounteredCheckpoints());
    }
    @Override public void resetForMissingSnapshot() { reset(); }
    private void reset() {
        admitted.clear(); owned.clear(); clocks.clear(); tasks.clear(); opportunities.clear(); fieldTargets.clear(); ownedChildren.clear(); pendingReplacements.clear(); projectileAges.clear(); budded.clear(); impacts.clear();
        entries = List.of(); chainDispatch = false; lastAir = false; companions.clear(); rides.clear(); encounteredCheckpoints.clear(); landmarkThumbnail=null; landmarkSheet=null; landmarkPalette=null; landmarkVisible=false;
    }
    @Override public void beforeAssembly(LevelLoadCause cause) {
        if (cause == LevelLoadCause.DECODE_ONLY || cause == LevelLoadCause.PREVIEW) return;
        releaseOwned(); reset(); services = null; assemblyEpoch++;
    }
    @Override public void failedAssembly(LevelLoadCause cause) { beforeAssembly(cause); }
    @Override public void bindRoster(SpriteManager sprites) { this.sprites = sprites; }
    @Override public void closeScreens() { }
    public void leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase phase) {
        if(phase!=com.openggf.game.GameplayObservation.Phase.SETUP
                && phase!=com.openggf.game.GameplayObservation.Phase.STAGE
                && phase!=com.openggf.game.GameplayObservation.Phase.TRANSITION
                && phase!=com.openggf.game.GameplayObservation.Phase.CLOSED) return;
        if(services!=null) for(var lease:owned) terminateOwnedGraph(lease);
        releaseOwned(); reset();
    }
    @Override public void retire() { leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase.CLOSED); retired=true; services = null; sprites = null; }

    @Override public void beforeNativeTick(LevelFrameContext context, LogicalInputSnapshot input) {
        if(retired) return;
        var currentSprites=context.spriteManager();
        ObjectManager objects = context.levelManager() == null ? null : context.levelManager().getObjectManager();
        var currentServices=objects==null?null:objects.getObjectServices();
        if(currentServices==null || currentServices.worldSession()!=world || currentSprites==null) return;
        sprites=currentSprites; services=currentServices;
        pendingReplacements.clear();
        entries = TransientMutatorPolicyAccess.entries(world).stream().filter(e->e.oneShot() || e.remainingPresentations()>0).toList();
        bossActive = context.levelEventProvider() instanceof com.openggf.game.AbstractLevelEventManager events && events.isBossActive();
        if (touchSizes == null && world.getDataSource().rom().isPresent()) {
            try {
                touchSizes = world.getGameModule().createTouchResponseTable(com.openggf.data.RomByteReader.fromRom(world.getDataSource().rom().orElseThrow()));
            } catch (java.io.IOException missing) {
                // A missing ROM table cannot become a guessed attack envelope.
            }
        }
        if (services == null || sprites == null) return;
        AbstractPlayableSprite player = sprites.getMainPlayable();
        if (player == null) return;
        observeCheckpoint();
        expireOwned();
        expireCompanions();
        expireRides();
        pruneState();
        if (player.getDead() || player.isDebugMode()) return;
        consumeCompletions(player);
        selectFieldTargets(player);
        for (var entry : entries) {
            var policy = entry.policy();
            if(ownsOneShot(policy) && entry.oneShot() && !admitted.contains(entry.activationId())) {
                int opportunity=opportunities.merge(entry.activationId(),1,Integer::sum);
                int deadline=policy instanceof EnemyRulePolicy enemy && enemy.kind()==EnemyRulePolicy.Kind.BUDDING?600:180;
                if(opportunity>=deadline || entry.agePresentations()>=deadline*2L) {
                    TransientMutatorPolicyAccess.report(world,entry.activationId(),"Failed: "+rejection(policy));
                    acknowledge(entry); continue;
                }
            }
            if (policy instanceof NativePowerupPolicy powerup) {
                if (powerup.kind() == NativePowerupPolicy.Kind.AIR) {
                    if (player.getDrowningController() != null) player.getDrowningController().replenishAir();
                    if (entry.oneShot()) acknowledge(entry);
                } else if (!admitted.contains(entry.activationId()) && grant(player, powerup)) {
                    TransientMutatorPolicyAccess.report(world,entry.activationId(),"Applied: native "+powerup.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_',' ')
                            +(powerup.kind()==NativePowerupPolicy.Kind.SHIELD?" inventory grant":" grant; at least "+powerup.durationTicks()+" native ticks"));
                    acknowledge(entry);
                }
            } else if (policy instanceof EncounterPolicy encounter && !admitted.contains(entry.activationId())) {
                if (!player.isObjectControlled() && encounter(entry, encounter, player)) acknowledge(entry);
            } else if(policy instanceof EnemyRulePolicy enemy && enemy.kind()==EnemyRulePolicy.Kind.BUDDING) {
                var pending=tasks.getOrDefault(entry.activationId(),new Task(0,0,0,0,0,false));
                tasks.put(entry.activationId(),new Task(0,0,0,pending.ticks()+1,0,false));
                if(entry.agePresentations()>=600 || pending.ticks()>=600) {
                    clocks.put(entry.activationId(),2);
                    TransientMutatorPolicyAccess.report(world,entry.activationId(),"Failed: no qualified badnik defeat before the encounter deadline");
                    acknowledge(entry);
                }
            } else if (policy instanceof RingEconomyPolicy economy) {
                economy(entry, economy, player);
            } else if (policy instanceof RelocationPolicy relocation) {
                relocation(entry, relocation, player);
            } else if (policy instanceof TransportPolicy transport) {
                transport(entry, transport, player);
            } else if (policy instanceof ChallengePolicy challenge) {
                challenge(entry, challenge, player);
            } else if (policy instanceof ProjectileRulePolicy projectile
                    && projectile.kind() == ProjectileRulePolicy.Kind.PLAYER_FIRE) {
                clocks.merge(entry.activationId(),1,Integer::sum);
                if (input.player1().actionPressedMask() != 0) firePlayerShot(entry, player);
            }
        }
        prepareLandmarkThumbnail();
    }
    @Override public void afterNativeTick(LevelFrameContext context, LogicalInputSnapshot input) {
        pendingReplacements.clear();
        if(retired || sprites==null) return;
        var player=sprites.getMainPlayable();
        if(player==null) return;
        observeCheckpoint();
        if(player.getAir() && player.isJumping() && !lastAir) advanceTasks(ChallengePolicy.Kind.SKILL_CHAIN,player,2);
        lastAir=player.getAir();
        for(var lease:List.copyOf(owned)) {
            if(!ownerAvailable(lease.owner())) ownerRetired(lease.owner());
            else if(lease.contentOwner()!=null && !ownerAvailable(lease.contentOwner())) ownerRetired(lease.contentOwner());
        }
        for(var lease:List.copyOf(companions)) if(!ownerAvailable(lease.owner())) ownerRetired(lease.owner());
        for(var lease:List.copyOf(rides)) if(!ownerAvailable(lease.owner())) ownerRetired(lease.owner());
        prepareLandmarkThumbnail();
    }
    private void observeCheckpoint() {
        var checkpoint=services==null?null:services.checkpointState();
        if(checkpoint==null || checkpoint.getStarPostActivationMark()<=0) return;
        // The saved native bank is exact visit evidence. A high-water subtype cannot certify
        // other layout posts on alternate routes, including posts sharing the same number.
        var destination=new CheckpointDestination(checkpoint.getSavedX(),checkpoint.getSavedY());
        if(encounteredCheckpoints.contains(destination)) return;
        if(encounteredCheckpoints.size()==32) encounteredCheckpoints.removeFirst();
        encounteredCheckpoints.add(destination);
    }
    private static boolean ownsOneShot(MutatorPolicy policy) {
        return policy instanceof EncounterPolicy || policy instanceof NativePowerupPolicy
                || policy instanceof RingEconomyPolicy || policy instanceof RelocationPolicy || policy instanceof EnemyRulePolicy;
    }
    private static String rejection(MutatorPolicy policy) {
        if(policy instanceof EncounterPolicy p) return p.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_',' ')+" has no safe native placement or resident ROM art";
        if(policy instanceof RelocationPolicy p) return p.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_',' ')+" has no eligible checkpoint, follower or clear landing";
        if(policy instanceof EnemyRulePolicy) return "no eligible badnik defeat before the budding deadline";
        return "native action could not be admitted before its opportunity deadline";
    }
    @Override public void ringAcquired(PlayableEntity player) {
        if(sprites!=null && player==sprites.getMainPlayable() && player instanceof AbstractPlayableSprite sprite)
            advanceTasks(ChallengePolicy.Kind.SKILL_CHAIN,sprite,1);
    }
    private void acknowledge(TransientMutatorPolicySource.Entry entry) {
        admitted.add(entry.activationId());
        if (entry.oneShot()) TransientMutatorPolicyAccess.acknowledge(world, entry.activationId());
    }
    private boolean grant(AbstractPlayableSprite player, NativePowerupPolicy powerup) {
        switch (powerup.kind()) {
            case SHIELD -> {
                if (player.getShieldType() != null) return true;
                ShieldType[] shields = {ShieldType.FIRE, ShieldType.LIGHTNING, ShieldType.BUBBLE};
                player.giveShield(shields[(services.rng().nextRaw() & 0x7FFF) % shields.length]);
            }
            case SPEED_SHOES -> player.giveSpeedShoes(powerup.durationTicks());
            case INVINCIBILITY -> player.giveInvincibility(powerup.durationTicks());
            case AIR -> { }
        }
        return true;
    }
    private boolean art(String key) {
        if (services.renderManager() == null) return false;
        if (!(services.renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider)) return false;
        return provider.ensureEncounterArtLoaded(world.getDataSource().rom().orElse(null), services.graphicsManager(), key);
    }
    private <T extends AbstractObjectInstance> T spawn(long activation, int ticks, Supplier<T> factory) {
        if (owned.size() >= MAX_OWNED_ROOTS) return null;
        T object = services.objectManager().createDynamicObject(factory);
        if (object != null) services.objectQuery().identityOf(object)
                .ifPresent(id -> owned.add(new Owned(activation, owner(activation), null, id, ticks, cancellationBound(activation))));
        return object;
    }
    private static ObjectSpawn at(int x, int y, int id, int subtype, int flags) {
        return new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, id, subtype, flags, false, 0);
    }
    private boolean encounter(TransientMutatorPolicySource.Entry entry, EncounterPolicy policy, AbstractPlayableSprite player) {
        int x = player.getCentreX(), y = player.getCentreY();
        long id = entry.activationId();
        int direction = player.getDirection() == Direction.LEFT ? -1 : 1;
        if(bossActive || !NativeEncounterGeometry.ordinary(services,player)) return false;
        switch (policy.kind()) {
            case BUMPER -> {
                long count = owned.stream().filter(o -> services.objectQuery().resolve(o.identity())
                        .orElse(null) instanceof CnzBumperObjectInstance).count();
                if (count >= 2 || !art(Sonic3kObjectArtKeys.CNZ_BUMPER)) return false;
                int dx = direction * (policy.side() == EncounterPolicy.Side.AHEAD ? 12 : -12);
                var bumper = spawn(id, 120, () -> new CnzBumperObjectInstance(at(x + dx, y, Sonic3kObjectIds.BUMPER, 0, 0)));
                if(bumper==null) return false;
                if(services.objectManager().admitTransientContactBeforePlayer(bumper)) return true;
                rollbackActivation(id); return false;
            }
            case GHOST -> {
                if (!art(Sonic3kObjectArtKeys.SOZ_GHOSTS)) return false;
                return spawn(id, 780, () -> new NativeGhostEncounterObjectInstance(at(x,y,0,0,0))) != null;
            }
            case GUST -> {
                return spawn(id, 72, () -> new NativeGustObjectInstance(at(x - direction*96,y,0,0,direction>0?1:0))) != null;
            }
            case VINE -> {
                // The native zip owner travels right and down; require its entire optional swept corridor.
                if(direction<0 || !NativeEncounterGeometry.clear(services,
                        new NativeEncounterGeometry.Body(x+160,y-72,128,88),null)
                        || !art(Sonic3kObjectArtKeys.AIZ_RIDE_VINE)) return false;
                return spawn(id,480,()->new AizRideVineObjectInstance(at(x+64,y-128,Sonic3kObjectIds.AIZ_RIDE_VINE,8,0)))!=null;
            }
            case RESCUE_PLATFORM, BLOCK_STACK -> {
                var bodies=new ArrayList<NativeEncounterGeometry.Body>();
                if(policy.kind()==EncounterPolicy.Kind.RESCUE_PLATFORM) {
                    if(!player.getAir() || player.getYSpeed()<0) return false;
                    bodies.add(new NativeEncounterGeometry.Body(x,y+player.getYRadius()+20,27,17));
                } else {
                    var landing=CoherentRelocationOps.landing(services,player,x+direction*64,y,24);
                    if(landing==null) return false;
                    for(int i=0;i<3;i++) bodies.add(new NativeEncounterGeometry.Body(
                            landing.x()+direction*i*64,landing.y()+player.getYRadius()-20-i*24,27,17));
                }
                if(owned.size()+bodies.size()>MAX_OWNED_ROOTS || bodies.stream().anyMatch(body->!NativeEncounterGeometry.clear(services,body,null))
                        || !art(Sonic3kObjectArtKeys.ICZ_PLATFORMS_MISC2)) return false;
                for(var body:bodies) if(spawn(id,480,()->new IczIceBlockObjectInstance(at(body.x(),body.y(),Sonic3kObjectIds.ICZ_ICE_BLOCK,0,0)))==null) {
                    rollbackActivation(id); return false;
                }
                return true;
            }
            case MONITOR_CAGE -> {
                if(player.getAir() || Math.abs(player.getAngle())>8) return false;
                var bodies=new ArrayList<NativeEncounterGeometry.Body>();
                // Two native breakable monitors flank the actor, leaving its centre and the top open.
                for(int side:new int[]{-1,1}) {
                    var landing=CoherentRelocationOps.landing(services,player,x+side*56,y,20);
                    if(landing==null) return false;
                    bodies.add(new NativeEncounterGeometry.Body(landing.x(),landing.y()+player.getYRadius()-20,25,17));
                }
                if(owned.size()+bodies.size()>MAX_OWNED_ROOTS || bodies.stream().anyMatch(body->!NativeEncounterGeometry.clear(services,body,null))) return false;
                for(var body:bodies) if(spawn(id,480,()->new Sonic3kMonitorObjectInstance(at(body.x(),body.y(),Sonic3kObjectIds.MONITOR,3,0)))==null) {
                    rollbackActivation(id); return false;
                }
                return true;
            }
            case FAKE_MONITOR -> {
                var landing=CoherentRelocationOps.landing(services,player,x+direction*64,y,24);
                if(landing==null) return false;
                var body=new NativeEncounterGeometry.Body(landing.x(),landing.y()+player.getYRadius()-20,25,17);
                if(!NativeEncounterGeometry.clear(services,body,null)) return false;
                var monitor=spawn(id,480,()->new Sonic3kMonitorObjectInstance(at(body.x(),body.y(),Sonic3kObjectIds.MONITOR,0,0)));
                if (monitor != null) monitor.disguiseEncounterAsRingMonitor();
                return monitor != null;
            }
            case DEBRIS -> {
                if (!NativeEncounterGeometry.clear(services,new NativeEncounterGeometry.Body(x+direction*64,y-96,16,24),null)
                        || !art(Sonic3kObjectArtKeys.LRZ_FALLING_SPIKE)) return false;
                var debris=spawn(id,240,()->new LrzFallingSpikeObjectInstance(at(x+direction*64,y-96,0,128,0)));
                if(debris!=null) { debris.giveEncounterWarning(); TransientMutatorPolicyAccess.report(world,id,"Task: falling debris overhead"); }
                return debris!=null;
            }
            case SPEED_TRAP -> {
                if(player.getAir() || Math.abs(player.getGSpeed())<0x600) return false;
                int warning=clocks.merge(id,1,Integer::sum);
                TransientMutatorPolicyAccess.report(world,id,"Task: speed trap ahead — roll or jump");
                if(warning<30 || !art(Sonic3kObjectArtKeys.BREAKABLE_WALL_AIZ)) return false;
                var landing=CoherentRelocationOps.landing(services,player,x+direction*96,y,24);
                if(landing==null) return false;
                var body=new NativeEncounterGeometry.Body(landing.x(),landing.y()+player.getYRadius()-44,27,41);
                if(!NativeEncounterGeometry.clear(services,body,null)) return false;
                return spawn(id,360,()->new NativeBreakableWallObjectInstance(at(body.x(),body.y(),Sonic3kObjectIds.BREAKABLE_WALL,0,0))) != null;
            }
        }
        return false;
    }
    private void rollbackActivation(long id) {
        for(int i=owned.size()-1;i>=0;i--) if(owned.get(i).activationId()==id) {
            terminateOwnedGraph(owned.get(i)); owned.remove(i);
        }
    }
    private void expireOwned() {
        for (int i=owned.size()-1;i>=0;i--) {
            Owned lease=owned.get(i);
            var object=services.objectQuery().resolve(lease.identity()).orElse(null);
            boolean cancelled=lease.cancelOnRemoval() && entries.stream().noneMatch(e->e.activationId()==lease.activationId());
            if(!ownerAvailable(lease.owner()) || lease.contentOwner()!=null && !ownerAvailable(lease.contentOwner())) { terminateOwnedGraph(lease); owned.remove(i); }
            else if (lease.remainingTicks() <= 0 || cancelled) {
                if(object!=null) expire(object); else releaseChildren(lease.identity());
                if(object instanceof NativeGhostEncounterObjectInstance)
                    owned.set(i,new Owned(lease.activationId(),lease.owner(),lease.contentOwner(),lease.identity(),0,lease.cancelOnRemoval()));
                else owned.remove(i);
            } else if((object==null || object.isDestroyed()) && !ownedChildren.containsKey(lease.identity())) {
                owned.remove(i);
            } else owned.set(i,new Owned(lease.activationId(),lease.owner(),lease.contentOwner(),lease.identity(),lease.remainingTicks()-1,lease.cancelOnRemoval()));
        }
    }
    private void expire(ObjectInstance object) {
        if(services!=null && sprites!=null) {
            for(var player:services.playerQuery().playersFor(ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
                if(services.objectManager().getRidingObject(player)==object) {
                    services.objectManager().releaseRidingObject(player,object); player.setAir(true);
                }
            }
        }
        if (object instanceof NativeGhostEncounterObjectInstance ghost) ghost.finishEncounter();
        else if (object instanceof AbstractObjectInstance nativeObject) {
            services.objectQuery().identityOf(object).ifPresent(this::releaseChildren);
            ObjectLifetimeOps.expireDynamic(nativeObject);
        }
    }
    private void pruneState() {
        Set<Long> active=new HashSet<>();
        for(var e:entries) active.add(e.activationId());
        clocks.keySet().retainAll(active); tasks.keySet().retainAll(active); opportunities.keySet().retainAll(active); fieldTargets.keySet().retainAll(active);
        for(var lease:owned) active.add(lease.activationId());
        for(var lease:companions) active.add(lease.activationId());
        for(var lease:rides) active.add(lease.activationId());
        admitted.retainAll(active);
        // Missing native parents remain graph anchors while their live descendants finish.
        ownedChildren.replaceAll((parent,children)->children.stream().filter(id->ownedChildren.containsKey(id)
                || services.objectQuery().resolve(id).filter(object->!object.isDestroyed()).isPresent()).toList());
        ownedChildren.values().removeIf(List::isEmpty);
        projectileAges.keySet().removeIf(id->services.objectQuery().resolve(id).isEmpty());
        budded.removeIf(id->services.objectQuery().resolve(id).isEmpty());
        impacts.removeIf(id->services.objectQuery().resolve(id).isEmpty());
    }
    private String owner(long id) {
        return entries.stream().filter(e->e.activationId()==id).map(TransientMutatorPolicySource.Entry::owner).findFirst().orElseThrow();
    }
    private boolean ownerAvailable(String owner) {
        var source=TransientMutatorPolicyAccess.source(world);
        return source!=null && source.ownerAvailable(owner);
    }
    public void ownerRetired(String owner) {
        if(services!=null) for(int i=owned.size()-1;i>=0;i--) if(owned.get(i).owner().equals(owner) || owner.equals(owned.get(i).contentOwner())) {
            terminateOwnedGraph(owned.get(i)); owned.remove(i);
        }
        for(int i=companions.size()-1;i>=0;i--) if(companions.get(i).owner().equals(owner)) {
            removeCompanion(companions.get(i)); companions.remove(i);
        }
        for(int i=rides.size()-1;i>=0;i--) if(rides.get(i).owner().equals(owner)) { releaseRide(rides.get(i)); rides.remove(i); }
    }
    @Override public void childSpawned(ObjectInstance parent,ObjectInstance child) {
        if(services==null || retired || child.isDestroyed()) return;
        var parentId=services.objectQuery().identityOf(parent).orElse(null);
        var childId=services.objectQuery().identityOf(child).orElse(null);
        if(parentId==null || childId==null || !belongsToOwnedGraph(parentId)) return;
        registerOwnedChild(parentId,childId,child);
    }
    private boolean belongsToOwnedGraph(ObjectRefId identity) {
        return owned.stream().anyMatch(lease->lease.identity().equals(identity))
                || ownedChildren.values().stream().anyMatch(children->children.contains(identity));
    }
    @Override public void replacementSpawned(int transferredSlot,ObjectInstance replacement) {
        var parent=pendingReplacements.remove(transferredSlot);
        if(services==null || retired || parent==null || replacement.isDestroyed() || !belongsToOwnedGraph(parent)) return;
        services.objectQuery().identityOf(replacement).ifPresent(child->registerOwnedChild(parent,child,replacement));
    }
    private void registerOwnedChild(ObjectRefId parentId,ObjectRefId childId,ObjectInstance child) {
        if(ownedChildren.values().stream().mapToInt(List::size).sum()>=64) {
            if(child instanceof AbstractObjectInstance object) ObjectLifetimeOps.expireDynamic(object);
            return;
        }
        var children=new ArrayList<>(ownedChildren.getOrDefault(parentId,List.of()));
        if(!children.contains(childId))children.add(childId);
        ownedChildren.put(parentId,List.copyOf(children));
    }
    private void releaseChildren(ObjectRefId parent) {
        var pending=new ArrayList<ObjectRefId>();pending.add(parent);var visited=new HashSet<ObjectRefId>();
        for(int index=0;index<pending.size();index++) {
            var id=pending.get(index);if(!visited.add(id))continue;
            var children=ownedChildren.remove(id);if(children==null)continue;
            pending.addAll(children);
            for(var child:children) services.objectQuery().resolve(child).ifPresent(object->{
                if(object instanceof AbstractObjectInstance nativeChild) ObjectLifetimeOps.expireDynamic(nativeChild);
            });
        }
    }
    private void terminateGraph(ObjectInstance object) {
        services.objectQuery().identityOf(object).ifPresent(this::releaseChildren);
        if(object instanceof NativeGhostEncounterObjectInstance ghost) {
            for(var body:services.objectQuery().activeObjectsOfType(SozHyudoroBodyObjectInstance.class))
                if(body.belongsToEncounter(ghost)) ObjectLifetimeOps.expireDynamic(body);
            ObjectLifetimeOps.expireDynamic(ghost);
        } else expire(object);
    }
    private void terminateOwnedGraph(Owned lease) {
        releaseChildren(lease.identity());
        services.objectQuery().resolve(lease.identity()).ifPresent(this::terminateGraph);
    }
    private boolean cancellationBound(long id) {
        return entries.stream().anyMatch(e->e.activationId()==id && !e.oneShot());
    }
    private void releaseRide(Ride lease) {
        if(services==null || sprites==null) return;
        var platform=services.objectQuery().resolve(lease.platform()).orElse(null);
        var main=sprites.getMainPlayable();
        if(platform!=null && main!=null && services.objectManager().getRidingObject(main)==platform) {
            services.objectManager().releaseRidingObject(main,platform); main.setAir(true);
        }
    }
    private void expireRides() {
        for(int i=rides.size()-1;i>=0;i--) {
            var lease=rides.get(i);
            if(lease.ticks()<=0 || !ownerAvailable(lease.owner()) || entries.stream().noneMatch(e->e.activationId()==lease.activationId())
                    || services.objectQuery().resolve(lease.platform()).isEmpty()) { releaseRide(lease); rides.remove(i); }
            else rides.set(i,new Ride(lease.activationId(),lease.owner(),lease.platform(),lease.ticks()-1));
        }
    }
    private void expireCompanions() {
        for (int i=companions.size()-1;i>=0;i--) {
            var lease=companions.get(i);
            boolean cancelled=lease.cancelOnRemoval() && entries.stream().noneMatch(e->e.activationId()==lease.activationId());
            if (lease.ticks()<=0 || cancelled || !ownerAvailable(lease.owner())) {
                removeCompanion(lease); companions.remove(i);
            } else companions.set(i,new Companion(lease.activationId(),lease.owner(),lease.code(),lease.ticks()-1,lease.cancelOnRemoval(),lease.carry(),lease.landingX(),lease.landingY()));
        }
    }
    private void removeCompanion(Companion lease) {
        if(sprites!=null && sprites.getSprite(lease.code()) instanceof AbstractPlayableSprite buddy) {
            var carry=buddy.getTailsCarryController();
            if(carry!=null && carry.isCarryingMainCharacter()) carry.clearAndReleaseMain();
            sprites.removeTemporarySidekick(buddy);
        }
    }
    private void releaseOwned() {
        if (services != null) for (Owned lease : owned) terminateOwnedGraph(lease);
        for (var lease:companions) removeCompanion(lease);
        for (var lease:rides) releaseRide(lease);
    }
    private boolean enemyRule(EnemyRulePolicy.Kind kind) {
        return entries.stream().anyMatch(e->ownerAvailable(e.owner()) && e.policy() instanceof EnemyRulePolicy p && p.kind()==kind
                && (kind!=EnemyRulePolicy.Kind.BUDDING || !admitted.contains(e.activationId())));
    }
    @Override public boolean permitsRoutine(ObjectInstance object, PlayableEntity player) {
        if (!(object instanceof AbstractBadnikInstance)) return true;
        if (enemyRule(EnemyRulePolicy.Kind.STASIS) && groundedMotionTarget(object)) {
            // A native init/wait release with no contact byte must establish its stock body first.
            if(object instanceof TouchResponseProvider touch && receiverCall(object,touch::getCollisionFlags)==0) return true;
            return services.objectQuery().activeObjectsOfType(AbstractBadnikInstance.class).stream()
                    .filter(this::groundedMotionTarget)
                    .filter(enemy->receiverCall(enemy,()->player!=null && Math.abs(enemy.getX()-player.getCentreX())<=256))
                    .limit(4).noneMatch(enemy->enemy==object);
        }
        return true;
    }
    @Override public boolean permitsContact(ObjectInstance object, PlayableEntity player) {
        if (!(object instanceof AbstractBadnikInstance)) return true;
        if (enemyRule(EnemyRulePolicy.Kind.TRUCE)) return false;
        return true;
    }
    private boolean groundedMotionTarget(ObjectInstance object) {
        return receiverCall(object,()->object instanceof NativeObjectFieldTarget target
                && target.fieldKind() == NativeObjectFieldTarget.Kind.BADNIK && target.supportsGroundedFieldMotion());
    }
    private NativeObjectFieldTarget.Kind fieldKind(ObjectInstance object) {
        return receiverCall(object,()->object instanceof NativeObjectFieldTarget target ? target.fieldKind() : null);
    }
    /** Foreign targets own their callbacks; an abort retains that receiver's owner and stops the frame. */
    private <T> T receiverCall(ObjectInstance object,Supplier<T> callback) {
        return ObjectCallbackDispatch.call(services==null?null:services.objectManager(),object,callback);
    }
    /** Native duplication retains the four bounded constructor recipes, independent of motion admission. */
    private static boolean clonableNativeBadnik(ObjectInstance object) {
        return object instanceof ButterdroidBadnikInstance || object instanceof RhinobotBadnikInstance
                || object instanceof PenguinatorBadnikInstance || object instanceof MushmeanieBadnikInstance;
    }

    private void selectFieldTargets(AbstractPlayableSprite player) {
        for(var entry:entries) if(entry.policy() instanceof ObjectFieldPolicy field && field.target()!=ObjectFieldPolicy.Target.RING) {
            int cap=field.target()==ObjectFieldPolicy.Target.BADNIK?4:6;
            var previous=fieldTargets.getOrDefault(entry.activationId(),new FieldTargets(List.of(),0));
            var selected=new ArrayList<>(previous.identities());
            selected.removeIf(id->{var object=services.objectQuery().resolve(id).orElse(null);
                return object==null || receiverCall(object,()->object.isDestroyed() || Math.abs(object.getX()-player.getCentreX())>256 || Math.abs(object.getY()-player.getCentreY())>160);});
            int acquired=previous.acquired();
            var candidates=services.objectQuery().activeObjectsOfType(ObjectInstance.class).stream()
                    .filter(object->receiverCall(object,()->!object.isDestroyed() && (field.target()==ObjectFieldPolicy.Target.BADNIK?groundedMotionTarget(object)
                            :object instanceof NativeObjectFieldTarget target && target.fieldKind()==NativeObjectFieldTarget.Kind.PROJECTILE)))
                    .filter(object->receiverCall(object,()->Math.abs(object.getX()-player.getCentreX())<=160 && Math.abs(object.getY()-player.getCentreY())<=96))
                    .sorted(java.util.Comparator.<ObjectInstance>comparingInt(object->receiverCall(object,()->Math.abs(object.getX()-player.getCentreX())+Math.abs(object.getY()-player.getCentreY())))
                            .thenComparingInt(object->services.objectQuery().identityOf(object).map(ObjectRefId::dynamicId).orElse(Integer.MAX_VALUE))
                            .thenComparingInt(object->services.objectQuery().identityOf(object).map(ObjectRefId::spawnId).orElse(Integer.MAX_VALUE))).toList();
            for(var object:candidates) {
                if(selected.size()>=cap || acquired>=cap*2) break;
                var identity=services.objectQuery().identityOf(object).orElse(null);
                if(identity!=null && !selected.contains(identity)) { selected.add(identity); acquired++; }
            }
            fieldTargets.put(entry.activationId(),new FieldTargets(selected,acquired));
        }
    }
    @Override public void afterRoutine(ObjectInstance object, PlayableEntity player) {
        if (services == null || player == null) return;
        receiverCall(object,()->{applyAfterRoutine(object,player);return null;});
    }
    private void applyAfterRoutine(ObjectInstance object,PlayableEntity player) {
        if (object.isDestroyed()) return;
        if (object instanceof NativeObjectFieldTarget target) {
            int fieldX=0,fieldY=0;
            for (var entry : entries) if (entry.policy() instanceof ObjectFieldPolicy field) {
                var selected=fieldTargets.get(entry.activationId());
                var identity=services.objectQuery().identityOf(object).orElse(null);
                boolean match=ownerAvailable(entry.owner()) && selected!=null && selected.identities().contains(identity);
                int dx=player.getCentreX()-object.getX(),dy=player.getCentreY()-object.getY();
                if (!match || Math.abs(dx)>160 || Math.abs(dy)>96) continue;
                int sign=field.mode()==ObjectFieldPolicy.Mode.REPEL ? -1:1;
                fieldX+=Integer.signum(dx)*sign; fieldY+=Integer.signum(dy)*sign;
            }
            if(fieldX!=0 || fieldY!=0) {
                int dx=Math.clamp(fieldX,-2,2),dy=Math.clamp(fieldY,-2,2);
                boolean clear=true;
                if(groundedMotionTarget(object)) {
                    int flags=((TouchResponseProvider)object).getCollisionFlags();
                    clear=touchSizes!=null && NativeEncounterGeometry.clear(services,new NativeEncounterGeometry.Body(object.getX()+dx,object.getY()+dy,
                            touchSizes.getWidthRadius(flags&63),Math.max(1,touchSizes.getHeightRadius(flags&63)-1)),null);
                }
                if(clear) target.displaceForField(dx,dy);
            }
            if (target.fieldKind()==NativeObjectFieldTarget.Kind.PROJECTILE) {
                var id=services.objectQuery().identityOf(object).orElse(null);
                if (id != null && projectileRule(ProjectileRulePolicy.Kind.SHORT_RANGE)) {
                    int age=projectileAges.merge(id,1,Integer::sum);
                    if (age>=90 || Math.abs(object.getX()-player.getCentreX())>160) expire(object);
                }
            }
        }
    }
    private boolean projectileRule(ProjectileRulePolicy.Kind kind) {
        return entries.stream().anyMatch(e->ownerAvailable(e.owner()) && e.policy() instanceof ProjectileRulePolicy p && p.kind()==kind);
    }
    @Override public void badnikDefeated(ObjectInstance object, PlayableEntity player) {
        if (services==null || !(player instanceof AbstractPlayableSprite sprite)) return;
        if(object instanceof AbstractObjectInstance nativeObject && nativeObject.getSlotIndex()>=0) {
            services.objectQuery().identityOf(object).filter(this::belongsToOwnedGraph)
                    .ifPresent(identity->pendingReplacements.put(nativeObject.getSlotIndex(),identity));
        }
        for (var e:entries) if (ownerAvailable(e.owner()) && e.policy() instanceof RingEconomyPolicy p && p.kind()==RingEconomyPolicy.Kind.BOUNTY) {
            int awarded=clocks.getOrDefault(e.activationId(),0);
            if(awarded<18) { rain(e.activationId(),object.getX(),object.getY(),3); clocks.put(e.activationId(),awarded+3); }
        }
        String contentOwner = groundedMotionTarget(object)
                ? ObjectCallbackDispatch.registeredContentOwner(services.objectManager(), object) : null;
        if ((clonableNativeBadnik(object) || contentOwner != null && ownerAvailable(contentOwner)) && enemyRule(EnemyRulePolicy.Kind.BUDDING)) {
            var identity=services.objectQuery().identityOf(object).orElse(null);
            var activation=entries.stream().filter(e->e.policy() instanceof EnemyRulePolicy p && p.kind()==EnemyRulePolicy.Kind.BUDDING).findFirst().orElse(null);
            if(identity!=null && activation!=null && budded.add(identity) && clocks.getOrDefault(activation.activationId(),0)<2) {
                var source=object.getSpawn();
                int flags=((TouchResponseProvider)object).getCollisionFlags();
                int halfWidth=touchSizes==null?0:touchSizes.getWidthRadius(flags&63);
                int halfHeight=touchSizes==null?0:touchSizes.getHeightRadius(flags&63);
                boolean clear=halfWidth>0 && halfHeight>0 && NativeEncounterGeometry.ordinary(services,sprite) && !bossActive && owned.size()+2<=MAX_OWNED_ROOTS;
                for(int side:new int[]{-1,1}) {
                    int x=object.getX()+side*48;
                    clear &= NativeEncounterGeometry.clear(services,
                            new NativeEncounterGeometry.Body(x,object.getY(),halfWidth,Math.max(1,halfHeight-1)),null);
                    if(contentOwner!=null) {
                        var floor=services.floorSurface(x,object.getY()+halfHeight,TerrainSurface.Layer.FOREGROUND,TerrainSurface.Path.PRIMARY);
                        clear &= floor.foundSurface() && floor.distance()>=-1 && floor.distance()<12;
                    }
                }
                if(!clear) {
                    TransientMutatorPolicyAccess.report(world,activation.activationId(),"Failed: the defeated badnik has no clear native child positions");
                    acknowledge(activation); return;
                }
                for(int side:new int[]{-1,1}) {
                    ObjectSpawn cloneSpawn=at(object.getX()+side*48,object.getY(),source.objectId(),source.subtype(),source.renderFlags());
                    var clone=contentOwner==null ? spawn(activation.activationId(),360,()->cloneMovable(object,cloneSpawn))
                            : ObjectCallbackDispatch.createRegisteredDerivative(services.objectManager(),object,cloneSpawn.x(),cloneSpawn.y());
                    if(clone!=null && contentOwner!=null) services.objectQuery().identityOf(clone).ifPresent(id ->
                            owned.add(new Owned(activation.activationId(),activation.owner(),contentOwner,id,360,cancellationBound(activation.activationId()))));
                    if(clone!=null) services.objectQuery().identityOf(clone).ifPresent(budded::add);
                }
                clocks.put(activation.activationId(),2);
                acknowledge(activation);
            }
        }
        for(var e:entries) if(e.policy() instanceof ChallengePolicy p && p.kind()==ChallengePolicy.Kind.AMNESTY) {
            var task=tasks.get(e.activationId());
            if(task!=null && !task.complete()) {
                tasks.put(e.activationId(),new Task(task.x(),task.y(),task.initialRings(),task.ticks(),0,true));
                TransientMutatorPolicyAccess.report(world,e.activationId(),"Failed: a badnik was defeated; pacifist bonus ended");
            }
        }
        advanceTasks(ChallengePolicy.Kind.SKILL_CHAIN,sprite,4);
        if (!chainDispatch && enemyRule(EnemyRulePolicy.Kind.CHAIN_REACTION)) {
            chainDispatch=true;
            try { blast(object.getX(),object.getY(),96,1,player,object); }
            finally { chainDispatch=false; }
        }
    }
    private static AbstractObjectInstance cloneMovable(ObjectInstance original,ObjectSpawn spawn) {
        if(original instanceof ButterdroidBadnikInstance) return new ButterdroidBadnikInstance(spawn);
        if(original instanceof RhinobotBadnikInstance) return new RhinobotBadnikInstance(spawn);
        if(original instanceof PenguinatorBadnikInstance) return new PenguinatorBadnikInstance(spawn);
        return new MushmeanieBadnikInstance(spawn);
    }
    private void blast(int x,int y,int radius,int maxHits,PlayableEntity player,ObjectInstance exclude) {
        int hits=0;
        for (var target:services.objectQuery().activeObjectsOfType(AbstractBadnikInstance.class)) {
            boolean hit=receiverCall(target,()->{
                if (!(target instanceof NativeObjectFieldTarget nativeTarget) || nativeTarget.fieldKind()!=NativeObjectFieldTarget.Kind.BADNIK
                        || target==exclude || target.isDestroyed() || Math.abs(target.getX()-x)>radius || Math.abs(target.getY()-y)>radius) return false;
                if (!(target instanceof PoweredScreenAttackable attackable)) return false;
                attackable.onPoweredScreenAttack(player);return true;
            });
            if (hit && ++hits>=maxHits) break;
        }
    }
    private void rain(long activation,int x,int y,int count) {
        if(!com.openggf.game.mutators.LevelMutatorPolicyAccess.ringsAllowed(world,com.openggf.game.mutators.RingAcquisitionDomain.MAIN_LEVEL)) return;
        for (int i=0;i<count;i++) {
            final int px=x+(i-count/2)*16;
            spawn(activation,240,()->LostRingObjectInstance.spawn(px,y,0,0,0,240,null));
        }
    }
    private void economy(TransientMutatorPolicySource.Entry entry,RingEconomyPolicy policy,AbstractPlayableSprite player) {
        int tick=policy.kind()==RingEconomyPolicy.Kind.RAIN || policy.kind()==RingEconomyPolicy.Kind.LEAK
                ?clocks.merge(entry.activationId(),1,Integer::sum):0;
        switch(policy.kind()) {
            case RAIN -> { if (tick%20==1) rain(entry.activationId(),player.getCentreX(),player.getCentreY()-72,2); }
            case LEAK -> {
                if (tick%60==0 && player.getRingCount()>1) { player.setRingCount(player.getRingCount()-1); rain(entry.activationId(),player.getCentreX(),player.getCentreY(),1); }
            }
            case BALANCE -> {
                if (!admitted.contains(entry.activationId())) {
                    int rings=player.getRingCount();
                    int delta=((services.rng().nextRaw()&0x7FFF)%21)-10;
                    int target=com.openggf.game.mutators.LevelMutatorPolicyAccess.ringsAllowed(world,com.openggf.game.mutators.RingAcquisitionDomain.MAIN_LEVEL)
                            ?Math.max(0,Math.min(Math.max(99,rings),rings+delta)):0;
                    if(target>rings) player.addRings(target-rings); else player.setRingCount(target);
                    acknowledge(entry);
                }
            }
            case BOUNTY, COMPLETION_REWARD -> { }
        }
    }
    private void consumeCompletions(AbstractPlayableSprite player) {
        var source=TransientMutatorPolicyAccess.source(world);
        if(source==null) return;
        for(var completion:source.completions()) {
            if(!source.ownerAvailable(completion.owner())) { source.acknowledgeCompletion(completion.activationId()); continue; }
            if(player.isObjectControlled() || player.isHurt()) continue;
            if(com.openggf.game.mutators.LevelMutatorPolicyAccess.ringsAllowed(world,
                    com.openggf.game.mutators.RingAcquisitionDomain.MAIN_LEVEL)) player.addRings(completion.ringReward());
            source.acknowledgeCompletion(completion.activationId());
        }
    }
    private void challenge(TransientMutatorPolicySource.Entry entry,ChallengePolicy policy,AbstractPlayableSprite player) {
        if(player.isObjectControlled() || bossActive || player.isHurt()) return;
        Task previous=tasks.get(entry.activationId());
        if(previous==null) {
            int x=player.getCentreX(),y=player.getCentreY(),initial=player.getRingCount();
            if(policy.kind()==ChallengePolicy.Kind.TASK) {
                if(player.getAir()) return;
                boolean canJump=entries.stream().noneMatch(e->e.policy() instanceof InputControlPolicy p && p.mode()==InputControlPolicy.Mode.NO_FRESH_JUMP);
                initial=canJump && services.rng().nextBoolean()?-2:-1;
            } else if(policy.kind()==ChallengePolicy.Kind.LANDMARK) {
                int direction=player.getDirection()==Direction.LEFT?-1:1;
                var monitor=services.objectQuery().activeObjectsOfType(Sonic3kMonitorObjectInstance.class).stream()
                        .filter(m->m.presentationMetadata().role()==ObjectPresentationMetadata.Role.PICKUP
                                && (m.getX()-player.getCentreX())*direction>=48
                                && Math.abs(m.getX()-player.getCentreX())<=320 && Math.abs(m.getY()-player.getCentreY())<=96)
                        .findFirst().orElse(null);
                if(monitor==null || CoherentRelocationOps.landing(services,player,monitor.getX(),monitor.getY(),24)==null) return;
                x=monitor.getX();y=monitor.getY();
            }
            previous=new Task(x,y,initial,0,0,false);tasks.put(entry.activationId(),previous);
        }
        if (previous.complete()) return;
        int progress=previous.progress();
        int ticks=previous.ticks()+1;
        String instruction;
        switch(policy.kind()) {
            case TASK -> {
                progress=previous.initialRings()==-1 ? player.getAnimationId()==8?1:0 : player.getAir() && player.isJumping()?1:0;
                instruction=previous.initialRings()==-1?"Sonic says: duck":"Sonic says: jump";
            }
            case KEEP_MOVING -> { progress=Math.abs(player.getXSpeed())>=0x100 ? progress+1:0; instruction="Keep moving for two seconds"; }
            case AMNESTY -> { progress=Math.abs(player.getCentreX()-previous.x())>=192?1:0; instruction="Travel 192 pixels without defeating a badnik"; }
            case LANDMARK -> { progress=Math.abs(player.getCentreX()-previous.x())<=24 && Math.abs(player.getCentreY()-previous.y())<=48?1:0; instruction="Reach the pictured monitor at x="+previous.x(); }
            case SKILL_CHAIN -> instruction="Link a jump, ring pickup and badnik defeat";
            default -> throw new IllegalStateException();
        }
        int goal=switch(policy.kind()){case TASK->1;case KEEP_MOVING->120;case SKILL_CHAIN->7;case AMNESTY,LANDMARK->1;};
        boolean complete=progress>=goal;
        tasks.put(entry.activationId(),new Task(previous.x(),previous.y(),previous.initialRings(),ticks,progress,complete));
        int displayed=policy.kind()==ChallengePolicy.Kind.SKILL_CHAIN?Integer.bitCount(progress):progress;
        int displayedGoal=policy.kind()==ChallengePolicy.Kind.SKILL_CHAIN?3:goal;
        TransientMutatorPolicyAccess.report(world,entry.activationId(),(complete?"Complete: ":"Task: ")+instruction+(complete?"":" ("+displayed+"/"+displayedGoal+")"));
        if(!complete && policy.kind()==ChallengePolicy.Kind.KEEP_MOVING && (ticks>=450 || entry.agePresentations()>=450)) {
            player.setRingCount(Math.max(0,player.getRingCount()-3));
            tasks.put(entry.activationId(),new Task(previous.x(),previous.y(),previous.initialRings(),ticks,progress,true));
            TransientMutatorPolicyAccess.report(world,entry.activationId(),"Failed: keep-moving challenge ended; three rings maximum lost");
        }
        if (complete && policy.kind()!=ChallengePolicy.Kind.SKILL_CHAIN) rain(entry.activationId(),player.getCentreX(),player.getCentreY()-48,5);
    }
    private void prepareLandmarkThumbnail() {
        landmarkVisible=!retired && entries.stream().anyMatch(e->e.policy() instanceof ChallengePolicy p && p.kind()==ChallengePolicy.Kind.LANDMARK
                && tasks.containsKey(e.activationId()) && !tasks.get(e.activationId()).complete());
        if(!landmarkVisible || services==null || services.currentLevel()==null || services.renderManager()==null) {
            landmarkThumbnail=null; landmarkSheet=null; landmarkPalette=null; return;
        }
        var sheet=services.renderManager().getArtProvider().getSheet(Sonic3kObjectArtKeys.MONITOR);
        if(sheet==null || sheet.getFrameCount()<=4) { landmarkThumbnail=null; return; }
        int[] palette=new int[64];
        for(int line=0;line<4;line++) for(int color=0;color<16;color++)
            palette[line*16+color]=com.openggf.level.render.PlaneRasterizer.argb(services.currentLevel().getPalette(line).getColor(color));
        if(landmarkThumbnail==null || landmarkSheet!=sheet || !java.util.Arrays.equals(landmarkPalette,palette)) {
            var pixels=com.openggf.level.render.SpriteMappingRasterizer.rasterize(
                    sheet.getFrame(4),sheet.getPatterns(),palette,sheet.getPaletteIndex(),0);
            landmarkThumbnail=new com.openggf.mods.scene.SceneImage(pixels.width(),pixels.height(),pixels.argb());
            landmarkSheet=sheet; landmarkPalette=palette;
        }
    }
    @Override public void onPresentation(com.openggf.game.session.GameplayModeContext context,LogicalInputSnapshot input,
            com.openggf.game.GameplayObservation.Phase phase) {
        if(!retired) prepareLandmarkThumbnail();
    }
    @Override public void drawOverlay(com.openggf.mods.ui.PixelCanvas canvas) {
        if(retired || !landmarkVisible || landmarkThumbnail==null) return;
        int originX=canvas.width()-44,originY=68;
        canvas.fill(originX-4,originY-4,40,40,0xD0203040);
        for(int y=0;y<Math.min(32,landmarkThumbnail.height());y++) for(int x=0;x<Math.min(32,landmarkThumbnail.width());x++) {
            int color=landmarkThumbnail.pixel(x,y);
            if((color>>>24)!=0) canvas.fill(originX+x,originY+y,1,1,color);
        }
    }
    private void advanceTasks(ChallengePolicy.Kind kind,AbstractPlayableSprite player,int amount) {
        for(var e:entries) if(ownerAvailable(e.owner()) && e.policy() instanceof ChallengePolicy p && p.kind()==kind) {
            Task t=tasks.get(e.activationId());
            if(t!=null && !t.complete()) tasks.put(e.activationId(),new Task(t.x(),t.y(),t.initialRings(),t.ticks(),kind==ChallengePolicy.Kind.SKILL_CHAIN?t.progress()|amount:t.progress()+amount,false));
        }
    }
    private void relocation(TransientMutatorPolicySource.Entry entry,RelocationPolicy policy,AbstractPlayableSprite player) {
        if(bossActive || !NativeEncounterGeometry.ordinary(services,player)) return;
        long id=entry.activationId();
        int tick=clocks.merge(id,1,Integer::sum);
        if(policy.kind()!=RelocationPolicy.Kind.STEPS && policy.kind()!=RelocationPolicy.Kind.DELAYED_RETURN && admitted.contains(id)) return;
        int x=player.getCentreX(),y=player.getCentreY();
        int direction=player.getDirection()==Direction.LEFT?-1:1;
        switch(policy.kind()) {
            case LAST_CHECKPOINT -> {
                if(!(services.checkpointState() instanceof CheckpointState checkpoint) || checkpoint.getStarPostActivationMark()<=0) return;
                x=checkpoint.getSavedX(); y=checkpoint.getSavedY();
            }
            case RANDOM_CHECKPOINT -> {
                var candidates=encounteredCheckpoints.stream()
                        .filter(destination->Math.abs(destination.x()-player.getCentreX())<=640).toList();
                if(candidates.isEmpty()) return;
                var selected=candidates.get(services.rng().nextInt(candidates.size())); x=selected.x(); y=selected.y();
            }
            case BLINK -> x+=direction*64;
            case STEPS -> { if(tick%45!=0 || tick>240) return; x+=direction*24; }
            case DELAYED_RETURN -> {
                Task marker=tasks.get(id);
                if(marker==null) {
                    var landing=CoherentRelocationOps.landing(services,player,x,y,24);
                    if(landing!=null) tasks.put(id,new Task(landing.x(),landing.y(),0,0,0,false));
                    return;
                }
                if(tick<240 || admitted.contains(id)) return;
                x=marker.x(); y=marker.y();
            }
            case TEAM_SWAP -> {
                var buddy=sprites.getSidekicks().stream().filter(member->!member.getDead() && !member.isObjectControlled()
                        && !member.isHurt() && Math.abs(member.getCentreX()-player.getCentreX())<=256).findFirst().orElse(null);
                if(buddy==null) return;
                var mainLanding=CoherentRelocationOps.landing(services,player,buddy.getCentreX(),buddy.getCentreY(),16,buddy);
                var buddyLanding=CoherentRelocationOps.landing(services,buddy,x,y,16,player);
                if(mainLanding==null || buddyLanding==null) return;
                CoherentRelocationOps.move(services,buddy,buddyLanding,false);
                CoherentRelocationOps.move(services,player,mainLanding,true); acknowledge(entry); return;
            }
        }
        if(Math.abs(x-player.getCentreX())>640) return;
        if(Math.abs(x-player.getCentreX())<16 && Math.abs(y-player.getCentreY())<16) return;
        var landing=CoherentRelocationOps.landing(services,player,x,y,24);
        if(landing!=null) { CoherentRelocationOps.move(services,player,landing,true); if(policy.kind()!=RelocationPolicy.Kind.STEPS) acknowledge(entry); }
    }
    private AbstractPlayableSprite companion(String code,AbstractPlayableSprite leader) {
        var tails=new com.openggf.sprites.playable.Tails(code,(short)(leader.getX()-24),(short)(leader.getY()-48));
        tails.setCpuControlled(true); tails.setAir(true);
        tails.setCpuController(new com.openggf.sprites.playable.SidekickCpuController(tails,leader));
        return tails;
    }
    private void transport(TransientMutatorPolicySource.Entry entry,TransportPolicy policy,AbstractPlayableSprite player) {
        if(admitted.contains(entry.activationId()) || bossActive || !NativeEncounterGeometry.ordinary(services,player)) return;
        if(policy.kind()==TransportPolicy.Kind.RIDE) {
            var platform=services.objectQuery().activeObjectsOfType(FloatingPlatformObjectInstance.class).stream()
                    .filter(p->!p.isDestroyed() && (p.getSpawn().subtype()&15)!=7 && Math.abs(p.getX()-player.getCentreX())<=192
                            && Math.abs(p.getY()-player.getCentreY())<=96).findFirst().orElse(null);
            if(platform==null) return;
            int targetY=platform.getY()-platform.getSolidParams().groundHalfHeight()-player.getYRadius()-2;
            var identity=services.objectQuery().identityOf(platform).orElse(null);
            if(identity==null || !CoherentRelocationOps.compatibleFluid(services,player,platform.getX(),targetY)
                    || !NativeEncounterGeometry.clear(services,new NativeEncounterGeometry.Body(platform.getX(),targetY,player.getXRadius(),player.getYRadius()),platform,player)) return;
            // Native SolidObjectTop establishes riding in the next eligible checkpoint, not a bootstrap write.
            CoherentRelocationOps.move(services,player,new CoherentRelocationOps.Landing(platform.getX(),targetY),true);
            player.setYSpeed((short)0x100);
            rides.add(new Ride(entry.activationId(),entry.owner(),identity,240)); acknowledge(entry); return;
        }
        if(!companions.isEmpty()) return;
        boolean taxi=policy.kind()==TransportPolicy.Kind.CARRY;
        if(taxi && (player.isInWater() || services.gameState().isReverseGravityActive())) return;
        int direction=player.getDirection()==Direction.LEFT?-1:1;
        var landing=taxi?CoherentRelocationOps.landing(services,player,player.getCentreX()+direction*192,player.getCentreY(),40):null;
        if(taxi && landing==null) return;
        String code="native-companion-"+entry.activationId();
        AbstractPlayableSprite buddy=companion(code,player);
        if(taxi) {
            NativePositionOps.writeXPosPreserveSubpixel(buddy,player.getCentreX());
            NativePositionOps.writeYPosPreserveSubpixel(buddy,player.getCentreY()-32);
        }
        if(!NativeEncounterGeometry.clear(services,NativeEncounterGeometry.actor(buddy),null,
                taxi?new AbstractPlayableSprite[]{player}:new AbstractPlayableSprite[0])) return;
        sprites.addTemporarySidekick(buddy,"tails",()->{
            var rebuilt=companion(code,sprites.getMainPlayable());
            sprites.addTemporarySidekick(rebuilt,"tails"); services.levelManager().refreshPlayableSpriteArt(); return rebuilt;
        });
        services.levelManager().refreshPlayableSpriteArt();
        if(buddy.getSpriteRenderer()==null) { sprites.removeTemporarySidekick(buddy); return; }
        if(taxi) {
            NativePositionOps.writeXPosPreserveSubpixel(buddy,player.getCentreX());
            NativePositionOps.writeYPosPreserveSubpixel(buddy,player.getCentreY()-32);
            var carry=buddy.getTailsCarryController();
            if(carry==null || !carry.tryEncounterCarry()) { sprites.removeTemporarySidekick(buddy); return; }
        }
        companions.add(new Companion(entry.activationId(),entry.owner(),code,taxi?480:900,cancellationBound(entry.activationId()),taxi,landing==null?0:landing.x(),landing==null?0:landing.y()));
        acknowledge(entry);
    }
    @Override public java.util.Optional<com.openggf.control.PlayerInputState> inputFor(AbstractPlayableSprite carrier) {
        if(retired || sprites==null || sprites.getSprite(carrier.getCode())!=carrier) return java.util.Optional.empty();
        for(var lease:companions) if(lease.carry() && lease.code().equals(carrier.getCode()) && ownerAvailable(lease.owner())) {
            var carry=carrier.getTailsCarryController();
            if(carry==null || !carry.isCarrying(sprites.getMainPlayable())) return java.util.Optional.empty();
            int dx=lease.landingX()-carrier.getCentreX();
            int horizontal=Math.abs(dx)<=12?0:dx<0?AbstractPlayableSprite.INPUT_LEFT:AbstractPlayableSprite.INPUT_RIGHT;
            boolean flap=Math.abs(dx)>32 && carrier.getCentreY()+28>lease.landingY()-24 && lease.ticks()%16==0;
            int action=flap?com.openggf.control.InputActionMasks.ACTION_A:0;
            return java.util.Optional.of(com.openggf.control.PlayerInputState.of(horizontal,0,action,action,false,false));
        }
        return java.util.Optional.empty();
    }
    private void firePlayerShot(TransientMutatorPolicySource.Entry entry,AbstractPlayableSprite player) {
        int tick=clocks.getOrDefault(entry.activationId(),0);
        Task previous=tasks.get(entry.activationId());
        if(previous!=null && (tick-previous.ticks()<12 || previous.progress()>=6) || player.isObjectControlled() || player.isHurt()
                || !art(Sonic3kObjectArtKeys.SSZ_EGG_ROBO)) return;
        boolean left=player.getDirection()==Direction.LEFT;
        var shot=spawn(entry.activationId(),120,()->EggRoboShotInstance.playerShot(at(player.getCentreX()+(left?-20:20),
                player.getCentreY(),0,0,left?0:1),left));
        if(shot!=null) tasks.put(entry.activationId(),new Task(0,0,0,tick,previous==null?1:previous.progress()+1,false));
    }
    @Override public void projectileImpact(ObjectInstance shot,PlayableEntity player) {
        if(services==null || fieldKind(shot)!=NativeObjectFieldTarget.Kind.PROJECTILE) return;
        var activation=entries.stream().filter(e->ownerAvailable(e.owner()) && e.policy() instanceof ProjectileRulePolicy p
                && p.kind()==ProjectileRulePolicy.Kind.IMPACT_BLAST).findFirst().orElse(null);
        if(activation==null || clocks.getOrDefault(activation.activationId(),0)>=6) return;
        var identity=services.objectQuery().identityOf(shot).orElse(null);
        if(identity==null || !impacts.add(identity)) return;
        clocks.merge(activation.activationId(),1,Integer::sum);
        int x=receiverCall(shot,shot::getX),y=receiverCall(shot,shot::getY);
        spawn(activation.activationId(),64,()->new ExplosionObjectInstance(0x27,x,y,
                services.renderManager(),com.openggf.game.sonic3k.audio.Sonic3kSfx.BREAK.id));
        if(!enemyRule(EnemyRulePolicy.Kind.TRUCE)) blast(x,y,64,2,player,null);
    }
    @Override public boolean playerProjectileStep(ObjectInstance shot,PlayableEntity player) {
        if(touchSizes==null || player==null || enemyRule(EnemyRulePolicy.Kind.TRUCE)) return false;
        int x=receiverCall(shot,shot::getX),y=receiverCall(shot,shot::getY);
        for(var target:services.objectManager().poweredAttacks().targetReadView()) {
            boolean hit=receiverCall(target,()->{
                if(!(target instanceof NativeObjectFieldTarget nativeTarget) || nativeTarget.fieldKind()!=NativeObjectFieldTarget.Kind.BADNIK
                        || !(target instanceof TouchResponseProvider provider)
                        || target.isDestroyed() || !(target instanceof PoweredScreenAttackable attackable)) return false;
                int flags=provider.getCollisionFlags();
                if(flags==0 || (flags&0xC0)!=0) return false;
                int width=touchSizes.getWidthRadius(flags&63);
                int height=touchSizes.getHeightRadius(flags&63);
                if(Math.abs(target.getX()-x)>width+touchSizes.getWidthRadius(0x1C) || Math.abs(target.getY()-y)>height+touchSizes.getHeightRadius(0x1C)) return false;
                attackable.onPoweredScreenAttack(player);return true;
            });
            if(hit){
                if(projectileRule(ProjectileRulePolicy.Kind.IMPACT_BLAST)) blast(x,y,64,2,player,target);
                return true;
            }
        }
        return false;
    }
}
