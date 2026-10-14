package com.openggf.level.objects;

import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.S3kAuthoredGoalInstance;
import com.openggf.game.sonic3k.objects.S3kSignpostInstance;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.mods.code.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestHostObjectKeys {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void decoratedHostKeysUseEngineProvenanceAndKeepCreatorOwnersDistinct() {
        TestEnvironment.resetAll();
        var base=new Sonic3kObjectRegistry();
        var keys=new ModObjectKeyRegistry(List.of(new ModObjectKeyRegistry.Registration(
                "creator","creator:goal",(spawn,registry)->new Dummy(spawn))));
        var registry=new ModDecoratedObjectRegistry(base,keys);
        ObjectSpawn host=hostSpawn();
        var callbackOwners=new java.util.ArrayList<String>();
        ObjectCallbackBoundary boundary=new ObjectCallbackBoundary() {
            @Override public <T> T call(String owner,java.util.function.Supplier<T> callback) {
                callbackOwners.add(owner);
                return callback.get();
            }
        };
        ObjectCallbackRouter router=new ObjectCallbackRouter(new ObservedRegistry(registry,boundary));
        ObjectInstance goal=router.construct(host,()->registry.create(host));
        router.register(goal,host);
        assertInstanceOf(S3kAuthoredGoalInstance.class,goal);
        assertNull(router.owner(goal));
        assertTrue(registry.browsableObjectKeys().contains("openggf:authored-goal"));
        var creator=new CommonObjectPlacementEncoding().createKeyed(64,64,"creator:goal",0,0,true,10);
        ObjectInstance owned=router.construct(creator,()->registry.create(creator)); router.register(owned,creator);
        assertEquals("creator",router.owner(owned));
        router.run(goal,()->{});
        router.run(owned,()->{});
        assertEquals(List.of("creator","creator"),callbackOwners,
                "Only creator factory and callbacks cross the creator fault boundary");
        assertThrows(IllegalStateException.class,()->router.run(goal,()->{
            throw new IllegalStateException("engine failure");
        }));
        assertEquals(List.of("creator","creator"),callbackOwners,
                "A built-in engine failure must not quarantine a creator owner");
        assertThrows(IllegalArgumentException.class,()->registry.create(new CommonObjectPlacementEncoding()
                .createKeyed(64,64,"openggf:missing",0,0,true,11)));
        assertFalse(registry.hasObjectKey("creator:missing"));
        assertThrows(IllegalArgumentException.class,()->registry.create(new CommonObjectPlacementEncoding()
                .createKeyed(64,64,"creator:missing",0,0,true,12)));
        assertThrows(IllegalArgumentException.class,()->new ObjectSpawn(64,64,0,0,0,true,64,9,
                "creator","openggf:authored-goal"));
        assertThrows(IllegalArgumentException.class,()->new ModObjectKeyRegistry(List.of(
                new ModObjectKeyRegistry.Registration("openggf","openggf:authored-goal",(spawn,r)->new Dummy(spawn)))));
    }

    @Test void aHostKeyCannotGrantAuthorityToAnUnrelatedReturnedClass() {
        var registry=new Sonic3kObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) { return new Dummy(spawn); }
        };
        var router=new ObjectCallbackRouter(registry);
        ObjectSpawn spawn=hostSpawn();
        assertThrows(IllegalStateException.class,()->router.construct(spawn,()->registry.create(spawn)));
    }

    @Test void goalRewindPreservesTaggedPlacementAndPreventsDuplicateNativeFinishGraphs() {
        TestEnvironment.resetAll();
        var player=new TestablePlayableSprite("sonic",(short)0,(short)0);
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,32);
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player,64);
        player.setAirForTest(false);
        var camera=new com.openggf.camera.Camera();
        ObjectManager[] ref={null};
        var bound=new StubObjectServices() {
            @Override public ObjectManager objectManager() { return ref[0]; }
            @Override public com.openggf.camera.Camera camera() { return camera; }
            @Override public com.openggf.configuration.SonicConfigurationService configuration() { return com.openggf.game.GameServices.configuration(); }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(()->player,List::of); }
        };
        var registry=new ModDecoratedObjectRegistry(new Sonic3kObjectRegistry(),new ModObjectKeyRegistry(List.of()));
        var objects=new ObjectManager(List.of(hostSpawn()),registry,0,null,null,null,camera,bound);
        ref[0]=objects;
        objects.setRewindClassResolver(new ModClassResolver(org.mockito.Mockito.mock(ModRuntime.class),getClass().getClassLoader()));
        objects.update(0,player,List.of(),1);
        var before=objects.rewindSnapshottable().capture();
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,64);
        objects.update(0,player,List.of(),2);
        assertEquals(1,objects.activeObjectsOfType(S3kSignpostInstance.class).size());
        var after=objects.rewindSnapshottable().capture();
        assertTrue(after.dynamicObjects().stream().allMatch(entry->entry.ownerModId()==null));
        objects.rewindSnapshottable().restore(before);
        assertEquals(0,objects.activeObjectsOfType(S3kSignpostInstance.class).size());
        objects.update(0,player,List.of(),3);
        assertEquals(1,objects.activeObjectsOfType(S3kSignpostInstance.class).size());
        objects.rewindSnapshottable().restore(after);
        objects.update(0,player,List.of(),4);
        assertEquals(1,objects.activeObjectsOfType(S3kSignpostInstance.class).size());
        assertEquals(hostSpawn(),objects.activeObjectsOfType(S3kAuthoredGoalInstance.class).getFirst().getSpawn());
    }

    private static ObjectSpawn hostSpawn() { return new CommonObjectPlacementEncoding()
            .createKeyed(64,64,"openggf:authored-goal",0,0,true,9); }
    private static final class ObservedRegistry implements ObjectRegistry,HostObjectKeyRegistry,
            java.util.function.Supplier<Object> {
        private final ModDecoratedObjectRegistry delegate;
        private final ObjectCallbackBoundary boundary;
        ObservedRegistry(ModDecoratedObjectRegistry delegate,ObjectCallbackBoundary boundary) {
            this.delegate=delegate; this.boundary=boundary;
        }
        @Override public ObjectInstance create(ObjectSpawn spawn) { return delegate.create(spawn); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { delegate.reportCoverage(spawns); }
        @Override public String getPrimaryName(int id) { return delegate.getPrimaryName(id); }
        @Override public boolean hasHostObjectKey(String key) { return delegate.hasHostObjectKey(key); }
        @Override public boolean ownsHostObject(ObjectSpawn spawn,ObjectInstance instance) {
            return delegate.ownsHostObject(spawn,instance);
        }
        @Override public Object get() {
            java.util.function.Supplier<String> noCreatorScope=()->null;
            return java.util.Map.entry(boundary,noCreatorScope);
        }
    }
    private static final class Dummy extends AbstractObjectInstance {
        Dummy(ObjectSpawn spawn) { super(spawn,"dummy"); }
        @Override public void update(int frame,com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }
}
