package com.openggf.game.rewind;

/**
 * Engine-internal, pure admission for a native publication's current lifetime.
 * The registry invokes this before any restore writes, only for adapters admitted
 * through NativeRewindAdapterPublication. Implementing this interface alone grants
 * no authority. A missing guarded payload rejects before any restore or reset.
 * Existing creator-facing snapshot interfaces remain unchanged.
 * At an admitted frame boundary, validation must leave captured gameplay state
 * unchanged and must not invoke restore, reset or callbacks. Immutable-source
 * authentication may be memoized; an already-broken owner may terminate itself
 * fail-closed under its existing lifecycle rules.
 */
public interface NativeRewindRestoreGuard<S> extends RewindSnapshottable<S> {
    void validateRestore(S snapshot);
}
