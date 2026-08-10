package com.openggf.tools.audio.completerun;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CompleteRunFixture;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.LifecycleRule;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NativeSoundIdentity;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ObserverProof;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.OwnershipTransition;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.PendingRequestPolicy;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerKind;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerRuntimeIdentity;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RawAudioRequest;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RoleOwner;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.StateInventory;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Strict process-local profile registry. Registration publishes an immutable snapshot. */
public final class CompleteRunAudioProfiles {
    private static final AtomicReference<Map<String, CompleteRunAudioProfile>> PROFILES =
            new AtomicReference<>(Map.of());

    private CompleteRunAudioProfiles() {
    }

    public static CompleteRunAudioProfile require(String id) {
        Objects.requireNonNull(id, "profile id");
        CompleteRunAudioProfile profile = PROFILES.get().get(id);
        if (profile == null) {
            throw new IllegalArgumentException("unknown complete-run audio profile: " + id);
        }
        return profile;
    }

    /** Registers one immutable profile exactly once; later readers observe the whole new registry. */
    public static void register(CompleteRunAudioProfile profile) {
        Objects.requireNonNull(profile, "profile");
        CompleteRunAudioProfile frozen = FrozenProfile.copyOf(profile);
        String id = frozen.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("profile id must be non-blank");
        }
        while (true) {
            Map<String, CompleteRunAudioProfile> current = PROFILES.get();
            if (current.containsKey(id)) {
                throw new IllegalArgumentException("complete-run audio profile is already registered: " + id);
            }
            Map<String, CompleteRunAudioProfile> next = new LinkedHashMap<>(current);
            next.put(id, frozen);
            if (PROFILES.compareAndSet(current, Map.copyOf(next))) {
                return;
            }
        }
    }

    public static Map<String, CompleteRunAudioProfile> registered() {
        return PROFILES.get();
    }

    /** Value snapshot prevents later mutations by a profile factory from changing capture validation. */
    private record FrozenProfile(String id, CompleteRunFixture fixture, List<HardwareRole> hardwareRoles,
            StateInventory stateInventory, Map<RawAudioRequest, NativeSoundIdentity> nativeSoundIdentities,
            Map<ProducerKind, ProducerRuntimeIdentity> producerRuntimeIdentities,
            Map<ProducerKind, ObserverProof> observerProofs,
            Map<NativeSoundIdentity, List<NativeSoundIdentity>> decisionResolutions,
            List<RoleOwner> baselineRoleOwners,
            Map<String, OwnershipTransition> ownershipTransitions,
            PendingRequestPolicy pendingRequestPolicy,
            int maximumRestoreDepth,
            Map<String, LifecycleRule> lifecycleRules)
            implements CompleteRunAudioProfile {
        private FrozenProfile {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("profile id must be non-blank");
            }
            Objects.requireNonNull(fixture, "profile fixture");
            hardwareRoles = CompleteRunAudioTrace.canonicalRoles(hardwareRoles, "profile hardware roles");
            stateInventory = new StateInventory(stateInventory.globalFields(), stateInventory.activeRoleFields());
            nativeSoundIdentities = Map.copyOf(nativeSoundIdentities);
            producerRuntimeIdentities = Map.copyOf(producerRuntimeIdentities);
            observerProofs = Map.copyOf(observerProofs);
            decisionResolutions = freezeResolutions(decisionResolutions);
            baselineRoleOwners = List.copyOf(baselineRoleOwners);
            ownershipTransitions = Map.copyOf(ownershipTransitions);
            Objects.requireNonNull(pendingRequestPolicy, "profile pending request policy");
            lifecycleRules = Map.copyOf(lifecycleRules);
            if (maximumRestoreDepth < 0 || maximumRestoreDepth > 16) {
                throw new IllegalArgumentException("profile restore depth must be between zero and sixteen");
            }
            if (!producerRuntimeIdentities.keySet().containsAll(EnumSet.allOf(ProducerKind.class))) {
                throw new IllegalArgumentException("profile must declare an allowed runtime identity for every producer");
            }
            for (Map.Entry<ProducerKind, ProducerRuntimeIdentity> entry : producerRuntimeIdentities.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "producer kind");
                entry.getValue().validateFor(entry.getKey());
            }
            if (!observerProofs.keySet().containsAll(EnumSet.allOf(ProducerKind.class))) {
                throw new IllegalArgumentException("profile must declare an observer proof for every producer");
            }
            for (ProducerKind kind : ProducerKind.values()) {
                Objects.requireNonNull(observerProofs.get(kind), "profile observer proof");
            }
            if (!decisionResolutions.keySet().containsAll(Set.copyOf(nativeSoundIdentities.values()))) {
                throw new IllegalArgumentException("profile must declare decision resolutions for every request identity");
            }
            if (baselineRoleOwners.size() != hardwareRoles.size()) {
                throw new IllegalArgumentException("profile must declare one baseline owner per hardware role");
            }
            for (int index = 0; index < hardwareRoles.size(); index++) {
                if (baselineRoleOwners.get(index).role() != hardwareRoles.get(index)) {
                    throw new IllegalArgumentException("profile baseline owners must follow hardware-role order");
                }
            }
            for (Map.Entry<String, OwnershipTransition> entry : ownershipTransitions.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    throw new IllegalArgumentException("profile ownership transitions need exact reasons and rules");
                }
            }
            if (ownershipTransitions.isEmpty()) {
                throw new IllegalArgumentException("profile must declare ownership transitions");
            }
            boolean usesRestoreStack = ownershipTransitions.containsValue(OwnershipTransition.SAVE_AND_ACQUIRE_REQUEST)
                    || ownershipTransitions.containsValue(OwnershipTransition.RESTORE_SAVED);
            if (usesRestoreStack != (maximumRestoreDepth > 0)) {
                throw new IllegalArgumentException("restore transitions and restore depth must be declared together");
            }
            for (Map.Entry<String, LifecycleRule> entry : lifecycleRules.entrySet()) {
                if (!entry.getKey().equals(Objects.requireNonNull(entry.getValue(), "lifecycle rule").kind())) {
                    throw new IllegalArgumentException("lifecycle rule map key must equal its declared kind");
                }
            }
        }

        private static FrozenProfile copyOf(CompleteRunAudioProfile profile) {
            Objects.requireNonNull(profile, "profile");
            CompleteRunFixture fixture = profile.fixture();
            CompleteRunFixture fixtureCopy = new CompleteRunFixture(fixture.romSha1(), fixture.romCrc32(),
                    fixture.bk2Sha256(), fixture.bk2RowCount(), fixture.runManifestSha256(),
                    List.copyOf(fixture.segments()), fixture.firstFrame(), fixture.exclusiveEnd());
            return new FrozenProfile(profile.id(), fixtureCopy, List.copyOf(profile.hardwareRoles()),
                    profile.stateInventory(), Map.copyOf(profile.nativeSoundIdentities()),
                    Map.copyOf(profile.producerRuntimeIdentities()), Map.copyOf(profile.observerProofs()),
                    profile.decisionResolutions(), profile.baselineRoleOwners(), profile.ownershipTransitions(),
                    profile.pendingRequestPolicy(), profile.maximumRestoreDepth(), profile.lifecycleRules());
        }

        private static Map<NativeSoundIdentity, List<NativeSoundIdentity>> freezeResolutions(
                Map<NativeSoundIdentity, List<NativeSoundIdentity>> resolutions) {
            Objects.requireNonNull(resolutions, "profile decision resolutions");
            Map<NativeSoundIdentity, List<NativeSoundIdentity>> frozen = new LinkedHashMap<>();
            for (Map.Entry<NativeSoundIdentity, List<NativeSoundIdentity>> entry : resolutions.entrySet()) {
                NativeSoundIdentity requested = Objects.requireNonNull(entry.getKey(), "requested identity");
                List<NativeSoundIdentity> allowed = List.copyOf(
                        Objects.requireNonNull(entry.getValue(), "allowed decision identities"));
                if (allowed.isEmpty() || allowed.stream().anyMatch(Objects::isNull)) {
                    throw new IllegalArgumentException("every request identity needs at least one allowed resolution");
                }
                frozen.put(requested, allowed);
            }
            return Map.copyOf(frozen);
        }
    }
}
