package com.openggf.mods;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestModApiRuntimePolicy {
    @Test
    void packagedRuntimeContractsMatchRepositoryReleasePolicy() throws Exception {
        ModApiReleasePolicy policy = ModApiReleasePolicy.read(
                Path.of("mod-api-release-policy.properties"));
        List<SemanticVersion> expected = new ArrayList<>(policy.publishedBaselines());
        expected.add(policy.currentApi());
        expected = expected.stream().distinct().sorted().toList();

        assertEquals(policy.currentApi(), ModApiVersion.CURRENT);
        assertEquals(expected, ModApiVersion.SUPPORTED_CONTRACTS);
    }
}
