package com.openggf.game.sonic1.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Live Obj3F reconstruction at its native initialization/display and animation boundaries. */
class TestS1BossExplosionRewind {
    @BeforeEach void setup() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void cleanup() { GraphicsManager.getInstance().resetState(); }

    @ParameterizedTest(name = "capture after {0} native object passes")
    @ValueSource(ints = {0, 1, 20})
    void liveExplosionReconstructsDisplayLifetimeAndSingleSoundRequest(int completedPasses) throws Exception {
        Harness h = new Harness();
        S1BossExplosion original = h.explosion();
        for (int n = 0; n < completedPasses; n++) h.step();
        State captured = state(original);
        assertEquals(completedPasses > 0, captured.initialized());
        assertEquals(completedPasses > 1, captured.display());
        assertEquals(completedPasses == 20 ? 2 : 0, captured.frame());
        assertEquals(completedPasses == 0 ? -1 : completedPasses == 1 ? 7 : 4, captured.duration());
        assertEquals(completedPasses == 0 ? Sonic1Sfx.BOSS_EXPLOSION.id : -1, captured.pendingSfx());
        assertRender(h, original, completedPasses > 1 ? 2 : null);
        var capturedId = h.manager.captureIdentityContext().requireIdentityTable().idFor(original);
        int capturedSlot = original.getSlotIndex();
        assertNotNull(capturedId);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        RewindRegistry registry = new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        var snapshot = registry.capture();

        List<Row> expected = h.remainingLife();
        // Expl_Main returns after QueueSound2, then ExItem_Animate displays five
        // eight-pass frames and deletes before displaying frame5.
        // Expl_Main (_incObj/27, 3F Explosions.asm:72-84);
        // ExItem_Animate (_incObj/27, 3F Explosions.asm:44-53).
        assertEquals(41 - completedPasses, expected.size(), "remaining native object passes include the deletion pass");
        assertEquals(List.of(Sonic1Sfx.BOSS_EXPLOSION.id), h.sounds,
                "one continuous lifetime requests exactly one sound from Expl_Main");
        assertTrue(original.isDestroyed());
        assertEquals(0, h.manager.getAllocatedSlotCount(), "deletion releases the actual SST slot");

        registry.restore(snapshot);
        S1BossExplosion restored = h.explosion();
        assertNotSame(original, restored, "restore must reconstruct the retired object");
        assertEquals(capturedId, h.manager.captureIdentityContext().requireIdentityTable().idFor(restored));
        assertEquals(capturedSlot, restored.getSlotIndex());
        assertEquals(1, h.manager.getAllocatedSlotCount());
        assertEquals(captured, state(restored), "subclass display flags and superclass animation/pending SFX restore together");
        assertRender(h, restored, completedPasses > 1 ? 2 : null);
        h.clock = completedPasses;
        int soundsBeforeReplay = h.sounds.size();
        assertEquals(expected, h.remainingLife(), "reconstruction preserves every remaining frame and the deletion boundary");
        assertEquals(completedPasses == 0 ? 1 : 0, h.sounds.size() - soundsBeforeReplay,
                "only a pre-init capture may request its sound again on the replayed initialization pass");
        assertTrue(h.sounds.stream().allMatch(sound -> sound == Sonic1Sfx.BOSS_EXPLOSION.id));
        assertEquals(0, h.manager.getAllocatedSlotCount());
        verify(h.renderManager, never()).getExplosionRenderer();
    }

    private static State state(S1BossExplosion explosion) throws Exception {
        return new State((boolean) read(explosion, "initialized"), (boolean) read(explosion, "displayThisPass"),
                (int) read(explosion, "animFrame"), (int) read(explosion, "animFrameDuration"),
                (int) read(explosion, "pendingSfxId"));
    }

    private static Object read(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Observe only; never invoke update or an animation helper to read state.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void assertRender(Harness h, S1BossExplosion explosion, Integer frame) {
        h.draws.clear();
        explosion.appendRenderCommands(new ArrayList<>());
        assertEquals(frame == null ? List.of() : List.of(new Draw(frame, 160, 96, false, false)), h.draws,
                "Obj3F uses the boss renderer, respects its init/display gate, and never displays frame5");
    }

    private record State(boolean initialized, boolean display, int frame, int duration, int pendingSfx) { }
    private record Draw(int frame, int x, int y, boolean flipX, boolean flipY) { }
    private record Row(State state, List<Draw> draws, boolean destroyed, int allocated, int sounds) { }

    private static final class Harness {
        final List<Integer> sounds = new ArrayList<>();
        final List<Draw> draws = new ArrayList<>();
        final ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        final ObjectManager manager;
        int clock;

        Harness() {
            PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
            when(renderManager.getBossExplosionRenderer()).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            doAnswer(call -> {
                draws.add(new Draw(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                        call.getArgument(3), call.getArgument(4)));
                return null;
            }).when(renderer).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
            Camera camera = new Camera();
            ObjectManager[] holder = new ObjectManager[1];
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public ObjectRenderManager renderManager() { return renderManager; }
                @Override public Camera camera() { return camera; }
                @Override public com.openggf.game.GameModule gameModule() { return new Sonic1GameModule(); }
                @Override public void playSfx(int sound) { sounds.add(sound); }
            };
            manager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(), 0,
                    null, null, GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.reset(0);
            assertNotNull(manager.createDynamicObject(() -> new S1BossExplosion(160, 96, renderManager)));
        }

        S1BossExplosion explosion() {
            var explosions = manager.getActiveObjects().stream().filter(S1BossExplosion.class::isInstance)
                    .map(S1BossExplosion.class::cast).toList();
            assertEquals(1, explosions.size(), "exactly one live Obj3F must own the captured slot");
            return explosions.getFirst();
        }

        void step() { manager.update(0, null, List.of(), ++clock); }

        List<Row> remainingLife() throws Exception {
            S1BossExplosion current = explosion();
            int initialSoundCount = sounds.size();
            List<Row> rows = new ArrayList<>();
            for (int n = 0; n < 42; n++) {
                step();
                int frame = Math.max(0, (clock - 1) / 8);
                assertRender(this, current, clock <= 1 || current.isDestroyed() ? null : frame);
                rows.add(new Row(state(current), List.copyOf(draws), current.isDestroyed(),
                        manager.getAllocatedSlotCount(), sounds.size() - initialSoundCount));
                if (current.isDestroyed()) {
                    assertTrue(manager.getActiveObjects().isEmpty(), "retired explosion must leave the live manager");
                    return rows;
                }
                assertSame(current, explosion(), "forward passes must neither replace nor duplicate the live explosion");
            }
            throw new AssertionError("Obj3F must retire after its five eight-pass animation frames");
        }
    }
}
