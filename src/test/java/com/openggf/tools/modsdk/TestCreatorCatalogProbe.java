package com.openggf.tools.modsdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Small correctness shape only. Larger measurements require explicit probe invocation. */
class TestCreatorCatalogProbe {
    @TempDir Path temp;

    @Test void productionPipelineAdmitsDistinctTrustedOwnersAndRejectsProductionBudgetExcess() throws Exception {
        Path compiled = CreatorCatalogProbe.compileFixture(temp.resolve("fixture"), 1024);
        var result = CreatorCatalogProbe.runCase(temp.resolve("catalog"), compiled, 2, 1024);
        assertEquals(2, result.get("registrations"));
        var rejections = CreatorCatalogProbe.rejectionCases(temp.resolve("malicious"));
        assertEquals(java.util.List.of("REPOSITORY_JAR_LIMIT_EXCEEDED"), rejections.get("catalogCount"));
        assertEquals(java.util.List.of("MOD_JAR_INVALID"), rejections.get("assetBytes"));
    }
}
