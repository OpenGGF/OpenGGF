package com.openggf.game.rewind;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Engine-internal provenance attached to actual owned adapters, never inferred from creator keys. */
public final class RewindAdapterOwnership {
    private static final ReferenceQueue<Object> EXPIRED = new ReferenceQueue<>();
    private static final Map<IdentityReference, Identity> OWNERS = new HashMap<>();
    // Concrete creator-class queries retain their real instance. Its provenance is
    // separate from published adapters so it still receives an owned proxy when captured.
    private static final Map<IdentityReference, Identity> DELEGATES = new HashMap<>();
    private static final Map<IdentityReference, Boolean> NATIVE_PUBLICATIONS = new HashMap<>();

    private RewindAdapterOwnership() { }

    public static synchronized String bind(Object adapter, String owner, String localKey) {
        requireEngineCaller();
        return bindIdentity(adapter, null, owner, localKey);
    }

    /** Binds the published adapter and records the real captured state without changing its type. */
    public static synchronized String bindDelegate(Object adapter, Object delegate, String owner, String localKey) {
        requireEngineCaller();
        return bindIdentity(adapter, Objects.requireNonNull(delegate, "delegate"), owner, localKey);
    }

    private static void requireEngineCaller() {
        Class<?> caller = StackWalker.getInstance(java.util.Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,
                StackWalker.Option.SHOW_HIDDEN_FRAMES)).walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != RewindAdapterOwnership.class && type.getClassLoader() != null)
                .findFirst().orElseThrow());
        if (caller.getClassLoader() != RewindAdapterOwnership.class.getClassLoader()) {
            throw new SecurityException("Creator rewind identity must come from its engine-owned registration");
        }
    }

    private static String bindIdentity(Object adapter, Object delegate, String owner, String localKey) {
        Objects.requireNonNull(adapter, "adapter");
        owner = com.openggf.game.ModKeySyntax.requireManifestId(owner);
        Objects.requireNonNull(localKey, "localKey");
        if (localKey.isBlank() || localKey.length() > 512) {
            throw new IllegalArgumentException("Rewind adapter key must contain 1 to 512 characters");
        }
        expunge();
        if (isNativePublication(adapter) || delegate != null && isNativePublication(delegate))
            throw new IllegalStateException("A native rewind service cannot become a creator-owned contribution");
        Identity identity = new Identity(owner, localKey);
        Identity existing = identityOf(adapter);
        Identity captured = delegate == null ? null : identityOf(delegate);
        if (existing != null && !existing.equals(identity) || captured != null && !captured.equals(identity))
            throw new IllegalStateException("Rewind adapter ownership cannot change");
        OWNERS.putIfAbsent(new IdentityReference(adapter, EXPIRED), identity);
        if (delegate != null) DELEGATES.putIfAbsent(new IdentityReference(delegate, EXPIRED), identity);
        return identity.key();
    }

    /** Reads engine provenance without accepting a reported key as authority. */
    public static synchronized boolean isBound(Object adapter) {
        expunge();
        return OWNERS.containsKey(new IdentityReference(Objects.requireNonNull(adapter), null));
    }

    /** Includes concrete state that is captured through an owned published adapter. */
    public static synchronized boolean isOwned(Object adapter) {
        expunge();
        return identityOf(Objects.requireNonNull(adapter)) != null || isNativePublication(adapter);
    }

    static synchronized void markNativePublication(Object adapter) {
        expunge();
        if (identityOf(Objects.requireNonNull(adapter)) != null)
            throw new IllegalStateException("A creator-owned adapter cannot become a native rewind service");
        NATIVE_PUBLICATIONS.putIfAbsent(new IdentityReference(adapter, EXPIRED), Boolean.TRUE);
    }

    public static synchronized boolean isNativePublication(Object adapter) {
        expunge();
        return NATIVE_PUBLICATIONS.containsKey(new IdentityReference(Objects.requireNonNull(adapter), null));
    }

    public static synchronized boolean isBoundToOwner(Object adapter, String owner) {
        expunge();
        Identity identity = identityOf(Objects.requireNonNull(adapter));
        return identity != null && identity.owner().equals(owner);
    }

    public static synchronized boolean hasIdentity(Object adapter, String owner, String localKey) {
        expunge();
        return new Identity(owner, localKey).equals(identityOf(Objects.requireNonNull(adapter)));
    }

    static synchronized boolean sameAuthority(Object first, Object second) {
        expunge();
        Identity identity = OWNERS.get(new IdentityReference(first, null));
        return identity != null && identity.equals(OWNERS.get(new IdentityReference(second, null)));
    }

    private static Identity identityOf(Object adapter) {
        IdentityReference reference = new IdentityReference(adapter, null);
        Identity published = OWNERS.get(reference);
        return published == null ? DELEGATES.get(reference) : published;
    }

    private static void expunge() {
        IdentityReference reference;
        while ((reference = (IdentityReference) EXPIRED.poll()) != null) {
            OWNERS.remove(reference);
            DELEGATES.remove(reference);
            NATIVE_PUBLICATIONS.remove(reference);
        }
    }

    private record Identity(String owner, String localKey) {
        String key() { return "mod:" + owner + ":" + localKey; }
    }

    private static final class IdentityReference extends WeakReference<Object> {
        private final int hash;
        IdentityReference(Object value, ReferenceQueue<Object> queue) {
            super(value, queue);
            hash = System.identityHashCode(value);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof IdentityReference reference
                    && get() != null && get() == reference.get();
        }
    }
}
