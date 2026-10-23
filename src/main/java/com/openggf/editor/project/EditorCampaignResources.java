package com.openggf.editor.project;

import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.runtime.StageplayAssets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/** Copies only declared scene dependencies from an immutable bounded project asset snapshot. */
public final class EditorCampaignResources {
    private EditorCampaignResources() { }

    public static void copy(Path sourceProjectParent, Path staging, List<String> resources) throws IOException {
        var scenes = new LinkedHashSet<>(List.copyOf(resources));
        if (scenes.isEmpty()) return;
        if (scenes.size() > StageplayAssets.MAX_ASSETS) throw new IOException("Too many authored scene resources");
        Path source = sourceProjectParent.toAbsolutePath().normalize();
        Path output = staging.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(output) || !Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Scene export staging must be an owned regular directory");
        var limits = ModInputLimits.production();
        var loader = ResourceLoader.forModAssetsOnly();
        var copied = new LinkedHashSet<String>();
        long total = 0;
        try (var assets = ModAssetRoot.directory(source.getParent(), source, limits, DirectoryAccess.DEVELOPMENT)) {
            for (String scene : scenes) {
                ModAssetRoot.requireNormalizedEntry(scene);
                if (!scene.endsWith(".stageplay")) throw new IOException("Authored scene must end in .stageplay: " + scene);
                byte[] documentBytes = loader.loadSingle(LoadOp.modAssetBase(assets, scene));
                var document = StageplayStore.decode(documentBytes);
                String prefix = scene.contains("/") ? scene.substring(0, scene.lastIndexOf('/') + 1) : "";
                var paths = new LinkedHashSet<String>();
                paths.add(scene);
                for (String image : StageplayAssets.imagePaths(document)) paths.add(resolve(prefix, image));
                for (var cue : document.audio()) if (cue.kind() == StageplayDocument.AudioKind.VOICE)
                    paths.add(resolve(prefix, cue.asset()));
                if (!document.set().levelProject().isEmpty()) paths.add(resolve(prefix, document.set().levelProject()));
                for (var change : document.sceneChanges()) if (!change.set().levelProject().isEmpty())
                    paths.add(resolve(prefix, change.set().levelProject()));
                for (String path : paths) {
                    if (!copied.add(path)) continue;
                    if (copied.size() > StageplayAssets.MAX_ASSETS) throw new IOException("Scene dependencies exceed the asset count budget");
                    byte[] bytes = path.equals(scene) ? documentBytes : loader.loadSingle(LoadOp.modAssetBase(assets, path));
                    long maximum = path.endsWith(".ogfproject") ? limits.maxAssetBytes() : StageplayAssets.MAX_ENCODED_BYTES;
                    if (bytes.length > maximum) throw new IOException("Scene asset exceeds the encoded size budget: " + path);
                    total += bytes.length;
                    if (total > StageplayAssets.MAX_TOTAL_BYTES) throw new IOException("Scene assets exceed the 64 MiB export budget");
                    publish(output, path, bytes, maximum);
                }
            }
        }
    }

    private static String resolve(String prefix, String relative) {
        return ModAssetRoot.requireNormalizedEntry(prefix + ModAssetRoot.requireNormalizedEntry(relative));
    }

    private static void publish(Path output, String entry, byte[] bytes, long maximum) throws IOException {
        Path destination = output.resolve(ModAssetRoot.requireNormalizedEntry(entry));
        Path current = output;
        for (Path component : output.relativize(destination)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) throw new IOException("Scene export cannot follow staged symbolic links: " + entry);
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.size(destination) > maximum)
                throw new IOException("Scene asset collides with staged content: " + entry);
            try (var input = Files.newInputStream(destination)) {
                if (!Arrays.equals(bytes, input.readNBytes(Math.toIntExact(maximum + 1))))
                    throw new IOException("Different campaign projects provide conflicting scene assets: " + entry);
            }
            return;
        }
        Files.createDirectories(destination.getParent());
        Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
    }
}
