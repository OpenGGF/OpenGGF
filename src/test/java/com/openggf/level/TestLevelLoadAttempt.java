package com.openggf.level;

import com.openggf.game.GameModule;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.LevelLoadRuntimeAccess;
import com.openggf.game.session.WorldSession;
import com.openggf.level.objects.ObjectCallbackAbortException;
import com.openggf.sprites.managers.SpriteManager;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Neutral callback aborts preserve typed recovery while load cleanup still runs. */
class TestLevelLoadAttempt {
    private static final class HostAbort extends ObjectCallbackAbortException {
        HostAbort(String message) { super(message, new IllegalStateException(message)); }
    }

    @Test void moduleSupplierAbortStaysDirectRatherThanBecomingACheckedDeferredFailure() {
        var original = new HostAbort("module owner unavailable");
        java.util.function.Supplier<GameModule> supplier = () -> { throw original; };
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, new LevelLoadContext());

        assertSame(original, assertThrows(HostAbort.class, () -> attempt.execute(supplier, 0)));
        assertSame(original, assertThrows(HostAbort.class, () -> attempt.failure(original, supplier, () -> {}, 0)));
        assertEquals(0, original.getSuppressed().length);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"profile", "steps"})
    void profileConstructionAbortStaysDirectRatherThanBecomingACheckedDeferredFailure(String source) {
        var module = mock(GameModule.class);
        var profile = mock(LevelInitProfile.class);
        var original = new HostAbort("profile owner unavailable");
        if (source.equals("profile")) when(module.getLevelInitProfile()).thenThrow(original);
        else {
            when(module.getLevelInitProfile()).thenReturn(profile);
            when(profile.levelLoadSteps(any())).thenThrow(original);
        }
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, new LevelLoadContext());

        assertSame(original, assertThrows(HostAbort.class, () -> attempt.execute(() -> module, 0)));
        assertSame(original, assertThrows(HostAbort.class,
                () -> attempt.failure(original, () -> module, () -> {}, 0)));
        assertEquals(0, original.getSuppressed().length);
    }

    @Test void originalTypedAbortSurvivesAbortDuringCancellation() {
        var module = mock(GameModule.class);
        var profile = mock(LevelInitProfile.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        var original = new HostAbort("load owner unavailable");
        doThrow(new HostAbort("cleanup owner unavailable")).when(profile).cancelPendingLevelLoadWork();
        var discarded = new AtomicBoolean();
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, new LevelLoadContext());

        assertSame(original, assertThrows(HostAbort.class,
                () -> attempt.failure(original, () -> module, () -> discarded.set(true), 0)));
        assertTrue(discarded.get());
        assertEquals(0, original.getSuppressed().length);
        verify(profile).cancelPendingLevelLoadWork();
    }

    @Test void selectedStepAbortClosesItsScopeAndPreservesDeferredFailureIdentity() {
        var module = mock(GameModule.class); var profile = mock(LevelInitProfile.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        var original = new HostAbort("actual selected step");
        when(profile.levelLoadSteps(any())).thenReturn(java.util.List.of(
                new com.openggf.game.InitStep("throw", "test selected source", () -> { throw original; })));
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, new LevelLoadContext());
        assertSame(original, assertThrows(HostAbort.class, () -> attempt.execute(() -> module, 0)));
        var deferred = attempt.failure(original, () -> module, () -> {}, 0);
        assertInstanceOf(DeferredLevelLoadException.class, deferred);
        assertSame(original, deferred.getCause());
        var reached = new AtomicBoolean();
        when(profile.levelLoadSteps(any())).thenReturn(java.util.List.of(
                new com.openggf.game.InitStep("next", "new actual attempt", () -> reached.set(true))));
        new LevelLoadAttempt(null, null, LevelLoadMode.FULL, new LevelLoadContext()).execute(() -> module, 0);
        assertTrue(reached.get());
    }

    @Test void ioCauseIsUnwrappedAfterQuarantinedCancellation() {
        var module = mock(GameModule.class);
        var profile = mock(LevelInitProfile.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        var cleanup = new HostAbort("cleanup owner unavailable");
        doThrow(cleanup).when(profile).cancelPendingLevelLoadWork();
        var io = new IOException("ROM unavailable");
        var original = new RuntimeException(io);
        var discarded = new AtomicBoolean();
        var attempt = new LevelLoadAttempt(null, null, LevelLoadMode.FULL, new LevelLoadContext());

        assertSame(io, attempt.failure(original, () -> module, () -> discarded.set(true), 0));
        assertTrue(discarded.get());
        assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
        verify(profile).cancelPendingLevelLoadWork();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = LevelLoadMode.class, names = {"FULL", "PREVIEW_CAPTURE"})
    void decodeAndPreviewExecuteWithoutAParticipantManager(LevelLoadMode mode) {
        var module = mock(GameModule.class);
        var profile = mock(LevelInitProfile.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        var world = new WorldSession(module);
        var context = new LevelLoadContext();
        var reached = new AtomicBoolean();
        when(profile.levelLoadSteps(same(context))).thenReturn(List.of(
                new InitStep("DecodeOnly", "test resource-only step", () -> {
                    assertSame(world, LevelLoadRuntimeAccess.requireWorld(context));
                    assertThrows(NullPointerException.class,
                            () -> LevelLoadRuntimeAccess.requireSprites(context));
                    reached.set(true);
                })));
        assertDoesNotThrow(() ->
                new LevelLoadAttempt(world, null, mode, context).execute(() -> module, 7));
        assertTrue(reached.get());
        assertFalse(context.isIncludePostLoadAssembly());
        assertEquals(mode, context.getLoadMode());
        assertEquals(7, context.getLevelIndex());
        verify(profile).levelLoadSteps(context);
    }

    @Test void executionWithoutAWorldLeavesOptionalBindingsAbsent() {
        var module = mock(GameModule.class);
        var profile = mock(LevelInitProfile.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        var context = new LevelLoadContext();
        var reached = new AtomicBoolean();
        when(profile.levelLoadSteps(same(context))).thenReturn(List.of(
                new InitStep("NoWorld", "test existing optional-world path", () -> {
                    assertThrows(NullPointerException.class,
                            () -> LevelLoadRuntimeAccess.requireWorld(context));
                    assertThrows(NullPointerException.class,
                            () -> LevelLoadRuntimeAccess.requireSprites(context));
                    reached.set(true);
                })));
        new LevelLoadAttempt(null, null, LevelLoadMode.FULL, context).execute(() -> module, 0);
        assertTrue(reached.get());
    }

    @Test void participantBridgeAcceptsAbsentThenIdenticalManagersAndRejectsReplacement() {
        var world = new WorldSession(mock(GameModule.class));
        var context = new LevelLoadContext();
        var sprites = mock(SpriteManager.class);
        LevelLoadRuntimeAccess.bindParticipants(context, world, null);
        LevelLoadRuntimeAccess.bindParticipants(context, world, null);
        assertSame(world, LevelLoadRuntimeAccess.requireWorld(context));
        assertThrows(NullPointerException.class,
                () -> LevelLoadRuntimeAccess.requireSprites(context));
        LevelLoadRuntimeAccess.bindParticipants(context, world, sprites);
        LevelLoadRuntimeAccess.bindParticipants(context, world, sprites);
        assertSame(sprites, LevelLoadRuntimeAccess.requireSprites(context));
        assertThrows(IllegalArgumentException.class, () ->
                LevelLoadRuntimeAccess.bindParticipants(context, world, mock(SpriteManager.class)));
        assertThrows(IllegalArgumentException.class, () ->
                LevelLoadRuntimeAccess.bindParticipants(context, world, null));
        assertSame(world, LevelLoadRuntimeAccess.requireWorld(context));
        assertSame(sprites, LevelLoadRuntimeAccess.requireSprites(context));
    }

    @Test void participantBridgePreservesTheExistingWorldIdentityGuard() {
        var world = new WorldSession(mock(GameModule.class));
        var foreign = new WorldSession(mock(GameModule.class));
        var context = new LevelLoadContext();
        LevelLoadRuntimeAccess.bindWorld(context, world);
        assertThrows(IllegalArgumentException.class, () ->
                LevelLoadRuntimeAccess.bindWorld(context, foreign));
        assertThrows(IllegalArgumentException.class, () ->
                LevelLoadRuntimeAccess.bindParticipants(context, foreign, mock(SpriteManager.class)));
        assertSame(world, LevelLoadRuntimeAccess.requireWorld(context));
        assertThrows(NullPointerException.class,
                () -> LevelLoadRuntimeAccess.requireSprites(context));
    }
}
