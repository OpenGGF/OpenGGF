package com.openggf.integration.presence;

public final class NoOpPresenceClient implements PresenceClient {
    public static final NoOpPresenceClient INSTANCE = new NoOpPresenceClient();

    private NoOpPresenceClient() {
    }

    @Override
    public void connect() {
    }

    @Override
    public void update(PresencePayload payload) {
    }

    @Override
    public void clear() {
    }

    @Override
    public void close() {
    }
}
