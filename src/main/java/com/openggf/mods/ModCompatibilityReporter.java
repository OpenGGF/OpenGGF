package com.openggf.mods;

import com.openggf.mods.code.ModContributionReport;
import com.openggf.mods.code.ModRegistrationException;
import com.openggf.mods.code.ModRuntime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static com.openggf.mods.ModCompatibilityDiagnostic.Level.*;

/** Projects host-owned publication/failure evidence; never executes creator code or changes eligibility. */
public final class ModCompatibilityReporter {
    private static final Set<String> PREPARATION_BLOCKERS = Set.of(
            "DEPENDENCY_CYCLE", "ORDERING_CYCLE", "DEPENDENCY_MISSING", "DEPENDENCY_DISABLED",
            "DEPENDENCY_VERSION_INCOMPATIBLE", "DEPENDENCY_BASE_GAME_MISMATCH", "DEPENDENCY_BLOCKED",
            "PATTERN_WINDOW_BUDGET_EXCEEDED", "MOD_COMPOSITION_REJECTED");
    private final ModCatalog catalog;
    private final Map<String, ModDescriptor> descriptors = new LinkedHashMap<>();
    private final Set<String> requestedOwners;

    public ModCompatibilityReporter(ModCatalog catalog) {
        this(catalog, null);
    }

