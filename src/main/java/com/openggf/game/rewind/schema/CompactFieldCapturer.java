package com.openggf.game.rewind.schema;

import com.openggf.level.objects.AbstractObjectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CompactFieldCapturer {
    private static final class Scratch {
        final RewindStateBuffer scalarData = new RewindStateBuffer();
        final ArrayList<Object> opaqueValues = new ArrayList<>();

        void reset() {
            scalarData.reset();
            opaqueValues.clear();
        }
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    public static RewindObjectStateBlob capture(Object target) {
        return capture(target, RewindCaptureContext.none());
    }

    public static RewindObjectStateBlob capture(Object target, RewindCaptureContext context) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(context, "context");
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(target.getClass());
        validateSupported(schema);
        return captureWithSchema(target, schema, context);
    }

    public static boolean supportsDefaultObjectSubclassScalars(Class<?> type) {
        Objects.requireNonNull(type, "type");
        RewindClassSchema schema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(type);
        return !schema.capturedFields().isEmpty() && schema.unsupportedFields().isEmpty();
    }

    public static RewindObjectStateBlob captureDefaultObjectSubclassScalars(AbstractObjectInstance target) {
        return captureDefaultObjectSubclassScalars(target, RewindCaptureContext.none());
    }

    public static RewindObjectStateBlob captureDefaultObjectSubclassScalars(
            AbstractObjectInstance target,
            RewindCaptureContext context) {

        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(context, "context");
        RewindClassSchema schema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(target.getClass());
        validateSupported(schema);
        return captureWithSchema(target, schema, context);
    }

    private static RewindObjectStateBlob captureWithSchema(
            Object target,
            RewindClassSchema schema,
            RewindCaptureContext context) {

        Scratch scratch = SCRATCH.get();
        scratch.reset();
        try {
            for (RewindFieldPlan field : schema.capturedFields()) {
                if (field.hasPrimitiveFastPath()) {
                    field.capturePrimitive(target, scratch.scalarData);
                } else {
                    field.codec().capture(field.field(), target, scratch.scalarData, scratch.opaqueValues, context);
                }
            }
            // toByteArray()/toArray() hand back fresh arrays, so ownership
            // transfers to the blob without the constructor's defensive copy.
            return RewindObjectStateBlob.owned(
                    schema.schemaId(), schema.type(),
                    scratch.scalarData.toByteArray(), scratch.opaqueValues.toArray());
        } finally {
            scratch.reset();
        }
    }

    public static void restore(Object target, RewindObjectStateBlob blob) {
        restore(target, blob, RewindCaptureContext.none());
    }

    public static void restore(Object target, RewindObjectStateBlob blob, RewindCaptureContext context) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(context, "context");
        if (target.getClass() != blob.type()) {
            throw new IllegalArgumentException("Cannot restore rewind blob for " + blob.type().getName()
                    + " into " + target.getClass().getName() + ".");
        }

        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(target.getClass());
        validateSupported(schema);
        if (schema.schemaId() != blob.schemaId()) {
            throw new IllegalArgumentException("Cannot restore rewind blob with schema id " + blob.schemaId()
                    + " into schema id " + schema.schemaId() + " for " + schema.type().getName() + ".");
        }
        restoreWithSchema(target, blob, context, schema);
    }

    public static void restoreDefaultObjectSubclassScalars(
            AbstractObjectInstance target,
            RewindObjectStateBlob blob) {

        restoreDefaultObjectSubclassScalars(target, blob, RewindCaptureContext.none());
    }

    public static void restoreDefaultObjectSubclassScalars(
            AbstractObjectInstance target,
            RewindObjectStateBlob blob,
            RewindCaptureContext context) {

        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(context, "context");
        if (target.getClass() != blob.type()) {
            throw new IllegalArgumentException("Cannot restore rewind blob for " + blob.type().getName()
                    + " into " + target.getClass().getName() + ".");
        }

        RewindClassSchema schema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(target.getClass());
        validateSupported(schema);
        if (schema.schemaId() != blob.schemaId()) {
            throw new IllegalArgumentException("Cannot restore rewind blob with schema id " + blob.schemaId()
                    + " into schema id " + schema.schemaId() + " for " + schema.type().getName() + ".");
        }
        restoreWithSchema(target, blob, context, schema);
    }

    private static void restoreWithSchema(
            Object target,
            RewindObjectStateBlob blob,
            RewindCaptureContext context,
            RewindClassSchema schema) {

        RewindStateBuffer.Reader scalarData = blob.scalarReader();
        Object[] opaqueValues = blob.opaqueValuesShared();
        RewindCodec.OpaqueIndex opaqueIndex = new RewindCodec.OpaqueIndex();
        for (RewindFieldPlan field : schema.capturedFields()) {
            if (field.hasPrimitiveFastPath()) {
                field.restorePrimitive(target, scalarData);
            } else {
                field.codec().restore(field.field(), target, scalarData, opaqueValues, opaqueIndex, context);
            }
        }
    }

    private static void validateSupported(RewindClassSchema schema) {
        if (schema.unsupportedFields().isEmpty()) {
            return;
        }
        String fields = schema.unsupportedFields().stream()
                .map(field -> field.key().declaringClassName() + "." + field.key().fieldName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("<unknown>");
        throw new IllegalStateException("Unsupported rewind fields on " + schema.type().getName() + ": " + fields);
    }

    private CompactFieldCapturer() {}
}
