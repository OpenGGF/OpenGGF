package echoes;

import com.openggf.io.*;
import java.nio.file.Path;
import java.util.Map;

/** Test-only authoring data access; installed mods use their registration asset root. */
final class RoomFixtures {
    static Map<String, RoomDefinition> catalogue() {
        Path root = Path.of("examples/sonic-echoes/src/main/resources").toAbsolutePath();
        try (var assets = ModAssetRoot.directory(root, root, ModInputLimits.production(), DirectoryAccess.TEST)) {
            return RoomDefinition.catalogue(assets);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    static RoomDefinition load(String id) { return catalogue().get(id); }
}