    /** Null means the caller has no startup selection; an explicit empty set requests no mods. */
    public ModCompatibilityReporter(ModCatalog catalog, Set<String> requestedOwners) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.requestedOwners = requestedOwners == null ? null : Set.copyOf(requestedOwners);
        catalog.scanned().stream().filter(ModDescriptor.class::isInstance).map(ModDescriptor.class::cast)
                .forEach(descriptor -> descriptors.putIfAbsent(descriptor.manifest().id(), descriptor));
    }

    /** Package hashes and effective composition order identify the frozen, active mod configuration. */
    public String contextFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            catalog.scanned().stream().filter(ModDescriptor.class::isInstance).map(ModDescriptor.class::cast)
                    .sorted(Comparator.comparing((ModDescriptor descriptor) -> descriptor.manifest().id())
                            .thenComparing(ModDescriptor::sha256))
                    .forEach(descriptor -> {
                        digest.update((descriptor.manifest().id() + "\n" + descriptor.manifest().version() + "\n"
                                + descriptor.sha256() + "\n").getBytes(StandardCharsets.UTF_8));
                        var eligibility = catalog.eligibility().get(descriptor.manifest().id());
                        if (eligibility != null) digest.update(eligibility.status().name().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    });
            for (ModDescriptor descriptor : catalog.effective().orderedEnabled()) {
                digest.update((descriptor.manifest().id() + "\n" + descriptor.sha256() + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            if (requestedOwners != null) {
                for (String owner : new TreeSet<>(requestedOwners)) {
                    digest.update(("requested:" + owner + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    public List<ModCompatibilityDiagnostic> catalogDiagnostics() {
        var result = new ArrayList<ModCompatibilityDiagnostic>();
        Set<String> effective = catalog.effective().orderedEnabled().stream()
                .map(descriptor -> descriptor.manifest().id()).collect(java.util.stream.Collectors.toSet());
        var declaredPairs = new LinkedHashSet<List<String>>();
        for (ModDescriptor descriptor : catalog.effective().orderedEnabled()) {
            String owner = descriptor.manifest().id();
            for (String neighbor : descriptor.manifest().composition().conflictsWith()) {
                if (!effective.contains(neighbor)) continue;
                List<String> participants = new TreeSet<>(List.of(owner, neighbor)).stream().toList();
                if (!declaredPairs.add(participants)) continue;
                result.add(diagnostic(SUSPECTED_INCOMPATIBILITY, "MOD_DECLARED_CONFLICT", "composition",
                        participants, "Author-declared incompatibility; the declaration alone does not block play.",
                        List.of("Declaration: " + owner + " conflictsWith " + neighbor,
                                "This declaration does not establish a crash or inability to play.")));
            }
        }
        catalog.eligibility().forEach((owner, eligibility) -> {
            if (eligibility.status() != ModEligibility.Status.BLOCKED
                    || requestedOwners != null && !requestedOwners.contains(owner)) return;
            for (ModEligibility.Reason reason : eligibility.reasons()) {
                if (!PREPARATION_BLOCKERS.contains(reason.code())) continue;
                var participants = new TreeSet<>(reason.relatedModIds());
                participants.add(owner);
                var blockedOwners = participants.stream().filter(participant -> {
                    var related = catalog.eligibility().get(participant);
                    return related != null && related.status() == ModEligibility.Status.BLOCKED
                            && (requestedOwners == null || requestedOwners.contains(participant));
                }).toList();
                result.add(diagnostic(VERIFIED_BLOCKER, reason.code(), "eligibility/" + reason.code(),
                        List.copyOf(participants), "Affected mod content cannot be prepared: " + reason.message(),
                        List.of("Mandatory preparation contract: " + reason.code(),
                                "This blocks the affected content, not unrelated mods or stock play."), blockedOwners));
            }
        });
        return List.copyOf(result);
    }

    public List<ModCompatibilityDiagnostic> preparedContributions(ModContributionReport report) {
        var result = new ArrayList<ModCompatibilityDiagnostic>();
        for (ModContributionReport.Target target : report.targets()) {
            if (target.winner() == null || target.shadowedOwners().isEmpty()) continue;
            var participants = new LinkedHashSet<>(target.shadowedOwners());
            participants.add(target.winner());
            if (participants.size() < 2) continue;
            String scopedTarget = target.gameId() + "/" + target.contribution();
            var evidence = new ArrayList<String>();
            evidence.add("Published registration/preparation; gameplay activation has not been verified.");
            evidence.add("Policy: " + target.policy());
            evidence.add("Winner: " + target.winner() + valueSuffix(target.winnerValue()));
            for (int index = 0; index < target.shadowedOwners().size(); index++) {
                String value = index < target.shadowedValues().size() ? target.shadowedValues().get(index) : null;
                evidence.add("Replaced: " + target.shadowedOwners().get(index) + valueSuffix(value));
            }
            result.add(diagnostic(OBSERVED_OVERLAP, "MOD_CONTRIBUTION_OVERLAP", scopedTarget,
                    List.copyOf(participants), target.winner() + " replaces other contributions to " + scopedTarget + ".",
                    evidence));
            addExclusiveWarning(result, scopedTarget, target.contribution(), List.copyOf(participants));
        }
        return List.copyOf(result);
    }

    public List<ModCompatibilityDiagnostic> audioOverrides(List<PreparedModMusic.OverrideTarget> report,
                                                          String gameCode) {
        var result = new ArrayList<ModCompatibilityDiagnostic>();
        for (var target : report) {
            if (!target.gameId().equals(gameCode) || target.shadowed().isEmpty()) continue;
            var participants = new LinkedHashSet<String>();
            target.shadowed().forEach(track -> participants.add(track.modId()));
            participants.add(target.winner().modId());
            if (participants.size() < 2) continue;
            String contribution = "audio:" + target.musicId();
            String scopedTarget = gameCode + "/" + contribution;
            var evidence = new ArrayList<String>();
            evidence.add("Successful prepared audio publication for " + gameCode + "; failed owners are excluded.");
            evidence.add("Policy: " + target.policy());
            evidence.add("Winner: " + trackName(target.winner()));
            target.shadowed().forEach(track -> evidence.add("Replaced: " + trackName(track)));
            result.add(diagnostic(OBSERVED_OVERLAP, "MOD_AUDIO_OVERLAP", scopedTarget, List.copyOf(participants),
                    target.winner().modId() + " replaces other music for " + scopedTarget + ".", evidence));
            addExclusiveWarning(result, scopedTarget, contribution, List.copyOf(participants));
        }
        return List.copyOf(result);
    }

    public List<ModCompatibilityDiagnostic> registrationFailures(Map<String, Throwable> failures) {
        var result = new ArrayList<ModCompatibilityDiagnostic>();
        failures.forEach((owner, failure) -> {
            var participants = new TreeSet<String>();
            participants.add(owner);
            ModDescriptor descriptor = descriptors.get(owner);
            if (descriptor != null) descriptor.manifest().dependencies().stream().map(ModDependency::id)
                    .filter(failures::containsKey).forEach(participants::add);
            String code = failure instanceof ModRegistrationException structured
                    ? structured.findingCode() : "MOD_REGISTRATION_FAILED";
            var evidence = new ArrayList<String>();
            evidence.add("Mandatory registration transaction did not publish for " + owner + ".");
            evidence.add("Failure type: " + failure.getClass().getName());
            if (failure instanceof ModRegistrationException structured) evidence.add(structured.getMessage());
            evidence.add("Other active owners are not inferred to have caused this failure.");
            result.add(diagnostic(VERIFIED_BLOCKER, code, "registration/" + owner, List.copyOf(participants),
                    owner + " cannot provide its registered content; required dependents cannot use it.", evidence));
        });
        return List.copyOf(result);
    }

    public List<ModCompatibilityDiagnostic> runtimeRejections(Map<String, ModRuntime.Rejection> rejections) {
        var result = new ArrayList<ModCompatibilityDiagnostic>();
        rejections.forEach((owner, rejection) -> {
            var participants = new TreeSet<String>();
            participants.add(owner);
            ModDescriptor descriptor = descriptors.get(owner);
            if (descriptor != null) descriptor.manifest().dependencies().stream().map(ModDependency::id)
                    .filter(rejections::containsKey).forEach(participants::add);
            result.add(diagnostic(VERIFIED_BLOCKER, "MOD_RUNTIME_OWNER_REJECTED", "runtime-load/" + owner,
                    List.copyOf(participants), owner + " cannot provide its content because runtime preparation rejected it.",
                    List.of("Runtime requirement: " + rejection.reason(), rejection.detail(),
                            "Unrelated prepared owners remain available.")));
        });
        return List.copyOf(result);
    }

    public ModCompatibilityDiagnostic callbackFailure(String owner, Throwable failure,
                                                      Collection<String> preparedOwners, String gameCode) {
        var participants = new LinkedHashSet<String>();
        participants.add(owner);
        ModDescriptor executing = descriptors.get(owner);
        String scope = gameCode == null || gameCode.isBlank()
                ? executing == null ? "unselected" : executing.manifest().baseGame() : gameCode;
        if (scope == null) scope = owner;
        for (String candidate : preparedOwners) {
            ModDescriptor descriptor = descriptors.get(candidate);
            if (descriptor != null && appliesTo(descriptor, scope)) participants.add(candidate);
        }
        return diagnostic(SUSPECTED_INCOMPATIBILITY, "MOD_CALLBACK_INTERACTION_UNKNOWN", scope + "/callback/" + owner,
                List.copyOf(participants), owner + " failed during a callback; combination causality is unverified.",
                List.of("Failure owner: " + owner, "Failure type: " + failure.getClass().getName(),
                        "Receiving game or preparation scope: " + scope,
                        "Other participants are prepared owners in that scope, not proven causes.",
                        "Existing recovery quarantines only the failed owner and its required dependents."));
    }

    public ModCompatibilityDiagnostic ownerFailure(Set<String> owners, Throwable failure, String code) {
        return diagnostic(VERIFIED_BLOCKER, code, "patch-resolution/" + code, List.copyOf(owners),
                "The host rejected affected patch content; a causal mod combination has not been established.",
                List.of("Affected owner/dependent closure: " + String.join(", ", new TreeSet<>(owners)),
                        "Failure type: " + failure.getClass().getName(),
                        "Mandatory patch metadata/application failed; the host retired these owners.",
                        "This blocks their patch content without identifying an incompatible pair."));
    }

    private void addExclusiveWarning(List<ModCompatibilityDiagnostic> result, String target,
                                     String contribution, List<String> participants) {
        List<String> claimants = participants.stream().filter(owner -> descriptors.containsKey(owner)
                && descriptors.get(owner).manifest().composition().exclusiveContributions().contains(contribution))
                .sorted().toList();
        if (claimants.isEmpty()) return;
        result.add(diagnostic(SUSPECTED_INCOMPATIBILITY, "MOD_EXCLUSIVE_OVERLAP", target, participants,
                "An exclusive contribution is shared; ordinary override policy still applies.",
                List.of("Exclusive claimants: " + String.join(", ", claimants),
                        "Exclusivity is an author declaration, not proof that this combination prevents play.")));
    }

    private ModCompatibilityDiagnostic diagnostic(ModCompatibilityDiagnostic.Level level, String code,
                                                  String target, List<String> participants, String summary,
                                                  List<String> evidence) {
        return diagnostic(level, code, target, participants, summary, evidence,
                level.blocking() ? participants : List.of());
    }

    private ModCompatibilityDiagnostic diagnostic(ModCompatibilityDiagnostic.Level level, String code,
                                                  String target, List<String> participants, String summary,
                                                  List<String> evidence, List<String> blockedOwners) {
        var details = new ArrayList<>(evidence);
        for (String owner : new TreeSet<>(participants)) {
            ModDescriptor descriptor = descriptors.get(owner);
            if (descriptor != null) details.add("Build: " + owner + " " + descriptor.manifest().version()
                    + " SHA-256 " + descriptor.sha256());
        }
        return new ModCompatibilityDiagnostic(level, code, target, participants, summary, details, blockedOwners);
    }

    private static boolean appliesTo(ModDescriptor descriptor, String gameCode) {
        return descriptor.manifest().type() == ModType.STANDALONE
                ? descriptor.manifest().id().equals(gameCode)
                : gameCode.equals(descriptor.manifest().baseGame()) || "any".equals(descriptor.manifest().baseGame());
    }

    private static String valueSuffix(String value) { return value == null ? "" : " (" + value + ")"; }
    private static String trackName(TrackKey key) { return key.modId() + ":" + key.localName(); }
}
