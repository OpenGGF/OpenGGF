package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class TestDevelopmentModSource {
    @TempDir Path temp;

    @Test void snapshotsExplodedDirectoryOnceAndRetainsItUntilClose() throws Exception {
        Path build = temp.resolve("build"); Files.createDirectories(build.resolve("META-INF"));
        Path manifest = build.resolve("META-INF/openggf-mod.yaml");
        Files.writeString(manifest, manifest("before"));
        DevelopmentModSource source = DevelopmentModSource.snapshot(build, ModInputLimits.production());
        Path repository = source.repositoryRoot();
        List<ModCatalogEntry> scanned = source.scan();
        assertEquals(1, scanned.size());
        assertEquals("before", ((ModDescriptor) scanned.getFirst()).manifest().description());

        Files.writeString(manifest, manifest("after"));
        List<ModCatalogEntry> rescanned = source.scan();
        assertEquals("before", ((ModDescriptor) rescanned.getFirst()).manifest().description());
        assertSame(source.retainedSource(),((ModDescriptor)rescanned.getFirst()).retainedSource());
        source.close();
        assertFalse(Files.exists(repository));
    }

    @Test void blankPropertyIsNotConfiguredAndTransferredOwnershipHasOneCloser() throws Exception {
        String previous=System.getProperty(DevelopmentModSource.PROPERTY);
        try {
            System.setProperty(DevelopmentModSource.PROPERTY,"   ");
            assertFalse(DevelopmentModSource.isConfigured());
            Path build=temp.resolve("transfer");Files.createDirectories(build.resolve("META-INF"));
            Files.writeString(build.resolve("META-INF/openggf-mod.yaml"),manifest("transfer"));
            DevelopmentModSource source=DevelopmentModSource.snapshot(build,ModInputLimits.production());
            Path retained=source.repositoryRoot();var owner=source.transferOwnership();
            source.close();assertTrue(Files.isDirectory(retained));
            assertThrows(IllegalStateException.class,source::transferOwnership);
            owner.close();assertFalse(Files.exists(retained));
        } finally { if(previous==null)System.clearProperty(DevelopmentModSource.PROPERTY);
            else System.setProperty(DevelopmentModSource.PROPERTY,previous); }
    }

    @Test void failedDescriptorParsingClosesAllocatedSnapshot() throws Exception {
        Path build=temp.resolve("invalid");Files.createDirectories(build.resolve("META-INF"));
        Files.writeString(build.resolve("META-INF/openggf-mod.yaml"),"not: a valid manifest");
        java.util.concurrent.atomic.AtomicReference<Path> retained=new java.util.concurrent.atomic.AtomicReference<>();
        assertThrows(Exception.class,()->DevelopmentModSource.snapshot(
                build,ModInputLimits.production(),retained::set));
        assertNotNull(retained.get());assertFalse(Files.exists(retained.get()));
    }

    private static String manifest(String description) { return """
            formatVersion: 1
            id: dev-mod
            name: Dev Mod
            version: 1.0.0
            authors: [Dev]
            description: %s
            engineApiRange: ">=0.7.0 <0.8.0"
            type: patch
            baseGame: s2
            dependencies: []
            audioOverrides: {}
            artOverrides: {}
            """.formatted(description); }
}
