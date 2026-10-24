package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CPU commands exercise real capture ownership; no GL commands or native buffers are used. */
class TestCapturedCommandCleanup {
    @Test void failedHeadlessDiscardsVisitEveryTailOnceAndRestoreOrdinaryQueue() {
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        var firstFailure = new IllegalStateException("first discard");
        var secondFailure = new IllegalArgumentException("second discard");
        var first = new Command("first", null, firstFailure, null);
        var second = new Command("second", null, secondFailure, null);
        var tail = new Command("tail", null, null, null);
        assertSame(firstFailure, assertThrows(IllegalStateException.class, () -> graphics.executeCapturedCommands(() -> {
            graphics.registerCommand(first); graphics.registerCommand(second); graphics.registerCommand(tail);
        }, 0, 0, 320, 224)));
        assertArrayEquals(new Throwable[] {secondFailure}, firstFailure.getSuppressed());
        assertEquals(1, first.discards); assertEquals(1, second.discards); assertEquals(1, tail.discards);
        assertEquals(0, first.executions + second.executions + tail.executions);
        assertOrdinaryAndNextCaptureWork(graphics);
        assertEquals(1, first.discards); assertEquals(1, second.discards); assertEquals(1, tail.discards);
    }

    @Test void failedNestedCaptureRestoresOuterTargetAndKeepsImmediateInnerSemantics() {
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        var order = new ArrayList<String>();
        var failure = new IllegalStateException("inner discard");
        var inner = new Command("inner", null, failure, order);
        graphics.executeCapturedCommands(() -> {
            graphics.registerCommand(new Command("outer-before", null, null, order));
            assertSame(failure, assertThrows(IllegalStateException.class, () -> graphics.executeCapturedCommands(
                    () -> graphics.registerCommand(inner), 0, 0, 320, 224)));
            assertEquals(List.of("inner"), order, "inner capture drains immediately, before either outer command");
            graphics.registerCommand(new Command("outer-after", null, null, order));
        }, 0, 0, 320, 224);
        assertEquals(List.of("inner", "outer-before", "outer-after"), order);
        assertOrdinaryAndNextCaptureWork(graphics);
        assertEquals(1, inner.discards);
    }

    @Test void producerFailureStaysPrimaryAcrossMultipleUnwindFailuresWithoutRetry() {
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        var primary = new IllegalStateException("producer");
        var firstCleanup = new IllegalArgumentException("first unwind");
        var secondCleanup = new AssertionError("second unwind");
        var first = new Command("first", null, firstCleanup, null);
        var second = new Command("second", null, secondCleanup, null);
        assertSame(primary, assertThrows(IllegalStateException.class, () -> graphics.executeCapturedCommands(() -> {
            graphics.registerCommand(first); graphics.registerCommand(second); throw primary;
        }, 0, 0, 320, 224)));
        assertArrayEquals(new Throwable[] {firstCleanup, secondCleanup}, primary.getSuppressed());
        assertEquals(1, first.discards); assertEquals(1, second.discards);
        assertOrdinaryAndNextCaptureWork(graphics);
        assertEquals(1, first.discards); assertEquals(1, second.discards);
    }

    @Test void executionFailureRemainsPrimaryWhenUnwindAndFrameCleanupBothThrow() {
        var cleanup = new AssertionError("frame cleanup");
        var graphics = new FrameCleanupGraphics(cleanup);
        graphics.setGlInitialized(true); // only plain CPU commands execute; cleanup is overridden below
        var primary = new IllegalStateException("execute");
        var unwind = new IllegalArgumentException("unwind");
        var failed = new Command("failed", primary, unwind, null);
        var tail = new Command("tail", null, null, null);
        assertSame(primary, assertThrows(IllegalStateException.class, () -> graphics.executeCapturedCommands(() -> {
            graphics.registerCommand(failed); graphics.registerCommand(tail);
        }, 0, 0, 320, 224)));
        assertArrayEquals(new Throwable[] {unwind, cleanup}, primary.getSuppressed());
        assertEquals(1, graphics.frameCleanups);
        assertEquals(1, failed.executions); assertEquals(0, tail.executions);
        assertEquals(1, failed.discards); assertEquals(1, tail.discards);
        assertOrdinaryAndNextCaptureWork(graphics);
        assertEquals(1, failed.executions); assertEquals(1, failed.discards);
    }

