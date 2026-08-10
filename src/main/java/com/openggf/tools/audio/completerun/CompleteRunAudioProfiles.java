package com.openggf.tools.audio.completerun;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CompleteRunFixture;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NativeSoundIdentity;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RawAudioRequest;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.StateInventory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            StateInventory stateInventory, Map<RawAudioRequest, NativeSoundIdentity> nativeSoundIdentities)
            implements CompleteRunAudioProfile {
        private FrozenProfile {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("profile id must be non-blank");
            }
            Objects.requireNonNull(fixture, "profile fixture");
            hardwareRoles = CompleteRunAudioTrace.canonicalRoles(hardwareRoles, "profile hardware roles");
            stateInventory = new StateInventory(stateInventory.globalFields(), stateInventory.activeRoleFields());
            nativeSoundIdentities = Map.copyOf(nativeSoundIdentities);
        }

        private static FrozenProfile copyOf(CompleteRunAudioProfile profile) {
            Objects.requireNonNull(profile, "profile");
            CompleteRunFixture fixture = profile.fixture();
            CompleteRunFixture fixtureCopy = new CompleteRunFixture(fixture.romSha1(), fixture.romCrc32(),
                    fixture.bk2Sha256(), fixture.bk2RowCount(), fixture.runManifestSha256(),
                    List.copyOf(fixture.segments()), fixture.firstFrame(), fixture.exclusiveEnd());
            return new FrozenProfile(profile.id(), fixtureCopy, List.copyOf(profile.hardwareRoles()),
                    profile.stateInventory(), Map.copyOf(profile.nativeSoundIdentities()));
        }
    }
}
