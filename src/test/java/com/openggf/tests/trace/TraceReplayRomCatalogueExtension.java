package com.openggf.tests.trace;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.PhysicalImage;
import com.openggf.data.RomGame;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.data.RomManager;
import com.openggf.tests.RomCatalogueFixture;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Makes supplied test ROMs available to the production built-in patch resolver. */
public final class TraceReplayRomCatalogueExtension
        implements BeforeTestExecutionCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(TraceReplayRomCatalogueExtension.class);

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        // @RequiresRom resets configuration before each test. Bind afterwards:
        // the primary ROM's JVM property alone does not expose donor images to
        // RomManager's catalogue, especially in a worktree without ROM links.
        var images = new EnumMap<RomGame, Path>(RomGame.class);
        add(images, RomGame.S1, RomTestUtils.ensureSonic1RomAvailable());
        add(images, RomGame.S2, RomTestUtils.ensureSonic2RomAvailable());
        add(images, RomGame.S3K, RomTestUtils.ensureSonic3kRomAvailable());
        Path directory = explicitLockOnDirectory();
        var fixture = RomCatalogueFixture.bind(SonicConfigurationService.getInstance(),
                RomManager.getInstance(), images, directory);
        context.getStore(NAMESPACE).put(RomCatalogueFixture.class, fixture);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var fixture = context.getStore(NAMESPACE)
                .remove(RomCatalogueFixture.class, RomCatalogueFixture.class);
        if (fixture != null) fixture.close();
    }

    private static void add(Map<RomGame, Path> images, RomGame game, File image) {
        if (image != null) images.put(game, image.toPath());
    }

    private static Path explicitLockOnDirectory() throws IOException {
        String explicit = System.getProperty("kis2.rom.path");
        if (explicit == null || explicit.isBlank()) return null;
        Path image = Path.of(explicit).toAbsolutePath().normalize();
        var catalogue = new RomImageCatalogue(
                List.of(PhysicalImage.probe(image)), Map.of(), false);
        if (!catalogue.isAvailable(RomIdentity.KIS2)) {
            throw new IllegalArgumentException("kis2.rom.path is not a KiS2 lock-on image: " + image);
        }
        return image.getParent();
    }
}