    @Test void frameCleanupFailureAfterSuccessRestoresNestedOwnerWithoutRetryingExecutedWork() {
        var cleanup = new IllegalStateException("frame cleanup");
        var graphics = new FrameCleanupGraphics(cleanup);
        graphics.initHeadless();
        var outerBefore = new Command("outer-before", null, null, null);
        var inner = new Command("inner", null, null, null);
        var outerAfter = new Command("outer-after", null, null, null);
        graphics.executeCapturedCommands(() -> {
            graphics.registerCommand(outerBefore);
            graphics.setHeadlessMode(false); graphics.setGlInitialized(true);
            assertSame(cleanup, assertThrows(IllegalStateException.class, () -> graphics.executeCapturedCommands(
                    () -> graphics.registerCommand(inner), 0, 0, 320, 224)));
            graphics.setHeadlessMode(true); graphics.setGlInitialized(false);
            graphics.registerCommand(outerAfter);
        }, 0, 0, 320, 224);
        assertEquals(1, inner.executions); assertEquals(1, inner.discards);
        assertEquals(1, outerBefore.discards); assertEquals(1, outerAfter.discards);
        assertEquals(1, graphics.frameCleanups);
        assertOrdinaryAndNextCaptureWork(graphics);
        assertEquals(1, inner.executions); assertEquals(1, inner.discards);
    }

    @Test void identicalExecutionAndCleanupThrowableDoesNotCauseSelfSuppressionOrLoseOwner() {
        var primary = new IllegalStateException("same failure instance");
        var graphics = new FrameCleanupGraphics(primary);
        graphics.setGlInitialized(true);
        var failed = new Command("failed", primary, primary, null);
        var tail = new Command("tail", null, null, null);
        assertSame(primary, assertThrows(IllegalStateException.class, () -> graphics.executeCapturedCommands(() -> {
            graphics.registerCommand(failed); graphics.registerCommand(tail);
        }, 0, 0, 320, 224)));
        assertEquals(0, primary.getSuppressed().length);
        assertEquals(1, failed.discards); assertEquals(1, tail.discards);
        assertOrdinaryAndNextCaptureWork(graphics);
    }

    private static void assertOrdinaryAndNextCaptureWork(GraphicsManager graphics) {
        graphics.setHeadlessMode(true); graphics.setGlInitialized(false);
        var ordinary = new Command("ordinary", null, null, null);
        graphics.registerCommand(ordinary);
        graphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        assertEquals(1, ordinary.discards, "registration must reach the restored ordinary queue");
        var next = new Command("next", null, null, null);
        graphics.executeCapturedCommands(() -> graphics.registerCommand(next), 0, 0, 320, 224);
        assertEquals(1, next.discards);
    }

    private static final class FrameCleanupGraphics extends GraphicsManager {
        final Throwable failure;
        int frameCleanups;
        FrameCleanupGraphics(Throwable failure) { this.failure = failure; }
        @Override void cleanupPatternFrameState() { frameCleanups++; raise(failure); }
    }

    private static final class Command implements GLCommandable {
        final String name;
        final Throwable executeFailure, discardFailure;
        final List<String> order;
        int executions, discards;
        Command(String name, Throwable executeFailure, Throwable discardFailure, List<String> order) {
            this.name = name; this.executeFailure = executeFailure; this.discardFailure = discardFailure; this.order = order;
        }
        @Override public void execute(int x, int y, int w, int h) {
            executions++;
            if (executeFailure != null) raise(executeFailure);
            discard(); // successful commands retain their existing release-on-execution contract
        }
        @Override public void discard() {
            discards++;
            if (order != null) order.add(name);
            if (discardFailure != null) raise(discardFailure);
        }
    }

    private static void raise(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        throw (Error) failure;
    }
}
