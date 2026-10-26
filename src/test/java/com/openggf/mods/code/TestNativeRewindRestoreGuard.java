package com.openggf.mods.code;

import com.openggf.game.rewind.*;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native visit admission must run before even the first unrelated restore or reset. */
@Isolated
class TestNativeRewindRestoreGuard {
    @BeforeEach void setup() { EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap()); }
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void retiredNativeOwnerRejectsBeforeEarlierAdaptersAndCallbacks(boolean course) {
        var events = new ArrayList<String>();
        var first = new IntegerState("first", 7, events);
        var old = new GuardState("native", 8, events);
        var root = new Root(old); var registry = new RewindRegistry();
        registry.register(first);
        NativeRewindAdapterPublication.register(registry, root, old);
        registry.registerPostRestoreCallback("after", () -> events.add("callback"));
        var capture = course ? registry.captureCourse() : registry.capture();
        first.value = 99;
        var current = new GuardState("native", 100, events);
        root.adapters = List.of(current);
        NativeRewindAdapterPublication.register(registry, root, current);

        assertThrows(IllegalArgumentException.class, () -> {
            if (course) registry.restoreCourse(capture); else registry.restore(capture);
        });
        assertEquals(List.of("validate:native"), events);
        assertEquals(99, first.value);
        assertEquals(100, current.value);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void validNativeCapturePreflightsThenKeepsExistingRestoreAndRngOrder(boolean course) {
        var events = new ArrayList<String>(); var registry = new RewindRegistry();
        var first = new IntegerState("first", 7, events);
        var rng = new IntegerState("gamerng", 9, events);
        var nativeState = new GuardState("native", 8, events);
        registry.register(first); registry.register(rng);
        NativeRewindAdapterPublication.register(registry, new Root(nativeState), nativeState);
        registry.registerPostRestoreCallback("after", () -> events.add("callback"));
        var capture = course ? registry.captureCourse() : registry.capture();
        first.value = rng.value = nativeState.value = 99;

        if (course) registry.restoreCourse(capture); else registry.restore(capture);

        assertEquals(List.of("validate:native", "restore:first", "restore:native", "restore:gamerng", "callback"), events);
        assertEquals(7, first.value); assertEquals(8, nativeState.value); assertEquals(9, rng.value);
    }

    @Test void missingNativeGuardPayloadRejectsBeforeAnyRestoreOrMissingReset() {
        var events = new ArrayList<String>(); var registry = new RewindRegistry();
        var first = new IntegerState("first", 7, events); registry.register(first);
        var beforePublication = registry.capture(); first.value = 99;
        var guard = new GuardState("native", 8, events);
        NativeRewindAdapterPublication.register(registry, new Root(guard), guard);
        registry.registerPostRestoreCallback("after", () -> events.add("callback"));

        assertThrows(IllegalArgumentException.class, () -> registry.restore(beforePublication));

        assertTrue(events.isEmpty()); assertEquals(99, first.value); assertEquals(8, guard.value);
    }

    @Test void ordinaryRegistrationDoesNotAcquireNativePreflightAuthority() {
        var events = new ArrayList<String>(); var registry = new RewindRegistry();
        var unowned = new GuardState("reported-native", 7, events);
        registry.register(unowned); var capture = registry.capture(); unowned.value = 99;

        registry.restore(capture);

        assertEquals(List.of("restore:reported-native"), events); assertEquals(7, unowned.value);
    }

    private static final class Root extends Sonic2GameModule {
        List<RewindSnapshottable<?>> adapters;
        Root(RewindSnapshottable<?> adapter) { adapters = List.of(adapter); }
        @Override public List<RewindSnapshottable<?>> rewindAdapters() { return adapters; }
    }
    private static class IntegerState implements RewindSnapshottable<Integer> {
        final String key; final List<String> events; int value;
        IntegerState(String key, int value, List<String> events) { this.key = key; this.value = value; this.events = events; }
        @Override public String key() { return key; }
        @Override public Integer capture() { return value; }
        @Override public void restore(Integer capture) { events.add("restore:" + key); value = capture; }
        @Override public void resetForMissingSnapshot() { events.add("reset:" + key); value = 0; }
    }
    private record Owned(Object owner, int value) { }
    private static final class GuardState implements NativeRewindRestoreGuard<Owned> {
        final String key; final List<String> events; final Object owner = new Object(); int value;
        GuardState(String key, int value, List<String> events) { this.key = key; this.value = value; this.events = events; }
        @Override public String key() { return key; }
        @Override public Owned capture() { return new Owned(owner, value); }
        @Override public void validateRestore(Owned capture) {
            events.add("validate:" + key);
            if (capture.owner() != owner) throw new IllegalArgumentException("Retired native visit");
        }
        @Override public void restore(Owned capture) { events.add("restore:" + key); value = capture.value(); }
        @Override public void resetForMissingSnapshot() { events.add("reset:" + key); value = 0; }
    }
}
