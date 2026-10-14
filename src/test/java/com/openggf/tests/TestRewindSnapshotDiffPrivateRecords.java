package com.openggf.tests;

import com.openggf.game.rewind.RewindSnapshotDiff;

import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Equality and diagnostic paths must inspect the same complete captured values. */
class TestRewindSnapshotDiffPrivateRecords {
    private record Counter(int value) { }
    private record Nested(List<Counter[]> children, byte[] bytes) { }
    private record Unreadable(int value) {
        @Override public int value() { throw new IllegalStateException("inaccessible snapshot value"); }
    }
    @Test void privateRecordMismatchCannotBecomeAnEmptyDiff() {
        different(new Counter(1),new Counter(2),".value");
    }
    @Test void privateRecordsInsideNestedArraysRetainEveryScalar() {
        different(new Nested(List.<Counter[]>of(new Counter[]{new Counter(7)}),new byte[]{1,2}),
                new Nested(List.<Counter[]>of(new Counter[]{new Counter(8)}),new byte[]{1,2}),".value");
    }
    @Test void topLevelArraysCompareByContentsAndKeepChangedBytesVisible() {
        assertTrue(RewindSnapshotDiff.keyEquals("bytes",new byte[]{1,2},new byte[]{1,2}));
        assertTrue(RewindSnapshotDiff.diffKey("bytes",new byte[]{1,2},new byte[]{1,2}).isEmpty());
        different(new byte[]{1,2},new byte[]{1,3},"[1]");
    }
    @Test void opaqueBlobArraysCompareTheirEntireCapturedContent() {
        var a=blob(new Nested(List.<Counter[]>of(new Counter[]{new Counter(7)}),new byte[]{1,2}));
        var b=blob(new Nested(List.<Counter[]>of(new Counter[]{new Counter(7)}),new byte[]{1,2}));
        assertTrue(RewindSnapshotDiff.keyEquals("blob",a,b));
        assertTrue(RewindSnapshotDiff.diffKey("blob",a,b).isEmpty());
    }
    @Test void privateBlobOpaqueMismatchCannotBecomeAnEmptyDiff() {
        different(blob(new Counter(1)),blob(new Counter(2)),".value");
    }
    @Test void blobSchemaTypeAndRawBytesRemainRequiredComparedChannels() {
        var before=blob(new Counter(1));
        different(before,new RewindObjectStateBlob(2,Counter.class,new byte[]{4,5},new Object[]{new Counter(1)}),"schemaId");
        different(before,new RewindObjectStateBlob(1,Nested.class,new byte[]{4,5},new Object[]{new Counter(1)}),"type");
        different(before,new RewindObjectStateBlob(1,Counter.class,new byte[]{4,6},new Object[]{new Counter(1)}),"scalarData[1]");
        assertFalse(RewindSnapshotDiff.keyEquals("array-kind",new byte[]{1},new short[]{1}));
        assertFalse(RewindSnapshotDiff.diffKey("array-kind",new byte[]{1},new short[]{1}).isEmpty());
    }
    @Test void failedInspectionIsReportedInsteadOfBeingTreatedAsEqual() {
        assertThrows(RuntimeException.class,()->RewindSnapshotDiff.keyEquals("closed",new Unreadable(1),new Unreadable(2)));
        assertThrows(RuntimeException.class,()->RewindSnapshotDiff.diffKey("closed",new Unreadable(1),new Unreadable(2)));
    }
    private static RewindObjectStateBlob blob(Object opaque) {
        return new RewindObjectStateBlob(1,Counter.class,new byte[]{4,5},new Object[]{opaque});
    }
    private static void different(Object a,Object b,String field) {
        assertFalse(RewindSnapshotDiff.keyEquals("snapshot",a,b));
        var diff=RewindSnapshotDiff.diffKey("snapshot",a,b);
        assertFalse(diff.isEmpty(),"a known mismatch must have an actionable diagnostic");
        assertTrue(diff.stream().anyMatch(value->value.contains(field)),diff.toString());
    }
}
