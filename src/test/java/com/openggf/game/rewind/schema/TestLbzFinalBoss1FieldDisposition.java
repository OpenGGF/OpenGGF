package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestLbzFinalBoss1FieldDisposition {
    @Test
    void actualDefaultCaptureHasNoUnownedSpawnInventory() {
        var schema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(LbzFinalBoss1Instance.class);
        assertTrue(GenericRewindEligibility.usesCompactDefaultSubclassCapture(LbzFinalBoss1Instance.class));
        assertEquals(List.of(), schema.unsupportedFields());
        var gaps = new ArrayList<RewindFieldDispositionAudit.Gap>();
        RewindFieldDispositionAudit.auditClass(LbzFinalBoss1Instance.class, gaps);
        assertEquals(List.of(), gaps, "the actual default path must account for every owner field");
    }
}
