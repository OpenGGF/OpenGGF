package com.openggf.mods.code;

import com.openggf.control.InputHandler;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.scene.ModSceneFactory;
import com.openggf.mods.scene.ModSceneHost;
import com.openggf.mods.scene.SceneRomArtFactory;
import com.openggf.mods.scene.SceneServices;
import com.openggf.tools.modsdk.GgfModCli;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.ToolProvider;

/**
 * Builds the Slay the Robotnik example from source and runs its startup scene against the
 * current S3K session, for tests and the screenshot capture tool. Not part of the mod.
 */
public final class SlayTheRobotnikHarness implements AutoCloseable {
    public static final Path PROJECT = Path.of("examples/slay-the-robotnik");

    private final URLClassLoader loader;
    private final ModRegistrationPlan plan;
    private final ModSceneHost host = new ModSceneHost();
    private final InputHandler input = new InputHandler();
    private final List<String> exits = new ArrayList<>();
    private final ModRuntimeFindingStore findings = new ModRuntimeFindingStore();

    private SlayTheRobotnikHarness(URLClassLoader loader, ModRegistrationPlan plan) {
        this.loader = loader;
        this.plan = plan;
    }

    /** Compiles {@code examples/slay-the-robotnik} into {@code work} and registers it as the engine would. */
    public static SlayTheRobotnikHarness build(Path work) throws Exception {
        Path classes = Files.createDirectories(work.resolve("classes"));
        List<String> args = new ArrayList<>(List.of("--release", "21", "-cp", System.getProperty("java.class.path"),
                "-d", classes.toString()));
        try (Stream<Path> files = Files.walk(PROJECT.resolve("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("Slay the Robotnik failed to compile");
        }
        copyTree(PROJECT.resolve("src/main/resources"), classes);
        Path jar = work.resolve("slay-the-robotnik.jar");
        if (GgfModCli.run(new String[] {"package", "--input", classes.toString(), "--out", jar.toString()},
                System.out) != 0) {
            throw new IllegalStateException("Slay the Robotnik failed to package");
        }
        URLClassLoader loader = new URLClassLoader(new java.net.URL[] {jar.toUri().toURL()},
                SlayTheRobotnikHarness.class.getClassLoader());
        ModRegistrationPlan plan;
        try (var assets = ModAssetRoot.jar(work, jar, ModInputLimits.production())) {
            ModContext context = new ModContext("slay-the-robotnik", "s3k", assets);
            ((GgfMod) loader.loadClass("slaytherobotnik.SlayTheRobotnikMod").getConstructor().newInstance())
                    .register(context);
            plan = context.freeze();
        }
        return new SlayTheRobotnikHarness(loader, plan);
    }

    private static void copyTree(Path from, Path to) throws IOException {
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

    /** Faults the engine's fault boundary caught from the mod, by owner (empty when it ran cleanly). */
    public Map<String, List<com.openggf.mods.ModFinding>> findings() {
        return findings.snapshot();
    }

    public ModRegistrationPlan plan() {
        return plan;
    }

    /** The module the engine would run with the mod applied on top of {@code base}. */
    public GameModule apply(GameModule base) {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule effective = new ModBackedGamePatch(plan, boundary).apply(base, null);
        for (var patch : plan.explicitPatches()) {
            effective = patch.apply(effective, null);
        }
        return effective;
    }

    /** Opens the startup scene with ROM art from the current session and storage under {@code saves}. */
    public void open(GameModule effective, Path saves, int width, int height) {
        ModSceneFactory factory = effective.getGameService(ModSceneFactory.class);
        var players = new java.util.function.Supplier<com.openggf.data.PlayerSpriteArtProvider>() {
            private com.openggf.data.PlayerSpriteArtProvider cached;

            @Override
            public com.openggf.data.PlayerSpriteArtProvider get() {
                if (cached == null) {
                    var session = SessionManager.getCurrentWorldSession();
                    Object game = effective.createGame(session.getDataSource());
                    cached = (com.openggf.data.PlayerSpriteArtProvider) game;
                }
                return cached;
            }
        };
        try {
            var rom = GameServices.rom().getRom();
            SceneServices services = new SceneServices(null, SceneRomArtFactory.create(rom, GameId.S3K, players,
                    effective::loadTailsTailArt, new com.openggf.game.sonic3k.Sonic3kZoneArt(rom)),
                    saves, (x, y) -> new int[] {(int) x, (int) y, 1}, () -> exits.add("game"),
                    () -> exits.add("master"));
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

    /** The open scene object (from the mod's class loader), unwrapped from the engine's fault-boundary wrapper. */
    public Object scene() throws ReflectiveOperationException {
        var field = ModSceneHost.class.getDeclaredField("scene");
        field.setAccessible(true);
        Object scene = field.get(host);
        while (scene != null && scene.getClass().getName().startsWith("com.openggf.")) {
            Object inner = null;
            for (var f : scene.getClass().getDeclaredFields()) {
                if (com.openggf.mods.scene.ModScene.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    inner = f.get(scene);
                }
            }
            if (inner == null) {
                break;
            }
            scene = inner;
        }
        return scene;
    }

    public ClassLoader loader() {
        return loader;
    }

    @Override
    public void close() throws IOException {
        host.close();
        loader.close();
    }
}
