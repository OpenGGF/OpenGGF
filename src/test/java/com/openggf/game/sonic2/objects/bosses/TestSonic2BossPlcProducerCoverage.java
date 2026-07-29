package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executes each ordinary boss's actual killing-hit hook, not a PLC facade. */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(SingletonResetExtension.class)
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2BossPlcProducerCoverage {
    @BeforeEach
    void setUp() throws Exception {
        GameServices.module().createGame(TestEnvironment.currentRom());
        GameplayModeContext gameplay = TestEnvironment.activeGameplayMode();
        GraphicsManager.getInstance().initHeadless();
        Sonic2ObjectArtProvider provider =
                (Sonic2ObjectArtProvider) GameServices.module().getObjectArtProvider();
        provider.loadArtForZone(Sonic2ZoneConstants.ROM_ZONE_EHZ);
        LevelManager manager = gameplay.getLevelManager();
        Field render = LevelManager.class.getDeclaredField("objectRenderManager");
        render.setAccessible(true);
        render.set(manager, new ObjectRenderManager(provider));
        Field level = LevelManager.class.getDeclaredField("level");
        level.setAccessible(true);
        Level liveLevel = org.mockito.Mockito.mock(Level.class);
        level.set(manager, liveLevel);
        gameplay.getWorldSession().setCurrentLevel(liveLevel);
        manager.refreshObjectArtPatterns();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bosses")
    void killingHitOwnerAppendsCapsulePlc(BossRoute route) {
        TestObjectServices services = new TestObjectServices()
                .withGameModule(GameServices.module())
                .withLevelManager(GameServices.level());
        ObjectConstructionContext.with(services, () -> route.invoke().accept(services));
        Sonic2PlcService queue = GameServices.module().getGameService(Sonic2PlcService.class);
        assertTrue(queue.isBusy(), route.name() + " must append the capsule PLC from onDefeatStarted");
    }

    private static Stream<BossRoute> bosses() {
        return Stream.of(
                boss("EHZ", s -> { Sonic2EHZBossInstance b = new Sonic2EHZBossInstance(spawn()); b.setServices(s); b.onDefeatStarted(); }),
                boss("HTZ", s -> { Sonic2HTZBossInstance b = new Sonic2HTZBossInstance(spawn()); b.setServices(s); b.onDefeatStarted(); }),
                boss("ARZ", s -> { Sonic2ARZBossInstance b = new Sonic2ARZBossInstance(spawn()); b.setServices(s); b.onDefeatStarted(); }),
                boss("MCZ", s -> { Sonic2MCZBossInstance b = new Sonic2MCZBossInstance(spawn()); b.setServices(s); b.onDefeatStarted(); }),
                boss("CNZ", s -> { Sonic2CNZBossInstance b = new Sonic2CNZBossInstance(spawn()); b.setServices(s); b.onDefeatStarted(); }),
                boss("CPZ", s -> { Sonic2CPZBossInstance b = new Sonic2CPZBossInstance(spawn()); b.setServices(s); b.onDefeatStarted(); }),
                boss("MTZ", TestSonic2BossPlcProducerCoverage::startMtzDefeat),
                boss("OOZ", s -> { Sonic2OOZBossInstance b = new Sonic2OOZBossInstance(spawn()); b.setServices(s); b.onDefeatStarted(); }));
    }

    private static BossRoute boss(String name, Consumer<TestObjectServices> invoke) {
        return new BossRoute(name, invoke);
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0, 0, 0, 0, 0, false, 0);
    }

    private static void startMtzDefeat(TestObjectServices services) {
        Sonic2MTZBossInstance boss = new Sonic2MTZBossInstance(spawn());
        boss.setServices(services);
        boss.onDefeatStarted();
        try {
            Method apply = Sonic2MTZBossInstance.class
                    .getDeclaredMethod("applyPendingHitReactionAfterMove");
            apply.setAccessible(true);
            apply.invoke(boss);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("MTZ's production post-move defeat boundary is unavailable", e);
        }
    }

    private record BossRoute(String name, Consumer<TestObjectServices> invoke) {
    }
}
