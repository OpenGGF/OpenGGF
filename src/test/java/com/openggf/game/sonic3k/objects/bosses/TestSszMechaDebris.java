package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszMechaDebris {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shippedFlipBugMovementFlickerAndEndingRetirementReplay(boolean flipped) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var manager = GameServices.level().getObjectManager();
        var camera = GameServices.camera();
        camera.setX((short) 0x240); camera.setY((short) 0x400);
        var parent = new SszMechaSonicObjectInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        parent.setServices(TestEnvironment.objectServices()); manager.addDynamicObject(parent);
        parent.update(0, fixture.sprite());
        // Reach the real act1 turn callback instead of injecting a render flag.
        for (int i = 0; flipped && i < 160 && !parent.renderFlippedForTest(); i++) parent.update(i, fixture.sprite());
        assertEquals(flipped, parent.renderFlippedForTest());
        assertTrue(SszMechaDebris.spawnFor(TestEnvironment.objectServices(), parent.getSlotIndex(), 0x340, 0x500, 0));
        var piece = manager.activeObjectsOfType(SszMechaDebris.class).getFirst();
        piece.update(0, fixture.sprite());
        assertEquals(0x32C, piece.getX()); assertEquals(0x4DC, piece.getY());
        assertEquals(flipped, piece.flippedForTest()); assertFalse(piece.visibleForTest());
        piece.update(1, fixture.sprite());
        assertEquals(0x329, piece.getX()); assertEquals(0x4D9, piece.getY());
        assertFalse(piece.visibleForTest(), "first move toggles old zero bit and skips Draw_Sprite");
        piece.update(2, fixture.sprite()); assertTrue(piece.visibleForTest());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        piece.update(3, fixture.sprite());
        int x = piece.getX(), y = piece.getY(); boolean visible = piece.visibleForTest();
        ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).setAct2EndingActive(true);
        piece.update(4, fixture.sprite()); assertTrue(piece.isDestroyed());
        registry.restore(saved);
        piece = manager.activeObjectsOfType(SszMechaDebris.class).getFirst();
        piece.update(3, fixture.sprite());
        assertEquals(x, piece.getX()); assertEquals(y, piece.getY()); assertEquals(visible, piece.visibleForTest());
        ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).setAct2EndingActive(true);
        piece.update(4, fixture.sprite()); assertTrue(piece.isDestroyed());
    }
}
