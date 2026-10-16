package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.objects.badniks.MantisBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** Native preset boundaries; these are object checks, not full campaign-route certification. */
class TestWestCampaignNativeRecipes {
    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(1000, 500, id, subtype, 0, true, 500, 7);
    }

    private static TestObjectServices services(int zone) {
        var config = SonicConfigurationService.createStandalone();
        return new TestObjectServices() { @Override public int romZoneId() { return zone; } }
                .withConfiguration(config).withCamera(new Camera(config));
    }

    @Test void brittleStandingSupportWarnsBeforeTheNativeWaveButTriggerAndStompStayStock() throws Exception {
        for (int zone : new int[]{1, 2}) {
            var svc = services(zone);
            var stock = ObjectConstructionContext.construct(svc, () -> new CollapsingBridgeObjectInstance(spawn(15, 0x12)));
            var brittle = ObjectConstructionContext.construct(svc, () -> new CollapsingBridgeObjectInstance(spawn(15, 0x12), true));
            assertEquals(40, field(stock, "collapseTimer"));
            assertEquals(16, field(brittle, "collapseTimer"));
            assertEquals(stock.getSolidParams(), brittle.getSolidParams());
            set(brittle, "state", 1);
            for (int tick = 0; tick < 15; tick++) brittle.update(tick, null);
            assertEquals(1, field(brittle, "state"));
            assertEquals(1, field(brittle, "collapseTimer"));
            assertEquals(1000, brittle.getX(), "visible warning must not displace the collision center");
        }
        var trigger = ObjectConstructionContext.construct(services(1), () -> new CollapsingBridgeObjectInstance(spawn(15, 0x81), true));
        var stomp = ObjectConstructionContext.construct(services(2), () -> new CollapsingBridgeObjectInstance(spawn(15, 0x22), true));
        assertEquals(8, field(trigger, "collapseTimer"));
        assertEquals(false, field(trigger, "brittleSupport"));
        assertTrue(stomp.isMgzStompMode());
        assertEquals(40, field(stomp, "collapseTimer"));
        assertEquals(false, field(stomp, "brittleSupport"));
    }

    @Test void mantisAndSpikerGuardAnEarlierLandingWithoutChangingTheirNativeMotion() throws Exception {
        var player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 920);
        for (boolean remix : new boolean[]{false, true}) {
            var mantis = new MantisBadnikInstance(spawn(0x9D, 0), remix);
            mantis.setServices(services(2));
            invoke(mantis, "updateWait", new Class<?>[]{AbstractPlayableSprite.class}, player);
            assertEquals(remix ? "PREPARE" : "WAIT", field(mantis, "state").toString());
            assertEquals(0, field(mantis, "yVelocity"));
            if (remix) {
                invoke(mantis, "beginLaunch", new Class<?>[0]);
                assertEquals(-0x600, field(mantis, "yVelocity"));
            }
            var spiker = new SpikerBadnikInstance(spawn(0x9C, 0), remix);
            spiker.setServices(services(2));
            invoke(spiker, "updateDetect", new Class<?>[]{AbstractPlayableSprite.class}, player);
            assertEquals(remix ? "RISE" : "DETECT", field(spiker, "state").toString());
            if (remix) assertEquals(7, field(spiker, "stateTimer"));
        }
    }

    @Test void turboSpikerCommitsAtTheLongerAlertRangeButKeepsItsNativeFacingGate() throws Exception {
        var player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 890);
        for (boolean remix : new boolean[]{false, true}) {
            var enemy = new TurboSpikerBadnikInstance(spawn(0x96, 0x20), remix);
            enemy.setServices(services(1));
            Object target = invoke(enemy, "findNearestTarget", new Class<?>[]{AbstractPlayableSprite.class}, player);
            assertEquals(remix, invoke(enemy, "shouldLaunchShell", new Class<?>[]{target.getClass()}, target));
            player.setCentreX((short) 1110);
            target = invoke(enemy, "findNearestTarget", new Class<?>[]{AbstractPlayableSprite.class}, player);
            assertEquals(false, invoke(enemy, "shouldLaunchShell", new Class<?>[]{target.getClass()}, target));
            player.setCentreX((short) 890);
        }
    }

    @Test void hydrocityExtraStrafeIsSelectedOnceAndCannotReplenishItsOwnBudget() throws Exception {
        for (boolean remix : new boolean[]{false, true}) {
            var boss = new HczMinibossInstance(spawn(0x99, 0), remix);
            boss.setServices(services(1));
            boss.getState().hitCount = 3;
            invoke(boss, "selectAttackPattern", new Class<?>[0]);
            int initial = (int) field(boss, "passCounter");
            assertEquals(remix ? 2 : 1, initial);
            invoke(boss, "finishStrafe", new Class<?>[0]);
            assertEquals(initial - 1, field(boss, "passCounter"));
            assertEquals(1, field(boss, "attackPatternIndex"));
            boss.getState().hitCount = 4;
            set(boss, "attackPatternIndex", 0);
            invoke(boss, "selectAttackPattern", new Class<?>[0]);
            assertEquals(1, field(boss, "passCounter"), "healthy cycles remain the native pattern");
        }
    }

    @Test void hydrocityBladePressureAlternatesWithTheNativeRecoveryInterval() throws Exception {
        var boss = endBoss(true);
        boss.setServices(services(1));
        boss.getState().hitCount = 4;
        set(boss, "attackPassCounter", 7);
        invoke(boss, "onAttackPassComplete", new Class<?>[0]);
        assertEquals(0x3F, field(boss, "waitTimer"));
        invoke(boss, "onAttackPassComplete", new Class<?>[0]);
        assertEquals(0x5F, field(boss, "waitTimer"));
        assertEquals(5, field(boss, "attackPassCounter"));
        assertEquals(true, field(boss, "bladeFireSignal"));
        var stock = endBoss(false);
        stock.setServices(services(1)); stock.getState().hitCount = 4;
        set(stock, "attackPassCounter", 7);
        invoke(stock, "onAttackPassComplete", new Class<?>[0]);
        assertEquals(0x5F, field(stock, "waitTimer"));
    }

    @Test void marbleGardenZoomCueTightensAlternatelyAndKeepsTheNativeAttackableMove() throws Exception {
        var boss = new MgzEndBossInstance(spawn(0xA1, 0), true);
        boss.setServices(services(2)); boss.getState().hitCount = 4;
        invoke(boss, "enterAirAttackWait", new Class<?>[0]);
        assertEquals(0x5F, field(boss, "waitTimer"));
        assertEquals(true, field(boss, "airZoomCueActive"));
        invoke(boss, "advanceAirAttackWait", new Class<?>[0]);
        assertEquals(0x20, field(boss, "waitTimer"));
        invoke(boss, "advanceAirAttackWait", new Class<?>[0]);
        assertEquals(0xFF, field(boss, "waitTimer"));
        assertEquals(false, field(boss, "airZoomCueActive"));
        invoke(boss, "enterAirAttackWait", new Class<?>[0]);
        assertEquals(0x9F, field(boss, "waitTimer"));
        assertEquals(4, boss.getState().hitCount);
    }

    @Test void knucklesSecondDropKeepsItsMotionAndOnlyChangesTheInterveningPause() throws Exception {
        for (boolean remix : new boolean[]{false, true}) {
            var boss = new MgzEndBossKnuxInstance(spawn(0xA2, 0), remix);
            boss.setServices(services(2)); boss.getState().hitCount = 4;
            set(boss, "nativeRoutine", 8); set(boss, "nativeTimer", 0);
            // Isolated committed-fight cadence component, not admission evidence.
            invoke(boss, "moveAndWait", new Class<?>[]{int.class,int.class,int.class},
                    0x28, 0x0A, remix ? 0x1F : 0x3F);
            assertEquals(0x0A, boss.getNativeRoutineForTesting());
            assertEquals(remix ? 0x1F : 0x3F, boss.getNativeTimerForTesting());
            assertEquals(4, boss.getState().hitCount);
        }
    }

    @Test void knucklesHydrocityBladeIntervalHasItsOwnLargerRecoveryBudget() throws Exception {
        var svc = services(1);
        svc.configuration().setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        var boss = ObjectConstructionContext.construct(svc, () -> new HczEndBossInstance(spawn(0x9A, 0), true));
        boss.setServices(svc); boss.getState().hitCount = 4;
        set(boss, "attackPassCounter", 7);
        invoke(boss, "onAttackPassComplete", new Class<?>[0]);
        assertEquals(0x4F, field(boss, "waitTimer"));
        invoke(boss, "onAttackPassComplete", new Class<?>[0]);
        assertEquals(0x5F, field(boss, "waitTimer"));
    }

    @Test void tunnelbotKeepsTheFullDropRumbleAndOnlyTightensTheDamagedCeilingPause() throws Exception {
        for (boolean remix : new boolean[]{false, true}) {
            var boss = new MgzMinibossInstance(spawn(0x9F, 0), remix);
            boss.setServices(services(2)); boss.getState().hitCount = 4;
            invoke(boss, "enterCeilingShake", new Class<?>[0]);
            assertEquals(0x7F, field(boss, "routineTimer"));
            set(boss, "routineTimer", 0);
            invoke(boss, "updateCeilingShake", new Class<?>[]{int.class}, 1);
            assertEquals(remix ? 0x1F : 0x3F, field(boss, "routineTimer"));
            invoke(boss, "updateWait", new Class<?>[]{com.openggf.game.PlayableEntity.class}, (Object) null);
            set(boss, "routineTimer", 0);
            invoke(boss, "updateWait", new Class<?>[]{com.openggf.game.PlayableEntity.class}, (Object) null);
            assertEquals(0x7F, field(boss, "routineTimer"), "the second full rumble remains visible");
        }
    }

    @Test void reconstructionRestoresPresetsAndTheCommittedTimers() throws Exception {
        AbstractObjectInstance[] objects = {
                new MantisBadnikInstance(spawn(0x9D, 0), true),
                new SpikerBadnikInstance(spawn(0x9C, 0), true),
                new TurboSpikerBadnikInstance(spawn(0x96, 0), true),
                new HczMinibossInstance(spawn(0x99, 0), true),
                endBoss(true),
                new MgzEndBossInstance(spawn(0xA1, 0), true),
                new MgzEndBossKnuxInstance(spawn(0xA2, 0), true)
        };
        for (var source : objects) {
            source.setServices(services(2));
            var context = com.openggf.game.rewind.schema.RewindCaptureContext.withIdentityTable(
                    new com.openggf.game.rewind.identity.RewindIdentityTable());
            var snapshot = source.captureRewindState(context);
            var recreateServices = services(2);
            var recreated = ObjectConstructionContext.construct(recreateServices, () ->
                    ((com.openggf.level.objects.RewindRecreatable) source).recreateForRewind(
                            new RewindRecreateContext(source.getSpawn(), snapshot, recreateServices)));
            recreated.setServices(services(2)); recreated.restoreRewindState(snapshot, context);
            String flag = source instanceof MantisBadnikInstance || source instanceof SpikerBadnikInstance
                    ? "landingGuard" : source instanceof TurboSpikerBadnikInstance ? "alert" : "counterCadence";
            assertEquals(true, field(recreated, flag), source.getClass().getSimpleName());
        }
    }

    private static HczEndBossInstance endBoss(boolean remix) {
        var svc = services(1);
        return ObjectConstructionContext.construct(svc, () -> new HczEndBossInstance(spawn(0x9A, 0), remix));
    }

    private static Field declared(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object field(Object target, String name) throws Exception { return declared(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { declared(target, name).set(target, value); }
    private static Object invoke(Object target, String name, Class<?>[] signature, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Method m = type.getDeclaredMethod(name, signature); m.setAccessible(true); return m.invoke(target, args); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}
