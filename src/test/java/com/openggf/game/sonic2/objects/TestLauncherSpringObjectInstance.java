package com.openggf.game.sonic2.objects;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLauncherSpringObjectInstance {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void launcherSpringUsesSolidObjectAlwaysOffscreenBypass() {
        LauncherSpringObjectInstance spring = new LauncherSpringObjectInstance(
                new ObjectSpawn(0x1070, 0x06F0, Sonic2ObjectIds.LAUNCHER_SPRING, 0, 0, false, 0),
                "LauncherSpring");

        assertTrue(spring.bypassesOffscreenSolidGate(),
                "Obj85 calls SolidObject_Always_SingleCharacter for both players");
    }

}
