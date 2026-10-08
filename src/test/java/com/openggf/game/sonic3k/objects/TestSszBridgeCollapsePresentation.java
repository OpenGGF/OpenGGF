package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszBridgeCollapsePresentation {
    @ParameterizedTest
    @CsvSource({"0,-1", "0,1", "1,-1", "1,1"})
    void fragmentsReplaceTheSlopeExactlyThenFallInNativeOrderAndReplay(int flags, int side) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        // Complete lazy player ability registration before component snapshots.
        fixture.sprite().tickStatus();
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var bridge = new SszCollapsingBridgeDiagonalObjectInstance(
                new ObjectSpawn(0x200, 0xC00, 0x7B, 0, flags, false, 0));
        bridge.setServices(services); manager.addDynamicObject(bridge);
        NativePositionOps.writeXPosPreserveSubpixel(fixture.sprite(), 0x200 + side * 0x20);
        NativePositionOps.writeYPosPreserveSubpixel(fixture.sprite(), 0xC00);
        // Component start before any gameplay steps: establish the title-card standing latch.
        manager.forceRidingObjectForBootstrap(fixture.sprite(), bridge);
        var intact = draw(bridge);
        assertEquals(64, intact.tiles().size(), "four native 4x4 mapping pieces");
        bridge.update(0, fixture.sprite());
        assertTrue(bridge.collapsedForTest());
        assertTrue(draw(bridge).tiles().isEmpty(), "loc_44EBA retains collision, without drawing the main image");
        var pieces = manager.activeObjectsOfType(SszBridgeDebrisObjectInstance.class);
        assertEquals(8, pieces.size());
        var fragmentTiles = new ArrayList<SpritePresentation.Tile>();
        for (int index = 0; index < pieces.size(); index++) {
            var piece = pieces.get(index);
            assertEquals((index + 1) * 6, piece.hangDelayForTest(), "native word delays survive ObjectSpawn normalization");
            assertTrue(piece.isHighPriority());
            fragmentTiles.addAll(draw(piece).tiles());
        }
        assertEquals(new HashSet<>(intact.tiles()), new HashSet<>(fragmentTiles),
                "splitting must preserve every ROM tile, position and flip without a duplicate intact bridge");
        int firstY = pieces.getFirst().getY(), lastY = pieces.getLast().getY();
        for (int pass = 1; pass <= 10; pass++) for (var piece : pieces) piece.update(pass, null);
        assertTrue(pieces.getFirst().getY() > firstY, "first fragment falls after six delayed passes");
        assertEquals(lastY, pieces.getLast().getY(), "last fragment still waits for its48-frame delay");
        assertEquals(38, pieces.getLast().hangDelayForTest());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int pass = 11; pass <= 20; pass++) {
            bridge.update(pass, fixture.sprite());
            for (var piece : pieces) piece.update(pass, null);
        }
        var forward = registry.capture();
        for (var piece : pieces) piece.setDestroyed(true);
        registry.restore(saved);
        bridge = manager.activeObjectsOfType(SszCollapsingBridgeDiagonalObjectInstance.class).getFirst();
        pieces = manager.activeObjectsOfType(SszBridgeDebrisObjectInstance.class);
        for (int pass = 11; pass <= 20; pass++) {
            bridge.update(pass, fixture.sprite());
            for (var piece : pieces) piece.update(pass, null);
        }
        var replay = registry.capture();
        for (String key : forward.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
            assertTrue(diff.isEmpty(), key + ": " + diff);
        }
    }

    private static SpritePresentation.Frame draw(AbstractObjectInstance object) {
        var graphics = GameServices.graphics();
        return SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            graphics.setCurrentSpriteHighPriority(object.isHighPriority());
            object.appendRenderCommands(new ArrayList<>());
        });
    }
}
