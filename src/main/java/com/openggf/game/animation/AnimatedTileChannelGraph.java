package com.openggf.game.animation;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.AnimatedTileChannelSnapshot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-owned coordinator for animated tile channels.
 *
 * <p>The graph holds the active channel set for the current zone/runtime and
 * remembers the last resolved phase for each channel id. That lets channels use
 * phase-based caching without keeping their own mutable frame state.
 */
public final class AnimatedTileChannelGraph implements RewindSnapshottable<AnimatedTileChannelSnapshot> {

    private List<AnimatedTileChannel> channels = List.of();
    private Map<String, Integer> channelIndexById = Map.of();
    private int[] installedPhases = new int[0];
    private boolean[] installedPhasePresent = new boolean[0];
    private int recordedInstalledPhaseCount;
    private final Map<String, Integer> diagnosticPhases = new LinkedHashMap<>();

    /**
     * Replaces the current channel set and clears any cached per-channel phase.
     */
    public void install(List<AnimatedTileChannel> channels) {
        List<AnimatedTileChannel> installed = List.copyOf(Objects.requireNonNull(channels, "channels"));
        Map<String, Integer> installedIndexes = new HashMap<>();
        for (int index = 0; index < installed.size(); index++) {
            AnimatedTileChannel channel = installed.get(index);
            if (installedIndexes.put(channel.channelId(), index) != null) {
                throw new IllegalArgumentException("Duplicate animated tile channelId: " + channel.channelId());
            }
        }
        this.channels = installed;
        channelIndexById = Map.copyOf(installedIndexes);
        installedPhases = new int[installed.size()];
        installedPhasePresent = new boolean[installed.size()];
        recordedInstalledPhaseCount = 0;
        diagnosticPhases.clear();
    }

    /** Returns the currently installed channel definitions. */
    public List<AnimatedTileChannel> channels() {
        return channels;
    }

    /** Removes all channels and any cached phase history. */
    public void clear() {
        channels = List.of();
        channelIndexById = Map.of();
        installedPhases = new int[0];
        installedPhasePresent = new boolean[0];
        recordedInstalledPhaseCount = 0;
        diagnosticPhases.clear();
    }

    /**
     * Resolves and applies each active channel for the current frame.
     *
     * <p>Each channel receives its own {@link ChannelContext}, derived from the
     * shared frame context plus the concrete channel being evaluated.
     */
    public void update(ChannelContext baseContext) {
        Objects.requireNonNull(baseContext, "baseContext");
        List<AnimatedTileChannel> activeChannels = channels;
        int[] activePhases = installedPhases;
        boolean[] activePhasePresent = installedPhasePresent;
        for (int channelIndex = 0; channelIndex < activeChannels.size(); channelIndex++) {
            AnimatedTileChannel channel = activeChannels.get(channelIndex);
            if (!channel.guard().allows()) {
                continue;
            }
            ChannelContext channelContext = new ChannelContext(
                    this,
                    channel,
                    baseContext.level(),
                    baseContext.runtimeState(),
                    baseContext.zoneIndex(),
                    baseContext.actIndex(),
                    baseContext.frameCounter());
            int phase = channel.phaseSource().resolve(channelContext);
            if (installedPhases == activePhases && installedPhasePresent == activePhasePresent) {
                if (channel.cachePolicy() == AnimatedTileCachePolicy.ON_PHASE_CHANGE
                        && activePhasePresent[channelIndex]
                        && activePhases[channelIndex] == phase) {
                    continue;
                }
                if (!activePhasePresent[channelIndex]) {
                    activePhasePresent[channelIndex] = true;
                    recordedInstalledPhaseCount++;
                }
                activePhases[channelIndex] = phase;
            } else {
                if (channel.cachePolicy() == AnimatedTileCachePolicy.ON_PHASE_CHANGE
                        && hasRecordedPhase(channel.channelId())
                        && getLastPhase(channel.channelId()) == phase) {
                    continue;
                }
                recordPhase(channel.channelId(), phase);
            }
            channel.applyStrategy().apply(channelContext);
        }
    }

    private boolean hasRecordedPhase(String channelId) {
        Integer channelIndex = channelId != null ? channelIndexById.get(channelId) : null;
        return channelIndex != null
                ? installedPhasePresent[channelIndex]
                : diagnosticPhases.containsKey(channelId);
    }

    /**
     * Records the last resolved phase for a channel. Package-private for testing.
     */
    void recordPhase(String channelId, int phase) {
        Integer channelIndex = channelId != null ? channelIndexById.get(channelId) : null;
        if (channelIndex == null) {
            diagnosticPhases.put(channelId, phase);
            return;
        }
        int index = channelIndex;
        if (!installedPhasePresent[index]) {
            installedPhasePresent[index] = true;
            recordedInstalledPhaseCount++;
        }
        installedPhases[index] = phase;
    }

    /**
     * Returns the last resolved phase for a channel, or -1 if not recorded.
     * Package-private for testing.
     */
    int getLastPhase(String channelId) {
        Integer channelIndex = channelId != null ? channelIndexById.get(channelId) : null;
        if (channelIndex != null) {
            int index = channelIndex;
            return installedPhasePresent[index] ? installedPhases[index] : -1;
        }
        Integer phase = diagnosticPhases.get(channelId);
        return phase != null ? phase : -1;
    }

    /** Returns the number of installed and diagnostic channels with recorded phases. */
    public int recordedPhaseCount() {
        return recordedInstalledPhaseCount + diagnosticPhases.size();
    }

    // ── RewindSnapshottable ───────────────────────────────────────────────

    @Override
    public String key() {
        return "animated-tile-channels";
    }

    @Override
    public AnimatedTileChannelSnapshot capture() {
        Map<String, Integer> phases = new LinkedHashMap<>(recordedPhaseCount());
        for (int index = 0; index < channels.size(); index++) {
            if (installedPhasePresent[index]) {
                phases.put(channels.get(index).channelId(), installedPhases[index]);
            }
        }
        phases.putAll(diagnosticPhases);
        return new AnimatedTileChannelSnapshot(phases);
    }

    @Override
    public void restore(AnimatedTileChannelSnapshot s) {
        Arrays.fill(installedPhasePresent, false);
        recordedInstalledPhaseCount = 0;
        diagnosticPhases.clear();
        for (Map.Entry<String, Integer> phase : s.lastPhaseByChannel().entrySet()) {
            recordPhase(phase.getKey(), phase.getValue());
        }
    }
}
