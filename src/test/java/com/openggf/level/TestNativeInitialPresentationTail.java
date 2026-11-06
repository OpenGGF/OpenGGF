package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.LevelInitProfile;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.animation.InitialLevelAnimationPass;
import com.openggf.level.objects.ObjectCallbackAbortException;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real initial object dispatch separated from CPU rendering and native tile animation. */
class TestNativeInitialPresentationTail {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void objectScopeClosesBeforePresentationAndAnimationReadsTheAdvancedClockOnce() {
        var fixture = fixture();
        int before = fixture.objects.getVblaCounter();
        var tail = fixture.executeObjects();
        assertEquals(1, fixture.objects.getFrameCounter(), "the real object pass already executed");
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch(), "no dispatch scope spans a loading row");
        assertTrue(fixture.events.isEmpty(), "tile animation remains real deferred work");
        tail.prepare(() -> fixture.events.add("sat:" + fixture.objects.getVblaCounter()));
        fixture.objects.advanceVblaCounter();
        tail.finish();
        assertEquals(List.of("sat:" + before, "animate:" + ((before + 1) & 0xffff)), fixture.events);
        assertThrows(IllegalStateException.class, tail::finish);
        assertThrows(IllegalStateException.class, () -> tail.prepare(() -> fail("already prepared")));
    }

    @Test void retiredObjectIncarnationCannotWriteThePreparedPresentationOrAnimation() {
        var fixture = fixture();
        var tail = fixture.executeObjects();
        ObjectManagerInternalAccess.retireInitialDispatch(fixture.objects);
        assertThrows(ObjectCallbackAbortException.class,
                () -> tail.prepare(() -> fail("retired owner must not prepare SAT")));
        assertTrue(fixture.events.isEmpty());
    }

    @Test void failedCpuPresentationCannotRetryOrRunItsAnimationTail() {
        var fixture = fixture();
        var tail = fixture.executeObjects();
        var failure = new IllegalStateException("partial SAT failure");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> tail.prepare(() -> { throw failure; })));
        assertThrows(IllegalStateException.class, () -> tail.prepare(() -> fail("cannot retry partial SAT")));
        assertThrows(IllegalStateException.class, tail::finish);
        assertTrue(fixture.events.isEmpty());
    }

    @Test void existingImmediateExecutorStillRunsItsAnimationWithinTheCall() {
        var fixture = fixture();
        int before = fixture.objects.getVblaCounter();
        new InitialProcessSpritesExecutor().execute(fixture.module, fixture.sprites,
                fixture.objects, fixture.camera, null, fixture.animator, 0);
        assertEquals(List.of("animate:" + before), fixture.events);
        assertEquals(1, fixture.objects.getFrameCounter());
        assertFalse(fixture.objects.hasActiveInitialProcessSpritesDispatch());
    }

    private record Fixture(GameModule module, SpriteManager sprites, ObjectManager objects,
                           Camera camera, AnimatedPatternManager animator, List<String> events) {
        InitialProcessSpritesExecutor.PresentationTail executeObjects() {
            return new InitialProcessSpritesExecutor().executeObjectsOnly(
                    module, sprites, objects, camera, null, animator, 0);
        }
    }

    private static Fixture fixture() {
        TestEnvironment.resetAll();
        var registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_3K);
        when(registry.objectWindowingStrategy()).thenReturn(
                com.openggf.level.objects.ObjectWindowingStrategy.LEGACY);
        var objects = new ObjectManager(List.of(), registry, 0, null, null);
        var module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(mock(LevelInitProfile.class));
        var sprites = mock(SpriteManager.class);
        when(sprites.getSidekicks()).thenReturn(List.of());
        when(module.createInitialFixedSstDispatcher(sprites, objects, null)).thenReturn(epoch -> { });
        var events = new ArrayList<String>();
        var animator = mock(AnimatedPatternManager.class,
                withSettings().extraInterfaces(InitialLevelAnimationPass.class));
        doAnswer(call -> { events.add("animate:" + objects.getVblaCounter()); return null; })
                .when((InitialLevelAnimationPass) animator).runInitialLevelAnimationPass();
        return new Fixture(module, sprites, objects, mock(Camera.class), animator, events);
    }
}
