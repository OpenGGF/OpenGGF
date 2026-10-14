package com.openggf.mods.code;

import com.openggf.LevelFrameContext;
import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.*;
import com.openggf.game.mutators.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.game.sonic3k.mutators.NativeMutatorRuntime;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.mods.*;
import com.openggf.mods.mutators.*;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Registered receiver callbacks through real native consumers and creator fault boundaries. */
class TestNativeObjectFieldCallbacks {
    @AfterEach void clear() { SessionManager.clear(); AbstractObjectInstance.resetCameraBoundsForTests(); }

    @Test void foreignFiringScanAbortsForTheThrowingReceiverWithoutQuarantiningTheCaller() throws Exception {
        var rig = new Rig(); var a = rig.actor("a", Fault.NONE, NativeObjectFieldTarget.Kind.BADNIK, 400);
        var b = rig.actor("b", Fault.KIND, NativeObjectFieldTarget.Kind.PROJECTILE, 440);
        rig.active(new EnemyRulePolicy(EnemyRulePolicy.Kind.FIRE_CADENCE));
        rig.aborted("b", () -> rig.objects.update(0, rig.player, List.of(), 1, false));
        assertEquals(0, a.updates, "the aborted frame cannot finish the caller's update");
        assertEquals(1, b.kindCalls);
        rig.retire(b);
        assertDoesNotThrow(() -> rig.objects.update(0, rig.player, List.of(), 2, false));
        assertEquals(1, a.updates); assertTrue(a.firingAllowed);
        assertEquals(1, b.kindCalls, "retired content is absent from the next healthy frame");
    }

    @Test void fieldSelectionAbortsForTheForeignReceiverBeforePublishingACompletedSelection() throws Exception {
        var rig = new Rig(); var a=rig.actor("a", Fault.NONE, NativeObjectFieldTarget.Kind.PROJECTILE, 400);
        var b=rig.actor("b", Fault.KIND, NativeObjectFieldTarget.Kind.PROJECTILE, 440);
        var policy = new ObjectFieldPolicy(ObjectFieldPolicy.Target.PROJECTILE, ObjectFieldPolicy.Mode.ATTRACT);
        rig.aborted("b", () -> rig.active(policy));
        assertFalse(rig.runtime.capture().fieldTargets().containsKey(1L));
        rig.retire(b);
        assertDoesNotThrow(() -> rig.active(policy));
        assertEquals(1, rig.runtime.capture().fieldTargets().get(1L).identities().size());
        int old=a.getX(); rig.runtime.afterRoutine(a, rig.player); assertEquals(old+1,a.getX());
        rig.runtime.beforeNativeTick(rig.frame, LogicalInputSnapshot.neutral());
        assertEquals(1,b.kindCalls);
    }

    @Test void foreignDisplacementFailureDoesNotMoveOrDisableItsCallerOrHealthyNeighbor() throws Exception {
        var rig=new Rig();var a=rig.actor("a",Fault.NONE,NativeObjectFieldTarget.Kind.PROJECTILE,400);
        var b=rig.actor("b",Fault.DISPLACE,NativeObjectFieldTarget.Kind.PROJECTILE,440);
        rig.active(new ObjectFieldPolicy(ObjectFieldPolicy.Target.PROJECTILE,ObjectFieldPolicy.Mode.ATTRACT));
        int old=b.getX(); rig.aborted("b", () -> ObjectCallbackDispatch.run(rig.objects,a,()->rig.runtime.afterRoutine(b,rig.player)));
        assertEquals(old,b.getX());int queried=b.kindCalls;
        rig.retire(b);
        rig.runtime.afterRoutine(b,rig.player);assertEquals(1,b.displacements);assertEquals(queried,b.kindCalls);
        old=a.getX();rig.runtime.afterRoutine(a,rig.player);assertEquals(old+1,a.getX());
    }

    @Test void stasisQueriesForeignGroundedCapabilityUnderItsReceiverOwner() throws Exception {
        var rig=new Rig();var b=rig.actor("b",Fault.GROUNDED,NativeObjectFieldTarget.Kind.BADNIK,440);
        var a=rig.actor("a",Fault.NONE,NativeObjectFieldTarget.Kind.BADNIK,400);
        rig.active(new EnemyRulePolicy(EnemyRulePolicy.Kind.STASIS));
        rig.aborted("b", () -> ObjectCallbackDispatch.run(rig.objects,a,()->rig.runtime.permitsRoutine(a,rig.player)));
        assertEquals(1,b.groundedCalls);
        rig.retire(b);
        assertFalse(rig.runtime.permitsRoutine(a,rig.player));
    }

