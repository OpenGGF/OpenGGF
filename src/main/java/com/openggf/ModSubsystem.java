package com.openggf;

import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModCatalog;
import com.openggf.mods.ModCatalogEntry;
import com.openggf.mods.ModFinding;
import com.openggf.mods.ModFindingSeverity;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModAudioPreparer;
import com.openggf.mods.ModTrackRegistry;
import com.openggf.mods.PendingModStateEditor;
import com.openggf.mods.PreparedAudioSession;
import com.openggf.mods.PreparedModMusic;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.DevelopmentModSource;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.EffectiveCatalogBuilder;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModStateStore;
import com.openggf.mods.ModState;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.graphics.PixelFont;
import com.openggf.mods.ui.ModManagerScreen;
import com.openggf.audio.AudioManager;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.io.ModInputLimits;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.LinkedHashMap;
import com.openggf.level.objects.RewindClassResolver;
import com.openggf.game.session.PatternWindowState;
import com.openggf.mods.code.ModPatternWindowAllocator;
import com.openggf.graphics.PatternAtlasRange;

/**
 * Process-lifetime mod catalog owner and the sole atomic owner of the session view.
 * Deterministic entry seams use the static boundary so they cannot accidentally scan.
 */
public final class ModSubsystem implements AutoCloseable {
    private static final AtomicReference<ModSubsystem> PROCESS = new AtomicReference<>(
            disabled(new ExternalContentPolicy(ExternalContentMode.STARTUP_DETERMINISTIC)));

    private final ModCatalog processCatalog;
    private final ModRuntimeFindingStore runtimeFindings;
    private final SessionViewFactory sessionFactory;
    private final PendingModStateEditor pendingEditor;
    private final Set<String> trustedCodeOwners;
    private final SessionAudioBoundary audioBoundary;
    private final ModPatternWindowAllocator patternWindowAllocator;
    private ExternalContentPolicy policy;
    private SessionExternalContentView sessionView = SessionExternalContentView.EMPTY;
    private long sessionEpoch;
    private RewindClassResolver rewindClassResolver = RewindClassResolver.ENGINE_ONLY;
    private AutoCloseable bootResource = () -> { };
    private final ModState startupModState;
    private boolean compiledModsSupported = true;

    public ModSubsystem(ModCatalog processCatalog, ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory) {
        this(processCatalog, null, runtimeFindings, sessionFactory,
                new DirectSessionAudioBoundary(),
                new ExternalContentPolicy(ExternalContentMode.NORMAL), Set.of(), ModState.EMPTY);
    }

    public ModSubsystem(ModCatalog processCatalog, ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary) {
        this(processCatalog, null, runtimeFindings, sessionFactory, audioBoundary,
                new ExternalContentPolicy(ExternalContentMode.NORMAL), Set.of(), ModState.EMPTY);
    }

