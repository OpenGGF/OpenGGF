package com.openggf.tools.modsdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The single channel catalogue and the build-default generator that reads it. */
class TestBundledNetworkDefaults {
    private static final Path CATALOGUE = Path.of("racing/deploy/master-channels.properties");

    @TempDir
    Path temp;

    @Test
    void theCommittedCatalogueListsEachBranchMaster() throws Exception {
        assertEquals(Map.of(
                        "master", new BundledNetworkDefaults.Channel("multi.openggf.com", 27900),
                        "develop", new BundledNetworkDefaults.Channel("multi-dev.openggf.com", 27910),
                        "next", new BundledNetworkDefaults.Channel("multi-next.openggf.com", 27920)),
                BundledNetworkDefaults.readCatalogue(CATALOGUE));
    }

    @Test
    void theChannelSelectsItsHostAndAnUnknownChannelFailsTheBuild() throws Exception {
        var catalogue = BundledNetworkDefaults.readCatalogue(CATALOGUE);
        assertEquals("multi-dev.openggf.com", BundledNetworkDefaults.defaultMaster(catalogue, "develop", ""));
        assertEquals("multi-next.openggf.com", BundledNetworkDefaults.defaultMaster(catalogue, " next ", null));
        assertEquals("multi.openggf.com", BundledNetworkDefaults.defaultMaster(catalogue, "master", ""));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> BundledNetworkDefaults.defaultMaster(catalogue, "feature/ai-x", ""));
        assertTrue(unknown.getMessage().contains("[develop, master, next]"), unknown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> BundledNetworkDefaults.defaultMaster(catalogue, "", ""));
    }

    @Test
    void anOverrideIsSyntaxCheckedWithoutNetworkIo() throws Exception {
        var catalogue = BundledNetworkDefaults.readCatalogue(CATALOGUE);
        assertEquals("private.example.org",
                BundledNetworkDefaults.defaultMaster(catalogue, "develop", "Private.Example.org."));
        assertEquals("wss://lan.example:9443/master",
                BundledNetworkDefaults.defaultMaster(catalogue, "develop", "wss://lan.example:9443"));
        for (String bad : new String[]{"ws://example.com/master", "localhost", "host:27900", "a b.example"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> BundledNetworkDefaults.defaultMaster(catalogue, "develop", bad), bad);
        }
    }

    @Test
    void malformedCataloguesAreRejected() throws Exception {
        for (String text : new String[]{"develop=multi-dev.openggf.com\n", "develop=multi-dev.openggf.com:0\n",
                "develop=wss://multi-dev.openggf.com:27910\n", "develop=Multi-Dev.openggf.com:27910\n",
                "develop=a.example:1\ndevelop=b.example:2\n", "Develop=a.example:1\n", "# only comments\n"}) {
            Path file = Files.writeString(temp.resolve("catalogue.properties"), text);
            assertThrows(IllegalArgumentException.class, () -> BundledNetworkDefaults.readCatalogue(file), text);
        }
    }

    @Test
    void theOverlayIsRecreatedSoAnEarlierBuildsValueNeverSurvives() throws Exception {
        Path overlay = Files.createDirectories(temp.resolve("overlay"));
        Files.writeString(overlay.resolve("stale.txt"), "old");
        BundledNetworkDefaults.write(overlay, "next", "multi-next.openggf.com");
        assertFalse(Files.exists(overlay.resolve("stale.txt")));
        String text = Files.readString(overlay.resolve(BundledNetworkDefaults.RESOURCE));
        assertTrue(text.contains("multiplayer.channel=next\n"), text);
        assertTrue(text.contains("multiplayer.defaultMaster=multi-next.openggf.com\n"), text);
    }
}
