package com.openggf.tools.audio.completerun;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CompleteRunFixture;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NativeSoundIdentity;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NormalizedState;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RawAudioRequest;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RoleState;
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
}
