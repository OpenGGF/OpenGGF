package com.openggf.game.rewind.schema;

import java.util.Arrays;
import java.util.Objects;

public final class RewindObjectStateBlob {
    private final int schemaId;
    private final Class<?> type;
    private final byte[] scalarData;
    private final int scalarLength;
    private final Object[] opaqueValues;

    public RewindObjectStateBlob(int schemaId, Class<?> type, byte[] scalarData, Object[] opaqueValues) {
        this(
                schemaId,
                type,
                Arrays.copyOf(Objects.requireNonNull(scalarData, "scalarData"), scalarData.length),
                scalarData.length,
                Arrays.copyOf(Objects.requireNonNull(opaqueValues, "opaqueValues"), opaqueValues.length));
    }

    private RewindObjectStateBlob(
            int schemaId,
            Class<?> type,
            byte[] scalarData,
            int scalarLength,
            Object[] opaqueValues) {

        this.schemaId = schemaId;
        this.type = Objects.requireNonNull(type, "type");
        this.scalarData = Objects.requireNonNull(scalarData, "scalarData");
        if (scalarLength < 0 || scalarLength > scalarData.length) {
            throw new IllegalArgumentException("scalarLength must be between 0 and scalarData.length: " + scalarLength);
        }
        this.scalarLength = scalarLength;
        this.opaqueValues = Objects.requireNonNull(opaqueValues, "opaqueValues");
    }

    static RewindObjectStateBlob fromOwnedArrays(
            int schemaId,
            Class<?> type,
            byte[] scalarData,
            int scalarLength,
            Object[] opaqueValues) {

        return new RewindObjectStateBlob(schemaId, type, scalarData, scalarLength, opaqueValues);
    }

    public int schemaId() {
        return schemaId;
    }

    public Class<?> type() {
        return type;
    }

    public byte[] scalarData() {
        return Arrays.copyOf(scalarData, scalarLength);
    }

    public Object[] opaqueValues() {
        return Arrays.copyOf(opaqueValues, opaqueValues.length);
    }

    RewindStateBuffer.Reader scalarDataReader() {
        return RewindStateBuffer.readerForOwnedBytes(scalarData, scalarLength);
    }

    RewindCodec.OpaqueValues opaqueValuesReader() {
        return RewindCodec.OpaqueValues.owned(opaqueValues);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RewindObjectStateBlob other)) return false;
        return schemaId == other.schemaId
                && type == other.type
                && scalarDataEquals(other)
                && Arrays.equals(opaqueValues, other.opaqueValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaId, type, scalarDataHashCode(), Arrays.hashCode(opaqueValues));
    }

    private boolean scalarDataEquals(RewindObjectStateBlob other) {
        return scalarLength == other.scalarLength
                && Arrays.equals(scalarData, 0, scalarLength, other.scalarData, 0, other.scalarLength);
    }

    private int scalarDataHashCode() {
        int result = 1;
        for (int i = 0; i < scalarLength; i++) {
            result = 31 * result + scalarData[i];
        }
        return result;
    }
}
