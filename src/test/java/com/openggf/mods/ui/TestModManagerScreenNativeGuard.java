package com.openggf.mods.ui;

import com.openggf.mods.ModDependency;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModState;
import com.openggf.mods.VersionRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModManagerScreenNativeGuard {
    @TempDir Path temp;

    @Test
    void codeModRowIsUnsupportedAndNotLoadedWithoutChangingEnabledIntent() {
        Fixture fixture = fixture(false);
        ModManagerScreen.RowView row = fixture.screen().rows().stream()
                .filter(candidate -> candidate.identity().equals("codeon"))
                .findFirst().orElseThrow();

        assertTrue(row.badges().contains("UNSUPPORTED"));
        assertTrue(row.notLoaded());
        assertTrue(row.enabled(), "runtime suppression must not rewrite pending enabled intent");
    }

    @Test
    void enablingCodeModIsRefusedOnNative() {
        Fixture fixture = fixture(false);
        TestModManagerScreen.select(fixture.screen(), "codeoff");

        TestModManagerScreen.accept(fixture.screen());

        assertTrue(fixture.screen().statusMessage().contains("not supported on native"));
        assertFalse(TestModManagerScreen.enabled(fixture.screen(), "codeoff"));
    }

    @Test
    void enablingDataModThatCascadesIntoCodeIsRefusedOnNative() {
        Fixture fixture = fixture(false);
        TestModManagerScreen.select(fixture.screen(), "datadep");

        TestModManagerScreen.accept(fixture.screen());

        assertTrue(fixture.screen().statusMessage().contains("not supported on native"));
        assertFalse(TestModManagerScreen.enabled(fixture.screen(), "datadep"));
        assertFalse(TestModManagerScreen.enabled(fixture.screen(), "codedep"));
    }

    @Test
    void disablingCodeModRemainsAllowedOnNative() {
        Fixture fixture = fixture(false);
        TestModManagerScreen.select(fixture.screen(), "codeon");

        TestModManagerScreen.accept(fixture.screen());

        assertFalse(TestModManagerScreen.enabled(fixture.screen(), "codeon"));
    }

    @Test
    void dataOnlyModEnablesNormallyOnNative() {
        Fixture fixture = fixture(false);
        TestModManagerScreen.select(fixture.screen(), "dataoff");

        TestModManagerScreen.accept(fixture.screen());

        assertTrue(TestModManagerScreen.enabled(fixture.screen(), "dataoff"));
    }

    @Test
    void supportedBuildEnablesTrustedCodeModNormally() {
        Fixture fixture = fixture(true);
        TestModManagerScreen.select(fixture.screen(), "codedep");

        TestModManagerScreen.accept(fixture.screen());

        ModManagerScreen.RowView row = fixture.screen().rows().stream()
                .filter(candidate -> candidate.identity().equals("codedep"))
                .findFirst().orElseThrow();
        assertTrue(TestModManagerScreen.enabled(fixture.screen(), "codedep"));
        assertFalse(row.badges().contains("UNSUPPORTED"));
        assertFalse(row.notLoaded());
    }

    private Fixture fixture(boolean compiledModsSupported) {
        ModDescriptor codeOn = TestModManagerScreen.codeDescriptor(
                "codeon", "Code On", "1".repeat(64));
        ModDescriptor codeOff = TestModManagerScreen.codeDescriptor(
                "codeoff", "Code Off", "2".repeat(64));
        ModDescriptor codeDependency = TestModManagerScreen.codeDescriptor(
                "codedep", "Code Dependency", "3".repeat(64));
        ModDescriptor dataOn = TestModManagerScreen.descriptor(
                "dataon", "Data On", List.of(), List.of());
        ModDescriptor dataOff = TestModManagerScreen.descriptor(
                "dataoff", "Data Off", List.of(), List.of());
        ModDescriptor dataDependent = TestModManagerScreen.descriptor(
                "datadep", "Data Dependent",
                List.of(new ModDependency("codedep", VersionRange.parse("*"))), List.of());
        List<ModDescriptor> descriptors = List.of(
                codeOn, codeOff, codeDependency, dataOn, dataOff, dataDependent);
        ModState startup = new ModState(ModState.CURRENT_FORMAT_VERSION, List.of(
                new ModState.Entry("codeon", true, 0, true, codeOn.sha256()),
                new ModState.Entry("codeoff", false, 1),
                new ModState.Entry("codedep", false, 2, true, codeDependency.sha256()),
                new ModState.Entry("dataon", true, 3),
                new ModState.Entry("dataoff", false, 4),
                new ModState.Entry("datadep", false, 5)));
        return new Fixture(TestModManagerScreen.nativeGuardScreen(
                temp.resolve(compiledModsSupported ? "supported" : "native"),
                descriptors, startup, compiledModsSupported));
    }

    private record Fixture(ModManagerScreen screen) {
    }
}
