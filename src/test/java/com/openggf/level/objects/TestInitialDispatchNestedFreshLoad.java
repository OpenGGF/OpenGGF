package com.openggf.level.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.snapshot.SolidExecutionSnapshot;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestInitialDispatchNestedFreshLoad {
    private SharedLevel shared;
    private Object oldSkipIntros;

    @BeforeEach
    void setup() {
        TestEnvironment.resetAll();
        var config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @AfterEach
    void cleanup() {
        if (shared != null) shared.dispose();
        TestEnvironment.resetAll();
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualFreshHczLoadKeepsItsCompletedOwnerWhenOldInitialDispatchUnwinds(boolean nested) throws Exception {
        shared = SharedLevel.load(SonicGame.SONIC_3K, 0, 1);
        var level = GameServices.level();
        var original = level.getObjectManager();
        if (level.hasPendingInitialProcessSpritesPass()) level.consumePendingInitialProcessSpritesPass();
        var probe = original.createDynamicObject(() -> new Probe(null));
        assertNotNull(probe);
        ObjectManager[] successor = new ObjectManager[1];
        Probe[] witness = new Probe[1];
        SolidExecutionSnapshot[] standing = new SolidExecutionSnapshot[1];
        Runnable load = () -> {
            try { level.loadZoneAndActForFreshRuntime(1, 0); }
            catch (java.io.IOException failure) { throw new AssertionError("genuine HCZ load must complete", failure); }
            assertEquals(1, level.getCurrentZone());
            assertEquals(0, level.getCurrentAct());
            successor[0] = level.getObjectManager();
            assertNotSame(original, successor[0]);
            assertTrue(level.consumePendingInitialProcessSpritesPass(), "B must complete its actual initial pass");
            assertFalse(level.hasPendingInitialProcessSpritesPass());
            assertFalse(successor[0].hasActiveInitialProcessSpritesDispatch());
            var player = GameServices.sprites().getMainPlayable();
            witness[0] = successor[0].createDynamicObject(() -> new Probe(null));
            assertNotNull(witness[0]);
            witness[0].marker = 0x52;
            var solids = (DefaultSolidExecutionRegistry) com.openggf.game.session.SessionManager.getCurrentGameplayMode().getSolidExecutionRegistry();
            solids.beginFrame(successor[0].getFrameCounter(), List.of(player));
            var contact = new PlayerSolidContactResult(ContactKind.TOP, true, true, false, false, null, null, 0);
            solids.publishCheckpoint(new SolidCheckpointBatch(witness[0], Map.of(player, contact)));
            solids.finishFrame();
            standing[0] = solids.capture();
            assertFalse(standing[0].previousStanding().isEmpty());
            com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry().capture();
        };
        if (nested) {
            probe.action = load;
            assertThrows(ObjectCallbackAbortException.class, () -> {
                try (var scope = original.beginInitialProcessSprites(GameServices.camera().getX(),
                        GameServices.sprites().getMainPlayable(), GameServices.sprites().getSidekicks())) {
                    original.loadInitialDynamicSlots(scope);
                    original.processInitialDynamicSlots(scope);
                    original.finishInitialProcessSprites(scope);
                }
            });
        } else {
            load.run();
        }
        assertNotNull(successor[0]);
        assertSame(successor[0], level.getObjectManager());
        assertEquals(0x52, witness[0].marker);
        assertTrue(successor[0].getActiveObjects().contains(witness[0]));
        assertEquals(standing[0], ((DefaultSolidExecutionRegistry) com.openggf.game.session.SessionManager.getCurrentGameplayMode().getSolidExecutionRegistry()).capture());
        com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry().capture();
        assertFalse(original.hasActiveInitialProcessSpritesDispatch());
    }

    private static final class Probe extends AbstractObjectInstance implements RewindRecreatable {
        int marker;
        @RewindTransient(reason = "test callback is outside captured gameplay state")
        Runnable action = () -> { };
        Probe(ObjectSpawn spawn) { super(spawn, "nested-load-probe"); }
        @Override public void update(int counter, PlayableEntity player) { action.run(); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean isPersistent() { return true; }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) { return new Probe(context.spawn()); }
    }
}
