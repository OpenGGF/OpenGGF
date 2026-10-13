package com.openggf.game.mutators;

import com.openggf.LevelFrameContext;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioMutatorAccess;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameServices;
import com.openggf.game.GameplayObservation.Phase;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.mods.mutators.AudioEffectPolicy;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Captured world-owned optional audio; all playback still enters the native ROM audio owner. */
public final class AudioMutatorRuntime implements WorldSessionPolicyState,
        RewindSnapshottable<AudioMutatorRuntime.Snapshot>, AudioMutatorAccess.Listener {
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final int MAX_MUSIC_TICKS = 180;
    private static final int WARNING_QUIET_TICKS = 30;
    private final long generation = GENERATIONS.incrementAndGet();
    private final WorldSession world;
    private AudioManager audio;
    private AudioMutatorAccess.Binding binding;
    private SpriteManager sprites;
    private long nativeTicks;
    private int nativeMusic = -1;
    private final List<Integer> nativeOverrides = new ArrayList<>();
    private boolean speedShoes;
    private int speedMultiplier = 1;
    private boolean tempoOwned;
    private long musicActivation;
    private int musicCue = -1;
    private long musicExpires;
    private long lastMusicTick = -600;
    private long lastCueTick = -600;
    private long warningUntil;
    private final Set<Long> handled = new HashSet<>();
    private final Map<String, PlayerGate> beforePlayers = new HashMap<>();
    private final Set<String> events = new HashSet<>();
    private boolean tickArmed;
    private boolean retired;

    // WorldSession is still constructing its policy composite here. Resolve its source only in callbacks.
    public AudioMutatorRuntime(WorldSession world) { this.world = Objects.requireNonNull(world); }
    @Override public <T> T getService(Class<T> type) { return type == AudioMutatorRuntime.class ? type.cast(this) : null; }
    @Override public RewindSnapshottable<?> rewindAdapter() { return this; }
    @Override public String key() { return "audio-mutator-runtime"; }

    private void bindAudio() {
        if (retired) return;
        var manager = GameServices.audio();
        if (binding != null && binding.active() && audio == manager) return;
        if (binding != null) binding.close();
        audio = manager;
        binding = AudioMutatorAccess.bind(manager, this);
    }
    @Override public void bindGameplay(GameplayModeContext context) {
        if (context.getWorldSession() != world) throw new IllegalArgumentException("Audio context belongs to another world");
        bindAudio();
        bindRoster(context.getSpriteManager());
    }
    @Override public void beforeAssembly(LevelLoadCause cause) {
        leaveOrdinaryGameplay(Phase.SETUP);
        nativeMusic = -1;
        nativeOverrides.clear();
        speedShoes = false;
        speedMultiplier = 1;
        tempoOwned = false;
        warningUntil = nativeTicks;
        bindAudio();
    }
    @Override public void bindRoster(SpriteManager sprites) {
        this.sprites = sprites;
        clearTick();
    }
    @Override public void failedAssembly(LevelLoadCause cause) { leaveOrdinaryGameplay(Phase.SETUP); }
    @Override public void closeScreens() { }
    @Override public void retire() {
        if (retired) return;
        leaveOrdinaryGameplay(Phase.CLOSED);
        retired = true;
        if (binding != null) binding.close();
        binding = null;
        sprites = null;
    }
    private boolean ownsAudio() { return !retired && binding != null && binding.active(); }

    @Override public void beforeNativeTick(LevelFrameContext context, LogicalInputSnapshot input) {
        clearTick();
        if (!ownsAudio() || context != null && context.gameModule() != world.getGameModule()) return;
        if (context != null) sprites = context.spriteManager();
        tickArmed = true;
        if (sprites == null) return;
        for (var sprite : sprites.getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite player) beforePlayers.put(player.getCode(), gate(player));
        }
    }
    @Override public void afterNativeTick(LevelFrameContext context, LogicalInputSnapshot input) {
        if (!tickArmed || !ownsAudio()) { clearTick(); return; }
        nativeTicks++;
        var source = TransientMutatorPolicyAccess.source(world);
        var entries = source == null ? List.<TransientMutatorPolicySource.Entry>of() : source.entries();
        reconcile(entries, source);
        TransientMutatorPolicySource.Entry music = null;
        TransientMutatorPolicySource.Entry landing = null;
        for (var entry : entries) {
            if (!(entry.policy() instanceof AudioEffectPolicy policy) || handled.contains(entry.activationId())) continue;
            if (policy.mode() == AudioEffectPolicy.Mode.MUSIC) music = entry;
            else landing = entry;
        }
        if (music != null) startMusic(music, source);
        if (landing != null && eligibleEvent()) playLanding(landing, source);
        clearTick();
    }
    @Override public void onPresentation(GameplayModeContext context, LogicalInputSnapshot input, Phase phase) {
        if (phase != Phase.ACTIVE) { leaveOrdinaryGameplay(phase); return; }
        if (!ownsAudio()) return;
        var source = TransientMutatorPolicyAccess.source(world);
        reconcile(source == null ? List.of() : source.entries(), source);
    }
    @Override public void leaveOrdinaryGameplay(Phase phase) {
        if (phase == Phase.PAUSED || phase == Phase.HELD || phase == Phase.REWIND || phase == Phase.ACTIVE) return;
        if (musicActivation != 0) {
            report(TransientMutatorPolicyAccess.source(world), musicActivation, "Cancelled on leaving ordinary gameplay");
            releaseMusic(true);
        }
        // A retained activation cannot spring back on stage return or a load boundary.
        for (var entry : TransientMutatorPolicyAccess.entries(world))
            if (entry.policy() instanceof AudioEffectPolicy) handled.add(entry.activationId());
        clearTick();
    }
    private void clearTick() { tickArmed = false; beforePlayers.clear(); events.clear(); }

    /** Accepted native landing only; generic air-bit changes never call this. */
    public void landed(AbstractPlayableSprite player) { event(player, false); }
    /** The native release branch, including a zero-inertia boundary-clamped release. */
    public void spindashReleased(AbstractPlayableSprite player) { event(player, true); }
    private void event(AbstractPlayableSprite player, boolean spindash) {
        if (!tickArmed || !ownsAudio() || sprites == null || player == null
                || sprites.getSprite(player.getCode()) != player) return;
        var before = beforePlayers.get(player.getCode());
        var now = gate(player);
        if (before != null && before.eligible() && now.eligible()
                && before.controlGeneration() == now.controlGeneration() && (!spindash || before.spindash()))
            events.add(player.getCode());
    }
    private boolean eligibleEvent() {
        if (sprites == null) return false;
        for (String code : events) {
            if (sprites.getSprite(code) instanceof AbstractPlayableSprite player) {
                var before = beforePlayers.get(code);
                var now = gate(player);
                if (before != null && now.eligible() && before.controlGeneration() == now.controlGeneration()) return true;
            }
        }
        return false;
    }
    private static PlayerGate gate(AbstractPlayableSprite player) {
        var routine = player.getObjectRoutineOverride();
        boolean eligible = player.isNativeSlotPresent() && !player.isHidden() && !player.isDebugMode()
                && !player.getDead() && !player.isHurt() && !player.isDrowningPreDeath() && !player.isInDeathRestartRoutine()
                && !player.isControlLocked() && !player.isObjectControlled() && !player.isObjectControlSuppressesMovement()
                && player.getForcedInputMask() == 0 && (routine == null || (routine & 0xFF) == 2);
        return new PlayerGate(eligible, player.getObjectControlGeneration(), player.getSpindash());
    }

    private void reconcile(List<TransientMutatorPolicySource.Entry> entries, TransientMutatorPolicySource source) {
        var present = new HashSet<Long>();
        for (var entry : entries) if (entry.policy() instanceof AudioEffectPolicy) present.add(entry.activationId());
        handled.retainAll(present);
        if (musicActivation != 0 && (!present.contains(musicActivation) || nativeTicks >= musicExpires)) {
            report(source, musicActivation, "Music surprise expired; native intent resumed");
            releaseMusic(true);
        }
    }
    private void startMusic(TransientMutatorPolicySource.Entry entry, TransientMutatorPolicySource source) {
        var policy = (AudioEffectPolicy) entry.policy();
        try { binding.prepareMusic(policy.cueId()); }
        catch (IllegalArgumentException | IllegalStateException failure) { reject(entry, source, failure); return; }
        if (binding.isOverride(nativeMusic) && musicActivation == 0) {
            Integer current = binding.foregroundMusic();
            if (current != null && current != nativeMusic && binding.permitsMusic(current)) {
                nativeMusic = current;
                nativeOverrides.clear();
            }
        }
        if (!binding.permitsMusic(nativeMusic) || speedShoes || speedMultiplier > 1 || tempoOwned) {
            report(source, entry.activationId(), "Native music/control has priority");
            return;
        }
        if (policy.cueId() == nativeMusic) {
            reject(entry, source, new IllegalArgumentException("Cue is already the current native song"));
            return;
        }
        if (nativeTicks - lastMusicTick < policy.minimumIntervalTicks()) return;
        // Superseding a surprise never reinstates another surprise when the newer lease expires.
        if (musicActivation != 0) report(source, musicActivation, "Superseded by a newer music surprise");
        binding.playTemporaryMusic(policy.cueId());
        musicActivation = entry.activationId();
        musicCue = policy.cueId();
        musicExpires = nativeTicks + Math.min(MAX_MUSIC_TICKS, Math.max(1, entry.remainingPresentations()));
        lastMusicTick = nativeTicks;
        handled.add(entry.activationId());
        if (entry.oneShot()) source.acknowledge(entry.activationId());
        report(source, entry.activationId(), "Native ROM music surprise playing");
    }
    private void playLanding(TransientMutatorPolicySource.Entry entry, TransientMutatorPolicySource source) {
        var policy = (AudioEffectPolicy) entry.policy();
        try { binding.prepareLandingCue(policy.cueId()); }
        catch (IllegalArgumentException | IllegalStateException failure) { reject(entry, source, failure); return; }
        if (nativeTicks < warningUntil || binding.warningActive()
                || !binding.permitsMusic(nativeMusic) || !nativeOverrides.isEmpty()) {
            report(source, entry.activationId(), "Native warning/music has priority");
            return;
        }
        if (nativeTicks - lastCueTick < policy.minimumIntervalTicks()) return;
        try { binding.playLandingCue(policy.cueId()); }
        catch (IllegalArgumentException | IllegalStateException failure) { reject(entry, source, failure); return; }
        lastCueTick = nativeTicks;
        if (entry.oneShot()) { handled.add(entry.activationId()); source.acknowledge(entry.activationId()); }
        report(source, entry.activationId(), "Native ROM landing cue played");
    }
    private void reject(TransientMutatorPolicySource.Entry entry, TransientMutatorPolicySource source, RuntimeException failure) {
        handled.add(entry.activationId());
        report(source, entry.activationId(), "Rejected: " + failure.getMessage());
    }
    private static void report(TransientMutatorPolicySource source, long id, String text) {
        if (source != null) source.report(id, text.length() > 120 ? text.substring(0, 120) : text);
    }
    private void releaseMusic(boolean resume) {
        if (musicActivation == 0) return;
        musicActivation = 0;
        musicCue = -1;
        musicExpires = 0;
        if (resume && ownsAudio() && binding.permitsMusic(nativeMusic)) binding.resumeNativeMusic(nativeMusic);
    }

    @Override public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) {
        if (retired) return;
        var source = TransientMutatorPolicyAccess.source(world);
        if (musicActivation != 0) report(source, musicActivation, "Cancelled: native audio intent has priority");
        switch (intent.kind()) {
            case MUSIC -> {
                boolean override = ownsAudio() && binding.isOverride(intent.value());
                // A native 1-up must save native zone music, never the optional foreground.
                releaseMusic(override);
                if (override && nativeMusic >= 0) nativeOverrides.add(nativeMusic);
                else nativeOverrides.clear();
                nativeMusic = intent.value();
                tempoOwned = false;
            }
            case RESTORE, END_OVERRIDE -> {
                releaseMusic(true);
                if (intent.kind() == AudioMutatorAccess.Kind.RESTORE || intent.value() == nativeMusic) {
                    if (!nativeOverrides.isEmpty()) nativeMusic = nativeOverrides.removeLast();
                }
            }
            case SPEED_SHOES -> { releaseMusic(true); speedShoes = intent.value() != 0; }
            case SPEED_MULTIPLIER -> { releaseMusic(true); speedMultiplier = intent.value(); }
            case TEMPO -> { releaseMusic(true); tempoOwned = true; }
            case COMMAND, STOP, FADE, FOREIGN -> {
                // Release before a driver command takes effect; the original command remains last and authoritative.
                releaseMusic(intent.kind() == AudioMutatorAccess.Kind.COMMAND);
                nativeMusic = -1;
                nativeOverrides.clear();
            }
        }
    }
    @Override public void onNativeSfx(int id) {
        if (ownsAudio() && binding.isWarning(id)) warningUntil = Math.max(warningUntil, nativeTicks + WARNING_QUIET_TICKS);
    }
    @Override public void onDisplaced() { releaseMusic(false); clearTick(); }

    @Override public Snapshot capture() {
        return new Snapshot(generation, nativeTicks, nativeMusic, nativeOverrides, speedShoes, speedMultiplier, tempoOwned,
                musicActivation, musicCue, musicExpires, lastMusicTick, lastCueTick, warningUntil, handled,
                beforePlayers, events, tickArmed);
    }
    @Override public void restore(Snapshot snapshot) {
        if (retired) throw new IllegalStateException("Audio runtime belongs to a retired world");
        if (snapshot.generation() != generation) throw new IllegalArgumentException("Audio snapshot belongs to another world");
        nativeTicks = snapshot.nativeTicks(); nativeMusic = snapshot.nativeMusic();
        nativeOverrides.clear(); nativeOverrides.addAll(snapshot.nativeOverrides());
        speedShoes = snapshot.speedShoes(); speedMultiplier = snapshot.speedMultiplier(); tempoOwned = snapshot.tempoOwned();
        musicActivation = snapshot.musicActivation(); musicCue = snapshot.musicCue(); musicExpires = snapshot.musicExpires();
        lastMusicTick = snapshot.lastMusicTick(); lastCueTick = snapshot.lastCueTick(); warningUntil = snapshot.warningUntil();
        handled.clear(); handled.addAll(snapshot.handled()); beforePlayers.clear(); beforePlayers.putAll(snapshot.beforePlayers());
        events.clear(); events.addAll(snapshot.events()); tickArmed = snapshot.tickArmed();
        // AudioManager's timeline/adapter restores presentation. Never emit commands during this logical restore.
    }
    public record PlayerGate(boolean eligible, int controlGeneration, boolean spindash) { }
    public record Snapshot(long generation, long nativeTicks, int nativeMusic, List<Integer> nativeOverrides,
            boolean speedShoes, int speedMultiplier, boolean tempoOwned, long musicActivation, int musicCue, long musicExpires,
            long lastMusicTick, long lastCueTick, long warningUntil, Set<Long> handled,
            Map<String, PlayerGate> beforePlayers, Set<String> events, boolean tickArmed) {
        public Snapshot {
            nativeOverrides = List.copyOf(nativeOverrides); handled = Set.copyOf(handled);
            beforePlayers = Map.copyOf(beforePlayers); events = Set.copyOf(events);
        }
    }
}
