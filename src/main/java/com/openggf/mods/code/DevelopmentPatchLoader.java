package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.ModManifest;
import com.openggf.mods.ModManifestParser;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import java.util.zip.ZipFile;

/**
 * Applies one packaged patch mod to a game module the way the launcher does for an enabled,
 * trusted mod, without the repository, catalogue or trust prompts. A development tool for
 * filming a mod headlessly ({@code GameplayCaptureTool --mod}); the launcher never uses it.
 *
 * <p>Origin: Sonic Survivors mod work, 2026-10-06.
 */
public final class DevelopmentPatchLoader {
    private DevelopmentPatchLoader() { }

    /**
     * Returns a decorator that registers the jar's entrypoint and wraps a module with its
     * object registrations and game patches. The jar's classes stay loaded for the process.
     */
    public static UnaryOperator<GameModule> fromJar(Path jar) throws IOException {
        Path absolute = jar.toAbsolutePath().normalize();
        ModManifest manifest;
        try (var zip = new ZipFile(absolute.toFile())) {
            var entry = zip.getEntry("META-INF/openggf-mod.yaml");
            if (entry == null) throw new IOException("No META-INF/openggf-mod.yaml in " + absolute);
            try (var in = zip.getInputStream(entry)) {
                manifest = new ModManifestParser().parse(in.readAllBytes());
            }
        } catch (com.openggf.mods.ModManifestException e) {
            throw new IOException("Invalid manifest in " + absolute + ": " + e.getMessage(), e);
        }
        if (manifest.entrypoint() == null) throw new IOException(absolute + " has no code entrypoint");
        var loader = new URLClassLoader(new java.net.URL[]{absolute.toUri().toURL()},
                DevelopmentPatchLoader.class.getClassLoader());
        // The declared asset root must contain the jar itself.
        Path assetsRoot = absolute.getParent();
        ModRegistrationPlan plan;
        try (var assets = ModAssetRoot.jar(assetsRoot, absolute, ModInputLimits.production())) {
            var context = new ModContext(manifest.id(), manifest.baseGame(), assets);
            var mod = (GgfMod) loader.loadClass(manifest.entrypoint()).getConstructor().newInstance();
            mod.register(context);
            plan = context.freeze();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IOException("Could not load entrypoint " + manifest.entrypoint(), e);
        }
        ModRegistrationPlan frozen = plan;
        return base -> {
            GameModule effective = new ModBackedGamePatch(frozen).apply(base, null);
            for (GamePatch patch : frozen.explicitPatches()) effective = patch.apply(effective, null);
            return effective;
        };
    }
}
