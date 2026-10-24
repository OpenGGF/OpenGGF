package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.game.sonic3k.objects.IczMinibossInstance;
import com.openggf.level.objects.boss.AbstractBossInstance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestIczMinibossFieldDisposition {
    @Test
    void actualDefaultCaptureIncludesHelpersCameraAndInheritedChildInventory() {
        RewindClassSchema schema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(IczMinibossInstance.class);
        assertEquals(java.util.List.of(), schema.unsupportedFields(),
                () -> "ICZ must stay on its compact capture path: " + schema.unsupportedFields());
        Set<FieldKey> captured = schema.capturedFields().stream()
                .map(RewindFieldPlan::key).collect(Collectors.toSet());
        assertTrue(captured.containsAll(Set.of(
                new FieldKey(IczMinibossInstance.class.getName(), "orbs"),
                new FieldKey(IczMinibossInstance.class.getName(), "shards"),
                new FieldKey(IczMinibossInstance.class.getName(), "cameraGate"),
                new FieldKey(AbstractBossInstance.class.getName(), "childComponents"))));
        var gaps = new ArrayList<RewindFieldDispositionAudit.Gap>();
        RewindFieldDispositionAudit.auditClass(IczMinibossInstance.class, gaps);
        assertEquals(java.util.List.of(), gaps, "ICZ's actual capture path must have no field obligation");
    }
}
