package com.openggf.audio.presentation;

import com.openggf.audio.output.OpenAlPcmSink;
import org.junit.jupiter.api.Test;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTThreadLocalContext;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Native coexistence check; run with ALSOFT_DRIVERS=null so no shared desktop audio device is used. */
class TestOpenAlIsolatedContext {
    @Test void isolatedOwnerOpenPumpAndClosePreserveTheGameplayContext() throws Exception {
        assumeTrue("null".equals(System.getenv("ALSOFT_DRIVERS")), "Requires explicitly isolated OpenAL null driver");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (var owner = Executors.newSingleThreadExecutor()) {
            OpenAlPcmSink gameplay = OpenAlPcmSink.openDefault(failure::set, ignored -> { });
            try {
                long gameplayContext = ALC10.alcGetCurrentContext();
                var gameplayCapabilities = AL.getCapabilities();
                assertNotEquals(0, gameplayContext);
                var isolated = owner.submit(() -> {
                    OpenAlPcmSink sink = OpenAlPcmSink.openIsolatedDefault(failure::set, ignored -> { });
                    try {
                        long threadContext = EXTThreadLocalContext.alcGetThreadContext();
                        assertNotEquals(0, threadContext);
                        assertNotEquals(gameplayContext, threadContext, "Isolated owner has a separate context");
                        assertEquals(threadContext, ALC10.alcGetCurrentContext(), "Owner queries see its thread context");
                        pump(sink);
                        return sink;
                    } catch (RuntimeException | Error openFailure) {
                        try { sink.close(); }
                        catch (RuntimeException | Error closeFailure) { openFailure.addSuppressed(closeFailure); }
                        throw openFailure;
                    }
                }).get(5, TimeUnit.SECONDS);
                try {
                    assertEquals(gameplayContext, ALC10.alcGetCurrentContext());
                    assertSame(gameplayCapabilities, AL.getCapabilities(), "Main AL dispatch stays bound to gameplay");
                    pump(gameplay);
                } finally {
                    owner.submit(() -> {
                        isolated.close();
                        assertEquals(0, EXTThreadLocalContext.alcGetThreadContext());
                    }).get(5, TimeUnit.SECONDS);
                }
                assertEquals(gameplayContext, ALC10.alcGetCurrentContext(), "Close must preserve process context");
                assertSame(gameplayCapabilities, AL.getCapabilities());
                pump(gameplay);
                assertEquals(AL10.AL_NO_ERROR, AL10.alGetError());
                assertNull(failure.get(), "Both speakers service their own valid context");
            } finally { gameplay.close(); }
        }
    }

    private static void pump(OpenAlPcmSink sink) {
        short[] silence = new short[OpenAlPcmSink.DEVICE_BUFFER_FRAMES * 8];
        var frame = new AudioPresentationFrameView(silence);
        frame.update(silence, silence.length / 2, 0, PresentationMode.FORWARD);
        sink.accept(frame);
        sink.updateDevice();
    }
}
