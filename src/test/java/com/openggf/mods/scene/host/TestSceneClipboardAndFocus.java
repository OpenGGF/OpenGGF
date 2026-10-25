package com.openggf.mods.scene.host;

import static com.openggf.mods.scene.SceneClipboard.Result.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import com.openggf.control.InputHandler;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.OwnedSceneFactory;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneClipboard;
import com.openggf.mods.scene.SceneContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Real scene ownership and platform adapter, with an in-memory native writer and clock. */
class TestSceneClipboardAndFocus {
    @TempDir Path temp;

    private final class Visit implements AutoCloseable {
        final ModSceneHost host = new ModSceneHost();
        final AtomicLong clock = new AtomicLong(1);
        InputHandler input = new InputHandler(com.openggf.InputBindingFactory.standaloneSupplier(),
                com.openggf.control.GamepadStateSource.noop(), clock::get);
        final List<String> written = new ArrayList<>();
        final List<Thread> writerThreads = new ArrayList<>();
        final List<Long> losses = new ArrayList<>();
        final ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        final ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), owners -> {});
        SceneContext ctx;
        SceneClipboard.Write operation;
        Consumer<SceneContext> action = ignored -> {};
        Consumer<SceneContext> onLoss = ignored -> {};
        boolean revealed = true;
        int updates;
        int exits;
        final OwnedSceneFactory factory;

        Visit() { this(true, null); }

        Visit(boolean available, Consumer<String> writer) {
            host.installClipboardPlatform(new SceneClipboardPlatform(!available ? null : text -> {
                if (writer != null) writer.accept(text);
                written.add(text);
                writerThreads.add(Thread.currentThread());
            }, clock::get));
            factory = ModContextTestAccess.ownedScene("cards", () -> new ModScene() {
                public void enter(SceneContext context) { ctx = context; }
                public void update(SceneContext context) { updates++; action.accept(context); }
                public void draw(SceneContext context, SceneCanvas canvas) {
                    canvas.text(revealed ? "SECRET" : "HIDDEN", 0, 0, 0xFFFFFFFF);
                }
                public void focusLost(SceneContext context) {
                    assertTrue(factory.isCallbackActive());
                    revealed = false;
                    losses.add(context.focusLossGeneration());
                    onLoss.accept(context);
                }
            }, boundary);
            host.open(factory, new SceneServices(null, null, temp, null, null, () -> exits++), 320, 224);
        }

        void press() { clock.incrementAndGet(); input.handleKeyEvent(GLFW_KEY_C, GLFW_PRESS); }
        void tick() { host.update(input); input.update(); }
        void copy(String text) { action = context -> operation = context.clipboard().writeText(text); }
        @Override public void close() { host.close(); }
    }

    @Test void writesOnlyAfterPlatformDrainAndReportsActualSuccess() {
        try (var visit = new Visit()) {
            String text = "A".repeat(4094) + "\uD83D\uDE80";
            visit.copy(text);
            visit.press();
            visit.tick();
            assertEquals(PENDING, visit.operation.result());
            assertTrue(visit.written.isEmpty());
            visit.host.flushClipboardWrites();
            assertEquals(SUCCESS, visit.operation.result());
            assertEquals(List.of(text), visit.written);
            assertEquals(List.of(Thread.currentThread()), visit.writerThreads);
            visit.host.flushClipboardWrites();
            assertEquals(1, visit.written.size());
        }
    }

    static Stream<String> invalidText() {
        return Stream.of(null, "", "X".repeat(4097), "A\0B", "\uD800", "\uDC00", "\uD800x");
    }

    @ParameterizedTest @MethodSource("invalidText")
    void invalidTextIsDeniedWithoutNativeWrite(String text) {
        try (var visit = new Visit()) {
            visit.press();
            visit.copy(text);
            visit.tick();
            visit.host.flushClipboardWrites();
            assertEquals(DENIED, visit.operation.result());
            assertTrue(visit.written.isEmpty());
        }
    }

    @Test void noPressHeldKeyAndConsumedAllowanceCannotWrite() {
        try (var visit = new Visit()) {
            visit.copy("ABC123");
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
            visit.press();
            visit.tick();
            visit.host.flushClipboardWrites();
            assertEquals(SUCCESS, visit.operation.result());
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
            visit.input.handleKeyEvent(GLFW_KEY_C, GLFW_REPEAT);
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
            assertEquals(List.of("ABC123"), visit.written);
        }
    }

    @Test void asynchronousCopyUsesOneAllowanceForLessThanFiveSeconds() {
        try (var visit = new Visit()) {
            visit.press();
            visit.tick();
            visit.clock.addAndGet(4_999_999_999L);
            visit.copy("LATE12");
            visit.tick();
            visit.host.flushClipboardWrites();
            assertEquals(SUCCESS, visit.operation.result());
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
            assertEquals(List.of("LATE12"), visit.written);
        }
    }

    @Test void allowanceExpiresAtFiveSecondsAndPressesDoNotAccumulate() {
        try (var visit = new Visit()) {
            visit.press();
            visit.tick();
            visit.clock.addAndGet(5_000_000_000L);
            visit.copy("EXPIRE");
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
            visit.input.handleKeyEvent(GLFW_KEY_C, GLFW_RELEASE);
            visit.tick();
            visit.press();
            visit.tick();
            visit.host.flushClipboardWrites();
            assertEquals(SUCCESS, visit.operation.result());
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
            assertEquals(1, visit.written.size());
        }
    }

    @Test void pendingWriteCannotOutliveTheGestureDeadline() {
        try (var visit = new Visit()) {
            visit.copy("EXPIRE");
            visit.press();
            visit.tick();
            assertEquals(PENDING, visit.operation.result());
            visit.clock.addAndGet(5_000_000_000L);
            visit.host.flushClipboardWrites();
            assertEquals(DENIED, visit.operation.result());
            assertTrue(visit.written.isEmpty());
        }
    }

    @Test void mousePressAuthorizesCopyWithoutAMouseMapper() {
        try (var visit = new Visit()) {
            visit.input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
            visit.copy("MOUSE1");
            visit.tick();
            visit.host.flushClipboardWrites();
            assertEquals(SUCCESS, visit.operation.result());
            assertEquals(List.of("MOUSE1"), visit.written);
        }
    }

    @Test void physicalGamepadPressAuthorizesCopy() {
        try (var visit = new Visit()) {
            boolean[] buttons = new boolean[15];
            visit.input = new InputHandler(com.openggf.InputBindingFactory.standaloneSupplier(),
                    () -> List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(
                            0, "pad", buttons, 0, 0)), visit.clock::get);
            visit.input.pollPhysicalGamepads();
            buttons[GLFW_GAMEPAD_BUTTON_A] = true;
            visit.clock.incrementAndGet();
            visit.input.pollPhysicalGamepads();
            visit.copy("PAD123");
            visit.tick();
            visit.host.flushClipboardWrites();
            assertEquals(SUCCESS, visit.operation.result());
            assertEquals(List.of("PAD123"), visit.written);
        }
    }

    @Test void unavailableRuntimeIsHarmlessAndConsumesTheAllowance() {
        try (var visit = new Visit(false, null)) {
            visit.press();
            visit.copy("ABC123");
            visit.tick();
            assertEquals(UNAVAILABLE, visit.operation.result());
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
            visit.host.flushClipboardWrites();
            assertTrue(visit.written.isEmpty());
        }
        assertEquals(UNAVAILABLE, SceneClipboard.unavailable().writeText("text").result());
        var host = new ModSceneHost();
        host.windowFocusChanged(false);
        host.flushClipboardWrites();
        host.close();
    }

    @Test void nativeFailureReturnsUnavailableWithoutExposingItsMessage() {
        try (var visit = new Visit(true, text -> { throw new IllegalStateException(text); })) {
            visit.copy("SECRET");
            visit.press();
            visit.tick();
            assertFalse(visit.operation.toString().contains("SECRET"));
            assertDoesNotThrow(visit.host::flushClipboardWrites);
            assertEquals(UNAVAILABLE, visit.operation.result());
            assertTrue(visit.written.isEmpty());
        }
    }

    @Test void retainedContextForeignOwnerAndOffThreadCallsAreDenied() throws InterruptedException {
        try (var visit = new Visit()) {
            visit.press();
            visit.tick();
            assertEquals(DENIED, visit.ctx.clipboard().writeText("outside").result());
            var foreign = new AtomicReference<SceneClipboard.Write>();
            var threaded = new AtomicReference<SceneClipboard.Write>();
            visit.action = context -> {
                visit.boundary.run("other", () -> foreign.set(context.clipboard().writeText("foreign")));
                Thread worker = new Thread(() -> threaded.set(context.clipboard().writeText("worker")));
                worker.start();
                try { worker.join(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
                visit.operation = context.clipboard().writeText("owned");
            };
            visit.tick();
            assertEquals(DENIED, foreign.get().result());
            assertEquals(DENIED, threaded.get().result());
            var failure = new AtomicReference<Throwable>();
            Thread worker = new Thread(() -> {
                try { visit.host.flushClipboardWrites(); } catch (Throwable thrown) { failure.set(thrown); }
            });
            worker.start();
            worker.join();
            assertInstanceOf(IllegalStateException.class, failure.get());
            assertEquals(PENDING, visit.operation.result());
            visit.host.flushClipboardWrites();
            assertEquals(List.of("owned"), visit.written);
        }
    }

    @Test void focusLossCancelsPendingWorkAndNotifiesWhileUpdatesArePaused() {
        try (var visit = new Visit()) {
            visit.copy("SECRET");
            visit.press();
            visit.tick();
            visit.host.draw(null, null);
            assertFalse(visit.host.recordedFrame().isEmpty());
            visit.host.windowFocusChanged(false);
            assertFalse(visit.revealed);
            assertEquals(List.of(1L), visit.losses);
            assertEquals(1, visit.updates);
            assertEquals(DENIED, visit.operation.result());
            assertTrue(visit.host.recordedFrame().isEmpty());
            visit.host.flushClipboardWrites();
            assertTrue(visit.written.isEmpty());
            visit.host.windowFocusChanged(true);
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
        }
    }

    @Test void staleInputAcrossFocusLossCannotGrantANewAllowance() {
        try (var visit = new Visit()) {
            visit.copy("SECRET");
            visit.press();
            visit.clock.incrementAndGet();
            visit.host.windowFocusChanged(false);
            visit.clock.incrementAndGet();
            visit.host.windowFocusChanged(true);
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
            visit.input.handleKeyEvent(GLFW_KEY_C, GLFW_RELEASE);
            visit.press();
            visit.tick();
            visit.host.flushClipboardWrites();
            assertEquals(SUCCESS, visit.operation.result());
            assertEquals(List.of("SECRET"), visit.written);
        }
    }

    @Test void pressesWhileUnfocusedAndStaleMouseStateCannotAuthorizeCopyOnReturn() {
        try (var visit = new Visit()) {
            visit.copy("SECRET");
            visit.host.windowFocusChanged(false);
            visit.press();
            visit.input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
            visit.clock.incrementAndGet();
            visit.host.windowFocusChanged(true);
            visit.tick();
            assertEquals(DENIED, visit.operation.result());
            visit.input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
            visit.tick();
            visit.clock.incrementAndGet();
            visit.input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
            visit.tick();
            visit.host.flushClipboardWrites();
            assertEquals(SUCCESS, visit.operation.result());
            assertEquals(List.of("SECRET"), visit.written);
        }
    }

    @Test void suspendedRecordingIsInvalidatedAndIconifyDoesNotRestoreFocus() {
        try (var visit = new Visit()) {
            visit.host.draw(null, null);
            visit.host.suspend();
            visit.host.windowFocusChanged(false);
            visit.host.windowIconifyChanged(true);
            assertEquals(List.of(1L), visit.losses);
            visit.host.windowIconifyChanged(false);
            assertEquals(List.of(1L), visit.losses);
            visit.host.draw(null, null);
            assertTrue(visit.host.recordedFrame().isEmpty());
            assertEquals(0, visit.updates);
        }
    }

    @Test void suspensionAndCloseRetirePendingWritesAndFocusCallbacks() {
        try (var visit = new Visit()) {
            visit.copy("SECRET");
            visit.press();
            visit.tick();
            visit.host.suspend();
            assertEquals(DENIED, visit.operation.result());
            visit.host.flushClipboardWrites();
            visit.host.close();
            visit.host.windowFocusChanged(false);
            assertTrue(visit.losses.isEmpty());
            assertEquals(DENIED, visit.ctx.clipboard().writeText("after close").result());
            assertTrue(visit.written.isEmpty());
        }
        try (var visit = new Visit()) {
            visit.copy("SECRET");
            visit.press();
            visit.tick();
            visit.host.close();
            visit.host.flushClipboardWrites();
            assertEquals(DENIED, visit.operation.result());
            assertTrue(visit.written.isEmpty());
        }
    }

    @Test void focusCallbackFailureStaysInsideTheOwnerBoundaryAndClosesVisit() {
        try (var visit = new Visit()) {
            visit.onLoss = ignored -> { throw new IllegalStateException("focus failed"); };
            assertDoesNotThrow(() -> visit.host.windowFocusChanged(false));
            assertFalse(visit.host.isOpen());
            assertEquals(1, visit.exits);
            assertTrue(visit.findings.snapshot().containsKey("cards"));
            visit.host.windowFocusChanged(true);
            visit.host.windowFocusChanged(false);
            assertEquals(List.of(1L), visit.losses);
        }
    }
}
