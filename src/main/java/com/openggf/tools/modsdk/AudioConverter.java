package com.openggf.tools.modsdk;

import com.openggf.io.ModInputLimits;
import com.openggf.mods.ModAudioAssetValidation;
import com.openggf.mods.ModAudioManifest;
import com.openggf.mods.ModAudioManifestParser;
import com.openggf.mods.ModAudioSfx;
import com.openggf.mods.ModAudioTrack;
import com.openggf.mods.ModManifestException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashMap;

/** Validates WAV/OGG sources and publishes canonical audio resources without transcoding. */
public final class AudioConverter {
    private final ModInputLimits limits;
    public AudioConverter(){this(ModInputLimits.production());}
    AudioConverter(ModInputLimits limits){this.limits=Objects.requireNonNull(limits);}

    public Result convert(String ownerId, Path manifestPath, Path sourceRoot, Path outputRoot)
            throws IOException {
        Path root = requireDirectory(sourceRoot);
        Path requestedManifest = Objects.requireNonNull(manifestPath, "manifestPath");
        Path manifest = requireContainedFile(root, requestedManifest.isAbsolute()
                ? requestedManifest : root.resolve(requestedManifest));
        byte[] manifestBytes = readBounded(manifest, limits.maxMetadataBytes());
        ModAudioManifest parsed;
        try {
            parsed = new ModAudioManifestParser(ownerId, limits).parse(manifestBytes);
        } catch (ModManifestException error) {
            throw new IOException("Invalid audio manifest: " + error.getMessage(), error);
        }
        LinkedHashMap<Path,byte[]> assets = new LinkedHashMap<>();
        for (ModAudioTrack track : parsed.tracks()) {
            Path asset = requireContainedFile(root, root.resolve(track.assetPath()));
            byte[] encoded = readBounded(asset, limits.maxAssetBytes());
            ModAudioAssetValidation.decodeAndValidate(track, encoded, limits);
            assets.put(asset,encoded);
        }
        for (ModAudioSfx sfx : parsed.sfx()) {
            Path asset = requireContainedFile(root, root.resolve(sfx.assetPath()));
            byte[] encoded = readBounded(asset, limits.maxAssetBytes());
            ModAudioAssetValidation.decodeAndValidate(sfx, encoded, limits);
            assets.put(asset, encoded);
        }

        Path target = Objects.requireNonNull(outputRoot, "outputRoot").toAbsolutePath().normalize();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Audio output already exists: " + target);
        Path parent = Objects.requireNonNull(target.getParent(), "audio output parent");
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, target.getFileName() + ".tmp-");
        List<Path> copies = new ArrayList<>(assets.keySet());
        copies.sort(Comparator.comparing(path -> root.relativize(path).toString().replace('\\','/')));
        try {
            for (Path source : copies) {
                Path destination = staging.resolve(root.relativize(source)).normalize();
                if (!destination.startsWith(staging)) throw new IOException("Audio output path escapes staging");
                Files.createDirectories(destination.getParent()); Files.write(destination,assets.get(source));
            }
            Path canonical=staging.resolve("audio/audio-manifest.yaml");Files.createDirectories(canonical.getParent());
            Files.write(canonical,new ModAudioManifestParser(ownerId,limits).writeCanonical(parsed));
            Files.move(staging, target);
            return new Result(target, parsed.tracks().size(), parsed.sfx().size());
        } catch (IOException | RuntimeException failure) {
            deleteTree(staging, failure); throw failure;
        }
    }

    private static Path requireDirectory(Path value) {
        Path path=Objects.requireNonNull(value,"sourceRoot").toAbsolutePath().normalize();
        if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path))
            throw new IllegalArgumentException("Audio source root must be a non-symlink directory");
        try{return path.toRealPath();}catch(IOException error){throw new IllegalArgumentException("Audio source root is unreadable",error);}
    }
    private static Path requireContainedFile(Path root,Path value)throws IOException{
        Path path=Objects.requireNonNull(value,"asset").toAbsolutePath().normalize();
        if(!path.startsWith(root))throw new IOException("Missing or escaping audio source: "+path);
        Path current=root;for(Path segment:root.relativize(path)){current=current.resolve(segment);
            if(Files.isSymbolicLink(current))throw new IOException("Symlinked audio source is not allowed: "+path);}
        Path real=path.toRealPath();
        if(!real.startsWith(root)||!Files.isRegularFile(real,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing or escaping audio source: "+path);
        return real;
    }
    private static byte[] readBounded(Path path,long cap)throws IOException{
        try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
            byte[] bytes=input.readNBytes(Math.toIntExact(Math.min(Integer.MAX_VALUE,cap+1)));
            if(bytes.length>cap)throw new IOException("Audio source exceeds byte limit: "+path);return bytes;}
    }
    private static void deleteTree(Path root,Throwable failure){
        if(!Files.exists(root))return;try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        catch(IOException cleanup){failure.addSuppressed(cleanup);}
    }
    public record Result(Path output,int trackCount,int sfxCount){
        public Result(Path output, int trackCount) {
            this(output, trackCount, 0);
        }
    }
}
