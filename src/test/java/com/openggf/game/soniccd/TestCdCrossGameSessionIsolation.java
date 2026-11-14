package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGetTexImage;
import static org.lwjgl.opengl.GL11.glGetTexLevelParameteri;

/**
 * A Sonic CD stage started after another game's session in the same process starts in the same
 * state as one started first. The master title screen switches games in one process, and Surefire
 * reuses one JVM for many classes.
 *
 * <p>The leak this pins: S3K's V-int publication ({@code LevelSpritePresentation.publish}) latches
 * level palette uploads in the process-wide {@code GraphicsManager}. Only {@code SpriteTablePublication}
 * profiles publish or release that latch, so a following Sonic CD session kept every level palette
 * write pending for good: palette cycles never reached the GPU palette (PPZ clouds, TTZ water and
 * wall colours, CCZ and MMZ background), while player and camera replay were untouched. The session
 * boundary now starts unlatched.
 *
 * <p>Compared state, at stage start and after 120 level frames: the upload latch, the lines left
 * pending, the GPU palette texture as uploaded, the level frame counter, the stage timer and the
 * camera. A deliberate re-latch after the boot is the negative control: the comparison must see it.
 */
@EnabledOnOs(OS.LINUX)
class TestCdCrossGameSessionIsolation {
    private static final int FRAMES = 120;
    private static final int S3K_FRAMES = 30;

    /** One observation of the stage-start state; {@code palette} is the uploaded GPU palette. */
    record StageState(int step, boolean latched, String pendingLines, int levelFrame, long timerFrames,
                      int cameraX, int cameraY, int paletteHash) { }

    @Test
    void sonicCdStageStartsTheSameAfterAnS3kSession() throws Exception {
        Path cue = CdTestMedia.usaCue();
        String s3kRom = System.getProperty("s3k.rom.path");
        Assumptions.assumeTrue(s3kRom != null && Files.isRegularFile(Path.of(s3kRom)),
                "Set s3k.rom.path to the S3&K locked-on ROM");
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try {
            List<StageState> first = sonicCdStage(cue, graphics -> { });

            boolean s3kLatched;
            try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
                var loop = boot.boot(Path.of(s3kRom), 0, 0);
                for (int i = 0; i < S3K_FRAMES; i++) loop.step();
                s3kLatched = latched(GameServices.graphics());
            }
            assertTrue(s3kLatched, "the S3K session must have latched palette uploads, or this test proves nothing");

            List<StageState> afterS3k = sonicCdStage(cue, graphics -> { });
            assertEquals(first, afterS3k, "Sonic CD stage state after an S3K session in the same process");

            // Negative control: the leaked latch, reproduced directly, must change the compared state.
            List<StageState> relatched = sonicCdStage(cue, PaletteUploadPresentation::publishAndLatch);
            assertNotEquals(first, relatched, "the comparison must detect a latched palette");
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }

    /** Boots R11A, applies {@code afterBoot} to the graphics manager, and observes frames 0 and 120. */
    private static List<StageState> sonicCdStage(Path cue, Consumer<GraphicsManager> afterBoot) throws Exception {
        List<StageState> out = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(),
                    SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A), SonicCdZoneRegistry.actIndex(CdStagePayload.R11A));
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            afterBoot.accept(GameServices.graphics());
            out.add(observe(0));
            for (int i = 1; i <= FRAMES; i++) loop.step();
            out.add(observe(FRAMES));
        }
        return out;
    }

    private static StageState observe(int step) throws Exception {
        GraphicsManager graphics = GameServices.graphics();
        var level = GameServices.level();
        var camera = GameServices.camera();
        return new StageState(step, latched(graphics), pendingLines(graphics), level.getFrameCounter(),
                level.getLevelGamestate().getTimerFrames(), camera.getX(), camera.getY(), paletteHash(graphics));
    }

    private static Object paletteOwner(GraphicsManager graphics) throws Exception {
        Method owner = GraphicsManager.class.getDeclaredMethod("paletteTextures");
        owner.setAccessible(true);
        return owner.invoke(graphics);
    }

    private static Object ownerField(GraphicsManager graphics, String name) throws Exception {
        Object owner = paletteOwner(graphics);
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static boolean latched(GraphicsManager graphics) throws Exception {
        return (Boolean) ownerField(graphics, "paletteUploadLatched");
    }

    private static String pendingLines(GraphicsManager graphics) throws Exception {
        boolean[] pending = (boolean[]) ownerField(graphics, "pendingPaletteUploads");
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < pending.length; i++) if (pending[i]) lines.append(i).append(' ');
        return lines.toString().trim();
    }

    /** The combined palette texture as the GPU holds it. */
    private static int paletteHash(GraphicsManager graphics) {
        Integer id = graphics.getCombinedPaletteTextureId();
        if (id == null) return 0;
        glBindTexture(GL_TEXTURE_2D, id);
        int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] bytes = new byte[pixels.remaining()];
        pixels.get(bytes);
        return Arrays.hashCode(bytes);
    }
}
