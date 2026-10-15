package com.openggf.graphics.color;

import com.openggf.control.InputHandler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_V;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestDisplayColorProfileController {

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test
    void savedExpansionAppliesAtRuntimeWithoutChangingTheProfileOrNativePalette() throws Exception {
        var config = com.openggf.configuration.SonicConfigurationService.createStandalone(directory);
        var graphics = new com.openggf.graphics.GraphicsManager();
        graphics.initHeadless();
        AtomicInteger refreshed = new AtomicInteger();
        var controller = DisplayColorProfileController.fromConfig(config, graphics, refreshed::incrementAndGet);
        assertEquals(DisplayColorExpansion.FULL, graphics.getDisplayColorExpansion());
        var draft = new com.openggf.configuration.EngineSettingsDraft(config);
        draft.set(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
        controller.synchronizeSavedSettings();
        assertEquals(DisplayColorExpansion.FULL, graphics.getDisplayColorExpansion(), "unsaved drafts are not live");
        draft.apply();
        controller.synchronizeSavedSettings();
        assertEquals(DisplayColorExpansion.LIMITED, graphics.getDisplayColorExpansion());
        assertEquals(DisplayColorProfile.RAW_RGB, controller.currentProfile());
        assertEquals(0, refreshed.get(), "output gain must not reload or mutate the native palette owner");
        var reloaded = com.openggf.configuration.SonicConfigurationService.createStandalone(directory);
        assertEquals("LIMITED", reloaded.getString(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_EXPANSION));
        draft.reset(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_EXPANSION);
        draft.apply();
        controller.synchronizeSavedSettings();
        assertEquals(DisplayColorExpansion.FULL, graphics.getDisplayColorExpansion());
        graphics.cleanup();
    }

    @Test
    void steadyStateSavedSettingSynchronizationAllocatesNothing() throws Exception {
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported(),
                "JVM cannot measure thread allocation");
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        var config = com.openggf.configuration.SonicConfigurationService.createStandalone(directory);
        var graphics = new com.openggf.graphics.GraphicsManager();
        graphics.initHeadless();
        var controller = DisplayColorProfileController.fromConfig(config, graphics, () -> { });
        var draft = new com.openggf.configuration.EngineSettingsDraft(config);
        draft.set(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
        draft.apply();
        controller.synchronizeSavedSettings();
        assertEquals(DisplayColorExpansion.LIMITED, graphics.getDisplayColorExpansion());
        // Warm the exact measured loops before enforcing the per-frame budget.
        for (int warm = 0; warm < 20; warm++) {
            synchronizeFrames(controller, 10_000);
            readSavedSettings(config, 10_000);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        synchronizeFrames(controller, 10_000);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        before = bean.getThreadAllocatedBytes(thread);
        readSavedSettings(config, 10_000);
        long reads = bean.getThreadAllocatedBytes(thread) - before;
        // The two saved-string reads are 0 B in an ordinary JVM. Another test in a
        // reused fork mocking SonicConfigurationService makes Mockito's inline mock
        // maker instrument every instance (measured 96 B per frame for both reads),
        // so the oracle is: the controller adds nothing beyond its own two reads.
        assertTrue(allocated <= reads,
                "an unchanged saved display setting must not parse or allocate per rendered frame: "
                        + allocated + " B for sync vs " + reads + " B for its two config reads");
        assertEquals(DisplayColorExpansion.LIMITED, graphics.getDisplayColorExpansion());
        graphics.cleanup();
    }

    private static void synchronizeFrames(DisplayColorProfileController controller, int frames) {
        for (int frame = 0; frame < frames; frame++) controller.synchronizeSavedSettings();
    }

    private static int readSink;

    private static void readSavedSettings(com.openggf.configuration.SonicConfigurationService config, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            readSink += config.getString(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_PROFILE).length()
                    + config.getString(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_EXPANSION).length();
        }
    }

    @Test
    void update_cyclesProfilePersistsRefreshesAndShowsNotification() {
        AtomicReference<DisplayColorProfile> persisted = new AtomicReference<>();
        AtomicInteger refreshCount = new AtomicInteger();
        DisplayColorProfileController controller = new DisplayColorProfileController(
                DisplayColorProfile.RAW_RGB,
                GLFW_KEY_V,
                persisted::set,
                refreshCount::incrementAndGet);
        InputHandler input = new InputHandler();

        input.handleKeyEvent(GLFW_KEY_V, GLFW_PRESS);
        controller.update(input);

        assertEquals(DisplayColorProfile.MD_ANALOG, controller.currentProfile());
        assertEquals(DisplayColorProfile.MD_ANALOG, persisted.get());
        assertEquals(1, refreshCount.get());
        assertEquals("Color: MD Analog", controller.notificationText());
    }

    @Test
    void update_appliesProfileBeforeRefreshingPalettes() {
        AtomicReference<DisplayColorProfile> applied = new AtomicReference<>(DisplayColorProfile.RAW_RGB);
        AtomicReference<DisplayColorProfile> profileAtRefresh = new AtomicReference<>();
        DisplayColorProfileController controller = new DisplayColorProfileController(
                DisplayColorProfile.RAW_RGB,
                GLFW_KEY_V,
                profile -> {
                },
                applied::set,
                () -> profileAtRefresh.set(applied.get()));
        InputHandler input = new InputHandler();

        input.handleKeyEvent(GLFW_KEY_V, GLFW_PRESS);
        controller.update(input);

        assertEquals(DisplayColorProfile.MD_ANALOG, applied.get());
        assertEquals(DisplayColorProfile.MD_ANALOG, profileAtRefresh.get());
    }


    @Test
    void update_hidesNotificationAfterTimeout() {
        DisplayColorProfileController controller = new DisplayColorProfileController(
                DisplayColorProfile.RAW_RGB,
                GLFW_KEY_V,
                profile -> {
                },
                () -> {
                });
        InputHandler input = new InputHandler();

        input.handleKeyEvent(GLFW_KEY_V, GLFW_PRESS);
        controller.update(input);
        input.handleKeyEvent(GLFW_KEY_V, GLFW_RELEASE);
        input.update();

        for (int i = 0; i < DisplayColorProfileController.NOTIFICATION_FRAMES; i++) {
            controller.update(input);
        }

        assertNull(controller.notificationText());
    }

    @Test
    void update_ignoresUnboundToggleKey() {
        AtomicReference<DisplayColorProfile> persisted = new AtomicReference<>();
        AtomicInteger refreshCount = new AtomicInteger();
        DisplayColorProfileController controller = new DisplayColorProfileController(
                DisplayColorProfile.RAW_RGB,
                -1,
                persisted::set,
                refreshCount::incrementAndGet);
        InputHandler input = new InputHandler();

        input.handleKeyEvent(GLFW_KEY_V, GLFW_PRESS);
        controller.update(input);

        assertEquals(DisplayColorProfile.RAW_RGB, controller.currentProfile());
        assertNull(persisted.get());
        assertEquals(0, refreshCount.get());
        assertNull(controller.notificationText());
    }
}
