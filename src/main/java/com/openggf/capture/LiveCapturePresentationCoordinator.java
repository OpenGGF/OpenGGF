package com.openggf.capture;

import java.util.Objects;
import java.util.function.Consumer;

/** Owns the post-FINAL presentation ordering shared by every rendered mode. */
public final class LiveCapturePresentationCoordinator {
    private final LiveCaptureController controller;
    private final Consumer<String> orderObserver;

    public LiveCapturePresentationCoordinator(LiveCaptureController controller) {
        this(controller, ignored -> { });
    }

    LiveCapturePresentationCoordinator(LiveCaptureController controller,
                                       Consumer<String> orderObserver) {
        this.controller = Objects.requireNonNull(controller);
        this.orderObserver = Objects.requireNonNull(orderObserver);
    }

    public void present(CaptureViewport viewport, Runnable screenshot, Runnable indicator) {
        present(viewport, true, screenshot, indicator);
    }

    /** Only an admitted output interval may drain PCM or request a capture picture. */
    public void present(CaptureViewport viewport, boolean capture, Runnable screenshot, Runnable indicator) {
        if (capture) {
            orderObserver.accept("capture");
            controller.capturePresentedFrame(viewport);
        }
        orderObserver.accept("screenshot");
        screenshot.run();
        orderObserver.accept("indicator");
        indicator.run();
    }
}
