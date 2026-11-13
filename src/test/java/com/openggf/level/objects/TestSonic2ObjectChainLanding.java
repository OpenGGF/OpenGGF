package com.openggf.level.objects;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PlayableEntity;
import com.openggf.game.internal.BadnikChainPolicy;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2ObjectChainLanding {
    @AfterEach void cleanup() { SessionManager.clear(); GameModuleRegistry.reset(); }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void rideObjectResetsOnAirborneArrivalOnlyIncludingCpuTails(boolean retainedPose) throws Exception {
        var host=new Sonic2GameModule(); GameModuleRegistry.setCurrent(host);
        TestEnvironment.configureGameModuleFixture(host);
        var chain=host.getGameService(BadnikChainPolicy.class);
        var sonic=new Sonic("sonic",(short)128,(short)200);
        var tails=new Tails("tails",(short)160,(short)200); tails.setCpuControlled(true);
        var controller=new ObjectSolidContactController(mock(ObjectManager.class));
        ObjectInstance object=retainedPose
                ? new com.openggf.game.sonic2.objects.LauncherSpringObjectInstance(
                        new ObjectSpawn(128,200,0x85,0,0,false,0),"pressure") : null;
        var land=ObjectSolidContactController.class.getDeclaredMethod("applyObjectLandingState",PlayableEntity.class,ObjectInstance.class);
        land.setAccessible(true);
        sonic.incrementBadnikChain();
        tails.setAir(false);
        land.invoke(controller,tails,object);
        assertEquals(2,chain.word(),"standing maintenance skips ResetOnFloor");
        tails.setAir(true);
        land.invoke(controller,tails,object);
        assertEquals(0,chain.word());
        assertFalse(tails.getAir());
        assertEquals(100,sonic.incrementBadnikChain());
    }
}
