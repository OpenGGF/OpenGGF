package com.openggf.tools.modsdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import com.openggf.io.ModInputLimits;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestJarPackager {
    @TempDir Path temp;

    @Test
    void packagesAValidDirectoryInStableNameOrderWithFixedTimestamps() throws Exception {
        Path input = validInput(temp.resolve("input"));
        Files.createDirectories(input.resolve("z"));
        Files.writeString(input.resolve("z/last.txt"), "last");
        Files.writeString(input.resolve("a-first.txt"), "first");

        Path first = temp.resolve("first.jar");
        Path second = temp.resolve("second.jar");
        JarPackager.packageDirectory(input, first);
        Thread.sleep(20);
        JarPackager.packageDirectory(input, second);

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        assertTrue(new ModJarValidator().validate(first).valid());
        try (JarFile jar = new JarFile(first.toFile())) {
            List<String> names = jar.stream().map(entry -> entry.getName()).toList();
            assertEquals(names.stream().sorted().toList(), names);
            assertTrue(jar.stream().allMatch(entry -> entry.getTime() == JarPackager.ENTRY_TIMESTAMP));
        }
    }

    @Test
    void validationFailureDoesNotPublishOrReplaceAnOutput() throws Exception {
        Path invalid = temp.resolve("invalid");
        Files.createDirectories(invalid.resolve("META-INF"));
        Files.writeString(invalid.resolve("META-INF/openggf-mod.yaml"), "not: canonical\n");
        Path output = temp.resolve("invalid.jar");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> JarPackager.packageDirectory(invalid, output));
        assertTrue(failure.getMessage().contains("MANIFEST_INVALID"), failure::getMessage);
        assertFalse(Files.exists(output));

        Path valid = validInput(temp.resolve("valid"));
        Files.writeString(output, "keep", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> JarPackager.packageDirectory(valid, output));
        assertEquals("keep", Files.readString(output));
    }

    @Test
    void rejectsUnsafeCaseCollidingAndReservedEntryNames() {
        assertThrows(IllegalArgumentException.class,
                () -> JarPackager.validateEntryNames(List.of("art/Foo.bin", "art/foo.bin")));
        assertThrows(IllegalArgumentException.class,
                () -> JarPackager.validateEntryNames(List.of("assets/CON.txt")));
        assertThrows(IllegalArgumentException.class,
                () -> JarPackager.validateEntryNames(List.of("META-INF/MANIFEST.MF")));
        assertThrows(IllegalArgumentException.class,
                () -> JarPackager.validateEntryNames(List.of("bad\\name")));
        assertThrows(IllegalArgumentException.class,
                () -> JarPackager.validateEntryNames(List.of("../escape")));
    }

    @Test void packagesImmutableSnapshotAndEnforcesSourceAndAggregateCaps() throws Exception {
        Path input=validInput(temp.resolve("snapshot"));Files.writeString(input.resolve("race.txt"),"before");
        Path output=temp.resolve("snapshot.jar");
        JarPackager.packageDirectory(input,output,ModInputLimits.production(),ignored->{
            try{Files.writeString(input.resolve("race.txt"),"after");}catch(Exception error){throw new RuntimeException(error);}});
        try(JarFile jar=new JarFile(output.toFile())){assertEquals("before",new String(
                jar.getInputStream(jar.getJarEntry("race.txt")).readAllBytes(),StandardCharsets.UTF_8));}

        Path capped=validInput(temp.resolve("capped"));Files.write(capped.resolve("large.bin"),new byte[600]);
        Path cappedOut=temp.resolve("capped.jar");ModInputLimits limits=ModInputLimits.loweringBuilder()
                .maxAssetBytes(512).maxModValidationBytes(700).build();
        assertThrows(Exception.class,()->JarPackager.packageDirectory(capped,cappedOut,limits,ignored->{}));
        assertFalse(Files.exists(cappedOut));
        Path aggregate=validInput(temp.resolve("aggregate"));Files.write(aggregate.resolve("one.bin"),new byte[400]);
        Files.write(aggregate.resolve("two.bin"),new byte[400]);Path aggregateOut=temp.resolve("aggregate.jar");
        assertThrows(Exception.class,()->JarPackager.packageDirectory(aggregate,aggregateOut,limits,ignored->{}));
        assertFalse(Files.exists(aggregateOut));
    }

    @Test void targetCreatedAtPublicationBoundaryIsNeverReplaced() throws Exception {
        Path input=validInput(temp.resolve("publish-race"));Path output=temp.resolve("publish-race.jar");
        assertThrows(Exception.class,()->JarPackager.packageDirectory(input,output,ModInputLimits.production(),
                ignored->{},()->{try{Files.writeString(output,"competitor");}
                    catch(Exception error){throw new RuntimeException(error);}}));
        assertEquals("competitor",Files.readString(output));
        try(var files=Files.list(temp)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".ggfmod-package-")));}
    }

    private static Path validInput(Path root) throws Exception {
        Files.createDirectories(root.resolve("META-INF"));
        Files.writeString(root.resolve("META-INF/openggf-mod.yaml"), """
                formatVersion: 1
                id: package-fixture
                name: Package Fixture
                version: 1.0.0
                authors: [Test]
                description: Deterministic package fixture
                engineApiRange: ">=0.7.0 <0.8.0"
                type: patch
                baseGame: s2
                dependencies: []
                audioOverrides: {}
                artOverrides: {}
                patternWindows: 1
                """);
        return root;
    }
}
