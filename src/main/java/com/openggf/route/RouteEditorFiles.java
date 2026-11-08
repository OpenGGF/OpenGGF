package com.openggf.route;

import com.openggf.route.*;
import java.io.IOException;
import java.nio.file.*;

/** Editor writes retain the original spelling/comments when their semantic data is unchanged. */
public final class RouteEditorFiles {
    private RouteEditorFiles() { }
    public static RouteFiles.Written save(Path stem, RouteArtifact route, ReplayEvidence evidence, boolean replace) throws IOException {
        Path manifest=Path.of(stem+RouteFiles.MANIFEST_SUFFIX);
        RouteArtifact old=Files.exists(manifest)?RouteFiles.readDraft(manifest):null;
        if(old!=null && !replace) throw new IOException("Route already exists: " + manifest);
        if (old!=null && old.equals(route) && old.engineQualified() && !old.manifest().draft()) {
            // The worker has still replayed this content. Retaining its existing complete stamp
            // avoids reformatting an unchanged document merely because the editor saved it.
            if (evidence.scope()!=ReplayScope.ENGINE || !evidence.content().equals(route.content()) || evidence.rows()!=route.tape().size())
                throw new IOException("Save evidence does not cover the route");
            return new RouteFiles.Written(manifest, script(manifest));
        }
        byte[] original=old!=null && old.tape().equals(route.tape())?Files.readAllBytes(script(manifest)):null;
        var written=replace?RouteFiles.replace(stem,route,evidence):RouteFiles.writeNew(stem,route,evidence);
        if(original!=null) Files.write(written.script(),original);
        return written;
    }
    public static RouteFiles.Written draft(Path stem, RouteArtifact route) throws IOException {
        Path manifest=Path.of(stem+RouteFiles.MANIFEST_SUFFIX);
        RouteArtifact old=Files.exists(manifest)?RouteFiles.readDraft(manifest):null;
        if(old!=null && old.equals(route) && old.manifest().draft()) return new RouteFiles.Written(manifest,script(manifest));
        byte[] original=old!=null && old.tape().equals(route.tape())?Files.readAllBytes(script(manifest)):null;
        var written=RouteFiles.writeDraft(stem,route);
        if(original!=null) Files.write(written.script(),original);
        return written;
    }
    private static Path script(Path manifest) throws IOException {
        return manifest.resolveSibling(RouteCodec.parseManifest(Files.readString(manifest)).scriptName());
    }
    public static Path resolveWitness(Path manifest, String movie) throws IOException {
        Path witness=Path.of(movie);
        if(witness.isAbsolute()) {
            if(Files.isRegularFile(witness)) return witness.normalize();
        } else {
            for(Path at=manifest.toAbsolutePath().getParent(); at!=null; at=at.getParent()) {
                Path candidate=at.resolve(witness).normalize();
                if(Files.isRegularFile(candidate)) return candidate;
            }
            if(Files.isRegularFile(witness)) return witness.toAbsolutePath().normalize();
        }
        throw new IOException("Native witness not found: "+movie);
    }
    /** Resolve a repository-relative witness from the route's own ancestry, never a guessed offset. */
    public static Path export(RouteArtifact route, Path manifest, Path destination) throws IOException {
        var facts=route.manifest().nativeFacts();
        if(!facts.exportBlocker().isBlank()) return RouteFiles.exportBk2(route,destination);
        var entry=facts.entry();
        if(entry.isPresent())entry=java.util.Optional.of(resolveEntry(manifest,entry.get()));
        var inlets=new java.util.ArrayList<NativeFacts.NativeInlet>();
        for(var inlet:facts.inlets())inlets.add(new NativeFacts.NativeInlet(inlet.engineFromRows(),resolveEntry(manifest,inlet.anchor())));
        route=new RouteArtifact(route.manifest().withNativeFacts(new NativeFacts(entry,facts.lag(),facts.exportBlocker(),inlets)),route.tape());
        return RouteFiles.exportBk2(route,destination);
    }
    private static NativeFacts.NativeEntry resolveEntry(Path manifest,NativeFacts.NativeEntry e)throws IOException {
        return new NativeFacts.NativeEntry(resolveWitness(manifest,e.movie()).toString(),e.prefixRows(),e.inputHash(),e.engineAnchorRows(),e.anchorBoundary());
    }

}
