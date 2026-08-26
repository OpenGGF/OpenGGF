package com.openggf.game.dataselect;

import com.openggf.control.InputHandler;
import com.openggf.game.DataSelectProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestDataSelectPresentationProviderWidescreen {

    @Test
    void forwardsWidthBeforeFirstDrawAndAfterResizeToConcreteDelegate() {
        RecordingProvider delegate = new RecordingProvider();
        DataSelectPresentationProvider provider = new DataSelectPresentationProvider(delegate, null);

        provider.setViewportWidth(352);
        provider.draw();
        provider.setViewportWidth(400);
        provider.draw();

        assertEquals(List.of(352, 400), delegate.viewportWidths);
        assertEquals(2, delegate.drawCalls);
    }

    private static final class RecordingProvider implements DataSelectProvider {
        private final List<Integer> viewportWidths = new ArrayList<>();
        private int drawCalls;

        @Override public void initialize() {}
        @Override public void update(InputHandler input) {}
        @Override public void draw() { drawCalls++; }
        @Override public void setViewportWidth(int width) { viewportWidths.add(width); }
        @Override public void setClearColor() {}
        @Override public void reset() {}
        @Override public Optional<String> launchErrorMessage() { return Optional.empty(); }
        @Override public State getState() { return State.ACTIVE; }
        @Override public boolean isExiting() { return false; }
        @Override public boolean isActive() { return true; }
    }
}
