package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TestModApiHookPolicy {
    private static final Path HOOK = Path.of(".githooks/validate-policy.sh").toAbsolutePath();
    private static final Path POWERSHELL_HOOK = Path.of(".githooks/validate-policy.ps1").toAbsolutePath();
    private static final String API = "src/main/java/com/openggf/mods/Example Api.java";
    private static final String PIN = "src/test/resources/mods/mod-api-signatures-0.7.txt";
    private static final String DESCRIPTOR = "mod-api-release-policy.properties";
    private static final String MESSAGE = """
            test: fixture

            Changelog: n/a
            Guide: n/a
            Known-Discrepancies: n/a
            S3K-Known-Discrepancies: n/a
            Agent-Docs: n/a
            Configuration-Docs: n/a
            Skills: n/a
            """;

    @TempDir Path temp;

    @Test void annotatedSurfaceAndCandidatePinMustChangeTogether() throws Exception {
        Path repo = fixture();
        write(repo, API, "@ModApi public interface Example { void added(); }\n");
        git(repo, "add", API);
        assertStagedFails(repo);
        write(repo, PIN, "changed\n");
        git(repo, "add", PIN);
        assertStagedPasses(repo);
    }

    @Test void candidatePinContentCannotChangeByItself() throws Exception {
        Path repo = fixture();
        write(repo, PIN, "changed\n");
        git(repo, "add", PIN);
        assertStagedFails(repo);
    }

    @Test void deletingAnnotatedDeclarationIsDetected() throws Exception {
        Path repo = fixture();
        git(repo, "rm", API);
        assertStagedFails(repo);
        write(repo, PIN, "changed after deletion\n");
        git(repo, "add", PIN);
        assertStagedPasses(repo);
    }

    @Test void pinRenameRequiresDescriptorAndCurrentRequiresBothCompanions() throws Exception {
        Path repo = fixture();
        git(repo, "mv", PIN, "src/test/resources/mods/mod-api-signatures-0.7.0.txt");
        assertStagedFails(repo);
        write(repo, DESCRIPTOR, descriptor("published", "0.7.0"));
        git(repo, "add", DESCRIPTOR);
        assertStagedPasses(repo);

        git(repo, "reset", "--hard", "HEAD");
        write(repo, "src/main/java/com/openggf/mods/ModApiVersion.java", "class ModApiVersion { static final String CURRENT = \"0.7.1\"; }\n");
        git(repo, "add", ".");
        assertStagedFails(repo);
    }

    @Test void ciPushAppliesSamePerCommitCoupling() throws Exception {
        Path repo = fixture();
        String base = gitText(repo, "rev-parse", "HEAD").trim();
        write(repo, API, "@ModApi public interface Example { void ciAdded(); }\n");
        git(repo, "add", API);
        commit(repo);
        String head = gitText(repo, "rev-parse", "HEAD").trim();
        assertHookFails(repo, "ci-push", base, head, "next");
    }

    @Test void ciPrAppliesSamePerCommitCoupling() throws Exception {
        Path repo = fixture();
        String base = gitText(repo, "rev-parse", "HEAD").trim();
        write(repo, API, "@ModApi public interface Example { void prAdded(); }\n");
        git(repo, "add", API);
        commit(repo);
        String head = gitText(repo, "rev-parse", "HEAD").trim();
        assertHookFails(repo, "ci-pr", base, head, "next", "feature/test");
    }

    @Test void currentTransitionPassesWithDescriptorAndNormalizedCandidateRename() throws Exception {
        Path repo = fixture();
        write(repo, API, "@ModApi public interface Example { void nextLine(); }\n");
        write(repo, "src/main/java/com/openggf/mods/ModApiVersion.java",
                "class ModApiVersion { static final String CURRENT = \"0.8.0\"; }\n");
        write(repo, DESCRIPTOR, descriptorForCurrent("0.8", "0.8.0"));
        git(repo, "mv", PIN, "src/test/resources/mods/mod-api-signatures-0.8.txt");
        git(repo, "add", DESCRIPTOR, API, "src/main/java/com/openggf/mods/ModApiVersion.java");
        assertStagedPasses(repo);
    }

    @Test void ciPrAcceptsValidApiAndCandidatePair() throws Exception {
        Path repo = fixture();
        String base = gitText(repo, "rev-parse", "HEAD").trim();
        write(repo, API, "@ModApi public interface Example { void paired(); }\n");
        write(repo, PIN, "paired signature\n");
        git(repo, "add", API, PIN);
        commit(repo);
        String head = gitText(repo, "rev-parse", "HEAD").trim();
        for (int result : runHooks(repo, "ci-pr", base, head, "next", "feature/test")) assertEquals(0, result);
    }

    @Test void structuralPinAdditionAndDeletionRequireDescriptorTransition() throws Exception {
        Path repo = fixture();
        write(repo, "src/test/resources/mods/mod-api-signatures-9.9.9.txt", "unexpected\n");
        git(repo, "add", ".");
        assertStagedFails(repo);
        git(repo, "reset", "--hard", "HEAD");
        git(repo, "rm", PIN);
        assertStagedFails(repo);
    }

    @Test void publishedFullVersionPinIsImmutableEvenWithApiDelta() throws Exception {
        Path repo = fixture();
        git(repo, "mv", PIN, "src/test/resources/mods/mod-api-signatures-0.7.0.txt");
        write(repo, DESCRIPTOR, descriptor("published", "0.7.0"));
        git(repo, "add", ".");
        commit(repo);
        write(repo, API, "@ModApi public interface Example { void forbidden(); }\n");
        write(repo, "src/test/resources/mods/mod-api-signatures-0.7.0.txt", "changed\n");
        git(repo, "add", ".");
        assertStagedFails(repo);
    }

    @Test void descriptorEditWithUnchangedNormalizedPinMapIsAllowed() throws Exception {
        Path repo = fixture();
        write(repo, DESCRIPTOR, descriptor("candidate", "") + "policyNote=clarified\n");
        git(repo, "add", DESCRIPTOR);
        assertStagedPasses(repo);
    }

    @Test void ordinaryCandidateRewriteMustNotEditDescriptor() throws Exception {
        Path repo = fixture();
        write(repo, API, "@ModApi public interface Example { void ordinary(); }\n");
        write(repo, PIN, "regenerated\n");
        write(repo, DESCRIPTOR, descriptor("candidate", "") + "policyNote=unrelated\n");
        git(repo, "add", ".");
        assertStagedFails(repo);
    }

    @Test void descriptorBootstrapAllowsPreexistingNormalizedCandidateAndToolingMentions() throws Exception {
        Path repo = temp.resolve("bootstrap repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "feature/test");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "config", "user.name", "Test");
        write(repo, PIN, "baseline\n");
        write(repo, "src/main/java/com/openggf/mods/ModApiVersion.java", """
                class ModApiVersion {
                    static final String CURRENT = "0.7.0";
                    static final String DIAGNOSTIC = "before";
                }
                """);
        write(repo, "src/main/java/com/openggf/mods/ModApiSignatureSurface.java", """
                /** Tooling that scans the text @ModApi without declaring an API. */
                class ModApiSignatureSurface { String marker = "@ModApi"; }
                """);
        git(repo, "add", ".");
        commit(repo);

        write(repo, DESCRIPTOR, descriptor("candidate", ""));
        write(repo, "src/main/java/com/openggf/mods/ModApiVersion.java", """
                class ModApiVersion {
                    static final String CURRENT = "0.7.0";
                    static final String DIAGNOSTIC = "after";
                }
                """);
        write(repo, "src/main/java/com/openggf/mods/ModApiSignatureSurface.java", """
                /** Tooling that scans literal @ModApi annotation names. */
                class ModApiSignatureSurface { String marker = "mentions @ModApi only"; }
                """);
        git(repo, "add", ".");
        assertStagedPasses(repo);
    }

    @Test void toolingAndJavadocMentionsAreNotApiSurfaceDeltas() throws Exception {
        Path repo = fixture();
        String tooling = "src/main/java/com/openggf/mods/ModApiSignatureSurface.java";
        write(repo, tooling, "/** Mentions @ModApi in documentation. */\nclass ModApiSignatureSurface { String text = \"@ModApi\"; }\n");
        git(repo, "add", tooling);
        commit(repo);
        write(repo, tooling, "/** Changed tooling mention of @ModApi. */\nclass ModApiSignatureSurface { String text = \"scan @ModApi\"; }\n");
        git(repo, "add", tooling);
        assertStagedPasses(repo);
    }

    private Path fixture() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "feature/test");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "config", "user.name", "Test");
        write(repo, DESCRIPTOR, descriptor("candidate", ""));
        write(repo, API, "@ModApi public interface Example { }\n");
        write(repo, "src/main/java/com/openggf/mods/ModApiVersion.java", "class ModApiVersion { static final String CURRENT = \"0.7.0\"; }\n");
        write(repo, PIN, "baseline\n");
        git(repo, "add", ".");
        commit(repo);
        return repo;
    }

    private static String descriptor(String status, String published) {
        return "schemaVersion=1\ntargetBranch=next\nmasterLine=0.5\ndevelopLine=0.6\nnextLine=0.7\ncurrentApi=0.7.0\ncurrentStatus=" + status + "\npublishedBaselines=" + published + "\n";
    }

    private static String descriptorForCurrent(String nextLine, String currentApi) {
        return "schemaVersion=1\ntargetBranch=next\nmasterLine=0.5\ndevelopLine=0.6\nnextLine=" + nextLine
                + "\ncurrentApi=" + currentApi + "\ncurrentStatus=candidate\npublishedBaselines=\n";
    }

    private void assertStagedPasses(Path repo) throws Exception {
        for (int result : staged(repo)) assertEquals(0, result);
    }

    private void assertStagedFails(Path repo) throws Exception {
        for (int result : staged(repo)) assertNotEquals(0, result);
    }

    private List<Integer> staged(Path repo) throws Exception {
        Path message = repo.resolve("message.txt");
        Files.writeString(message, MESSAGE);
        return runHooks(repo, "commit-msg", message.toString());
    }

    private void assertHookFails(Path repo, String... args) throws Exception {
        for (int result : runHooks(repo, args)) assertNotEquals(0, result);
    }

    private static List<Integer> runHooks(Path repo, String... args) throws Exception {
        List<Integer> results = new ArrayList<>();
        List<String> bash = new ArrayList<>(List.of("bash", HOOK.toString()));
        bash.addAll(List.of(args));
        results.add(run(repo, bash.toArray(String[]::new)));
        if (commandAvailable("pwsh")) {
            List<String> powershell = new ArrayList<>(List.of("pwsh", "-NoProfile", "-File", POWERSHELL_HOOK.toString()));
            powershell.addAll(List.of(args));
            results.add(run(repo, powershell.toArray(String[]::new)));
        }
        return results;
    }

    private static boolean commandAvailable(String command) throws Exception {
        Process process = new ProcessBuilder("sh", "-c", "command -v " + command).start();
        process.getInputStream().readAllBytes();
        return process.waitFor() == 0;
    }

    private static void commit(Path repo) throws Exception {
        Path message = repo.resolve("commit-message.txt");
        Files.writeString(message, MESSAGE);
        git(repo, "commit", "-F", message.toString());
    }

    private static void write(Path repo, String relative, String content) throws IOException {
        Path path = repo.resolve(relative);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static void git(Path repo, String... args) throws Exception {
        List<String> command = new ArrayList<>(); command.add("git"); command.addAll(List.of(args));
        int code = run(repo, command.toArray(String[]::new));
        if (code != 0) throw new AssertionError("git command failed: " + command);
    }

    private static String gitText(Path repo, String... args) throws Exception {
        List<String> command = new ArrayList<>(); command.add("git"); command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new AssertionError(output);
        return output;
    }

    private static int run(Path repo, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }
}
