package com.openggf.mods.scene.host;

import com.openggf.game.ModKeySyntax;
import com.openggf.mods.scene.SceneStorage;
import java.nio.file.Path;
import java.util.Objects;

/** Engine-only storage admission; callers supply the registration/scene host's verified owner. */
public final class ModStorageFactory {
    private ModStorageFactory() { }
    public static SceneStorage forOwner(Path saveRoot, String verifiedOwner) {
        // Creator code receives a handle from its verified ModContext/SceneContext.
        // The public bridge exists only because these engine hosts occupy different packages.
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(ModStorageFactory.class);
        if (caller.getClassLoader() != ModStorageFactory.class.getClassLoader()) {
            throw new SecurityException("Creator storage must come from its owner-scoped context");
        }
        String owner = ModKeySyntax.requireManifestId(verifiedOwner);
        Path root = Objects.requireNonNull(saveRoot, "saveRoot");
        // Legacy mod settings used root/<owner>; built-in game codes own that path
        // for engine save slots and cannot supply a creator migration source.
        boolean builtInSaveNamespace = java.util.Arrays.stream(com.openggf.game.GameId.values())
                .anyMatch(game -> game.code().equals(owner));
        return new FileSceneStorage(root, root.resolve("mods").resolve(owner),
                builtInSaveNamespace ? null : root.resolve(owner));
    }
}
