package openggf.timeattack;

import com.openggf.io.ModAssetRoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The build default master read from the packager-generated mod asset. */
class TestNetworkDefaults {
    @Test
    void readsTheGeneratedDefaultMaster() {
        assertEquals("multi-dev.openggf.com", NetworkDefaults.parse(
                "# Generated\nmultiplayer.defaultMaster=multi-dev.openggf.com\n"));
        assertEquals("wss://lan.example:9443/master", NetworkDefaults.parse(
                "multiplayer.defaultMaster = wss://lan.example:9443/master\n"));
    }

    @Test
    void anAbsentOrUnusableAssetMeansNoBuildDefault() {
        // Development loads have no generated asset; the in-memory root has no entries.
        assertEquals("", NetworkDefaults.defaultMaster(ModAssetRoot.forTests("no generated asset")));
        assertEquals("", NetworkDefaults.parse(""));
        assertEquals("", NetworkDefaults.parse("other=value\n"));
        assertEquals("", NetworkDefaults.parse("multiplayer.defaultMaster=" + "x".repeat(300) + "\n"));
        assertEquals("", NetworkDefaults.parse("multiplayer.defaultMaster=\\u12\n"));
    }
}
