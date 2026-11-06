package com.openggf.game.sonic3k.objects;

import com.openggf.game.*;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.LevelFrameTestStep;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.EngineTiming;
import com.openggf.level.LevelManager;

/** 4515S/8501S missile alignment: actual stock launch reaches homing; target
 * position and V-int phase distinguish native steering. No seeded flight state. */
class TestTasS3kDdzMissileSteering {
    /** Phase prerequisite only: a native launcher reaches homing in a component
     * world, then real Pause_Loop admission shifts both homing and signpost RNG.
     * This does not claim the published DDZ route or a missile/boss impact. */
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    @ParameterizedTest @ValueSource(ints = {0, 3})
    void nativePauseShiftsReachedHomingAndSignpostRngTogether(int pausedTicks) throws Exception {
        var player = new TestablePlayableSprite("sonic", (short) 0x600, (short) 0x100);
        var sprites = mock(SpriteManager.class); when(sprites.getMainPlayable()).thenReturn(player);
        var camera = GameServices.camera(); camera.setY((short) 0x100);
        var state = GameServices.gameState();
        var rng = new GameRng(GameRng.Flavour.S3K, 0x1234L);
        var services = new TestObjectServices().withCamera(camera).withGameState(state)
                .withGameModule(GameServices.module()).withSpriteManager(sprites).withRng(rng);
        var manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null, null, camera, services);
        services.withDirectObjectManager(manager); manager.reset(0);
        EngineTiming.vIntRunCounter().bindObjectClock(() -> Integer.toUnsignedLong(manager.getVblaCounter()));
        var boss = manager.createDynamicObject(() -> new DdzEndBossObjectInstance(new ObjectSpawn(0x200, 0x80, 0xB6, 0, 0, false, 0)));
        var launcher = manager.createDynamicObject(() -> new DdzEndBossLauncherObjectInstance(boss));
        // The parked root supplies the launcher's native origin. This component
        // world intentionally has no DDZ flight controller or boss arena state.
        launcher.update(manager.getVblaCounter(), player);
        var missile = manager.activeObjectsOfType(DdzMissileObjectInstance.class).getFirst();
        for (int row = 0; ; row++) {
            assertTrue(row < 140, "stock launch must naturally reach homing");
            manager.update(0, player, List.of(), row, false);
            if (number(missile, "state") == DdzMissileObjectInstance.STATE_HOME
                    && (manager.getVblaCounter() & 3) == 3) break;
        }
        player.setCentreX((short) missile.getX()); player.setCentreY((short) (missile.getY() + 0x100));
        int angle = missile.angleForExhaust();
        int x = number(missile, "xPos"), y = number(missile, "yPos");
        var sign = manager.createDynamicObject(() -> new S3kSignpostInstance(0x200, 0));
        sign.update(manager.getVblaCounter(), player); // native falling-sign init
        rng.setSeed(0x1234L);
        var level = mock(LevelManager.class); when(level.getObjectManager()).thenReturn(manager);
        var original = LevelFrameContext.from(SessionManager.getCurrentGameplayMode());
        var context = new LevelFrameContext(original.gameModule(), original.gameRules(), null, null,
                sprites, state, original.timerManager(), original.paletteOwnershipRegistry(), original.kosinskiModuleQueue(),
                original.hardwareTiming(), original.hardwareTimingBoundaryObserver(), original.runtimeArtCoordinator(), level);
        var registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable()); registry.register(state); registry.register(rng);
        com.openggf.game.internal.NativePauseRewindAccess.register(registry, state);
        registry.register(EngineTiming.vIntRunCounter());
        var before = registry.capture();
        int clock = manager.getVblaCounter();
        com.openggf.game.rewind.CompositeSnapshot expected = null;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) registry.restore(before);
            for (int tick = 0; tick < pausedTicks; tick++) {
                assertEquals(LevelFrameResult.PAUSED, LevelFrameTestStep.executeWithPause(context, level, camera,
                        () -> fail("paused component world cannot run player physics"), tick == 0, LevelFrameStep.DIRECT_WRAPPER));
                assertEquals(clock + tick + 1, manager.getVblaCounter());
                var liveMissile = manager.activeObjectsOfType(DdzMissileObjectInstance.class).getFirst();
                assertEquals(x, number(liveMissile, "xPos")); assertEquals(y, number(liveMissile, "yPos"));
                assertEquals(angle, liveMissile.angleForExhaust()); assertEquals(0x1234L, rng.getSeed());
            }
            if (pausedTicks != 0) assertFalse(state.applyPauseToggle(true), "fresh Start releases the component pause");
            manager.update(0, player, List.of(), 0, false);
            var liveMissile = manager.activeObjectsOfType(DdzMissileObjectInstance.class).getFirst();
            assertEquals(clock + pausedTicks + 1, manager.getVblaCounter());
            assertEquals(pausedTicks == 0 ? angle : (angle + 2) & 0xFF, liveMissile.angleForExhaust(),
                    "sub_82A82 skips only the unpaused modulo-four control");
            assertNotEquals(x, number(liveMissile, "xPos"), "movement still runs in both admitted phases");
            assertEquals(pausedTicks == 0 ? 0xEA56EA54L : 0x1234L, rng.getSeed(),
                    "the same pause moves the falling signpost off its sparkle RNG phase");
            if (replay == 0) expected = registry.capture();
            else {
                var actual = registry.capture();
                for (String key : expected.entries().keySet()) {
                    var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
                    assertTrue(diff.isEmpty(), () -> key + ": " + diff);
                }
            }
        }
    }

    @AfterEach void cleanup() { SessionManager.clear(); }
    @ParameterizedTest @ValueSource(ints={4,5})
    void globalClockPhaseChangesHomingButNotTheMovementTick(int vIntPhase) throws Exception {
        try (var reel = com.openggf.tests.reel.S3kTestReelPresentation.open()) {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        var p=new TestablePlayableSprite("sonic",(short)0x600,(short)0x100);
        var sprites=mock(SpriteManager.class);when(sprites.getMainPlayable()).thenReturn(p);
        var camera=GameServices.camera();var services=new TestObjectServices().withCamera(camera)
                .withGameState(new GameStateManager()).withGameModule(new Sonic3kGameModule()).withSpriteManager(sprites);
        services.zoneRuntimeRegistry().install(new DdzZoneRuntimeState(0,PlayerCharacter.SONIC_ALONE));
        var manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,null,camera,services);
        services.withDirectObjectManager(manager);manager.reset(0);
        var boss=manager.createDynamicObject(()->new DdzEndBossObjectInstance(new ObjectSpawn(0x200,0x80,0xB6,0,0,false,0)));
        var launcher=manager.createDynamicObject(()->new DdzEndBossLauncherObjectInstance(boss));
        launcher.update(0,p);
        var missiles=manager.activeObjectsOfType(DdzMissileObjectInstance.class);
        assertEquals(3,missiles.size());var missile=missiles.getFirst();
        assertEquals(0,number(missile,"index")); reel.afterObjects(List.of(missile),12,0,0x238,0x90,Sonic3kObjectArtKeys.DDZ_MISC);
        for(int i=0;i<100&&number(missile,"state")!=DdzMissileObjectInstance.STATE_HOME;i++) { missile.update(i+1,p); reel.afterObjects(List.of(missile),12,0,0x238,0x90,Sonic3kObjectArtKeys.DDZ_MISC); }
        assertEquals(DdzMissileObjectInstance.STATE_HOME,number(missile,"state"));
        assertEquals(0xC0,missile.angleForExhaust(),"first missile completes its native half-turn");
        p.setCentreX((short)missile.getX());p.setCentreY((short)(missile.getY()+0x100));
        int oldX=number(missile,"xPos"),oldY=number(missile,"yPos");
        missile.update(vIntPhase,p); reel.afterObjects(List.of(missile),12,0,0x238,0x90,Sonic3kObjectArtKeys.DDZ_MISC);
        assertEquals(vIntPhase==4?0xC0:0xC2,missile.angleForExhaust(),"sub_82A82 skips turns at V-int &3 ==0");
        assertNotEquals(oldX,number(missile,"xPos"),"both phases still move");
        if(vIntPhase==4) assertEquals(oldY,number(missile,"yPos"));
        else assertTrue(number(missile,"yPos")>oldY,"two-angle turn curves toward Player 1");
        assertFalse(missile.isDestroyed());
            }
    }
    private static int number(Object owner,String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return ((Number)field.get(owner)).intValue();
    }
}
