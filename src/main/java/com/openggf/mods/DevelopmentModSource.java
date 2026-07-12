package com.openggf.mods;

import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.SnapshotModAssetRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One retained immutable directory source for explicit {@code ggfmod run}. */
public final class DevelopmentModSource implements AutoCloseable {
    public static final String PROPERTY="ggfmod.dev.modDir";
    private final SnapshotModAssetRoot snapshot;
    private final ModDescriptor descriptor;
    private boolean transferred;

    private DevelopmentModSource(SnapshotModAssetRoot snapshot,ModDescriptor descriptor){
        this.snapshot=snapshot;this.descriptor=descriptor;
    }

    public static Optional<Path> configuredPath(){
        String value=System.getProperty(PROPERTY);
        return value==null||value.isBlank()?Optional.empty():Optional.of(Path.of(value));
    }
    public static boolean isConfigured(){return configuredPath().isPresent();}

    public static DevelopmentModSource snapshot(Path buildOutput,ModInputLimits limits)throws IOException{
        return snapshot(buildOutput,limits,ignored->{});
    }
    static DevelopmentModSource snapshot(Path buildOutput,ModInputLimits limits,
                                          java.util.function.Consumer<Path> afterSnapshot)throws IOException{
        Path source=Objects.requireNonNull(buildOutput,"buildOutput").toAbsolutePath().normalize();
        if(source.getParent()==null)throw new IllegalArgumentException("Dev build output requires a parent");
        SnapshotModAssetRoot root=ModAssetRoot.snapshotDirectory(source.getParent(),source,
                Objects.requireNonNull(limits,"limits"),DirectoryAccess.DEVELOPMENT);
        try{
            Objects.requireNonNull(afterSnapshot,"afterSnapshot").accept(root.immutableContentPath());
            List<String> names=root.validatedEntryNames();
            if(!names.contains(DefaultModRepositoryScanner.MANIFEST_PATH))
                throw new IOException("Required manifest is missing");
            ModManifest manifest=new ModManifestParser(limits).parse(root.readBounded(
                    DefaultModRepositoryScanner.MANIFEST_PATH,limits.maxMetadataBytes()));
            ModDescriptor descriptor=new ModDescriptor(root.immutableContentPath(),manifest,
                    root.immutableSha256(),names.stream().anyMatch(n->n.endsWith(".class")),List.of(),root);
            return new DevelopmentModSource(root,descriptor);
        }catch(Exception failure){root.close();if(failure instanceof IOException io)throw io;
            throw new IOException("Invalid development mod",failure);}
    }

    public List<ModCatalogEntry> scan(){return List.of(descriptor);}
    public Path repositoryRoot(){return descriptor.jarPath();}
    public SnapshotModAssetRoot retainedSource(){return snapshot;}
    public SnapshotModAssetRoot transferOwnership(){
        if(transferred)throw new IllegalStateException("Development source ownership already transferred");
        transferred=true;return snapshot;
    }
    @Override public void close(){if(!transferred)try{snapshot.close();}catch(IOException ignored){}}
}
