package com.openggf.integration.presence;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PresenceManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(PresenceManager.class.getName());
    private static final long TIMER_UPDATE_INTERVAL_MS = 15_000L;

    private final boolean configuredEnabled;
    private final boolean showTimer;
    private final boolean showZone;
    private final PresenceSnapshotProvider snapshotProvider;
    private final PresenceFormatter formatter;
    private final PresenceClient client;
    private final LongSupplier clockMillis;

    private boolean enabledForRun;
    private boolean connected;
    private PresenceSnapshot lastSnapshot;
    private long lastPublishMillis = Long.MIN_VALUE;

    public PresenceManager(boolean configuredEnabled,
                           boolean showTimer,
                           boolean showZone,
                           PresenceSnapshotProvider snapshotProvider,
                           PresenceFormatter formatter,
                           PresenceClient client,
                           LongSupplier clockMillis) {
        this.configuredEnabled = configuredEnabled;
        this.showTimer = showTimer;
        this.showZone = showZone;
        this.snapshotProvider = Objects.requireNonNull(snapshotProvider, "snapshotProvider");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.client = Objects.requireNonNull(client, "client");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
        this.enabledForRun = configuredEnabled;
    }

    public void tick() {
        if (!configuredEnabled || !enabledForRun) {
            return;
        }
        try {
            if (!connected) {
                client.connect();
                connected = true;
            }

            PresenceSnapshot snapshot = snapshotProvider.capture();
            if (shouldPublish(snapshot)) {
                client.update(formatter.format(snapshot, showTimer, showZone));
                lastSnapshot = snapshot;
                lastPublishMillis = clockMillis.getAsLong();
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Discord Rich Presence disabled for this run after a client failure.", e);
            disableForRun();
        }
    }

    public boolean isEnabledForRun() {
        return enabledForRun;
    }

    private boolean shouldPublish(PresenceSnapshot snapshot) {
        if (lastSnapshot == null) {
            return true;
        }
        if (!lastSnapshot.sameExceptTimer(snapshot)) {
            return true;
        }
        if (Objects.equals(lastSnapshot, snapshot)) {
            return false;
        }
        return clockMillis.getAsLong() - lastPublishMillis >= TIMER_UPDATE_INTERVAL_MS;
    }

    private void disableForRun() {
        enabledForRun = false;
        try {
            client.close();
        } catch (Exception closeFailure) {
            LOGGER.log(Level.FINE, "Failed to close Discord Rich Presence client.", closeFailure);
        }
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to close Discord Rich Presence client.", e);
        }
    }
}
