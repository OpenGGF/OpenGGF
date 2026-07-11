package com.openggf;

import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModCatalog;
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
import com.openggf.graphics.PixelFont;
import com.openggf.mods.ui.ModManagerScreen;
import com.openggf.audio.AudioManager;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.io.ModInputLimits;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
    private final SessionAudioBoundary audioBoundary;
    private ExternalContentPolicy policy;
    private SessionExternalContentView sessionView = SessionExternalContentView.EMPTY;
    private long sessionEpoch;

    public ModSubsystem(ModCatalog processCatalog, ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory) {
        this(processCatalog, null, runtimeFindings, sessionFactory,
                new DirectSessionAudioBoundary(),
                new ExternalContentPolicy(ExternalContentMode.NORMAL));
    }

    public ModSubsystem(ModCatalog processCatalog, ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary) {
        this(processCatalog, null, runtimeFindings, sessionFactory, audioBoundary,
                new ExternalContentPolicy(ExternalContentMode.NORMAL));
    }

    public ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                        ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory) {
        this(processCatalog, Objects.requireNonNull(pendingEditor, "pendingEditor"),
                runtimeFindings, sessionFactory, new DirectSessionAudioBoundary(),
                new ExternalContentPolicy(ExternalContentMode.NORMAL));
    }

    public ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                        ModRuntimeFindingStore runtimeFindings,
                        SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary) {
        this(processCatalog, Objects.requireNonNull(pendingEditor, "pendingEditor"),
                runtimeFindings, sessionFactory, audioBoundary,
                new ExternalContentPolicy(ExternalContentMode.NORMAL));
    }

    private ModSubsystem(ModCatalog processCatalog, PendingModStateEditor pendingEditor,
                         ModRuntimeFindingStore runtimeFindings,
                         SessionViewFactory sessionFactory, SessionAudioBoundary audioBoundary,
                         ExternalContentPolicy policy) {
        this.processCatalog = Objects.requireNonNull(processCatalog, "processCatalog");
        this.runtimeFindings = Objects.requireNonNull(runtimeFindings, "runtimeFindings");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.pendingEditor = pendingEditor;
        this.audioBoundary = Objects.requireNonNull(audioBoundary, "audioBoundary");
        this.policy = Objects.requireNonNull(policy, "policy");
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

    public ModRuntimeFindingStore runtimeFindings() { return runtimeFindings; }

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
                policy);
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
            var startup = stateStore.load().state();
            ModCatalog catalog = new EffectiveCatalogBuilder().build(validated.entries(), startup);
            PendingModStateEditor editor = new PendingModStateEditor(
                    startup, catalog.scanned(), stateStore);
            ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
            ModAudioPreparer preparer = new ModAudioPreparer(root, limits, findings,
                    ModAudioPreparer.FailureStateSink.pending(editor));
            return new ModSubsystem(catalog, editor, findings,
                    preparedAudioFactory(preparer, catalog.effective(), validated.registry()),
                    audioBoundary);
        };
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
