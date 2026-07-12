package com.openggf.mods.code;

import com.openggf.game.patch.GamePatch;
import com.openggf.io.ModAssetRoot;
import com.openggf.game.ModKeySyntax;
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
    private final boolean standalone;
    private final ModAssetRoot assets;
    private final Map<String, ObjectFactory> objects = new LinkedHashMap<>();
    private final Map<String, BakedSheetRef> art = new LinkedHashMap<>();
    private final Map<String, String> objectPreviewArtKeys = new LinkedHashMap<>();
    private final List<GamePatch> patches = new ArrayList<>();
    private final List<ModZoneContribution> zones = new ArrayList<>();
    private final java.util.Set<String> zoneKeys = new java.util.HashSet<>();
    private final String defaultInsertAfter;
    private final java.util.Set<String> patchIds = new java.util.HashSet<>();
    private final Map<com.openggf.game.CharacterKey, com.openggf.game.CharacterDefinition> characters
            = new LinkedHashMap<>();
    private boolean frozen;
    private ModRegistrationException poison;
    private com.openggf.game.GameModule gameModule;

    ModContext(String owner, String baseGame, ModAssetRoot assets) {
        this(owner, baseGame, assets, null);
    }

    ModContext(String owner, String baseGame, ModAssetRoot assets, String defaultInsertAfter) {
        this(owner, baseGame, assets, defaultInsertAfter, false);
    }

    ModContext(String owner, String baseGame, ModAssetRoot assets, String defaultInsertAfter,
            boolean standalone) {
        this.owner = ModKeySyntax.requireManifestId(owner);
        this.standalone = standalone;
        this.baseGame = standalone ? baseGame : Objects.requireNonNull(baseGame, "baseGame");
        if (standalone && baseGame != null) {
            throw new IllegalArgumentException("Standalone registration forbids a base game");
        }
        this.assets = Objects.requireNonNull(assets, "assets");
        this.defaultInsertAfter = defaultInsertAfter;
    }

    public String ownerModId() { return owner; }
    public String baseGameId() { return baseGame; }
    public ModAssetRoot modAssets() { requireOpen(); return assets; }

    /** Stages the single no-ROM module owned by a standalone manifest. */
    public void registerGameModule(com.openggf.game.GameModule module) {
        mutate(() -> {
            if (!standalone) throw failure("Only standalone manifests may register a game module");
            Objects.requireNonNull(module, "module");
            if (module.getGameId() != com.openggf.game.GameId.STANDALONE) {
                throw failure("Standalone module must report GameId.STANDALONE");
            }
            if (!owner.equals(module.getGameCode()) || !owner.equals(module.getIdentifier())) {
                throw failure("Standalone module identifier and game code must equal owner " + owner);
            }
            if (gameModule != null) throw failure("Standalone game module is already registered");
            gameModule = module;
        });
    }

    public void registerGamePatch(GamePatch patch) {
        mutate(() -> {
            if (standalone) throw failure("Standalone manifests cannot register game patches");
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

    /** Stages one owner-tagged playable character in this registration transaction. */
    public void registerCharacter(String localName, com.openggf.game.CharacterDefinition definition) {
        mutate(() -> {
            com.openggf.game.CharacterKey expected = com.openggf.game.CharacterKey.mod(owner, localName);
            Objects.requireNonNull(definition, "definition");
            if (!expected.equals(definition.key())) {
                throw failure("Character definition key must equal owner-scoped key " + expected.persisted());
            }
            if (characters.putIfAbsent(expected, definition) != null) {
                throw failure("Duplicate character key: " + expected.persisted());
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

    /** Explicit editor-preview association; object identity and art identity remain independent. */
    public void registerObjectPreview(String objectKey, String artKey) {
        mutate(() -> {
            String object=ModKeySyntax.requireOwnedKey(owner,objectKey);
            String artIdentity=ModKeySyntax.requireOwnedKey(owner,artKey);
            if(objectPreviewArtKeys.putIfAbsent(object,artIdentity)!=null)
                throw failure("Duplicate object preview mapping: "+object);
        });
    }

    public void registerZone(ModZoneContribution contribution) {
        mutate(() -> {
            if (!"s2".equals(baseGame)) {
                throw failure("Additive zones are supported only for Sonic 2 in Phase 2");
            }
            Objects.requireNonNull(contribution, "contribution");
            ModZoneContribution frozen = contribution.insertAfter() == null
                    ? contribution.withDefaultAnchor(defaultInsertAfter == null ? "mtz3" : defaultInsertAfter)
                    : contribution;
            if (!com.openggf.mods.StockProgressionAnchors.contains("s2", frozen.insertAfter())) {
                throw failure("Zone insertion anchor is not results-driven: " + frozen.insertAfter());
            }
            if (!zoneKeys.add(frozen.localKey())) {
                throw failure("Duplicate zone key: " + owner + ":" + frozen.localKey());
            }
            zones.add(frozen);
        });
    }

    /** Stages a manifest-declared stock-key override without creator namespacing. */
    void registerManifestArtOverride(String stockKey, BakedSheetRef sheet) {
        mutate(() -> {
            String key = com.openggf.mods.ModManifest.requireArtOverrideKey(stockKey);
            if (art.putIfAbsent(key, Objects.requireNonNull(sheet, "sheet")) != null) {
                throw failure("Duplicate object-art key: " + key);
            }
        });
    }

    ModRegistrationPlan freeze() {
        requireOpen();
        try {
            if (standalone && gameModule == null) {
                throw failure("Standalone manifest must register exactly one game module");
            }
            objectPreviewArtKeys.forEach((objectKey,artKey)-> {
                if(!objects.containsKey(objectKey))throw failure("Preview maps unknown object key: "+objectKey);
                if(!art.containsKey(artKey))throw failure("Preview maps unknown art key: "+artKey);
            });
            java.util.ArrayList<PreparedModZone> prepared = new java.util.ArrayList<>();
            java.util.HashSet<Integer> levelIds = new java.util.HashSet<>();
            java.util.HashSet<Integer> zoneIds = new java.util.HashSet<>();
            for (ModZoneContribution zone : zones) {
                ModLevelDefinition definition = ModLevelDefinitionParser.read(assets, zone.level());
                if (!levelIds.add(definition.levelIndex()) || !zoneIds.add(definition.zoneIndex())) {
                    throw new IllegalArgumentException("Duplicate authored level or zone index");
                }
                prepared.add(PreparedModZone.prepared(owner, zone, definition));
            }
            frozen = true;
            return new ModRegistrationPlan(owner, baseGame, objects, art, Map.of(), patches,
                    zones, prepared,objectPreviewArtKeys,characters,gameModule);
        } catch (java.io.IOException | RuntimeException rejected) {
            if (rejected instanceof ModRegistrationException registration) poison = registration;
            else poison = new ModRegistrationException(owner, "MOD_LEVEL_ASSET_INVALID",
                    "Missing or invalid baked level registration", null, rejected);
            throw poison;
        }
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
