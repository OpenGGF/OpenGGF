package com.openggf.sprites.playable;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.internal.BadnikChainPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.physics.*;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Production native reset owners; direct bit writes deliberately do not own the S2 word. */
class TestSonic2ChainResetOwners {
    Sonic2GameModule host;
    BadnikChainPolicy chain;
    Sonic sonic;
    Tails tails;
    @BeforeEach void setup() {
        host = new Sonic2GameModule();
        GameModuleRegistry.setCurrent(host);
        TestEnvironment.configureGameModuleFixture(host);
        chain = host.getGameService(BadnikChainPolicy.class);
        sonic = new Sonic("sonic",(short)128,(short)200);
        tails = new Tails("tails",(short)160,(short)200);
        tails.setCpuControlled(true);
    }
    @AfterEach void cleanup() { SessionManager.clear(); GameModuleRegistry.reset(); }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void terrainFloorResetClearsTheSharedWordIncludingCpuTails(boolean cpu) throws Exception {
        var actor=cpu?tails:sonic;
        assertEquals(100,sonic.incrementBadnikChain());
        actor.setAir(true);
        var method=PlayableSpriteMovement.class.getDeclaredMethod("resetOnFloor");
        method.setAccessible(true);
        method.invoke(actor.getMovementManager());
        assertFalse(actor.getAir());
        assertEquals(0,chain.word());
        assertEquals(100,sonic.incrementBadnikChain());
        assertEquals(200,tails.incrementBadnikChain());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void angledCeilingAirCollisionResetsBothActorsSharedWordButFlatBumpDoesNot(boolean cpu) throws Exception {
        var actor=cpu?tails:sonic;
        sonic.incrementBadnikChain(); tails.incrementBadnikChain();
        actor.setAir(true); actor.setYSpeed((short)-0x200);
        var collisions=new CollisionSystem(new TerrainCollisionManager());
        var ceiling=CollisionSystem.class.getDeclaredMethod("doCeilingCollision",AbstractPlayableSprite.class,SensorResult[].class);
        ceiling.setAccessible(true);
        ceiling.invoke(collisions,actor,new SensorResult[]{new SensorResult((byte)0x80,(byte)-1,14,Direction.UP),null});
        assertTrue(actor.getAir());
        assertEquals(4,chain.word(),"flat ceiling bumps never call ResetOnFloor");
        ceiling.invoke(collisions,actor,new SensorResult[]{new SensorResult((byte)0x40,(byte)-1,14,Direction.UP),null});
        assertFalse(actor.getAir());
        assertEquals(0,chain.word(),"loc_1B02C / loc_1CADC call ResetOnFloor");
        assertEquals(100,sonic.incrementBadnikChain());
    }

    @Test void rawAirborneBitWritesAndRewindHydrationDoNotSynthesizeAReset() {
        sonic.incrementBadnikChain();
        var snapshot=sonic.captureRewindState();
        for(var actor:new AbstractPlayableSprite[]{sonic,tails}) {
            actor.setAir(true); actor.setAir(false);
            actor.setAir(true); actor.clearAirForNativeControlRestore();
            actor.setAir(true); actor.setAirAfterObjectHurtLanding();
        }
        assertEquals(2,chain.word());
        sonic.restoreRewindState(snapshot);
        assertEquals(2,chain.word());
        assertEquals(200,tails.incrementBadnikChain());
    }

    @ParameterizedTest @ValueSource(strings={"hurt","death","cpu"})
    void hurtDeathAndCpuBoundaryDeathUseTheNativeFloorTail(String owner) throws Exception {
        sonic.incrementBadnikChain();
        switch(owner) {
            case "hurt" -> PlayableHurtRadiusTransition.apply(tails);
            case "death" -> PlayableHurtRadiusTransition.applyDeath(sonic);
            case "cpu" -> {
                var cpu=new SidekickCpuController(tails,sonic);
                var boundary=SidekickCpuController.class.getDeclaredMethod("applyKillCharacterTouchFloorReset");
                boundary.setAccessible(true); boundary.invoke(cpu);
            }
            default -> throw new AssertionError(owner);
        }
        assertEquals(0,chain.word());
        assertEquals(100,sonic.incrementBadnikChain());
    }

    @Test void cylinderRideResetRunsOnlyOnAirborneArrival() throws Exception {
        var cylinder=new com.openggf.game.sonic2.objects.SpiralObjectInstance(
                new com.openggf.level.objects.ObjectSpawn(256,256,6,0x80,0,false,0),"cylinder");
        var reset=cylinder.getClass().getDeclaredMethod("applyRideObjectAirborneReset",AbstractPlayableSprite.class);
        reset.setAccessible(true);
        sonic.incrementBadnikChain(); tails.setAir(true);
        reset.invoke(cylinder,tails);
        assertEquals(0,chain.word());
        sonic.incrementBadnikChain(); tails.setAir(false);
        reset.invoke(cylinder,tails);
        assertEquals(2,chain.word(),"a grounded RideObject call does not run ResetOnFloor");
    }

    @Test void cpuTailsOilArrivalResetsTheWordButStandingMaintenanceRetainsIt() {
        var oil=new com.openggf.game.sonic2.OilSurfaceManager();
        oil.setOilSurfaceY(0x300);
        sonic.setCentreY((short)0);
        oil.updateSurface(sonic); // Establish the native P1 submersion bank, far above oil.
        tails.setCentreY((short)(0x300-tails.getYRadius()));
        tails.setAir(true); tails.setYSpeed((short)0x100);
        assertEquals(100,sonic.incrementBadnikChain());
        oil.updateSurface(tails);
        assertTrue(oil.isStandingOnOil(tails));
        assertFalse(tails.getAir());
        assertEquals(0,chain.word());
        assertEquals(100,sonic.incrementBadnikChain());
        oil.updateSurface(tails);
        assertEquals(2,chain.word(),"MvSonicOnPtfm standing maintenance skips ResetOnFloor");
    }

    @Test void drowningResetsBeforeItsPreDeathPause() {
        sonic.incrementBadnikChain();
        assertTrue(tails.applyDrownDeath());
        assertEquals(0,chain.word());
        assertEquals(100,sonic.incrementBadnikChain());
        assertFalse(tails.applyDrownDeath(),"already drowning does not call the reset again");
        assertEquals(2,chain.word());
    }
}
