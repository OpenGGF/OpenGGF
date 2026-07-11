package com.openggf.mods.code;

import com.openggf.game.patch.GamePatch;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModKeySyntax;
import com.openggf.level.objects.ObjectFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creator-facing, owner-scoped transaction. Nothing is visible until {@link #freeze()}. */
@com.openggf.game.ModApi
public final class ModContext {
    private final String owner;
    private final String baseGame;
    private final ModAssetRoot assets;
    private final Map<String, ObjectFactory> objects = new LinkedHashMap<>();
    private final Map<String, BakedSheetRef> art = new LinkedHashMap<>();
    private final List<GamePatch> patches = new ArrayList<>();
    private final java.util.Set<String> patchIds = new java.util.HashSet<>();
    private boolean frozen;
    private ModRegistrationException poison;

    ModContext(String owner, String baseGame, ModAssetRoot assets) {
        this.owner = ModKeySyntax.requireManifestId(owner);
        this.baseGame = Objects.requireNonNull(baseGame, "baseGame");
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    public String ownerModId() { return owner; }
    public String baseGameId() { return baseGame; }
    public ModAssetRoot modAssets() { requireOpen(); return assets; }

    public void registerGamePatch(GamePatch patch) {
        mutate(() -> {
            Objects.requireNonNull(patch, "patch");
            if (!baseGame.equals(patch.baseGameId())) {
                throw failure("Patch targets " + patch.baseGameId() + " instead of " + baseGame);
            }
            String id = ownedPatchId(patch.id());
            if (id.equals(owner + ":content") || !patchIds.add(id)) {
                throw failure("Duplicate or reserved patch id: " + id);
            }
            patches.add(new NamespacedPatch(id, patch));
        });
    }

    public void registerObject(String key, ObjectFactory factory) {
        mutate(() -> {
            String owned = ModKeySyntax.requireOwnedKey(owner, key);
            if (objects.putIfAbsent(owned, Objects.requireNonNull(factory, "factory")) != null) {
                throw failure("Duplicate object key: " + owned);
            }
        });
    }

    public void registerObjectArt(String key, BakedSheetRef sheet) {
        mutate(() -> {
            String owned = ModKeySyntax.requireOwnedKey(owner, key);
            if (art.putIfAbsent(owned, Objects.requireNonNull(sheet, "sheet")) != null) {
                throw failure("Duplicate object-art key: " + owned);
            }
        });
    }

    ModRegistrationPlan freeze() {
        requireOpen();
        frozen = true;
        return new ModRegistrationPlan(owner, baseGame, objects, art, patches);
    }

    private String ownedPatchId(String value) {
        Objects.requireNonNull(value, "patch id");
        if (value.indexOf(':') >= 0) {
            String display = ModKeySyntax.requireDisplayKey(value);
            if (!display.startsWith(owner + ":")) throw failure("Patch id belongs to another owner: " + value);
            return display;
        }
        return ModKeySyntax.requireOwnedKey(owner, value);
    }

    private void requireOpen() {
        if (poison != null) throw poison;
        if (frozen) throw failure("Registration transaction is already frozen");
    }

    private void mutate(Runnable mutation) {
        requireOpen();
        try {
            mutation.run();
        } catch (RuntimeException rejected) {
            if (poison == null) {
                poison = rejected instanceof ModRegistrationException structured
                        ? structured
                        : new ModRegistrationException(owner,
                                "Registration transaction rejected: " + safeMessage(rejected), rejected);
            }
            throw poison;
        }
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private ModRegistrationException failure(String message) {
        return new ModRegistrationException(owner, message);
    }

    /** Freezes creator-controlled identity while delegating behavior to the patch. */
    private record NamespacedPatch(String id, GamePatch delegate) implements GamePatch {
        @Override public String displayName() { return delegate.displayName(); }
        @Override public String baseGameId() { return delegate.baseGameId(); }
        @Override public boolean activatesFor(com.openggf.game.patch.GameplayLaunchRequest request) {
            return delegate.activatesFor(request);
        }
        @Override public java.util.Set<com.openggf.game.patch.LogicalRom> romPrerequisites() {
            return delegate.romPrerequisites();
        }
        @Override public java.util.List<String> providedMainCharacters() {
            return delegate.providedMainCharacters();
        }
        @Override public com.openggf.game.GameModule apply(com.openggf.game.GameModule base,
                com.openggf.game.patch.PatchContext context) {
            return delegate.apply(base, context);
        }
    }
}
