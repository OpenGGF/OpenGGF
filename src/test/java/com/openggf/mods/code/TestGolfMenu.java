package com.openggf.mods.code;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the actual external title/HUD source, compiled against the public engine classpath. */
class TestGolfMenu {
    @TempDir static Path temp;
    static URLClassLoader loader;

    @BeforeAll static void compileExternalUi() throws Exception {
        Path project = Path.of("examples/putt-putt-paradise");
        Path source = project.resolve("src/main/java/paradise/ui");
        assertTrue(Files.isDirectory(source), "External golf menu has not been implemented");
        Path classes = Files.createDirectory(temp.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString()));
        for (Path root : List.of(source, project.resolve("src/test/java/paradise/ui"))) {
            try (var files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
            }
        }
        var output = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, output, output,
                args.toArray(String[]::new)), output.toString(StandardCharsets.UTF_8));
        loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, TestGolfMenu.class.getClassLoader());
    }
    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }
    private void check(String method) throws Exception {
        try { loader.loadClass("paradise.ui.GolfMenuChecks").getMethod(method).invoke(null); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }
    @Test void practiceActTwoReachesConsumerBeforeStandardOnePlayerExit() throws Exception { check("practice"); }
    @Test void twoLocalGolfersCanChooseDifferentOrDuplicateCharacters() throws Exception { check("characters"); }
    @Test void heldAcceptCannotEnterSetupAndLaunchInOnePress() throws Exception { check("heldAccept"); }
    @Test void joinEditorValidatesAddressAndPortWithoutStartingGameplay() throws Exception { check("joinValidation"); }
    @Test void hostCanEditPortAndTextCancelRestoresTheOldValue() throws Exception { check("hostEditing"); }
    @Test void returnToModePickerDoesNotLaunchOrLeakTypedText() throws Exception { check("backAndReset"); }
    @Test void everyConfigViewportQueuesBoundedMenuAndOverlayGeometry() throws Exception { check("renderWidths"); }
}
