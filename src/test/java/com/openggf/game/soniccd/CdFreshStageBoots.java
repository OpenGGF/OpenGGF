package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.audio.NullAudioBackend;
import com.openggf.game.GameServices;
import com.openggf.tools.HeadlessGameBoot;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Boots each stage of a picture test in its own {@link HeadlessGameBoot} (a fresh session and GL context), as
 * {@link CdFixtureReplay} replays each fixture and as native records each movie from power-on. {@link #boot} closes the
 * previous stage's boot first; {@link #close} closes the last one.
 */
final class CdFreshStageBoots implements AutoCloseable {
    private final int width;
    private final int height;
    private HeadlessGameBoot boot;

    CdFreshStageBoots(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Closes the previous stage's boot and boots {@code payload} from {@code disc} in a new one, without audio. */
    GameLoop boot(CdDiscDataSource disc, CdStagePayload payload) throws Exception {
        close();
        boot = new HeadlessGameBoot(width, height);
        GameLoop loop = boot.boot(disc, new SonicCdGameModule(), SonicCdZoneRegistry.zoneIndex(payload),
                SonicCdZoneRegistry.actIndex(payload));
        assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
        return loop;
    }

    @Override
    public void close() throws Exception {
        if (boot != null) {
            HeadlessGameBoot closing = boot;
            boot = null;
            closing.close();
        }
    }
}
