package com.openggf.game.sonic3k.titlescreen;

import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.tests.TestEnvironment;

import com.openggf.data.RomManager;
import com.openggf.game.session.EngineContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSonic3kTitleScreenBootstrap {

    @BeforeEach
    public void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
    }

    @Test
    public void titleScreenManagerConstructsWithConfiguredEngineServices() {
        Sonic3kTitleScreenManager manager = new Sonic3kTitleScreenManager();
        assertNotNull(manager);
        manager.reset();
    }

    @Test
    public void titleScreenDataLoaderReturnsFalseWithoutRom() {
        RomManager.getInstance().setRom(null);
        Sonic3kTitleScreenDataLoader loader = new Sonic3kTitleScreenDataLoader();
        assertFalse(loader.loadData());
    }
}


