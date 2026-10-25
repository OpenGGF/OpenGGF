package com.openggf.game.internal;

/** Internal explicit-world delegation; implementations return only already-owned service identities. */
public interface WorldPlcServiceProvider {
    <T> T worldPlcService(Class<T> type);
}