    @Test void impactBlastAbortsForTheForeignPoweredReceiverBeforeTouchingLaterTargets() throws Exception {
        var rig=new Rig();var a=rig.actor("a",Fault.NONE,NativeObjectFieldTarget.Kind.PROJECTILE,400);
        var b=rig.actor("b",Fault.POWERED,NativeObjectFieldTarget.Kind.BADNIK,440);
        var c=rig.actor("c",Fault.NONE,NativeObjectFieldTarget.Kind.BADNIK,450);
        rig.active(new ProjectileRulePolicy(ProjectileRulePolicy.Kind.IMPACT_BLAST));
        rig.aborted("b", () -> ObjectCallbackDispatch.run(rig.objects,a,()->rig.runtime.projectileImpact(a,rig.player)));
        assertEquals(1,b.attacks);assertEquals(0,c.attacks);
    }

    @Test void virtualMachineFatalCallbackEscapesWithoutQuarantiningAnyOwner() throws Exception {
        var rig=new Rig();rig.actor("b",Fault.FATAL,NativeObjectFieldTarget.Kind.PROJECTILE,440);
        assertThrows(InternalError.class,()->rig.active(new ObjectFieldPolicy(ObjectFieldPolicy.Target.PROJECTILE,ObjectFieldPolicy.Mode.ATTRACT)));
        assertTrue(rig.boundary.isOwnerAvailable("b"));assertTrue(rig.disabled.isEmpty());
    }

