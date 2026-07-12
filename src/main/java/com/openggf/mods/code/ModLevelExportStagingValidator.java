package com.openggf.mods.code;

import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.LevelExportStagingValidator;

import java.io.IOException;
import java.nio.file.Path;

/** Strict ModLevelDefinition-v1 validation adapter for editor export staging. */
public final class ModLevelExportStagingValidator implements LevelExportStagingValidator {
    @Override
    public void validate(Path stagingDirectory) throws IOException {
        Path rootDirectory = stagingDirectory.toAbsolutePath().normalize();
        try (ModAssetRoot assets = ModAssetRoot.directory(rootDirectory.getParent(), rootDirectory,
                ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
            ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
        }
    }
}
