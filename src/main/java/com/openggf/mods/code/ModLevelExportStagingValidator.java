package com.openggf.mods.code;

import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.LevelExportStagingValidator;

import java.io.IOException;
import java.nio.file.Path;

/** Strict format and optional destination-host validation adapter for editor export staging. */
public final class ModLevelExportStagingValidator implements LevelExportStagingValidator {
    private final com.openggf.game.GameModule destination;
    public ModLevelExportStagingValidator() { this.destination=null; }
    public ModLevelExportStagingValidator(com.openggf.game.GameModule destination) {
        this.destination=java.util.Objects.requireNonNull(destination);
    }

    @Override
    public void validate(Path stagingDirectory) throws IOException {
        Path rootDirectory = stagingDirectory.toAbsolutePath().normalize();
        try (ModAssetRoot assets = ModAssetRoot.directory(rootDirectory.getParent(), rootDirectory,
                ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
            ModLevelDefinition definition=ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
            validateHostData(ModZoneLoader.prepareHostData(definition));
        }
    }

    @Override
    public void validateHostData(com.openggf.game.modzone.ModZoneLevelData data) throws IOException {
        if (destination == null) return;
        destination.getModZoneAdapter().validate("editor", data);
        var registry = destination.createObjectRegistry();
        for (var object : data.objects()) {
            if (object.objectKey() != null && !registry.hasObjectKey(object.objectKey()))
                throw new IOException("Missing enabled object dependency: " + object.objectKey());
        }
    }
}
