package com.openggf.graphics.shaderlib;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class TestDisplayShaderPackDiagnostics {
    private static final String ENABLED_PROPERTY = "shaderlib.diagnostic.enabled";
    private static final String ROOT_PROPERTY = "shaderlib.diagnostic.root";
    private static final String REPORT_PROPERTY = "shaderlib.diagnostic.report";
    private static final String MAX_PROPERTY = "shaderlib.diagnostic.max";

    @Test
    public void writeCompatibilityReportForLocalShaderPack() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean(ENABLED_PROPERTY),
                "Set -D" + ENABLED_PROPERTY + "=true to scan a local shader pack");

        Path root = Path.of(System.getProperty(ROOT_PROPERTY, "shaders")).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isDirectory(root), "Shader root does not exist: " + root);
        Path report = Path.of(System.getProperty(REPORT_PROPERTY,
                "target/display-shader-failures.txt")).toAbsolutePath().normalize();
        int maxEntries = Integer.getInteger(MAX_PROPERTY, Integer.MAX_VALUE);

        DisplayShaderLibrary library = DisplayShaderLibrary.scan(root);
        DisplayShaderPresetLoader loader = new DisplayShaderPresetLoader();
        List<String> failures = new ArrayList<>();
        int attempted = 0;
        int passed = 0;

        try (GlContext ignored = GlContext.open()) {
            DisplayShaderPipeline pipeline = new DisplayShaderPipeline();
            pipeline.resize(320, 224, 640, 448);
            for (DisplayShaderPresetRef ref : library.entries()) {
                if (ref.kind() == DisplayShaderPresetRef.Kind.OFF) {
                    continue;
                }
                if (attempted >= maxEntries) {
                    break;
                }
                attempted++;
                pipeline.dispose();
                pipeline.resize(320, 224, 640, 448);
                try {
                    DisplayShaderPreset preset = loader.load(ref, ShaderPhase.PRESENTATION);
                    if (pipeline.activate(preset)) {
                        passed++;
                    } else {
                        failures.add(ref.relativePath() + "\n  " + pipeline.lastActivationFailure());
                    }
                } catch (Exception e) {
                    failures.add(ref.relativePath() + "\n  " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
            pipeline.dispose();
        }

        Files.createDirectories(report.getParent());
        Files.writeString(report, reportText(root, attempted, passed, failures));
        System.out.println("Display shader diagnostic report: " + report);
        System.out.println("Attempted " + attempted + ", passed " + passed + ", failed " + failures.size());
    }

    private static String reportText(Path root, int attempted, int passed, List<String> failures) {
        StringBuilder out = new StringBuilder();
        out.append("Display shader compatibility report\n");
        out.append("Root: ").append(root).append('\n');
        out.append("Attempted: ").append(attempted).append('\n');
        out.append("Passed: ").append(passed).append('\n');
        out.append("Failed: ").append(failures.size()).append("\n\n");
        for (String failure : failures) {
            out.append(failure).append("\n\n");
        }
        return out.toString();
    }

    private static final class GlContext implements AutoCloseable {
        private final long window;

        private GlContext(long window) {
            this.window = window;
        }

        static GlContext open() {
            boolean initialized = glfwInit();
            Assumptions.assumeTrue(initialized, "GLFW init failed; skipping shader diagnostic");

            long window = 0L;
            try {
                glfwDefaultWindowHints();
                glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
                glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

                window = glfwCreateWindow(64, 64, "DisplayShaderPackDiagnostics", 0L, 0L);
                Assumptions.assumeTrue(window != 0L, "OpenGL 4.1 core context unavailable; skipping shader diagnostic");
                glfwMakeContextCurrent(window);
                GL.createCapabilities();
                return new GlContext(window);
            } catch (Throwable t) {
                if (window != 0L) {
                    glfwDestroyWindow(window);
                }
                glfwTerminate();
                Assumptions.assumeTrue(false, "OpenGL context unavailable; skipping shader diagnostic: " + t);
                return null;
            }
        }

        @Override
        public void close() {
            glfwMakeContextCurrent(0L);
            if (window != 0L) {
                glfwDestroyWindow(window);
            }
            glfwTerminate();
        }
    }
}