    enum Fault { NONE, KIND, GROUNDED, DISPLACE, POWERED, FATAL }
    static final class Actor extends AbstractBadnikInstance implements NativeObjectFieldTarget,PoweredScreenAttackable,RewindRecreatable {
        final Fault fault;final Kind kind;int kindCalls,groundedCalls,displacements,attacks,updates;boolean firingAllowed;
        Actor(ObjectSpawn spawn,Fault fault,Kind kind) {super(spawn,"registered field callback fixture");this.fault=fault;this.kind=kind;}
        public Kind fieldKind(){kindCalls++;if(fault==Fault.FATAL)throw new InternalError("fatal fixture");if(fault==Fault.KIND)throw new IllegalStateException("field-kind fixture");return kind;}
        public boolean supportsGroundedFieldMotion(){groundedCalls++;if(fault==Fault.GROUNDED)throw new IllegalStateException("grounded fixture");return true;}
        public void displaceForField(int x,int y){displacements++;if(fault==Fault.DISPLACE)throw new IllegalStateException("displacement fixture");currentX+=x;currentY+=y;}
        public void onPoweredScreenAttack(PlayableEntity player){attacks++;if(fault==Fault.POWERED)throw new IllegalStateException("powered fixture");}
        protected void updateMovement(int vIntRunCount,PlayableEntity player){firingAllowed=ObjectCombatRules.permitsFiring(services(),currentX);updates++;}
        protected int getCollisionSizeIndex(){return 0x0b;}
        public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
        public Actor recreateForRewind(RewindRecreateContext context){return new Actor(context.spawn(),fault,kind);}
    }
    static final class Rig {
        final AtomicReference<List<TransientMutatorPolicySource.Entry>> entries=new AtomicReference<>(List.of());
        final Set<String> disabled=new LinkedHashSet<>();
        final ModFaultBoundary boundary=new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),owners->new ModStateSaveResult.Saved(),disabled::addAll);
        final TestablePlayableSprite player=new TestablePlayableSprite("sonic",(short)500,(short)300);
        final Map<String,Fault> faults=new HashMap<>();final Map<String,NativeObjectFieldTarget.Kind> kinds=new HashMap<>();
        final ModDecoratedObjectRegistry registry;final ObjectManager objects;final NativeMutatorRuntime runtime;final LevelFrameContext frame=mock(LevelFrameContext.class);
        Rig() throws Exception {
            var module=mock(GameModule.class);var data=mock(GameDataSource.class);
            when(data.rom()).thenReturn(Optional.of(com.openggf.data.Rom.fromReader(com.openggf.data.RomByteReader.fromBytes(new byte[128]),"field-callback fixture")));
            var sizes=mock(TouchResponseTable.class);when(sizes.getWidthRadius(anyInt())).thenReturn(16);when(sizes.getHeightRadius(anyInt())).thenReturn(16);
            when(module.createTouchResponseTable(any(com.openggf.data.RomByteReader.class))).thenReturn(sizes);
            var source=mock(TransientMutatorPolicySource.class);when(source.entries()).thenAnswer(call->entries.get());when(source.ownerAvailable(anyString())).thenAnswer(call->boundary.isOwnerAvailable(call.getArgument(0)));
            var sourceState=new WorldSessionPolicyState(){
                public <T>T getService(Class<T> type){return type.isInstance(source)?type.cast(source):null;}
                public RewindSnapshottable<?> rewindAdapter(){return null;}
                public void beforeAssembly(LevelLoadCause cause){}public void bindRoster(SpriteManager sprites){}public void failedAssembly(LevelLoadCause cause){}public void closeScreens(){}public void retire(){}
            };
            var holder=new NativeMutatorRuntime[1];when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world->{holder[0]=new NativeMutatorRuntime(world);return CompositeWorldSessionPolicyState.of(sourceState,holder[0]);});
            var world=new WorldSession(module,module,data,null);runtime=holder[0];
            var camera=mock(Camera.class);when(camera.getWidth()).thenReturn((short)1000);when(camera.getHeight()).thenReturn((short)800);when(camera.getFocusedSprite()).thenReturn(player);
            var managerHolder=new ObjectManager[1];var services=new StubObjectServices(){
                public ObjectManager objectManager(){return managerHolder[0];}public WorldSession worldSession(){return world;}public Camera camera(){return camera;}
                public ObjectQuery objectQuery(){return managerHolder[0].objectQuery();}
            };
            var base=mock(ObjectRegistry.class);when(base.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_3K);
            when(base.objectWindowingStrategy()).thenReturn(ObjectWindowingStrategy.LEGACY);
            var keys=new ModObjectKeyRegistry(List.of("a","b","c").stream().map(owner->new ModObjectKeyRegistry.Registration(owner,owner+":actor",
                    (spawn,ignored)->new Actor(spawn,faults.get(owner),kinds.get(owner)))).toList());
            registry=new ModDecoratedObjectRegistry(base,keys,Map.of(),boundary);
            objects=new ObjectManager(List.of(),registry,0,null,sizes,null,camera,services);managerHolder[0]=objects;
            var sprites=mock(SpriteManager.class);when(sprites.getMainPlayable()).thenReturn(player);when(sprites.getSidekicks()).thenReturn(List.of());
            var level=mock(LevelManager.class);when(level.getObjectManager()).thenReturn(objects);when(frame.spriteManager()).thenReturn(sprites);when(frame.levelManager()).thenReturn(level);
            AbstractObjectInstance.updateCameraBounds(0,0,1000,800,0);
        }
        Actor actor(String owner,Fault fault,NativeObjectFieldTarget.Kind kind,int x){
            var spawn=new ObjectSpawn(x,300,0,0,0,false,300,-1,owner,owner+":actor");
            faults.put(owner,fault);kinds.put(owner,kind);
            return (Actor)objects.createDynamicObject(()->registry.create(spawn));
        }
        void active(MutatorPolicy policy){entries.set(List.of(new TransientMutatorPolicySource.Entry("producer",1,policy,0,600,false)));runtime.beforeNativeTick(frame,LogicalInputSnapshot.neutral());}
        void aborted(String owner,Runnable callback){
            var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,callback::run);
            assertEquals(owner,failure.owner());onlyFailed(owner);
        }
        // Bounded stand-in for host retirement after an aborted frame. Disabling
        // an owner alone does not retire objects; the real Engine recovery is tested separately.
        void retire(Actor actor){actor.setDestroyed(true);objects.removeDynamicObject(actor);}
        void onlyFailed(String owner){assertEquals(Set.of(owner),disabled);assertFalse(boundary.isOwnerAvailable(owner));for(String healthy:List.of("a","b","c","producer"))if(!healthy.equals(owner))assertTrue(boundary.isOwnerAvailable(healthy),healthy);}
    }
}
