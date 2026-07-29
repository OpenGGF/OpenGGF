package com.openggf.game.resources;

import com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance;
import com.openggf.game.sonic2.objects.ResultsScreenObjectInstance;
import com.openggf.graphics.FadeManager;
import com.openggf.level.Level;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestPlcObjectOwnedFadeLifecycle {

    @Test
    void sonic1ResultsCompletionStartsRevealOnTheNextToken() throws Exception {
        Sonic1ResultsScreenObjectInstance results =
                new Sonic1ResultsScreenObjectInstance(30, 0, 1);
        assertResultsLifecycle(results, "triggerFadeToBlack");
    }

    @Test
    void sonic2ResultsCompletionStartsRevealOnTheNextToken() throws Exception {
        ResultsScreenObjectInstance results =
                new ResultsScreenObjectInstance(30, 0, 1, false);
        assertResultsLifecycle(results, "triggerFadeToBlack");
    }

    private static void assertResultsLifecycle(
            com.openggf.level.objects.AbstractObjectInstance results,
            String triggerMethod) throws Exception {
        List<String> events = new ArrayList<>();
        AtomicInteger transitions = new AtomicInteger();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(events));
        FadeManager fade = new FadeManager();
        results.setServices(new TestObjectServices() {
            @Override
            public NativeFadeLifecycle nativeFadeLifecycle() {
                return coordinator;
            }

            @Override
            public Level currentLevel() {
                return mock(Level.class);
            }

            @Override
            public void advanceToNextLevel() {
                transitions.incrementAndGet();
            }
        }.withFadeManager(fade));
        Method trigger = results.getClass().getDeclaredMethod(triggerMethod);
        trigger.setAccessible(true);
        trigger.invoke(results);

        int completionEventCount = -1;
        for (int iteration = 0; iteration < 30 && transitions.get() == 0; iteration++) {
            coordinator.runLogicalIteration(fade::update, frame -> null);
            if (transitions.get() == 1) {
                completionEventCount = events.size();
            }
        }

        assertEquals(1, transitions.get());
        assertTrue(completionEventCount >= 2);
        assertEquals("service:PALETTE_FADE",
                events.get(completionEventCount - 2));
        assertEquals("prepare:PALETTE_FADE",
                events.get(completionEventCount - 1));

        coordinator.runLogicalIteration(fade::update, frame -> null);
        assertEquals("service:PALETTE_FADE",
                events.get(completionEventCount));
    }

    private static PlcLifecycleService recording(List<String> events) {
        return new PlcLifecycleService() {
            @Override
            public void serviceVBlank(PlcLifecyclePhase phase) {
                events.add("service:" + phase);
            }

            @Override
            public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                return phase == PlcLifecyclePhase.PALETTE_FADE;
            }

            @Override
            public void prepareAfterLoop(PlcLifecyclePhase phase) {
                events.add("prepare:" + phase);
            }
        };
    }
}
