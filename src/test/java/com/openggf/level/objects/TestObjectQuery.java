package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestObjectQuery {
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() { SessionManager.clear(); }

    public static final class Probe extends AbstractObjectInstance implements ModRewindRecreatable {
        int counter;
        public Probe(ObjectSpawn spawn) { super(spawn,"Query probe"); }
        public void update(int count,PlayableEntity player) { counter++; }
        public void appendRenderCommands(List<GLCommand> commands) { }
        public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
            assertNotNull(context.objects());
            return new Probe(context.spawn());
        }
    }

    @Test void stableQueriesFollowRecreatedInstancesAndRetirePriorObjects() {
        ObjectManager[] holder=new ObjectManager[1];
        var services=new StubObjectServices() {
            public ObjectManager objectManager() { return holder[0]; }
        };
        ObjectRegistry registry=new ObjectRegistry() {
            public ObjectInstance create(ObjectSpawn spawn) { return new Probe(spawn); }
            public void reportCoverage(List<ObjectSpawn> spawns) { }
            public String getPrimaryName(int id) { return "Query probe"; }
        };
        var manager=new ObjectManager(List.of(),registry,0,null,null,null,null,services);
        holder[0]=manager;
        Probe first=manager.createDynamicObject(()->new Probe(new ObjectSpawn(40,80,0,0,0,false,80)));
        Probe second=manager.createDynamicObject(()->new Probe(new ObjectSpawn(80,80,0,0,0,false,80)));
        var query=services.objectQuery();
        assertEquals(List.of(first,second),query.activeObjectsOfType(Probe.class));
        assertThrows(UnsupportedOperationException.class,()->query.activeObjectsOfType(Probe.class).clear());
        var identity=query.identityOf(first).orElseThrow();
        var secondIdentity=query.identityOf(second).orElseThrow();
        first.counter=7;
        var snapshot=manager.rewindSnapshottable().capture();
        first.counter=99; manager.setRewindInPlaceRestoreEnabledForTest(false);
        manager.rewindSnapshottable().restore(snapshot);
        assertTrue(query.identityOf(first).isEmpty());
        Probe recreated=(Probe)query.resolve(identity).orElseThrow();
        assertNotSame(first,recreated); assertEquals(7,recreated.counter);
        assertEquals(List.of(identity,secondIdentity),query.activeObjectsOfType(Probe.class).stream()
                .map(object->query.identityOf(object).orElseThrow()).toList());
        manager.reset(0);
        assertTrue(query.activeObjectsOfType(Probe.class).isEmpty()); assertTrue(query.resolve(identity).isEmpty());
    }

    @Test void creatorReconstructionPreservesServicesAndParentQueriesWithoutRestoreBookkeeping() {
        var parent=new Probe(new ObjectSpawn(10,20,0,0,0,false,20));
        var services=new StubObjectServices();
        var context=new ObjectReconstructionContext(new RewindRecreateContext(parent.getSpawn(),null,services));
        assertSame(parent.getSpawn(),context.spawn()); assertSame(services,context.services());
        assertTrue(context.objects().activeObjectsOfType(Probe.class).isEmpty());
        assertTrue(context.payload(PerObjectRewindSnapshot.ObjectSubclassRewindExtra.class).isEmpty());
        assertDoesNotThrow(()->context.enqueuePendingPlayerBoundEntry(Probe.class));
    }
}
