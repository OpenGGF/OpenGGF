package com.openggf.game.resources;

import com.openggf.game.sonic1.credits.Sonic1CreditsManager;
import com.openggf.game.sonic1.credits.Sonic1EndingProvider;
import com.openggf.game.sonic2.credits.Sonic2EndingProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPlcProviderOwnedFadeLifecycle {

    @Test
    void sonic2ProviderCompletionCannotTransferTheOutgoingToken() throws Exception {
        List<String> events = new ArrayList<>();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(events));
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        provider.bindNativeFadeLifecycle(coordinator);
        AtomicInteger callbacks = new AtomicInteger();
        Method nativeCompletion = Sonic2EndingProvider.class.getDeclaredMethod(
                "nativeCompletion", Runnable.class);
        nativeCompletion.setAccessible(true);
        Runnable completion = (Runnable) nativeCompletion.invoke(
                provider, (Runnable) () -> {
                    callbacks.incrementAndGet();
                    coordinator.beginNativeBlockingFade();
                });

        coordinator.runLogicalIteration(() -> completion.run(), frame -> {
            frame.claim(PlcLifecyclePhase.CREDITS_TEXT);
            return null;
        });
        assertEquals(1, callbacks.get());
        assertEquals(List.of(
                "service:PALETTE_FADE",
                "prepare:PALETTE_FADE"), events);

        coordinator.runLogicalIteration(() -> { }, frame -> null);
        assertEquals("service:PALETTE_FADE", events.get(2));
    }

    @Test
    void realSonic1CreditsOwnerSelectsSlowFadeForAllSixtyIterations()
            throws Exception {
        List<String> events = new ArrayList<>();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(events));
        Sonic1CreditsManager credits = new Sonic1CreditsManager();
        setField(credits, "state", Sonic1CreditsManager.State.DEMO_FADING_OUT);
        Sonic1EndingProvider provider = new Sonic1EndingProvider();
        setField(provider, "creditsManager", credits);

        for (int iteration = 0; iteration < 60; iteration++) {
            PlcLifecyclePhase phase =
                    provider.plcLifecyclePhaseOverride().orElseThrow();
            coordinator.runLogicalIteration(() -> { }, frame -> {
                frame.claim(phase);
                if (recording(events).hasPreparationBoundary(phase)) {
                    frame.prepareAfterLoop(phase);
                }
                return null;
            });
        }
        long slowServices = events.stream()
                .filter("service:CREDITS_DEMO_FADE"::equals).count();
        long slowPrepares = events.stream()
                .filter("prepare:CREDITS_DEMO_FADE"::equals).count();
        assertEquals(60, slowServices);
        assertEquals(0, slowPrepares);

        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.CREDITS_DEMO);
            frame.prepareAfterLoop(PlcLifecyclePhase.CREDITS_DEMO);
            return null;
        });
        assertEquals("prepare:CREDITS_DEMO", events.get(events.size() - 1));
    }

    private static PlcLifecycleService recording(List<String> events) {
        return new PlcLifecycleService() {
            @Override
            public void serviceVBlank(PlcLifecyclePhase phase) {
                events.add("service:" + phase);
            }

            @Override
            public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                return phase == PlcLifecyclePhase.PALETTE_FADE
                        || phase == PlcLifecyclePhase.CREDITS_DEMO;
            }

            @Override
            public void prepareAfterLoop(PlcLifecyclePhase phase) {
                events.add("prepare:" + phase);
            }
        };
    }

    private static void setField(Object target, String name, Object value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
