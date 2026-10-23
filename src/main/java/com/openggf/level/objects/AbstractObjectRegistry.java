package com.openggf.level.objects;

import com.openggf.level.LevelManager;
import com.openggf.game.GameServices;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Base class for game-specific object registries.
 *
 * <p>Provides the shared {@code factories} map, lazy {@code ensureLoaded()} guard,
 * default placeholder factory, and the standard {@link #create(ObjectSpawn)} flow.
 * Subclasses implement {@link #registerDefaultFactories()} to populate the map
 * and {@link #getPrimaryName(int)} to return human-readable object names.
 */
public abstract class AbstractObjectRegistry implements ObjectRegistry, HostObjectKeyRegistry {

    @Override public boolean hasObjectKey(String key) { return hasHostObjectKey(key); }
    @Override public boolean hasHostObjectKey(String key) { return com.openggf.level.objects.authored.AuthoredGizmos.handlesKey(key); }
    @Override public java.util.List<String> browsableObjectKeys() { return com.openggf.level.objects.authored.AuthoredGizmos.KEYS; }
    @Override public boolean ownsHostObject(ObjectSpawn spawn, ObjectInstance instance) {
        return spawn != null && hasHostObjectKey(spawn.objectKey())
                && instance instanceof com.openggf.level.objects.authored.AuthoredGizmoInstance
                && instance.getClass().getClassLoader() == AbstractObjectRegistry.class.getClassLoader()
                && spawn.equals(instance.getSpawn());
    }

    private static final Logger LOG = Logger.getLogger(AbstractObjectRegistry.class.getName());

    protected final Map<Integer, ObjectFactory> factories = new HashMap<>();
    private final Map<Integer, ObjectRegistryCatalog.Role> factoryRoles = new HashMap<>();
    private boolean loaded;

    protected final ObjectFactory defaultFactory = (spawn, registry) ->
            new PlaceholderObjectInstance(spawn, registry.getPrimaryName(spawn.objectId()));

    // ------------------------------------------------------------------
    // ObjectRegistry contract
    // ------------------------------------------------------------------

    @Override
    public ObjectInstance create(ObjectSpawn spawn) {
        if (com.openggf.level.objects.authored.AuthoredGizmos.handlesKey(spawn.objectKey()))
            return com.openggf.level.objects.authored.AuthoredGizmos.create(spawn);
        if (spawn.objectKey() != null) throw new IllegalArgumentException("Unknown host object: " + spawn.objectKey());
        ensureLoaded();
        int id = spawn.objectId();
        ObjectFactory factory = factories.getOrDefault(id, defaultFactory);
        return factory.create(spawn, this);
    }

    @Override
    public void reportCoverage(List<ObjectSpawn> spawns) {
        // No-op by default; S2 overrides with coverage logging.
    }

    @Override
    public abstract String getPrimaryName(int objectId);

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Registers a factory for the given object ID.
     * Called from {@link #registerDefaultFactories()} in each subclass.
     */
    protected void registerFactory(int objectId, ObjectFactory factory) {
        factories.put(objectId, factory);
        factoryRoles.remove(objectId);
    }

    /** Native registration trait; includes badnik spawners that do not inherit a shared enemy base. */
    protected void registerBadnikFactory(int objectId, ObjectFactory factory) {
        registerFactory(objectId, factory);
        factoryRoles.put(objectId, ObjectRegistryCatalog.Role.BADNIK);
    }

    /** Reads a declaration without constructing or inspecting the factory's implementation. */
    protected final ObjectRegistryCatalog.Role registeredFactoryRole(int objectId) {
        return factoryRoles.getOrDefault(objectId, ObjectRegistryCatalog.Role.UNRESOLVED);
    }

    /** Internal metadata hook; factory construction is deliberately outside catalog enumeration. */
    protected ObjectRegistryCatalog.Entry catalogEntry(int objectId, ObjectRegistryCatalog.Context context) {
        ensureLoaded();
        boolean registered = factories.containsKey(objectId);
        return new ObjectRegistryCatalog.Entry(objectId, getPrimaryName(objectId), "stock",
                registered ? ObjectRegistryCatalog.FactoryStatus.REGISTERED : ObjectRegistryCatalog.FactoryStatus.ABSENT,
                registered ? "registered" : "none", editorPreviewArtKey(objectId).orElse(""), true,
                catalogSemantics(objectId, context));
    }

    protected ObjectRegistryCatalog.Semantics catalogSemantics(int objectId, ObjectRegistryCatalog.Context context) {
        var role = registeredFactoryRole(objectId);
        return role == ObjectRegistryCatalog.Role.UNRESOLVED ? ObjectRegistryCatalog.Semantics.NONE
                : new ObjectRegistryCatalog.Semantics(role, ObjectRegistryCatalog.PositionRule.UNVERIFIED, List.of());
    }

    protected java.util.Optional<ObjectRegistryCatalog.AuxiliaryFactory> releasedAnimalFactory(ObjectRegistryCatalog.Context context) {
        return java.util.Optional.empty();
    }

    /**
     * Returns the ROM zone id for the currently loaded level, or -1 when no level
     * is active yet. Centralized here so per-game registries don't need their own
     * direct LevelManager singleton lookups.
     */
    protected int currentRomZoneId() {
        LevelManager levelManager = GameServices.levelOrNull();
        return levelManager != null ? levelManager.getRomZoneId() : -1;
    }

    /**
     * Returns the currently loaded {@link com.openggf.level.Level}, or {@code null} when
     * no level is active yet. Centralized here so per-game registries don't need their
     * own direct {@link LevelManager#getInstance()} calls (which are guarded by
     * {@code TestRuntimeSingletonGuard}).
     */
    protected com.openggf.level.Level currentLevel() {
        LevelManager levelManager = GameServices.levelOrNull();
        return levelManager != null ? levelManager.getCurrentLevel() : null;
    }

    /**
     * Lazily initialises the registry on first access.
     * Subclasses may override to perform additional setup (e.g. loading name tables)
     * but <em>must</em> call {@code super.ensureLoaded()} first.
     */
    protected void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        registerDefaultFactories();
        LOG.fine(getClass().getSimpleName() + " loaded with " + factories.size() + " factories.");
    }

    /**
     * Populates {@link #factories} with game-specific object factories.
     * Called exactly once from {@link #ensureLoaded()}.
     */
    protected abstract void registerDefaultFactories();
}
