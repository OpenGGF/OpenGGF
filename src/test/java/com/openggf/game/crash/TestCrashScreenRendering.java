package com.openggf.game.crash;

import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Draws the page for every fault kind at the narrowest and a widescreen width. Set
 * {@code OPENGGF_CRASH_SCREEN_SPECIMENS} to a folder to keep the pictures for review.
 */
@Isolated
class TestCrashScreenRendering {
    private static final int HEIGHT = 224;

    private static CrashScreen screen(FaultKind kind) {
        String frame = switch (kind) {
            case REWIND -> "com.openggf.game.rewind.RewindController.seekTo";
            case LEVEL_LOAD -> "com.openggf.level.LevelManager.loadZoneAndAct";
            case ENGINE -> "com.openggf.level.objects.ObjectManager.update";
        };
        var failure = new IllegalStateException("Cannot restore object slot 42: the captured spawn "
                + "is not in the level's object layout (zone 0, act 1)");
        int dot = frame.lastIndexOf('.');
        String owner = frame.substring(0, dot);
        failure.setStackTrace(new StackTraceElement[] {new StackTraceElement(owner, frame.substring(dot + 1),
                owner.substring(owner.lastIndexOf('.') + 1) + ".java", 410)});
        var report = new CrashReport(FaultKind.classify(failure), failure, Instant.EPOCH,
                CrashReport.buildFields(), List.of(new CrashReport.Field("Game", "s3k")), "");
        assertEquals(kind, report.kind());
        return new CrashScreen(report,
                Optional.of(Path.of("/games/OpenGGF/crash-reports/crash-20261114-120000-000.txt")),
                new CrashScreen.Host() {
                    public boolean openIssue(String query) { return true; }
                    public boolean copy(String text) { return true; }
                    public void returnToGames() { }
                });
    }

    @Test
    void everyGagDrawsInsideItsPanelAtEveryWidth() throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(400, HEIGHT,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
             var renderer = new CrashScreenRenderer()) {
            for (FaultKind kind : FaultKind.values()) {
                for (int width : new int[] {320, 400}) {
                    CrashScreen screen = screen(kind);
                    glViewport(0, 0, width, HEIGHT);
                    glEnable(GL_BLEND);
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                    glClearColor(0, 0, 0, 1);
                    glClear(GL_COLOR_BUFFER_BIT);
                    renderer.render(screen, new Matrix4f().ortho(0, width, 0, HEIGHT, -1, 1).get(new float[16]), width);
                    RgbaImage capture = ScreenshotCapture.captureFramebuffer(width, HEIGHT);
                    assertEquals(GL_NO_ERROR, glGetError());
                    CrashScreen.Box art = screen.artBox();
                    long artColours = distinctColours(capture, art.x(), art.y(), art.width(), art.height());
                    assertTrue(artColours > 6, kind + " art colours " + artColours);
                    long textColours = distinctColours(capture, art.x() + art.width() + 10, 37, width - art.x() - art.width() - 20, 60);
                    assertTrue(textColours > 2, kind + " text colours " + textColours);
                    export(kind.name().toLowerCase() + "-" + width, capture);
                }
            }
            glDisable(GL_BLEND);
        }
    }

    @Test
    void theQrCodeDrawsEveryModuleWhereItBelongs() throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(320, HEIGHT,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
             var renderer = new CrashScreenRenderer()) {
            CrashScreen screen = screen(FaultKind.REWIND);
            screen.toggleQr();
            glViewport(0, 0, 320, HEIGHT);
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            renderer.render(screen, new Matrix4f().ortho(0, 320, 0, HEIGHT, -1, 1).get(new float[16]), 320);
            RgbaImage capture = ScreenshotCapture.captureFramebuffer(320, HEIGHT);
            assertEquals(GL_NO_ERROR, glGetError());
            CrashQrCode code = screen.qrCode();
            CrashScreen.Box art = screen.artBox();
            int scale = code.scale(art.width());
            int tile = (code.size() + 2 * CrashQrCode.QUIET) * scale;
            int left = art.x() + (art.width() - tile) / 2 + CrashQrCode.QUIET * scale;
            int top = art.y() + (art.height() - tile) / 2 + CrashQrCode.QUIET * scale;
            for (int y = -CrashQrCode.QUIET; y < code.size() + CrashQrCode.QUIET; y++) {
                for (int x = -CrashQrCode.QUIET; x < code.size() + CrashQrCode.QUIET; x++) {
                    boolean dark = x >= 0 && y >= 0 && x < code.size() && y < code.size() && code.dark(x, y);
                    int argb = capture.argb(left + x * scale + scale / 2, top + y * scale + scale / 2);
                    int luma = ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
                    assertEquals(dark, luma < 3 * 64, "module " + x + "," + y);
                }
            }
            export("qr-320", capture);
        }
    }

    /** Distinct colours in a top-down region of a capture. */
    private static long distinctColours(RgbaImage image, int x, int y, int w, int h) {
        var colours = new java.util.HashSet<Integer>();
        for (int row = y; row < y + h; row++) {
            for (int column = x; column < x + w; column++) colours.add(image.argb(column, row));
        }
        return colours.size();
    }

    private static void export(String name, RgbaImage image) throws Exception {
        String destination = System.getenv("OPENGGF_CRASH_SCREEN_SPECIMENS");
        if (destination == null || destination.isBlank()) return;
        Path root = Path.of(destination);
        Files.createDirectories(root);
        ScreenshotCapture.savePNG(image, root.resolve(name + ".png"));
    }
}
