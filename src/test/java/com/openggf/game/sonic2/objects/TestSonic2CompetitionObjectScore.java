package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.*;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2CompetitionObjectScore {
    @Test void eachSourceCreditsOnlyNativeAttackerAndOrdinaryFallback() throws Exception {
        for (int source = 0; source < 3; source++) {
            for (int slot = -1; slot < 2; slot++) {
                var first = new Sonic("sonic", (short)128, (short)256);
                var second = new Tails("tails", (short)128, (short)256);
                var fallback = new GameStateManager();
                var banks = new GameStateManager[]{new GameStateManager(), new GameStateManager()};
                var roster = mock(NativeCompetitionRoster.class);
                var actor = slot == 1 ? second : first;
                when(roster.slotOf(actor)).thenReturn(slot);
                for (int i = 0; i < 2; i++) when(roster.gameState(i)).thenReturn(banks[i]);
                var module = mock(GameModule.class);
                var policy = mock(WorldSessionPolicyState.class);
                when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policy);
                when(policy.getService(NativeCompetitionRoster.class)).thenReturn(roster);
                var world = new WorldSession(module);
                var services = new StubObjectServices() {
                    @Override public WorldSession worldSession() { return world; }
                    @Override public GameStateManager gameState() { return fallback; }
                    @Override public Camera camera() { return new Camera(); }
                    @Override public ObjectRenderManager renderManager() { return mock(ObjectRenderManager.class); }
                };
                AbstractObjectInstance object;
                Method method;
                var spawn = new ObjectSpawn(128, 256, 0x44, 0, 0, false, 256);
                int points;
                if (source == 0) {
                    object = new BumperObjectInstance(spawn, "Bumper");
                    method = BumperObjectInstance.class.getDeclaredMethod("applyBounce", AbstractPlayableSprite.class, int.class);
                    points = 10;
                } else if (source == 1) {
                    object = new BonusBlockObjectInstance(spawn, "BonusBlock");
                    method = BonusBlockObjectInstance.class.getDeclaredMethod("handleHit", AbstractPlayableSprite.class);
                    points = 10;
                } else {
                    object = new PointPokeyObjectInstance(spawn, "PointPokey") {
                        @Override protected boolean isWithinOnScreenRenderBounds() { return true; }
                    };
                    var timer = PointPokeyObjectInstance.class.getDeclaredField("countdown");
                    timer.setAccessible(true); timer.setInt(object, 17);
                    method = PointPokeyObjectInstance.class.getDeclaredMethod("updateOccupied", AbstractPlayableSprite.class, int.class);
                    points = 100;
                }
                object.setServices(services);
                method.setAccessible(true);
                if (method.getParameterCount() == 2) method.invoke(object, actor, 0);
                else method.invoke(object, actor);
                assertEquals(slot == 0 ? points : 0, banks[0].getScore(), "P1 source " + source);
                assertEquals(slot == 1 ? points : 0, banks[1].getScore(), "P2 source " + source);
                assertEquals(slot < 0 ? points : 0, fallback.getScore(), "fallback source " + source);
            }
        }
    }
}