    public ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                        ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory) {
        this(processCatalog, Objects.requireNonNull(pendingEditor, "pendingEditor"),
                runtimeFindings, sessionFactory, new DirectSessionAudioBoundary(),
                new ExternalContentPolicy(ExternalContentMode.NORMAL), Set.of(), ModState.EMPTY);
    }

    public ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                        ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary) {
        this(processCatalog, Objects.requireNonNull(pendingEditor, "pendingEditor"),
                runtimeFindings, sessionFactory, audioBoundary,
                new ExternalContentPolicy(ExternalContentMode.NORMAL), Set.of(), ModState.EMPTY);
    }

    private ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                         ModRuntimeFindingStore runtimeFindings,
                         SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary,
                         ExternalContentPolicy policy, Set<String> trustedCodeOwners,
                         ModState startupModState) {
        this.processCatalog = Objects.requireNonNull(processCatalog, "processCatalog");
        this.runtimeFindings = Objects.requireNonNull(runtimeFindings, "runtimeFindings");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.pendingEditor = pendingEditor;
        this.audioBoundary = Objects.requireNonNull(audioBoundary, "audioBoundary");
        int firstFreePatternId = java.util.Arrays.stream(PatternAtlasRange.values())
                .mapToInt(PatternAtlasRange::endExclusive).max().orElse(0);
        this.patternWindowAllocator = new ModPatternWindowAllocator(
                processCatalog.effective(), firstFreePatternId);
        this.policy = Objects.requireNonNull(policy, "policy");
        this.trustedCodeOwners = Set.copyOf(Objects.requireNonNull(
                trustedCodeOwners, "trustedCodeOwners"));
        this.startupModState = Objects.requireNonNull(startupModState, "startupModState");
    }

    public static ModSubsystem current() { return PROCESS.get(); }

    public static void installProcess(ModSubsystem replacement) {
        Objects.requireNonNull(replacement, "replacement");
        ModSubsystem previous = PROCESS.getAndSet(replacement);
        if (previous != replacement) previous.close();
    }

    /** The supplier owns all root resolution and discovery, so the disabled path invokes none of it. */
    public static void installAtBoot(ExternalContentPolicy policy,
                                     Supplier<ModSubsystem> normalBootLoader) {
        installAtBoot(policy, normalBootLoader, true);
    }

    public static void installAtBoot(ExternalContentPolicy policy,
                                     Supplier<ModSubsystem> normalBootLoader,
                                     boolean compiledModsSupported) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(normalBootLoader, "normalBootLoader");
        ModSubsystem replacement = policy.mayScanAtBoot()
                ? Objects.requireNonNull(normalBootLoader.get(), "normalBootLoader result")
                : disabled(policy);
        replacement.compiledModsSupported = compiledModsSupported;
        installProcess(replacement);
    }

    public static void disableCurrentSessionForDeterminism() {
        current().disableForDeterministicSession();
    }

    public static void clearProcess() {
        installProcess(disabled(new ExternalContentPolicy(
                ExternalContentMode.STARTUP_DETERMINISTIC)));
    }

    public synchronized ExternalContentPolicy policy() { return policy; }

    public ModCatalog processCatalog() { return processCatalog; }

    public ModState startupModState() { return startupModState; }

    public boolean compiledModsSupported() { return compiledModsSupported; }

    public Set<String> trustedCodeOwners() { return trustedCodeOwners; }

    public synchronized RewindClassResolver rewindClassResolver() { return rewindClassResolver; }

    public synchronized void installRewindClassResolver(RewindClassResolver resolver) {
        rewindClassResolver = Objects.requireNonNull(resolver, "resolver");
    }

    public ModRuntimeFindingStore runtimeFindings() { return runtimeFindings; }

    /** Transfers the explicit dev snapshot from boot ownership to ModRuntime construction. */
    public synchronized void transferDevelopmentSourceOwnership() {
        if (bootResource instanceof DevelopmentModSource development) {
            development.transferOwnership();
            bootResource = () -> { };
        }
    }

    /** Engine-owned callback boundary factory used by later object/provider registration wrappers. */
    public synchronized com.openggf.mods.code.ModFaultBoundary createFaultBoundary(
            com.openggf.mods.code.ModRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        com.openggf.mods.ModAudioPreparer.FailureStateSink sink = owners -> {
            if (pendingEditor == null) return new com.openggf.mods.ModStateSaveResult.Saved();
            pendingEditor.setEnabledCascade(owners, false);
            return pendingEditor.save();
        };
        return new com.openggf.mods.code.ModFaultBoundary(runtime.ownerDependencies(),
                runtimeFindings, sink, runtime::disableOwnersForProcess);
    }

    /** Publishes one launch-preparation failure batch and pending-disables its owners. */
    public synchronized void recordRegistrationFailures(Map<String, Throwable> failures) {
        Objects.requireNonNull(failures, "failures");
        failures.forEach((owner, failure) -> {
            java.util.logging.Logger.getLogger(ModSubsystem.class.getName()).log(
                    java.util.logging.Level.SEVERE, "Compiled-mod registration failed for " + owner,
                    failure);
            runtimeFindings.replaceOwner(owner, List.of(registrationFinding(failure)));
        });
        if (failures.isEmpty() || pendingEditor == null) return;
        pendingEditor.setEnabledCascade(failures.keySet(), false);
        com.openggf.mods.ModStateSaveResult saved = pendingEditor.save();
        if (saved instanceof com.openggf.mods.ModStateSaveResult.Failed failed) {
            failures.keySet().forEach(owner -> runtimeFindings.replaceOwner(owner, List.of(
                    registrationFinding(failures.get(owner)),
                    new com.openggf.mods.ModFinding(com.openggf.mods.ModFindingSeverity.ERROR,
                            "MOD_REGISTRATION_DISABLE_SAVE_FAILED", failed.message(), null))));
        }
    }

    private static com.openggf.mods.ModFinding registrationFinding(Throwable failure) {
        if (failure instanceof com.openggf.mods.code.ModRegistrationException structured) {
            return new com.openggf.mods.ModFinding(com.openggf.mods.ModFindingSeverity.ERROR,
                    structured.findingCode(), structured.getMessage(), structured.path());
        }
        return new com.openggf.mods.ModFinding(com.openggf.mods.ModFindingSeverity.ERROR,
                "MOD_REGISTRATION_FAILED", "Compiled-mod registration failed", null);
    }

    /** Publishes a patch/runtime failure closure and pending-disables every affected mod owner. */
    public synchronized void recordOwnerFailures(Set<String> owners, Throwable failure,
                                                   String findingCode) {
        Set<String> affected = Set.copyOf(Objects.requireNonNull(owners, "owners"));
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(findingCode, "findingCode");
        java.util.logging.Logger.getLogger(ModSubsystem.class.getName()).log(
                java.util.logging.Level.SEVERE, "Compiled-mod owner failure: " + affected, failure);
        affected.forEach(owner -> runtimeFindings.replaceOwner(owner, List.of(
                new com.openggf.mods.ModFinding(com.openggf.mods.ModFindingSeverity.ERROR,
                        findingCode, "Compiled-mod owner failed; restart required", null))));
        if (affected.isEmpty() || pendingEditor == null) return;
        pendingEditor.setEnabledCascade(affected, false);
        com.openggf.mods.ModStateSaveResult saved = pendingEditor.save();
        if (saved instanceof com.openggf.mods.ModStateSaveResult.Failed failed) {
            affected.forEach(owner -> runtimeFindings.replaceOwner(owner, List.of(
                    new com.openggf.mods.ModFinding(com.openggf.mods.ModFindingSeverity.ERROR,
                            findingCode, "Compiled-mod owner failed; restart required", null),
                    new com.openggf.mods.ModFinding(com.openggf.mods.ModFindingSeverity.ERROR,
                            "MOD_DISABLE_SAVE_FAILED", failed.message(), null))));
        }
    }

    public ModManagerScreenHost createManager(PixelFont font) {
        if (pendingEditor == null) {
            throw new IllegalStateException("The disabled subsystem has no pending-state editor");
        }
        ModManagerScreen.TextSink text = font == null ? null : ModManagerScreenHost.textSink(font);
        return new ModManagerScreenHost(new ModManagerScreen(
                processCatalog, pendingEditor, runtimeFindings, text, patternWindowAllocator,
                compiledModsSupported));
    }

    public synchronized SessionExternalContentView sessionView() { return sessionView; }

    public synchronized PatternWindowState patternWindowStateForSession() {
        return policy.mayUseInSession() ? patternWindowAllocator : PatternWindowState.EMPTY;
    }

    public void beginNormalSession(int outputRate, String gameCode) {
        long expectedEpoch;
        synchronized (this) {
            if (!policy.mayUseInSession()) return;
            expectedEpoch = sessionEpoch;
        }
        SessionExternalContentView replacement = Objects.requireNonNull(
                sessionFactory.prepare(outputRate, gameCode), "prepared session view");
        synchronized (this) {
            if (sessionEpoch != expectedEpoch || !policy.mayUseInSession()) {
                replacement.close();
                return;
            }
            SessionExternalContentView previous = sessionView;
            sessionView = SessionExternalContentView.EMPTY;
            previous.close();
            try {
                replacement.transferTo(audioBoundary);
                sessionView = replacement;
                policy = new ExternalContentPolicy(ExternalContentMode.NORMAL);
            } catch (RuntimeException error) {
                replacement.close();
                throw error;
            }
        }
    }

    public synchronized void disableForDeterministicSession() {
        sessionEpoch++;
        SessionExternalContentView previous = sessionView;
        sessionView = SessionExternalContentView.EMPTY;
        policy = new ExternalContentPolicy(ExternalContentMode.SESSION_DETERMINISTIC);
        previous.close();
    }

    /** Return-to-title retires leases but deliberately retains the immutable process catalog. */
    public synchronized void returnToTitle() {
        sessionEpoch++;
        SessionExternalContentView previous = sessionView;
        sessionView = SessionExternalContentView.EMPTY;
        if (policy.mode() == ExternalContentMode.SESSION_DETERMINISTIC) {
            policy = new ExternalContentPolicy(ExternalContentMode.NORMAL);
        }
        previous.close();
    }

    /** Retires presentation resources without changing the selected session policy. */
    public synchronized void invalidateSessionPresentation() {
        sessionEpoch++;
        SessionExternalContentView previous = sessionView;
        sessionView = SessionExternalContentView.EMPTY;
        previous.close();
    }

    @Override public void close() {
        returnToTitle();
        try { bootResource.close(); } catch (Exception ignored) { }
        bootResource = () -> { };
    }

    private static ModSubsystem disabled(ExternalContentPolicy policy) {
        return new ModSubsystem(new ModCatalog(List.of(), EffectiveModCatalog.EMPTY),
                null, new ModRuntimeFindingStore(),
                (rate, game) -> SessionExternalContentView.EMPTY,
                new DirectSessionAudioBoundary(),
                policy, Set.of(), ModState.EMPTY);
    }

    /** Production preparation path: decode, build immutable indexes, then transfer the lease. */
    public static SessionViewFactory preparedAudioFactory(ModAudioPreparer preparer,
                                                          EffectiveModCatalog effective,
                                                          ModTrackRegistry registry) {
        return preparedAudioFactory(preparer, effective, registry, com.openggf.mods.ModSfxRegistry.EMPTY);
    }

    public static SessionViewFactory preparedAudioFactory(ModAudioPreparer preparer,
                                                          EffectiveModCatalog effective,
                                                          ModTrackRegistry registry,
                                                          com.openggf.mods.ModSfxRegistry sfxRegistry) {
        Objects.requireNonNull(preparer, "preparer");
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(sfxRegistry, "sfxRegistry");
        return (outputRate, gameCode) -> {
            boolean stock = "s1".equals(gameCode) || "s2".equals(gameCode) || "s3k".equals(gameCode);
            boolean selectedTrack = stock ? !registry.tracks().isEmpty() : registry.tracks().stream()
                    .anyMatch(track -> track.key().modId().equals(gameCode));
            boolean selectedSfx = !stock && sfxRegistry.sfx().stream()
                    .anyMatch(sfx -> sfx.key().modId().equals(gameCode));
            if (!selectedTrack && !selectedSfx) return SessionExternalContentView.EMPTY;
            PreparedAudioSession audio = preparer.prepare(effective, registry, sfxRegistry, outputRate);
            PreparedModMusic music;
            try {
                music = PreparedModMusic.build(effective, registry, sfxRegistry, audio, outputRate,
                        stock ? null : gameCode);
            } catch (RuntimeException error) {
                audio.close();
                throw error;
            }
            return SessionExternalContentView.fromPreparedMusic(music, gameCode);
        };
    }

    /**
     * Builds the normal-boot loader. Root resolution and every filesystem operation
     * remain inside the returned supplier, after {@link #installAtBoot} applies policy.
     */
    public static Supplier<ModSubsystem> normalBootLoader(
            Supplier<Path> rootSupplier, ModInputLimits limits,
            ModCatalogValidator.StockMusicDomain stockMusicDomain,
            SessionAudioBoundary audioBoundary) {
        Objects.requireNonNull(rootSupplier, "rootSupplier");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(stockMusicDomain, "stockMusicDomain");
        Objects.requireNonNull(audioBoundary, "audioBoundary");
        return () -> {
            Path declared = Objects.requireNonNull(rootSupplier.get(), "mod root");
            Path root = declared.toAbsolutePath().normalize();
            if (!declared.equals(root)) {
                throw new IllegalArgumentException("Mod root supplier must return an absolute normalized path");
            }
            DevelopmentModSource development = null;
            java.util.Optional<Path> developmentPath = DevelopmentModSource.configuredPath();
            if (developmentPath.isPresent()) {
                try {
                    development = DevelopmentModSource.snapshot(developmentPath.orElseThrow(), limits);
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException("Unable to snapshot explicit development mod", failure);
                }
            }
            var scanned = development == null ? new DefaultModRepositoryScanner(limits).scan(root)
                    : development.scan();
            boolean completed=false;
            try {
            ModCatalogValidator.ValidationResult validated = new ModCatalogValidator(
                    root, limits, stockMusicDomain).validate(scanned);
            ModStateStore stateStore = development == null ? new ModStateStore(root, limits) : null;
            BootTrustReconciliation trust;
            ModState startup;
            if (development == null) {
                var loadedState = stateStore.load().state();
                trust = reconcileBootTrust(loadedState, validated.entries(), stateStore::save);
                startup = trust.state();
            } else {
                ModDescriptor descriptor = validated.entries().stream()
                        .filter(ModDescriptor.class::isInstance).map(ModDescriptor.class::cast)
                        .findFirst().orElseThrow(() -> new IllegalStateException("Development descriptor invalid"));
                startup = new ModState(ModState.CURRENT_FORMAT_VERSION,List.of(new ModState.Entry(
                        descriptor.manifest().id(),true,0,descriptor.containsCode(),
                        descriptor.containsCode()?descriptor.sha256():null)));
                trust = new BootTrustReconciliation(startup,
                        descriptor.containsCode()?Set.of(descriptor.manifest().id()):Set.of(),Map.of());
            }
            ModCatalog catalog = new EffectiveCatalogBuilder().build(validated.entries(), startup);
            if(development!=null&&catalog.effective().orderedEnabled().size()!=1)
                throw new IllegalStateException("Development mod must be the sole effective owner");
            PendingModStateEditor editor = development == null ? new PendingModStateEditor(
                    startup, catalog.scanned(), stateStore) : null;
            ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
            trust.findings().forEach(findings::replaceOwner);
            ModAudioPreparer preparer = new ModAudioPreparer(root, limits, findings,
                    editor == null ? owners -> new ModStateSaveResult.Saved()
                            : ModAudioPreparer.FailureStateSink.pending(editor));
            Set<String> trustedOwners = development == null ? trust.trustedCodeOwners()
                    : validated.entries().stream().filter(entry -> entry instanceof com.openggf.mods.ModDescriptor)
                    .map(entry -> ((com.openggf.mods.ModDescriptor) entry).manifest().id())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            ModSubsystem subsystem = new ModSubsystem(catalog, editor, findings,
                    preparedAudioFactory(preparer, catalog.effective(), validated.registry(), validated.sfxRegistry()),
                    audioBoundary, new ExternalContentPolicy(ExternalContentMode.NORMAL),
                    trustedOwners, startup);
            if (development != null) subsystem.bootResource = development;
            completed=true;
            return subsystem;
            } finally {if(!completed&&development!=null)development.close();}
        };
    }

    static BootTrustReconciliation reconcileBootTrust(
            ModState loaded, List<? extends ModCatalogEntry> scanned,
            Function<ModState, ModStateSaveResult> saver) {
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(scanned, "scanned");
        Objects.requireNonNull(saver, "saver");
        ModState reconciled = loaded.revokeMismatchedTrust(scanned);
        Map<String, List<ModFinding>> findings = new LinkedHashMap<>();
        if (!reconciled.equals(loaded)) {
            ModStateSaveResult saveResult = Objects.requireNonNull(
                    saver.apply(reconciled), "trust revocation save result");
            if (saveResult instanceof ModStateSaveResult.Failed failed) {
                Map<String, ModState.Entry> reconciledById = new LinkedHashMap<>();
                reconciled.entries().forEach(entry -> reconciledById.put(entry.id(), entry));
                for (ModState.Entry previous : loaded.entries()) {
                    ModState.Entry current = reconciledById.get(previous.id());
                    if (previous.trusted() && current != null && !current.trusted()) {
                        findings.put(previous.id(), List.of(new ModFinding(
                                ModFindingSeverity.WARNING, "TRUST_REVOCATION_SAVE_FAILED",
                                "Jar hash changed; trust was revoked in memory but modstate.json "
                                        + "could not be updated: " + failed.message(), null)));
                    }
                }
            }
        }
        return new BootTrustReconciliation(reconciled,
                reconciled.trustedCodeOwners(scanned), findings);
    }

    record BootTrustReconciliation(ModState state, Set<String> trustedCodeOwners,
                                   Map<String, List<ModFinding>> findings) {
        BootTrustReconciliation {
            state = Objects.requireNonNull(state, "state");
            trustedCodeOwners = Set.copyOf(Objects.requireNonNull(
                    trustedCodeOwners, "trustedCodeOwners"));
            Map<String, List<ModFinding>> copied = new LinkedHashMap<>();
            Objects.requireNonNull(findings, "findings")
                    .forEach((owner, ownerFindings) -> copied.put(owner, List.copyOf(ownerFindings)));
            findings = Map.copyOf(copied);
        }
    }

    @FunctionalInterface
    public interface SessionViewFactory {
        SessionExternalContentView prepare(int outputRate, String gameCode);
    }

    public interface SessionAudioBoundary {
        void install(StreamedMusicPort port);
        void clear();

        static SessionAudioBoundary audioManager(AudioManager audio) {
            Objects.requireNonNull(audio, "audio");
            audio.setStreamedMusicSessionInvalidator(
                    () -> ModSubsystem.current().invalidateSessionPresentation());
            return new SessionAudioBoundary() {
                @Override public void install(StreamedMusicPort port) {
                    audio.installStreamedMusicPort(port);
                }
                @Override public void clear() {
                    audio.resetStreamedMusicPort();
                }
            };
        }
    }

    private static final class DirectSessionAudioBoundary implements SessionAudioBoundary {
        private StreamedMusicPort owned = StreamedMusicPort.EMPTY;
        @Override public synchronized void install(StreamedMusicPort port) {
            owned = Objects.requireNonNull(port, "port");
        }
        @Override public synchronized void clear() {
            StreamedMusicPort previous = owned;
            owned = StreamedMusicPort.EMPTY;
            previous.close();
        }
    }
}
