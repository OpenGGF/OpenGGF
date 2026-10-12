package com.openggf.mods.testing;

import com.openggf.architecture.CompositionRoot;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.StockMusicDomains;
import com.openggf.io.ModInputLimits;
import com.openggf.game.GameModule;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.EffectiveCatalogBuilder;
import com.openggf.mods.ModCatalog;
import com.openggf.mods.ModCatalogEntry;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModEligibility;
import com.openggf.mods.ModFinding;
import com.openggf.mods.ModFindingSeverity;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModState;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.EffectiveCatalogPatchEnablement;
import com.openggf.mods.code.ModClassLoaderFactory;
import com.openggf.mods.code.ModClassResolver;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.ModRegistrationPlan;
import com.openggf.mods.code.ModRuntime;
import com.openggf.mods.code.OwnedSceneFactory;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.mods.scene.host.SceneDrawOp;
import com.openggf.mods.scene.host.SceneServices;
import com.openggf.level.objects.RewindClassResolver;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Creator testing through production discovery, validation, snapshot loaders, transactions,
 * module resolution and owner fault boundaries. This is distributed in the matched testkit
 * artifact, rather than the runtime Mod API. It never changes the user's enabled/trusted state.
 */
@CompositionRoot
public final class ModTestKit implements AutoCloseable {
    private final ModCatalog catalog;
    private final ModRuntime runtime;
    private final ModuleResolutionService resolver;
    private final ModRuntimeFindingStore findings;
    private final Path storageRoot;
    private final ModSceneHost scene = new ModSceneHost();
    private final DeterministicInput input;
    private final RewindClassResolver rewindClasses;
    private final List<String> exits = new ArrayList<>();

    private ModTestKit(ModCatalog catalog, ModRuntime runtime, ModuleResolutionService resolver,
            ModRuntimeFindingStore findings, Path storageRoot, SonicConfigurationService configuration) {
        this.catalog = catalog;
        this.runtime = runtime;
        this.resolver = resolver;
        this.findings = findings;
        this.storageRoot = storageRoot;
        this.input = new DeterministicInput(configuration);
        this.rewindClasses = new ModClassResolver(runtime, ModTestKit.class.getClassLoader());
    }

    /** Locally grants exact scanned hashes for tests only; missing ROM prerequisites remain unavailable. */
    public static ModTestKit openTrusted(Path packedRepository, Path storageRoot) throws IOException {
        return openTrusted(packedRepository, storageRoot, new LogicalRomResolver(() -> null),
                SonicConfigurationService.createStandalone(), List.of());
    }

