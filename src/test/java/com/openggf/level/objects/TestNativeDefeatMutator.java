package com.openggf.level.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic1.objects.badniks.Sonic1YadrinBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.BlastoidBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.JawzBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.MegaChopperBadnikInstance;
import com.openggf.tests.MutatorPhysicsWorld;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Actual object-owned kill callbacks, including the native deferred SPECIAL routes. */
@Isolated
class TestNativeDefeatMutator {
    @AfterEach void clear() { SessionManager.clear(); }
    @ParameterizedTest
    @CsvSource({"yadrin,100,-1024", "yadrin,150,-1536", "jawz,100,-1024", "jawz,150,-1536",
            "blastoid,100,-1024", "blastoid,150,-1536", "mega,100,-1024", "mega,150,-1536"})
    void actualDefeatOwnerAppliesNativeThenWorldReboundOnce(String type, int percent, int expected) {
        TestEnvironment.resetAll();
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 210); player.setCentreY((short) 85);
        player.setInvincibleFrames(20); player.setRolling(true); player.setAnimationId(2);
        player.setYSpeed((short) 1024); player.setXSpeed((short) 123);
        WorldSession world = MutatorPhysicsWorld.create(() -> new GameplayMutatorPolicy(100, 0, percent, 0xC00, 100, false));
        GameStateManager state = mock(GameStateManager.class);
        var services = new StubObjectServices() {
            @Override public WorldSession worldSession() { return world; }
            @Override public GameStateManager gameState() { return state; }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> player, List::of); }
        };
        AbstractObjectInstance badnik = switch (type) {
            case "yadrin" -> new Sonic1YadrinBadnikInstance(new ObjectSpawn(160, 100, 0x50, 0, 0, false, 0));
            case "jawz" -> new JawzBadnikInstance(new ObjectSpawn(160, 100, 0, 0, 0, false, 0));
            case "blastoid" -> new BlastoidBadnikInstance(new ObjectSpawn(160, 100, 0, 0, 0, false, 0));
            default -> new MegaChopperBadnikInstance(new ObjectSpawn(160, 100, 0, 0, 0, false, 0));
        };
        badnik.setServices(services);
        if (!type.equals("yadrin")) {
            badnik.update(0, player); badnik.refreshPostCameraRenderState();
            badnik.update(1, player); badnik.update(2, player);
        }
        ((TouchResponseListener) badnik).onTouchResponse(player,
                new TouchResponseResult(type.equals("yadrin") ? 0x0C : 0x17, 20, 16, TouchCategory.SPECIAL, 0), 3);
        if (!type.equals("yadrin")) badnik.update(3, player);
        assertTrue(badnik.isDestroyed(), "the kill route must actually run");
        assertEquals(expected, player.getYSpeed());
        assertEquals(123, player.getXSpeed());
    }
}
