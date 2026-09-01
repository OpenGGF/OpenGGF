package com.openggf.audio.session;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.synth.ChipWriteObserver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestOwnedSmpsAudioStream {
    @Test
    void initializesOneStableSessionDeviceAndLogicalDriver() {
        AtomicInteger writes = new AtomicInteger();
        try (OwnedSmpsAudioStream stream = stream(writes)) {
            SmpsDriver driver = stream.logicalDriver();
            SmpsDriverSession session = session(stream);

            assertSame(driver, stream.logicalDriver());
            assertSame(driver, session.logicalDriverForTesting());
            assertEquals(202, writes.get(),
                    "legacy compatibility boot must happen exactly once");

            try (OwnedSmpsAudioStream other = stream(new AtomicInteger())) {
                assertNotSame(session(other).physicalIdentityForTesting(),
                        session.physicalIdentityForTesting());
                assertNotSame(other.logicalDriver(), driver);
            }
        }
    }

    @Test
    void directReadKeepsSampleCadenceOutsideOuterFrameService() {
        try (OwnedSmpsAudioStream stream = stream(new AtomicInteger())) {
            SmpsDriverSession session = session(stream);
            short[] pcm = new short[128];

            assertEquals(pcm.length, stream.read(pcm));
            assertEquals(0, session.serviceInvocationCountForTesting(),
                    "direct reads must not invent a V-blank service");
            assertEquals(64, session.renderedStereoFramesForTesting());
            assertEquals(1, session.renderInvocationCountForTesting(),
                    "a safe 64-frame direct-read span stays batched");
        }
    }

    @Test
    void closeDoesNotEmitTheGlobalStopProgram() {
        AtomicInteger writes = new AtomicInteger();
        OwnedSmpsAudioStream stream = stream(writes);
        int initializedWrites = writes.get();

        stream.close();
        stream.close();

        assertEquals(initializedWrites, writes.get(),
                "adapter close is resource release, not global stop");
    }

    private static OwnedSmpsAudioStream stream(AtomicInteger writes) {
        SmpsPhysicalDevice.Settings settings =
                new SmpsPhysicalDevice.Settings(44_100, false, false);
        return new OwnedSmpsAudioStream(
                "test", 0, settings,
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE,
                new ChipWriteObserver() {
                    @Override
                    public void onYm2612Write(
                            int port, int register, int value) {
                        writes.incrementAndGet();
                    }

                    @Override
                    public void onPsgWrite(int value) {
                        writes.incrementAndGet();
                    }
                });
    }

    private static SmpsDriverSession session(
            OwnedSmpsAudioStream stream) {
        try {
            Field field = OwnedSmpsAudioStream.class
                    .getDeclaredField("session");
            field.setAccessible(true);
            return (SmpsDriverSession) field.get(stream);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