    public static ModTestKit openTrusted(Path packedRepository, Path storageRoot,
            LogicalRomResolver roms, SonicConfigurationService configuration,
            List<RegisteredPatch> builtIns) throws IOException {
        Path repository = packedRepository.toAbsolutePath().normalize();
        List<ModCatalogEntry> scanned = new DefaultModRepositoryScanner().scan(repository);
        scanned = new ModCatalogValidator(repository, ModInputLimits.production(),
                StockMusicDomains::containsSupported).validate(scanned).entries();
        List<ModState.Entry> grants = new ArrayList<>();
        for (ModCatalogEntry entry : scanned) {
            if (!(entry instanceof ModDescriptor descriptor) || descriptor.hasErrors()) {
                throw new IOException("Creator repository scan failed: " + entry.findings());
            }
            grants.add(new ModState.Entry(descriptor.manifest().id(), true, grants.size(),
                    descriptor.containsCode(), descriptor.containsCode() ? descriptor.sha256() : null));
        }
        if (grants.isEmpty()) throw new IOException("Creator repository contains no packed mods");
        ModState state = new ModState(ModState.CURRENT_FORMAT_VERSION, grants);
        ModCatalog catalog = new EffectiveCatalogBuilder().build(scanned, state);
        if (catalog.effective().orderedEnabled().size() != scanned.size()
                || catalog.eligibility().values().stream().anyMatch(
                        result -> result.status() != ModEligibility.Status.EFFECTIVE)) {
            throw new IOException("Creator repository is not eligible: " + catalog.eligibility());
        }
        Path storage = Objects.requireNonNull(storageRoot, "storage root").toAbsolutePath().normalize();
        Files.createDirectories(storage);
        ModRuntime runtime = new ModClassLoaderFactory(ModTestKit.class.getClassLoader())
                .create(catalog.effective(), state.trustedCodeOwners(scanned), true);
        try {
            ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
            runtime.installFaultBoundary(new ModFaultBoundary(runtime.ownerDependencies(), findings,
                    owners -> new ModStateSaveResult.Saved(), runtime::disableOwnersForProcess));
            runtime.installSaveFindingSink((owner, finding) -> findings.upsertOwnerFinding(owner,
                    new ModFinding(ModFindingSeverity.WARNING, finding.code(), finding.detail(), null)));
            runtime.installStorageRoot(storage);
            ModuleResolutionService resolver = new ModuleResolutionService(List.copyOf(builtIns),
                    new EffectiveCatalogPatchEnablement(catalog.effective()),
                    Objects.requireNonNull(roms, "logical ROM resolver"),
                    Objects.requireNonNull(configuration, "configuration"), ignored -> runtime.newRegistrationPlan());
            runtime.newRegistrationPlan();
            if (!runtime.rejectedOwners().isEmpty() || !runtime.registrationFailures().isEmpty()) {
                throw new IOException("Creator runtime rejected registration: " + runtime.rejectedOwners()
                        + "; failures: " + runtime.registrationFailures());
            }
            return new ModTestKit(catalog, runtime, resolver, findings, storage, configuration);
        } catch (IOException | RuntimeException | Error failure) {
            try { runtime.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Package already compiled creator classes through the SDK CLI before loading the immutable jar. */
    public static ModTestKit packageAndOpen(Path compiledClasses, Path packedRepository, Path storageRoot)
            throws IOException {
        Path repository = packedRepository.toAbsolutePath().normalize();
        Files.createDirectories(repository);
        try (var contents = Files.list(repository)) {
            if (contents.findAny().isPresent()) {
                throw new IOException("Packaging tests require an empty dedicated mod repository: " + repository);
            }
        }
        try {
            Class<?> cli = Class.forName("com.openggf.tools.modsdk.GgfModCli");
            int code = (int) cli.getMethod("run", String[].class, PrintStream.class).invoke(null,
                    new String[] {"package", "--input", compiledClasses.toString(),
                            "--out", repository.resolve("creator-test.jar").toString()}, System.out);
            if (code != 0) throw new IOException("Creator SDK packaging failed with exit code " + code);
        } catch (InvocationTargetException failure) {
            throw new IOException("Creator SDK packaging failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Packaging tests require the matching openggf-mod-sdk jar", failure);
        }
        return openTrusted(repository, storageRoot);
    }

    public ModCatalog catalog() { return catalog; }
    public Map<String, List<ModFinding>> findings() { return findings.snapshot(); }
    public Set<String> disabledOwners() { return runtime.runtimeDisabledOwners(); }
    /** The master-title entry {@code verifiedOwnerId} registered, owner-bound and fault-bounded. */
    public com.openggf.mods.code.OwnedTitleEntry titleEntry(String verifiedOwnerId) {
        return runtime.titleEntries().stream().filter(entry -> entry.ownerModId().equals(verifiedOwnerId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("No title entry for " + verifiedOwnerId));
    }

    public ModRegistrationPlan plan(String verifiedOwnerId) {
        ModRegistrationPlan plan = runtime.registrationPlans().get(verifiedOwnerId);
        if (plan == null) throw new IllegalArgumentException("No successful plan for " + verifiedOwnerId);
        return plan;
    }
    public Class<?> loadOwned(String verifiedOwnerId, String name) throws ClassNotFoundException {
        return runtime.loadOwned(verifiedOwnerId, name);
    }
    /** Attach to a real session's LevelManager before loading for owner-aware object rewind. */
    public RewindClassResolver rewindClassResolver() { return rewindClasses; }
    public ClassLoader loader(String verifiedOwnerId) throws ClassNotFoundException {
        ModDescriptor descriptor = catalog.effective().orderedEnabled().stream()
                .filter(entry -> entry.manifest().id().equals(verifiedOwnerId)).findFirst().orElseThrow();
        return loadOwned(verifiedOwnerId, descriptor.manifest().entrypoint()).getClassLoader();
    }

    public GameModule launch(GameModule root, GameplayLaunchRequest request) {
        return resolver.resolveForLaunch(root, request, ModuleResolutionService.LaunchPolicy.STANDARD);
    }
    public GameModule standalone(String verifiedOwnerId) {
        return runtime.prepareStandaloneModule(verifiedOwnerId).orElseThrow(
                () -> new IllegalArgumentException("No standalone module for " + verifiedOwnerId));
    }

    /** Open the production scene host silently, with caller-supplied ROM art when needed. */
    public void openScene(GameModule module, int width, int height) {
        openScene(module, width, height, new SceneServices(null, null, storageRoot, null,
                () -> exits.add("game"), () -> exits.add("master")));
    }
    public void openScene(GameModule module, int width, int height, SceneServices services) {
        OwnedSceneFactory factory = module.getGameService(OwnedSceneFactory.class);
        if (factory == null) throw new IllegalArgumentException("Module has no registered startup scene");
        SceneServices supplied = Objects.requireNonNull(services, "scene services");
        SceneServices isolated = new SceneServices(supplied.audio(), supplied.romArt(), storageRoot,
                supplied.mouse(), supplied.toGameTitle(), supplied.toMasterTitle(), supplied.romLibrary());
        scene.open(factory, isolated, width, height);
    }
    public ModSceneHost sceneHost() { return scene; }
    public DeterministicInput input() { return input; }
    public List<String> exits() { return List.copyOf(exits); }
    public void tick() {
        input.beginTick();
        try { scene.update(input.handler()); } finally { input.endTick(); }
    }
    public List<SceneDrawOp> draw() { scene.draw(null, null); return scene.recordedFrame(); }

    /** A module-only roundtrip; full gameplay callers supply their real session registry. */
    public static RewindRegistry moduleState(GameModule module) {
        RewindRegistry registry = new RewindRegistry();
        module.rewindAdapters().forEach(registry::register);
        return registry;
    }
    public static CompositeSnapshot capture(RewindRegistry registry) { return registry.capture(); }
    public static void restore(RewindRegistry registry, CompositeSnapshot snapshot) { registry.restore(snapshot); }

    @Override public void close() throws IOException {
        try { scene.cleanup(); } finally { runtime.close(); }
    }
}
