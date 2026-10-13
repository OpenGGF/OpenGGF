package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.GameServices;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.PatchContext;
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
        return openJar(jar);
    }

    /** A capture owns this loader until its final world has retired. */
    public static PreparedDecorator openJar(Path jar) throws IOException {
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
        try {
            var entrypoint = loader.loadClass(manifest.entrypoint()).asSubclass(GgfMod.class);
            entrypoint.getConstructor();
            return new PreparedDecorator(absolute, manifest, loader, entrypoint);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            loader.close();
            throw new IOException("Could not load entrypoint " + manifest.entrypoint(), failure);
        }
    }

    /** One jar loader, fresh transaction and callback identities for every native launch. */
    public static final class PreparedDecorator implements UnaryOperator<GameModule>, AutoCloseable {
        private final Path jar;
        private final ModManifest manifest;
        private final URLClassLoader loader;
        private final Class<? extends GgfMod> entrypoint;
        private boolean closed;

        private PreparedDecorator(Path jar, ModManifest manifest, URLClassLoader loader,
                                  Class<? extends GgfMod> entrypoint) {
            this.jar = jar;
            this.manifest = manifest;
            this.loader = loader;
            this.entrypoint = entrypoint;
        }

        @Override public GameModule apply(GameModule base) {
            return apply(base, GameplayLaunchRequest.fromConfig(GameServices.configuration(), base.getGameId().code()));
        }

        public GameModule apply(GameModule base, GameplayLaunchRequest request) {
            if (closed) throw new IllegalStateException("Development mod loader is closed");
            java.util.Objects.requireNonNull(request, "request");
            ModRegistrationPlan frozen;
            try (var assets = ModAssetRoot.jar(jar.getParent(), jar, ModInputLimits.production())) {
                var context = new ModContext(manifest.id(), manifest.baseGame(), assets);
                entrypoint.getConstructor().newInstance().register(context);
                frozen = context.freeze();
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException("Could not open development mod " + jar, failure);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not register entrypoint " + manifest.entrypoint(), failure);
            }
            // Every launch receives a distinct callback quarantine as well as fresh registration objects.
            var boundary = new ModFaultBoundary(java.util.Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                    disabled -> new com.openggf.mods.ModStateSaveResult.Saved(), disabled -> { });
            String game = base.getGameId().code();
            ModRegistrationPlan nativePlan = frozen.stockScenePlans().stream()
                    .filter(candidate -> game.equals(candidate.baseGameId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "Development mod " + manifest.id() + " does not support " + game));
            var config = GameServices.configuration();
            var logicalRoms = LogicalRomResolver.fromRomManager(GameServices.rom());
            var patchContext = new PatchContext(logicalRoms::openOrThrow, config);
            GameModule effective = new ModBackedGamePatch(nativePlan, boundary).apply(base, patchContext);
            for (GamePatch patch : nativePlan.explicitPatches()) {
                var owned = com.openggf.mods.runtime.OwnerBoundGamePatch.wrap(manifest.id(), patch, boundary);
                // Match the launch resolver: another game's decorator or a declined
                // team must not wrap this title. Stacking shared Lab decorators used
                // to expose an outer ACTIVE menu while the native intro still ran.
                if (game.equals(owned.baseGameId()) && owned.activatesFor(request))
                    effective = owned.apply(effective, patchContext);
            }
            return effective;
        }

        @Override public void close() throws IOException {
            if (closed) return;
            closed = true;
            loader.close();
        }
    }
}
