package com.openggf.game.rewind.schema;

import java.util.Objects;

/**
 * Serializes a zone-event handler's mutable state via the rewind schema
 * system, replacing hand-counted byte layouts in
 * {@code Sonic3kLevelEventManager.captureExtra()}.
 *
 * <p>Handlers must annotate structural fields (services, managers, level
 * references, immutable tables) with {@code @RewindTransient}; every
 * remaining field must have a codec or the schema rejects the class at
 * first capture. Scalar-only by design: a handler whose schema produces
 * opaque values (e.g. String fields) needs explicit codec/policy work
 * before conversion.
 */
public final class ZoneEventSchemaSidecar {

    private ZoneEventSchemaSidecar() {
    }

    public static byte[] capture(Object handler) {
        Objects.requireNonNull(handler, "handler");
        RewindObjectStateBlob blob = CompactFieldCapturer.capture(handler);
        if (blob.opaqueValues().length != 0) {
            throw new IllegalStateException("Zone-event sidecar for "
                    + handler.getClass().getName()
                    + " produced opaque values; scalar-only fields required");
        }
        return blob.scalarData();
    }

    public static void restore(Object handler, byte[] bytes) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(bytes, "bytes");
        int expectedLength = capture(handler).length;
        if (bytes.length != expectedLength) {
            throw new IllegalArgumentException("Zone-event sidecar for "
                    + handler.getClass().getName()
                    + " expected " + expectedLength + " scalar bytes but got " + bytes.length);
        }
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(handler.getClass());
        CompactFieldCapturer.restore(handler,
                new RewindObjectStateBlob(schema.schemaId(), handler.getClass(), bytes, new Object[0]));
    }
}
