package com.openggf.tools.audio.completerun;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CompleteRunFixture;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Lifecycle;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.LifecycleRule;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NativeSoundIdentity;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NormalizedState;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ObserverProof;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.OwnershipTransition;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.PendingRequestPolicy;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerKind;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerRuntimeIdentity;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RawAudioRequest;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RoleState;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RoleOwner;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.StateInventory;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, tooling-side declaration of one game's canonical audio-state inventory. */
public interface CompleteRunAudioProfile {
    String id();

    CompleteRunFixture fixture();

    List<HardwareRole> hardwareRoles();

    StateInventory stateInventory();

    /** Complete native request-to-ROM-content resolution owned by this immutable profile. */
    Map<RawAudioRequest, NativeSoundIdentity> nativeSoundIdentities();

    /** Runtime identities explicitly permitted for each capture producer kind. */
    Map<ProducerKind, ProducerRuntimeIdentity> producerRuntimeIdentities();

    /** Exact observer/callback proof pinned independently for each producer kind. */
    Map<ProducerKind, ObserverProof> observerProofs();

    /** Allowed request-to-admission identity transformations, with no game checks in shared code. */
    Map<NativeSoundIdentity, List<NativeSoundIdentity>> decisionResolutions();

    /** Exact live owner for every hardware role at the comparison baseline. */
    List<RoleOwner> baselineRoleOwners();

    /** Generic ownership transition selected by the exact recorded decision reason. */
    Map<String, OwnershipTransition> ownershipTransitions();

    /** Hard bound for unresolved request identities retained by validation. */
    PendingRequestPolicy pendingRequestPolicy();

    /** Hard per-role bound for profile-declared save/restore ownership transitions. */
    int maximumRestoreDepth();

    /** Allowed out-of-service lifecycle markers and their exact detail-field inventories. */
    Map<String, LifecycleRule> lifecycleRules();

    default NativeSoundIdentity resolveRequest(RawAudioRequest request) {
        NativeSoundIdentity identity = nativeSoundIdentities().get(Objects.requireNonNull(request, "request"));
        if (identity == null) {
            throw new IllegalArgumentException("profile does not resolve raw audio request: " + request);
        }
        return identity;
    }

    /** Validates canonical field and role order without consulting a runtime audio owner. */
    default void validateState(NormalizedState state) {
        Objects.requireNonNull(state, "state");
        List<HardwareRole> expectedRoles = CompleteRunAudioTrace.canonicalRoles(hardwareRoles(),
                "profile hardware roles");
        StateInventory inventory = Objects.requireNonNull(stateInventory(), "state inventory");
        if (!state.fields().stream().map(field -> field.name()).toList().equals(inventory.globalFields())) {
            throw new IllegalArgumentException("state fields do not match the profile inventory");
        }
        if (state.roles().size() != expectedRoles.size()) {
            throw new IllegalArgumentException("state roles do not match the profile inventory");
        }
        for (int index = 0; index < expectedRoles.size(); index++) {
            RoleState roleState = state.roles().get(index);
            if (roleState.role() != expectedRoles.get(index)) {
                throw new IllegalArgumentException("state roles are not in profile order");
            }
            List<String> names = roleState.fields().stream().map(field -> field.name()).toList();
            if (!roleState.active() && !names.isEmpty()) {
                throw new IllegalArgumentException("inactive role contains stale state fields");
            }
            if (roleState.active() && !names.equals(inventory.activeRoleFields())) {
                throw new IllegalArgumentException("active role fields do not match the profile inventory");
            }
        }
    }

    default void validateLifecycle(Lifecycle lifecycle) {
        Objects.requireNonNull(lifecycle, "lifecycle");
        LifecycleRule rule = Objects.requireNonNull(lifecycleRules(), "profile lifecycle rules")
                .get(lifecycle.kind());
        if (rule == null || !rule.kind().equals(lifecycle.kind())
                || !rule.detailFields().equals(lifecycle.details().keySet().stream().toList())) {
            throw new IllegalArgumentException("lifecycle does not match the profile rule");
        }
    }
}
