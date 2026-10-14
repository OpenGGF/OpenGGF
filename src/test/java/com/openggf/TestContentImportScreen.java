package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.editor.project.EditorProjectDocument;
import com.openggf.game.GameId;
import com.openggf.graphics.PixelFont;
import com.openggf.mods.code.ModLevelDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestContentImportScreen {
    @TempDir Path directory;

    @Test
    void droppingInspectsOffTheInputThreadAndRequiresAFreshConfirmationBeforeInstalling() throws Exception {
        var service = service();
        var work = new QueuedWork();
        var host = new RecordingHost();
        var screen = screen(service, host, work);
        var input = new InputHandler();
        screen.drop(List.of(mod("example")));
        assertTrue(screen.isOpen());
        assertTrue(screen.busy());
        assertFalse(Files.exists(modRoot()));
        work.runNext();
        assertFalse(Files.exists(modRoot()), "inspection must never install the package");
        press(screen, input, GLFW_KEY_ENTER);
        assertTrue(screen.isOpen(), "publishing a ready candidate must consume the existing confirm edge");
        assertEquals(0, host.installed.size());
        press(screen, input, GLFW_KEY_ENTER);
        assertTrue(screen.busy());
        assertTrue(screen.isOpen());
        work.runNext();
        assertTrue(host.installed.isEmpty(), "the host callback belongs to the update thread");
        screen.update(input);
        assertFalse(screen.isOpen());
        assertEquals(List.of(modRoot().resolve("example.jar")), host.installed);
        assertTrue(Files.exists(host.installed.getFirst()));
        assertFalse(Files.exists(modRoot().resolve("modstate.json")), "import does not enable or trust a mod");
    }

    @Test
    void multipleFileDropReportsOneFileLimitAndSchedulesNoInspection() throws Exception {
        var work = new QueuedWork();
        var screen = screen(service(), new RecordingHost(), work);
        screen.drop(List.of(mod("first"), mod("second")));
        assertTrue(screen.isOpen());
        assertFalse(screen.busy());
        assertTrue(screen.error().contains("one"));
        assertTrue(work.jobs.isEmpty());
        assertFalse(Files.exists(modRoot()));
    }

    @Test
    void replacingAReadyDropClosesItsLeaseAndOnlyTheNewCandidateCanInstall() throws Exception {
        var service = service();
        var inspected = new ArrayList<ContentImportService.PreparedImport>();
        var work = new QueuedWork();
        var host = new RecordingHost();
        var screen = new ContentImportScreen(path -> {
            var candidate = service.inspect(path); inspected.add(candidate); return candidate;
        }, service::installMod, host, work);
        screen.drop(List.of(mod("first")));
        work.runNext();
        screen.update(new InputHandler());
        screen.drop(List.of(mod("second")));
        assertThrows(IllegalStateException.class, inspected.getFirst()::modDescriptor);
        work.runNext();
        screen.update(new InputHandler());
        press(screen, new InputHandler(), GLFW_KEY_ENTER);
        work.runNext(); screen.update(new InputHandler());
        assertEquals(List.of(modRoot().resolve("second.jar")), host.installed);
        assertFalse(Files.exists(modRoot().resolve("first.jar")));
        assertThrows(IllegalStateException.class, inspected.getLast()::modDescriptor);
    }

    @Test
    void cancelingWhileInspectionIsRunningClosesTheLateCandidate() throws Exception {
        var service = service();
        var inspected = new AtomicReference<ContentImportService.PreparedImport>();
        var worker = new AtomicReference<Thread>();
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var host = new RecordingHost();
        var screen = new ContentImportScreen(path -> {
            var candidate = service.inspect(path);
            inspected.set(candidate); acquired.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                candidate.close(); throw new IllegalStateException("Test did not release the inspector");
            }
            return candidate;
        }, service::installMod, host, runnable -> worker.set(Thread.ofVirtual().start(runnable)));
        try {
            screen.drop(List.of(mod("late")));
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            screen.close();
            release.countDown();
            worker.get().join(5_000);
            assertFalse(worker.get().isAlive());
            assertFalse(screen.isOpen());
            assertThrows(IllegalStateException.class, inspected.get()::modDescriptor);
            assertTrue(host.installed.isEmpty());
            assertFalse(Files.exists(modRoot()));
        } finally {
            release.countDown(); screen.close();
        }
    }

    @Test
    void replacingAnUnpublishedInspectionClosesItsLeaseWithoutPublishingIt() throws Exception {
        var service = service();
        var inspected = new ArrayList<ContentImportService.PreparedImport>();
        var work = new QueuedWork();
        var screen = new ContentImportScreen(path -> {
            var candidate = service.inspect(path); inspected.add(candidate); return candidate;
        }, service::installMod, new RecordingHost(), work);
        screen.drop(List.of(mod("old")));
        work.runNext();
        screen.drop(List.of(mod("new")));
        assertThrows(IllegalStateException.class, inspected.getFirst()::modDescriptor);
        screen.close();
        work.runNext();
        assertEquals(1, inspected.size(), "a canceled queued request need not read its source");
    }

    @Test
    void disabledModInstallKeepsTheCandidateAndShowsHowToContinue() throws Exception {
        var service = service();
        var work = new QueuedWork();
        var host = new RecordingHost(); host.allowMods = false;
        var screen = screen(service, host, work);
        screen.drop(List.of(mod("title-only")));
        work.runNext(); screen.update(new InputHandler());
        press(screen, new InputHandler(), GLFW_KEY_ENTER);
        assertTrue(screen.isOpen());
        assertTrue(screen.error().contains("title screen"));
        assertTrue(host.installed.isEmpty());
        assertFalse(Files.exists(modRoot()));
        screen.close();
    }

    @Test
    void confirmedInstallKeepsItsLeaseThroughBackAndShutdownCleanup() throws Exception {
        var service = service();
        var inspected = new AtomicReference<ContentImportService.PreparedImport>();
        var work = new QueuedWork();
        var host = new RecordingHost();
        var screen = new ContentImportScreen(path -> {
            var candidate = service.inspect(path); inspected.set(candidate); return candidate;
        }, service::installMod, host, work);
        screen.drop(List.of(mod("finishing")));
        work.runNext(); screen.update(new InputHandler());
        var input = new InputHandler();
        press(screen, input, GLFW_KEY_ENTER);
        press(screen, input, GLFW_KEY_ESCAPE);
        assertTrue(screen.isOpen(), "a confirmed publication finishes before navigation resumes");
        screen.drop(List.of(mod("replacement")));
        assertTrue(screen.error().contains("in progress"));
        assertEquals(1, work.jobs.size());
        screen.close();
        assertDoesNotThrow(inspected.get()::modDescriptor, "shutdown must not close a snapshot still used by its worker");
        work.runNext();
        assertTrue(Files.exists(modRoot().resolve("finishing.jar")));
        assertFalse(Files.exists(modRoot().resolve("replacement.jar")));
        assertThrows(IllegalStateException.class, inspected.get()::modDescriptor);
        assertTrue(host.installed.isEmpty(), "cleanup never invokes a detached host callback");
    }

    @Test
    void hostFailureClosesThePreparedLeaseAndReportsTheInstalledFileWithoutReinstalling() throws Exception {
        var service = service();
        var inspected = new AtomicReference<ContentImportService.PreparedImport>();
        var work = new QueuedWork();
        var host = new RecordingHost(); host.failCallback = true;
        var screen = new ContentImportScreen(path -> {
            var candidate = service.inspect(path); inspected.set(candidate); return candidate;
        }, service::installMod, host, work);
        screen.drop(List.of(mod("callback-failure")));
        work.runNext(); screen.update(new InputHandler());
        press(screen, new InputHandler(), GLFW_KEY_ENTER);
        work.runNext(); screen.update(new InputHandler());
        assertTrue(screen.isOpen());
        assertTrue(screen.error().contains("refresh failed"));
        assertTrue(Files.exists(modRoot().resolve("callback-failure.jar")));
        assertThrows(IllegalStateException.class, inspected.get()::modDescriptor);
        assertEquals(1, host.installed.size());
        assertEquals(0, host.closed);
        screen.close();
        assertEquals(1, host.closed, "closing a failed handoff lets the host restore its previous editor view");
        screen.close();
        assertEquals(1, host.closed, "cleanup must not announce the same closing twice");
    }

    @Test
    void failedInspectionReportsAnErrorAndLeavesTheScreenReadyForAnotherDrop() throws Exception {
        var work = new QueuedWork();
        var screen = screen(service(), new RecordingHost(), work);
        screen.drop(List.of(directory.resolve("missing.zip")));
        work.runNext(); screen.update(new InputHandler());
        assertTrue(screen.isOpen());
        assertFalse(screen.busy());
        assertNotNull(screen.error());
        screen.drop(List.of(mod("corrected")));
        assertNull(screen.error());
        work.runNext(); screen.update(new InputHandler());
        assertFalse(screen.busy());
        screen.close();
    }

    @Test
    void browseReusesPathTextCaptureAndBackCancelsEachModalWithoutLeakingTheClosingFrame() {
        var screen = screen(service(), new RecordingHost(), new QueuedWork());
        var input = new InputHandler();
        screen.open();
        press(screen, input, GLFW_KEY_ENTER);
        assertFalse(screen.capturesText());
        press(screen, input, GLFW_KEY_ENTER);
        assertTrue(screen.capturesText());
        press(screen, input, GLFW_KEY_ESCAPE);
        assertFalse(screen.capturesText());
        assertTrue(screen.isOpen());
        press(screen, input, GLFW_KEY_ESCAPE);
        assertTrue(screen.isOpen());
        press(screen, input, GLFW_KEY_ESCAPE);
        assertFalse(screen.isOpen());
        assertFalse(screen.update(input));
    }

    @Test
    void typedPathUsesTheSameInspectionAndConfirmationFlowAsADrop() throws Exception {
        var work = new QueuedWork();
        var screen = screen(service(), new RecordingHost(), work);
        var input = new InputHandler();
        screen.open();
        press(screen, input, GLFW_KEY_ENTER);
        press(screen, input, GLFW_KEY_ENTER);
        mod("typed").toString().codePoints().forEach(codepoint -> MenuInput.handleCharEvent(input, codepoint));
        input.refreshLogicalSnapshot(); screen.update(input); input.update();
        press(screen, input, GLFW_KEY_ENTER);
        press(screen, input, GLFW_KEY_DOWN);
        press(screen, input, GLFW_KEY_ENTER);
        assertTrue(screen.busy());
        assertEquals(1, work.jobs.size());
        work.runNext(); screen.update(input);
        assertFalse(Files.exists(modRoot()));
        screen.close();
    }

    @Test
    void landingAndReviewTextStayWithinNativeAndWideViewports() throws Exception {
        var work = new QueuedWork();
        var screen = screen(service(), new RecordingHost(), work);
        screen.open();
        for (int width : List.of(320, 426, 640)) assertFits(screen, width);
        screen.drop(List.of(mod("a-long-example-mutator-identifier")));
        work.runNext(); screen.update(new InputHandler());
        for (int width : List.of(320, 426, 640)) assertFits(screen, width);
        screen.close();
    }

    private ContentImportService service() { return new ContentImportService(modRoot()); }
    private Path modRoot() { return directory.resolve("installed"); }
    private static ContentImportScreen screen(ContentImportService service, RecordingHost host, Executor work) {
        return new ContentImportScreen(service::inspect, service::installMod, host, work);
    }

    private Path mod(String id) throws Exception {
        Path path = directory.resolve(id + ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("META-INF/openggf-mod.yaml"));
            output.write(("""
                    formatVersion: 1
                    id: %s
                    name: Example Mutator Package
                    version: 1.0.0
                    authors: [Test Author]
                    description: Import screen test.
                    engineApiRange: "*"
                    type: patch
                    baseGame: s2
                    dependencies: []
                    audioOverrides: {}
                    artOverrides: {}
                    """).formatted(id).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    private static void press(ContentImportScreen screen, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS); input.refreshLogicalSnapshot();
        assertTrue(screen.update(input), "an open import screen owns its closing frame");
        input.update(); input.handleKeyEvent(key, GLFW_RELEASE); input.update();
    }

    private static void assertFits(ContentImportScreen screen, int width) {
        var font = new BoundsFont();
        screen.render(font, width);
        assertFalse(font.cells.isEmpty());
        for (var cell : font.cells) {
            assertTrue(cell.x >= 0 && cell.y >= 0, cell.text);
            assertTrue(cell.x + Math.round(cell.text.length() * 9 * cell.scale) <= width, cell.text);
            assertTrue(cell.y + Math.round(10 * cell.scale) <= 224, cell.text);
        }
    }

    private static final class QueuedWork implements Executor {
        final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        @Override public void execute(Runnable command) { jobs.add(command); }
        void runNext() { jobs.removeFirst().run(); }
    }

    private static final class RecordingHost implements ContentImportScreen.Host {
        final List<Path> installed = new ArrayList<>();
        int closed;
        boolean allowMods = true, failCallback;
        @Override public void openProject(EditorProjectDocument candidate) { fail("Unexpected project import"); }
        @Override public void importLevel(ModLevelDefinition level, GameId game) { fail("Unexpected level import"); }
        @Override public void modInstalled(Path installedPath) {
            installed.add(installedPath);
            if (failCallback) throw new IllegalStateException("Mod manager refresh failed");
        }
        @Override public boolean allowedToInstallMods() { return allowMods; }
        @Override public void closed() { closed++; }
    }

    private static final class BoundsFont extends PixelFont {
        record Cell(String text, int x, int y, float scale) { }
        final List<Cell> cells = new ArrayList<>();
        @Override public void drawText(String text, int x, int y, float scale, float r, float g, float b, float a) {
            cells.add(new Cell(text, x, y, scale));
        }
    }
}
