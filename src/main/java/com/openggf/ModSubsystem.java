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
    private ExternalContentPolicy policy;
    private SessionExternalContentView sessionView = SessionExternalContentView.EMPTY;
    private long sessionEpoch;

    public ModSubsystem(ModCatalog processCatalog, ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory) {
        this(processCatalog, null, runtimeFindings, sessionFactory,
                new DirectSessionAudioBoundary(),
                new ExternalContentPolicy(ExternalContentMode.NORMAL), Set.of());
    }

    public ModSubsystem(ModCatalog processCatalog, ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary) {
        this(processCatalog, null, runtimeFindings, sessionFactory, audioBoundary,
                new ExternalContentPolicy(ExternalContentMode.NORMAL), Set.of());
    }

    public ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                        ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory) {
        this(processCatalog, Objects.requireNonNull(pendingEditor, "pendingEditor"),
                runtimeFindings, sessionFactory, new DirectSessionAudioBoundary(),
                new ExternalContentPolicy(ExternalContentMode.NORMAL), Set.of());
    }

    public ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                        ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary) {
        this(processCatalog, Objects.requireNonNull(pendingEditor, "pendingEditor"),
                runtimeFindings, sessionFactory, audioBoundary,
                new ExternalContentPolicy(ExternalContentMode.NORMAL), Set.of());
    }

    private ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                         ModRuntimeFindingStore runtimeFindings,
                         SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary,
                         ExternalContentPolicy policy, Set<String> trustedCodeOwners) {
        this.processCatalog = Objects.requireNonNull(processCatalog, "processCatalog");
        this.runtimeFindings = Objects.requireNonNull(runtimeFindings, "runtimeFindings");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.pendingEditor = pendingEditor;
        this.audioBoundary = Objects.requireNonNull(audioBoundary, "audioBoundary");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.trustedCodeOwners = Set.copyOf(Objects.requireNonNull(
                trustedCodeOwners, "trustedCodeOwners"));
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
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(normalBootLoader, "normalBootLoader");
        ModSubsystem replacement = policy.mayScanAtBoot()
                ? Objects.requireNonNull(normalBootLoader.get(), "normalBootLoader result")
                : disabled(policy);
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

    public Set<String> trustedCodeOwners() { return trustedCodeOwners; }

    public ModRuntimeFindingStore runtimeFindings() { return runtimeFindings; }

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
            runtimeFindings.replaceOwner(owner, List.of(new com.openggf.mods.ModFinding(
                    com.openggf.mods.ModFindingSeverity.ERROR, "MOD_REGISTRATION_FAILED",
                    "Compiled-mod registration failed", null)));
        });
        if (failures.isEmpty() || pendingEditor == null) return;
        pendingEditor.setEnabledCascade(failures.keySet(), false);
        com.openggf.mods.ModStateSaveResult saved = pendingEditor.save();
        if (saved instanceof com.openggf.mods.ModStateSaveResult.Failed failed) {
            failures.keySet().forEach(owner -> runtimeFindings.replaceOwner(owner, List.of(
                    new com.openggf.mods.ModFinding(com.openggf.mods.ModFindingSeverity.ERROR,
                            "MOD_REGISTRATION_FAILED", "Compiled-mod registration failed", null),
                    new com.openggf.mods.ModFinding(com.openggf.mods.ModFindingSeverity.ERROR,
                            "MOD_REGISTRATION_DISABLE_SAVE_FAILED", failed.message(), null))));
        }
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
                processCatalog, pendingEditor, runtimeFindings, text));
    }

    public synchronized SessionExternalContentView sessionView() { return sessionView; }

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

    @Override public void close() { returnToTitle(); }

    private static ModSubsystem disabled(ExternalContentPolicy policy) {
        return new ModSubsystem(new ModCatalog(List.of(), EffectiveModCatalog.EMPTY),
                null, new ModRuntimeFindingStore(),
                (rate, game) -> SessionExternalContentView.EMPTY,
                new DirectSessionAudioBoundary(),
                policy, Set.of());
    }

    /** Production preparation path: decode, build immutable indexes, then transfer the lease. */
    public static SessionViewFactory preparedAudioFactory(ModAudioPreparer preparer,
                                                          EffectiveModCatalog effective,
                                                          ModTrackRegistry registry) {
        Objects.requireNonNull(preparer, "preparer");
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(registry, "registry");
        return (outputRate, gameCode) -> {
            if (registry.tracks().isEmpty()) return SessionExternalContentView.EMPTY;
            PreparedAudioSession audio = preparer.prepare(effective, registry, outputRate);
            PreparedModMusic music;
            try {
                music = PreparedModMusic.build(effective, registry, audio, outputRate);
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
            var scanned = new DefaultModRepositoryScanner(limits).scan(root);
            ModCatalogValidator.ValidationResult validated = new ModCatalogValidator(
                    root, limits, stockMusicDomain).validate(scanned);
            ModStateStore stateStore = new ModStateStore(root, limits);
            var loadedState = stateStore.load().state();
            BootTrustReconciliation trust = reconcileBootTrust(
                    loadedState, validated.entries(), stateStore::save);
            var startup = trust.state();
            ModCatalog catalog = new EffectiveCatalogBuilder().build(validated.entries(), startup);
            PendingModStateEditor editor = new PendingModStateEditor(
                    startup, catalog.scanned(), stateStore);
            ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
            trust.findings().forEach(findings::replaceOwner);
            ModAudioPreparer preparer = new ModAudioPreparer(root, limits, findings,
                    ModAudioPreparer.FailureStateSink.pending(editor));
            return new ModSubsystem(catalog, editor, findings,
                    preparedAudioFactory(preparer, catalog.effective(), validated.registry()),
                    audioBoundary, new ExternalContentPolicy(ExternalContentMode.NORMAL),
                    trust.trustedCodeOwners());
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
