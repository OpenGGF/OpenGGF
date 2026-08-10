package com.openggf.tools.audio.completerun;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CompleteRunFixture;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.LifecycleRule;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NativeSoundIdentity;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ObserverProof;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ObserverRuntimeIdentity;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.OwnershipTransition;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.PendingRequestPolicy;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerKind;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerRuntimeIdentity;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RawAudioRequest;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RoleOwner;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RestoreStackPolicy;
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
    public static final String GPGX_AUDIO_TRACE_ABI_NAME = "gpgx.audio-trace.v1";
    public static final int GPGX_AUDIO_TRACE_ABI_VERSION = 1;
    public static final int GPGX_AUDIO_TRACE_EVENT_SIZE = 32;
    public static final int GPGX_AUDIO_TRACE_CAPACITY = 65_536;
    public static final String GPGX_AUDIO_TRACE_CORE_BUILD_ID = "8e822239d27df092";
    public static final String GPGX_AUDIO_TRACE_PATCH_SHA256 =
            "45d85fc19405457c788be4f6c17d2b14281d33fbff163cd42eead76e08f7f6d2";
    public static final String GPGX_AUDIO_TRACE_CORE_SHA256 =
            "ba276573fc7802fb2313c051471dbdd664959c06aaafa6ef73564799886d083f";
    public static final String GPGX_AUDIO_TRACE_CORE_UNCOMPRESSED_SHA256 =
            "7807b57ffdfa303465ec2a2e707a5aacc38bd56cd10e201aca2965620eb71fb2";
    public static final String GPGX_AUDIO_TRACE_SOURCE_BUNDLE_SHA256 =
            "abd68651d633a0a75d01cb9569cfb9dc15da4a7540eb072fc2d8eb11e548ed0e";
    public static final String GPGX_AUDIO_TRACE_TOOLCHAIN_SHA256 =
            "9caa5c02dcd2d9c01e5d0196956787a0f31760195c6544a2ceafcb771f469521";
    public static final String GPGX_AUDIO_TRACE_BUILD_RECIPE_SHA256 =
            "eee5fa9e4eda2480ea76207edc0cbb3b4a89e54ac767e9cba744dca1f4420f71";
    public static final String GPGX_AUDIO_TRACE_IDENTITY_SHA256 =
            "6f4739f28771e55bcec0ca0e6f0c57badb3530d4cee36d39c8b19b14104ddfce";
    public static final String GPGX_AUDIO_TRACE_ADAPTER_SOURCE_SHA256 =
            "770dfcfef0fabc2eb7211add26d7a3716e33b75ddbe7dd3d7ba1568c8cb3a102";
    public static final String GPGX_AUDIO_TRACE_HOST_SOURCE_SHA256 =
            "052090e4a93c6614f3c4465526c47876779dc40ded1897d0cc4d24c3c04ed497";
    public static final String BIZHAWK_BIZINVOKE_SHA256 =
            "8d05389bf0e02be1244bdc7a2adcd93b4cff95acf199fc927987ca699760a1b7";
    public static final String BIZHAWK_BASE_COMMON_SHA256 =
            "438a49d6a45d9fcac17016240ae205d1af7a4632865f6f70468b684b82323f33";

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
            Map<ProducerKind, ObserverRuntimeIdentity> observerRuntimeIdentities,
            Map<NativeSoundIdentity, List<NativeSoundIdentity>> decisionResolutions,
            List<RoleOwner> baselineRoleOwners,
            Map<String, OwnershipTransition> ownershipTransitions,
            PendingRequestPolicy pendingRequestPolicy,
            RestoreStackPolicy restoreStackPolicy,
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
            observerRuntimeIdentities = Map.copyOf(observerRuntimeIdentities);
            decisionResolutions = freezeResolutions(decisionResolutions);
            baselineRoleOwners = List.copyOf(baselineRoleOwners);
            ownershipTransitions = Map.copyOf(ownershipTransitions);
            Objects.requireNonNull(pendingRequestPolicy, "profile pending request policy");
            RestoreStackPolicy declaredRestorePolicy = Objects.requireNonNull(restoreStackPolicy,
                    "profile restore stack policy");
            restoreStackPolicy = new RestoreStackPolicy(declaredRestorePolicy.maximumDepth(),
                    declaredRestorePolicy.terminalDepths(), declaredRestorePolicy.terminalAllowanceReason());
            lifecycleRules = Map.copyOf(lifecycleRules);
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
            if (!observerRuntimeIdentities.keySet().containsAll(EnumSet.allOf(ProducerKind.class))) {
                throw new IllegalArgumentException(
                        "profile must declare an observer runtime identity for every producer");
            }
            for (ProducerKind kind : ProducerKind.values()) {
                Objects.requireNonNull(observerProofs.get(kind), "profile observer proof");
                ObserverRuntimeIdentity observerIdentity = Objects.requireNonNull(
                        observerRuntimeIdentities.get(kind), "profile observer runtime identity");
                producerRuntimeIdentities.get(kind).validateFor(kind, observerIdentity);
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
            boolean savesOwner = ownershipTransitions.containsValue(OwnershipTransition.SAVE_AND_ACQUIRE_REQUEST)
                    || lifecycleRules.values().stream().anyMatch(rule ->
                            rule.ownershipAction()
                                    == CompleteRunAudioTrace.LifecycleOwnershipAction.SAVE_CURRENT);
            boolean restoresOwner = lifecycleRules.values().stream().anyMatch(rule ->
                    rule.ownershipAction() == CompleteRunAudioTrace.LifecycleOwnershipAction.RESTORE_SAVED);
            boolean usesRestoreStack = savesOwner || restoresOwner;
            if (usesRestoreStack != (restoreStackPolicy.maximumDepth() > 0)) {
                throw new IllegalArgumentException("restore transitions and restore depth must be declared together");
            }
            if (restoresOwner && !savesOwner) {
                throw new IllegalArgumentException("restore lifecycle needs a profile-owned save action");
            }
            for (Map.Entry<String, LifecycleRule> entry : lifecycleRules.entrySet()) {
                LifecycleRule rule = Objects.requireNonNull(entry.getValue(), "lifecycle rule");
                if (!entry.getKey().equals(rule.kind())) {
                    throw new IllegalArgumentException("lifecycle rule map key must equal its declared kind");
                }
                for (List<HardwareRole> roles : rule.ownershipRoleSets()) {
                    if (!hardwareRoles.containsAll(roles)) {
                        throw new IllegalArgumentException(
                                "lifecycle rule role set is outside the profile hardware inventory");
                    }
                }
            }
            for (var terminalDepth : restoreStackPolicy.terminalDepths()) {
                if (!hardwareRoles.contains(terminalDepth.role())) {
                    throw new IllegalArgumentException(
                            "terminal restore stack role is outside the profile hardware inventory");
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
                    Map.copyOf(profile.observerRuntimeIdentities()),
                    profile.decisionResolutions(), profile.baselineRoleOwners(), profile.ownershipTransitions(),
                    profile.pendingRequestPolicy(), profile.restoreStackPolicy(), profile.lifecycleRules());
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
