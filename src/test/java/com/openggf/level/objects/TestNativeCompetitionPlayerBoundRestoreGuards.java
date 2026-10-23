package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeCompetitionPlayerBoundRestoreGuards {
    @BeforeEach void setUp() { TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2); }
    @AfterEach void tearDown() { TestEnvironment.resetAll(); }

    @Test void isolatedOrdinaryDynamicRestoreAcceptsNullInjectedServices() {
        var objects = new ObjectManager(List.of(), null, 0, null, null, null, null, null);
        var source = new OrdinaryDynamic();
        source.word = 0x1234;
        objects.addDynamicObject(source);
        var adapter = objects.rewindSnapshottable();
        var saved = adapter.capture();
        adapter.restore(saved);
        assertEquals(0x1234, objects.activeObjectsOfType(OrdinaryDynamic.class).getFirst().word);
        assertTrue(RewindSnapshotDiff.diffKey("object-manager", saved, adapter.capture()).isEmpty());
    }

    @Test void nullServicesLeaveCapturedHumanOwnerUntouched() {
        var human = new Sonic("sonic", (short) 0, (short) 0);
        var entry = new DynamicObjectEntry("shield", null, 20, null, human);
        assertSame(entry, NativeCompetitionPlayerBoundRestore.rebind(null, null, entry));
    }

    @Test void ordinaryDynamicAndCpuOwnerNeverResolveNativeWorldServices() {
        var services = mock(ObjectServices.class);
        var ordinary = new DynamicObjectEntry("ordinary", null, 20, null);
        assertSame(ordinary, NativeCompetitionPlayerBoundRestore.rebind(null, services, ordinary));
        var cpu = new Sonic("sonic", (short) 0, (short) 0);
        cpu.setCpuControlled(true);
        var entry = new DynamicObjectEntry("shield", null, 20, null, cpu);
        assertSame(entry, NativeCompetitionPlayerBoundRestore.rebind(null, services, entry));
        verifyNoInteractions(services);
    }

    public static final class OrdinaryDynamic extends AbstractObjectInstance implements RewindRecreatable {
        int word;
        public OrdinaryDynamic() { super(null, "ordinary-null-services"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new OrdinaryDynamic();
        }
    }
}
