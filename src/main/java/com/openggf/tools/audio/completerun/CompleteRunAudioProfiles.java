package com.openggf.tools.audio.completerun;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CompleteRunFixture;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CutoffFrontierPolicy;
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
    public static final int GPGX_AUDIO_TRACE_ABI_VERSION = 3;
    public static final int GPGX_AUDIO_TRACE_EVENT_SIZE = 32;
    public static final int GPGX_AUDIO_TRACE_CONFIG_SIZE = 64;
    public static final int GPGX_AUDIO_TRACE_KIND_SIZE = 16;
    public static final int GPGX_AUDIO_TRACE_HOOK_SIZE = 32;
    public static final int GPGX_AUDIO_TRACE_RANGE_SIZE = 16;
    public static final int GPGX_AUDIO_TRACE_CAPACITY = 65_536;
    public static final String GPGX_AUDIO_TRACE_CORE_BUILD_ID = "44fb4d8c232fc98f";
    public static final String GPGX_AUDIO_TRACE_PATCH_SHA256 =
            "8eca789eaf52dd57704f34956356044f37b7a8ca312b158b349b9daa66613eaa";
    public static final String GPGX_AUDIO_TRACE_CORE_SHA256 =
            "bad0aa996672e3e344c3450ad846dbc15e4fb29bfb6fb247eecf2ba826ec5790";
    public static final String GPGX_AUDIO_TRACE_CORE_UNCOMPRESSED_SHA256 =
            "4715106ed3711e610b900f0dee19dcfc34de347be2717692d1c25f836d957bf5";
    public static final String GPGX_AUDIO_TRACE_SOURCE_BUNDLE_SHA256 =
            "d6dbc370e05ed1732500cd8262e0c77f3a1dafc0ddf01fa0e44fbaa45299f1bc";
    public static final String GPGX_AUDIO_TRACE_TOOLCHAIN_SHA256 =
            "9caa5c02dcd2d9c01e5d0196956787a0f31760195c6544a2ceafcb771f469521";
    public static final String GPGX_AUDIO_TRACE_BUILD_RECIPE_SHA256 =
            "3b01a7c86be24e563defc30bd3f6bc52630218efcf97b6ab4d02aad508263abf";
    public static final String GPGX_AUDIO_TRACE_IDENTITY_SHA256 =
            "f9e986419ac08b4bd51212a2169fbbf1b6d85a1552aa2364792b1b77836fb8b2";
    public static final String GPGX_AUDIO_TRACE_ADAPTER_SOURCE_SHA256 =
            "046ab11f4ffaf100651dda49625e14f3b08e54a33f61ed415d039a0d27b9bb93";
    public static final String GPGX_AUDIO_TRACE_HOST_BRIDGE_SOURCE_SHA256 =
            "af9da7ed2f08d27c663176f4f1c852504c4a515e437655abb0fd5d20a3364bf1";
    public static final String BIZHAWK_BIZINVOKE_SHA256 =
            "8d05389bf0e02be1244bdc7a2adcd93b4cff95acf199fc927987ca699760a1b7";
    public static final String BIZHAWK_BASE_COMMON_SHA256 =
            "438a49d6a45d9fcac17016240ae205d1af7a4632865f6f70468b684b82323f33";
    public static final String TASK8_HARNESS_EXECUTABLE_SHA256 =
            "01b0a5faf1f3b08346c86c45e327ad796f3db4fc7f5af239fb21b7249186bdec";
    public static final String TASK8_COLLECTOR_SOURCE_SHA256 =
            "516555cdef86d403fca64571bf0300711894da16b74ca46856cbdfce817a49b0";
    public static final String TASK8_HOST_SOURCE_SHA256 =
            "c45d7de53bd29101d896fadb0a69eda1ae206d1fac43a5733afb3f4bd7f86be7";
    public static final String GPGX_AUDIO_CAPABILITY_SHA256 =
            "0a2be19ae646e3a06da37e05f87dff0616c81a2b531725c9b736cf5b0bda5eaf";
    public static final String REFERENCE_INSTALLATION_TREE_SHA256 =
            "3baf6a12d2ddf7a97f26aad4222285cd7a1cfc7288ffacd06af6c71baf0a0130";

    private static final AtomicReference<Map<String, CompleteRunAudioProfile>> PROFILES =
            new AtomicReference<>(Map.of());

    private CompleteRunAudioProfiles() {
    }

    public static CompleteRunAudioProfile require(String id) {
        Objects.requireNonNull(id, "profile id");
        CompleteRunAudioProfile profile = PROFILES.get().get(id);
        if (profile == null && CompleteRunAudioProducerRegistry.tryLoadProfile(id)) {
            profile = PROFILES.get().get(id);
        }
        if (profile == null) {
            throw new IllegalArgumentException("unknown complete-run audio profile: " + id);
        }
        return profile;
    }

    static boolean isRegistered(String id) {
        return PROFILES.get().containsKey(id);
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
            Map<ProducerKind, CompleteRunAudioTrace.ProducerBinding> producerBindings,
            Map<ProducerKind, ObserverProof> observerProofs,
            Map<ProducerKind, ObserverRuntimeIdentity> observerRuntimeIdentities,
            CutoffFrontierPolicy cutoffFrontierPolicy,
            Map<ProducerKind, CompleteRunAudioTrace.NativeCapabilitySummary> completeRunCapabilities,
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
            producerBindings = Map.copyOf(producerBindings);
            observerProofs = Map.copyOf(observerProofs);
            observerRuntimeIdentities = Map.copyOf(observerRuntimeIdentities);
            Objects.requireNonNull(cutoffFrontierPolicy, "profile cutoff-frontier policy");
            completeRunCapabilities = Map.copyOf(Objects.requireNonNull(completeRunCapabilities,
                    "profile complete-run capabilities"));
            decisionResolutions = freezeResolutions(decisionResolutions);
            baselineRoleOwners = List.copyOf(baselineRoleOwners);
            ownershipTransitions = Map.copyOf(ownershipTransitions);
            Objects.requireNonNull(pendingRequestPolicy, "profile pending request policy");
            RestoreStackPolicy declaredRestorePolicy = Objects.requireNonNull(restoreStackPolicy,
                    "profile restore stack policy");
            restoreStackPolicy = new RestoreStackPolicy(declaredRestorePolicy.maximumDepth(),
                    declaredRestorePolicy.terminalDepths(), declaredRestorePolicy.terminalAllowanceReason());
            lifecycleRules = Map.copyOf(lifecycleRules);
            if (!producerBindings.keySet().containsAll(EnumSet.allOf(ProducerKind.class))) {
                throw new IllegalArgumentException("profile must declare a producer binding for every producer");
            }
            for (ProducerKind kind : ProducerKind.values()) {
                CompleteRunAudioTrace.ProducerBinding binding = producerBindings.get(kind);
                if (binding instanceof CompleteRunAudioTrace.UnavailableProducerBinding) {
                    if (producerRuntimeIdentities.containsKey(kind) || observerProofs.containsKey(kind)
                            || observerRuntimeIdentities.containsKey(kind)
                            || completeRunCapabilities.containsKey(kind)) {
                        throw new IllegalArgumentException(
                                "unavailable producer must not declare runtime or observer identities");
                    }
                    continue;
                }
                if (binding instanceof CompleteRunAudioTrace.PinnedProducerBinding pinned
                        && !pinned.runtimeIdentity().equals(producerRuntimeIdentities.get(kind))) {
                    throw new IllegalArgumentException("pinned producer binding and runtime identity disagree");
                }
                if (!observerProofs.containsKey(kind)) {
                    throw new IllegalArgumentException("pinned producer must declare an observer proof");
                }
                ObserverRuntimeIdentity observerIdentity = observerRuntimeIdentities.get(kind);
                if (observerIdentity == null) {
                    throw new IllegalArgumentException("pinned producer must declare an observer runtime identity");
                }
                producerRuntimeIdentities.get(kind).validateFor(kind, observerIdentity);
                boolean buffered = observerIdentity instanceof CompleteRunAudioTrace.BufferedNativeObserverIdentity;
                if (buffered != completeRunCapabilities.containsKey(kind)) {
                    throw new IllegalArgumentException(
                            "buffered observation and exact complete-run capability must be declared together");
                }
            }
            for (Map.Entry<ProducerKind, ProducerRuntimeIdentity> entry : producerRuntimeIdentities.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "producer kind");
                entry.getValue().validateFor(entry.getKey());
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
                    Map.copyOf(profile.producerRuntimeIdentities()), Map.copyOf(profile.producerBindings()), Map.copyOf(profile.observerProofs()),
                    Map.copyOf(profile.observerRuntimeIdentities()),
                    profile.cutoffFrontierPolicy(),
                    profile.completeRunCapabilities(),
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
