package com.openggf.mods.code;

import com.openggf.control.InputHandler;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.mods.ModManifest;
import com.openggf.mods.ModManifestParser;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.mods.scene.host.SceneServices;
import com.openggf.tools.modsdk.GgfModCli;
import com.openggf.mods.testing.ModTestKit;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.configuration.SonicConfigurationService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.ToolProvider;

/**
 * Builds an example mod from its source tree ({@code examples/<name>}), packages and validates
 * it through {@code ggfmod}, registers it as the engine would, and runs its startup scene
 * against the current session. Tests and {@link ExampleModCapture} use it; it is not part of
 * any mod. The mod's id, base game and entry point come from its manifest.
 *
 * <p>Origin: the Slay the Robotnik example mod's harness (2026-10-05), generalised to any
 * example mod with a startup scene on 2026-10-06.
 */
public final class ExampleModHarness implements AutoCloseable {
    private final ModManifest manifest;
    private final ModTestKit kit;
    private final ModSceneHost host = new ModSceneHost();
    private final InputHandler input = new InputHandler();
    private final List<String> exits = new ArrayList<>();

    private ExampleModHarness(ModManifest manifest, ModTestKit kit) {
        this.manifest = manifest;
        this.kit = kit;
    }

    /**
     * Compiles {@code project/src/main/java} into {@code work}, packages it with its resources
     * through {@code ggfmod package} (which runs the mod validator) and registers it.
     */
    public static ExampleModHarness build(Path project, Path work) throws Exception {
        ModManifest manifest = new ModManifestParser().parse(
                Files.readAllBytes(project.resolve("src/main/resources/META-INF/openggf-mod.yaml")));
        Path classes = Files.createDirectories(work.resolve("classes"));
        List<String> args = new ArrayList<>(List.of("--release", "21", "-cp", System.getProperty("java.class.path"),
                "-d", classes.toString()));
        try (Stream<Path> files = Files.walk(project.resolve("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)) != 0) {
            throw new IllegalStateException(manifest.id() + " failed to compile");
        }
        copyTree(project.resolve("src/main/resources"), classes);
        generateResources(project, classes);
        Path jar = work.resolve(manifest.id() + ".jar");
        if (GgfModCli.run(new String[] {"package", "--input", classes.toString(), "--out", jar.toString()},
                System.out) != 0) {
            throw new IllegalStateException(manifest.id() + " failed to package");
        }
        ModTestKit kit = ModTestKit.openTrusted(work, work.resolve("storage"),
                LogicalRomResolver.fromRomManager(GameServices.rom()),
                SonicConfigurationService.createStandalone(), List.of());
        return new ExampleModHarness(manifest, kit);
    }

    /** Same optional trusted generator convention as both example build launchers. */
    static void generateResources(Path project, Path classes) throws Exception {
        Path generator = project.resolve("generate_resources.py").toAbsolutePath();
        if (!Files.isRegularFile(generator)) return;
        Process process = new ProcessBuilder(System.getenv().getOrDefault("PYTHON_BIN", "python3"),
                generator.toString(), classes.toAbsolutePath().toString())
                .directory(project.toAbsolutePath().toFile()).inheritIO().start();
        if (process.waitFor() != 0) throw new IllegalStateException("Resource generation failed: " + project);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        try (Stream<Path> files = Files.walk(from)) {
            for (Path p : files.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public ModManifest manifest() {
        return manifest;
    }

    /** Faults the engine's fault boundary caught from the mod, by owner (empty when it ran cleanly). */
    public Map<String, List<com.openggf.mods.ModFinding>> findings() {
        return kit.findings();
    }

    public ModRegistrationPlan plan() {
        return kit.plan(manifest.id());
    }

    /** The module the engine would run with the mod applied on top of {@code base}. */
    public GameModule apply(GameModule base) {
        return kit.launch(base, new GameplayLaunchRequest(base.getGameCode(), "sonic", List.of()));
    }

    /** Opens the startup scene with ROM art from the current session and storage under {@code saves}, silently. */
    public void open(GameModule effective, Path saves, int width, int height) {
        open(effective, saves, width, height, false);
    }

    /**
     * Opens the startup scene with ROM art from the current session and storage under
     * {@code saves}; with {@code audio}, its music and sound effects go to the session's audio
     * manager (for recording), otherwise nowhere.
     */
    public void open(GameModule effective, Path saves, int width, int height, boolean audio) {
        OwnedSceneFactory factory = effective.getGameService(OwnedSceneFactory.class);
        if (factory == null) {
            throw new IllegalStateException(manifest.id() + " registers no startup scene");
        }
        try {
            // The same ROM art the engine gives the scene: the module's characters, zone pictures
            // and title cards.
            SceneServices services = new SceneServices(audio ? GameServices.audio() : null,
                    SceneRomArtFactory.forModule(effective, GameServices.rom().getRom()),
                    saves, (x, y) -> new int[] {(int) x, (int) y, 1}, () -> exits.add("game"),
                    () -> exits.add("master"), new com.openggf.mods.scene.host.SceneRomLibrary(
                            effective, GameServices.rom().getRom(), GameServices.rom()));
            host.open(factory, services, width, height);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ModSceneHost host() {
        return host;
    }

    public InputHandler input() {
        return input;
    }

    /** Where the scene asked to go ("game" or "master"), in order. */
    public List<String> exits() {
        return exits;
    }

    /** One tick with the current input state, advancing key edges afterwards. */
    public void tick() {
        // The game loop samples the mapped controls each frame; without this, directions never arrive.
        input.refreshLogicalSnapshot();
        host.update(input);
        input.update();
    }

    /** Presses and releases a key over two ticks. */
    public void press(int glfwKey) {
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        tick();
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        tick();
    }

    /**
     * Sends a debug command to the open scene through {@code DebuggableScene}, inside the mod's
     * fault boundary; false when the scene has no debug entry or did not take the command.
     */
    public boolean debugJump(String command) {
        return host.debugJump(command);
    }

    /** The open scene object (from the mod's class loader), for tests that drive it directly. */
    public Object scene() {
        return com.openggf.mods.scene.host.SceneHostTestAccess.scene(host);
    }

    public ClassLoader loader() {
        try {
            return kit.loader(manifest.id());
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Example entry point was not loaded", failure);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            host.close();
        } finally {
            kit.close();
        }
    }
}
