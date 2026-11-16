package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestFbzWrappedEventTransition {
    @AfterEach void close() { TestEnvironment.resetAll(); }
    @Test void stockFbzReloadWorksBehindTheProductionModEventWrapper() {
        var stock = new Sonic3kGameModule();
        var manager = (Sonic3kLevelEventManager) stock.getLevelEventProvider();
        // The stock-zone route through the real wrapper used by zone-publishing mods.
        var wrapper = new ModZoneEventProvider(manager, stock.getZoneRegistry().getZoneCount(), List.of(), null);
        TestEnvironment.configureGameModuleFixture(new DelegatingGameModule(stock, "test:fbz-wrapper") {
            @Override public LevelEventProvider getLevelEventProvider() { return wrapper; }
        });
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(4, 0)
                .startPosition((short) 0x2EE1, (short) 0x540).startPositionIsCentre().build();
        assertSame(wrapper, GameServices.module().getLevelEventProvider());
        var source = manager.getFbzEvents();
        source.setEventsFg5(true);
        assertDoesNotThrow(() -> source.updateAct1BackgroundEvent(
                fixture.sprite().getCentreX(), fixture.sprite().getCentreY(), false));
        assertEquals(1, GameServices.level().getCurrentAct());
        var runtime = assertInstanceOf(FbzZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());
        assertTrue(runtime.isBackedBy(manager.getFbzEvents()));
        assertEquals(fixture.camera().getXCopy(), runtime.scrollPublication().cameraX());
        assertEquals(fixture.camera().getYCopy(), runtime.scrollPublication().foregroundY());
    }
}
