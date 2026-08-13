package com.openggf.tools.audio.completerun.s2;

import com.openggf.tools.audio.completerun.CompleteRunAudioProfile;
import com.openggf.tools.audio.completerun.CompleteRunAudioProfiles;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pinned Sonic 2 World REV01 Sonic-and-Tails all-emeralds audio contract. */
public final class S2CompleteRunAudioProfile {
    public static final String ID = "s2_rev01_complete_emeralds.v1";
    private static final CompleteRunAudioProfile PROFILE = new Profile();

    static {
        CompleteRunAudioProfiles.register(PROFILE);
    }

    private S2CompleteRunAudioProfile() { }

    public static CompleteRunAudioProfile profile() {
        return PROFILE;
    }

    private static final class Profile implements CompleteRunAudioProfile {
        private static final List<CompleteRunAudioTrace.HardwareRole> ROLES =
                List.of(CompleteRunAudioTrace.HardwareRole.values());
        private static final Map<CompleteRunAudioTrace.RawAudioRequest,
                CompleteRunAudioTrace.NativeSoundIdentity> IDENTITIES =
                S2NativeSoundResolver.rev01().nativeRequestIdentities();
        private static final Map<CompleteRunAudioTrace.NativeSoundIdentity,
                List<CompleteRunAudioTrace.NativeSoundIdentity>> RESOLUTIONS = resolutions();
        private static final CompleteRunAudioTrace.OwnerRef NONE = new CompleteRunAudioTrace.OwnerRef(
                CompleteRunAudioTrace.OwnerClass.NONE, "none", 0,
                CompleteRunAudioTrace.OwnerOrigin.NONE, -1);

        @Override public String id() { return ID; }
        @Override public CompleteRunAudioTrace.CompleteRunFixture fixture() { return S2CompleteRunAudioProfile.fixture(); }
        @Override public List<CompleteRunAudioTrace.HardwareRole> hardwareRoles() { return ROLES; }
        @Override public CompleteRunAudioTrace.StateInventory stateInventory() {
            return new CompleteRunAudioTrace.StateInventory(
                    S2CompleteRunStateNormalizer.GLOBAL_FIELDS,
                    S2CompleteRunStateNormalizer.ACTIVE_ROLE_FIELDS);
        }
        @Override public Map<CompleteRunAudioTrace.RawAudioRequest,
                CompleteRunAudioTrace.NativeSoundIdentity> nativeSoundIdentities() { return IDENTITIES; }
        @Override public Map<CompleteRunAudioTrace.ProducerKind,
                CompleteRunAudioTrace.ProducerRuntimeIdentity> producerRuntimeIdentities() { return Map.of(); }
        @Override public Map<CompleteRunAudioTrace.ProducerKind,
                CompleteRunAudioTrace.ProducerBinding> producerBindings() {
            return Map.of(
                    CompleteRunAudioTrace.ProducerKind.REFERENCE,
                    new CompleteRunAudioTrace.UnavailableProducerBinding("Task 2 S2 reference adapter is not installed"),
                    CompleteRunAudioTrace.ProducerKind.OPENGGF,
                    new CompleteRunAudioTrace.UnavailableProducerBinding("Task 5 S2 OpenGGF producer is not installed"));
        }
        @Override public Map<CompleteRunAudioTrace.ProducerKind,
                CompleteRunAudioTrace.ObserverProof> observerProofs() { return Map.of(); }
        @Override public Map<CompleteRunAudioTrace.ProducerKind,
                CompleteRunAudioTrace.ObserverRuntimeIdentity> observerRuntimeIdentities() { return Map.of(); }
        @Override public CompleteRunAudioTrace.CutoffFrontierPolicy cutoffFrontierPolicy() {
            var empty = CompleteRunAudioTrace.CutoffFrontier.empty(
                    new CompleteRunAudioTrace.NormalizedState(List.of(), List.of()));
            return new CompleteRunAudioTrace.CutoffFrontierPolicy(List.of(), 0, 0, 0, 0, 0, 0, 0, 0,
                    false, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    CompleteRunAudioTrace.CutoffFrontierPolicy.capabilityDigest(empty), null);
        }
        @Override public Map<CompleteRunAudioTrace.ProducerKind,
                CompleteRunAudioTrace.NativeCapabilitySummary> completeRunCapabilities() { return Map.of(); }
        @Override public Map<CompleteRunAudioTrace.NativeSoundIdentity,
                List<CompleteRunAudioTrace.NativeSoundIdentity>> decisionResolutions() { return RESOLUTIONS; }
        @Override public List<CompleteRunAudioTrace.RoleOwner> baselineRoleOwners() {
            return ROLES.stream().map(role -> new CompleteRunAudioTrace.RoleOwner(role, NONE)).toList();
        }
        @Override public Map<String, CompleteRunAudioTrace.OwnershipTransition> ownershipTransitions() {
            return Map.of("accepted", CompleteRunAudioTrace.OwnershipTransition.ACQUIRE_REQUEST,
                    "rejected_priority", CompleteRunAudioTrace.OwnershipTransition.REJECT_PRESERVE,
                    "suppressed", CompleteRunAudioTrace.OwnershipTransition.REJECT_PRESERVE,
                    "one_up", CompleteRunAudioTrace.OwnershipTransition.SAVE_AND_ACQUIRE_REQUEST);
        }
        @Override public CompleteRunAudioTrace.PendingRequestPolicy pendingRequestPolicy() {
            return new CompleteRunAudioTrace.PendingRequestPolicy(3, 0, null);
        }
        @Override public CompleteRunAudioTrace.RestoreStackPolicy restoreStackPolicy() {
            return new CompleteRunAudioTrace.RestoreStackPolicy(1, List.of(), null);
        }
        @Override public Map<String, CompleteRunAudioTrace.LifecycleRule> lifecycleRules() {
            return Map.of(
                    "one_up_save", new CompleteRunAudioTrace.LifecycleRule("one_up_save", List.of(),
                            CompleteRunAudioTrace.LifecycleOwnershipAction.SAVE_CURRENT, List.of(ROLES)),
                    "one_up_restore", new CompleteRunAudioTrace.LifecycleRule("one_up_restore", List.of(),
                            CompleteRunAudioTrace.LifecycleOwnershipAction.RESTORE_SAVED, List.of(ROLES)));
        }
    }

    private static CompleteRunAudioTrace.CompleteRunFixture fixture() {
        List<CompleteRunAudioTrace.ManifestSegment> segments = new ArrayList<>();
        segment(segments, "seg1_ehz1", 769, 3710); segment(segments, "ss", 4480, 5681);
        segment(segments, "seg2_ehz1", 10334, 3377); segment(segments, "ss_2", 13712, 6361);
        segment(segments, "seg3_ehz1", 20246, 3960); segment(segments, "ss_3", 24207, 7092);
        segment(segments, "seg4_ehz1", 31472, 1288); segment(segments, "seg5_ehz2", 32931, 6046);
        segment(segments, "ss_4", 38978, 7224); segment(segments, "seg6_ehz2", 46374, 3794);
        segment(segments, "ss_5", 50169, 6690); segment(segments, "seg7_ehz2", 57031, 3997);
        segment(segments, "seg8_cpz1", 61206, 6613); segment(segments, "seg9_cpz2", 67996, 5837);
        segment(segments, "ss_6", 73834, 8310); segment(segments, "seg10_cpz2", 82342, 7088);
        segment(segments, "seg11_arz1", 89600, 3420); segment(segments, "ss_7", 93021, 8498);
        segment(segments, "seg12_arz1", 101691, 4889); segment(segments, "seg13_arz2", 106753, 6409);
        segment(segments, "seg14_cnz1", 113340, 12145); segment(segments, "seg15_cnz2", 125661, 13045);
        segment(segments, "seg16_htz1", 138902, 7535); segment(segments, "seg17_htz2", 146636, 8460);
        segment(segments, "seg18_mcz1", 155265, 6213); segment(segments, "seg19_mcz2", 161649, 8610);
        segment(segments, "seg20_ooz1", 170435, 11557); segment(segments, "seg21_ooz2", 182168, 8591);
        segment(segments, "seg22_mtz1", 190944, 7590); segment(segments, "seg23_mtz2", 198719, 6542);
        segment(segments, "seg24_mtz3", 205445, 11341); segment(segments, "seg25_scz1", 216944, 4707);
        segment(segments, "seg26_scz1", 221809, 7611); segment(segments, "seg27_wfz1", 229619, 9667);
        segment(segments, "seg28_dez1", 239443, 5578);
        return new CompleteRunAudioTrace.CompleteRunFixture(
                "8bca5dcef1af3e00098666fd892dc1c2a76333f9", "7b905383",
                "e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5", 259590,
                "dfb220822eab3c524472aa02d6d78463a9489233b97fdd9ccd9340c9f3a10411",
                segments, 769, 259590);
    }

    private static void segment(List<CompleteRunAudioTrace.ManifestSegment> segments,
            String id, int start, int count) {
        segments.add(new CompleteRunAudioTrace.ManifestSegment(id, start, start + count));
    }

    private static Map<CompleteRunAudioTrace.NativeSoundIdentity,
            List<CompleteRunAudioTrace.NativeSoundIdentity>> resolutions() {
        Map<CompleteRunAudioTrace.NativeSoundIdentity, List<CompleteRunAudioTrace.NativeSoundIdentity>> result =
                new LinkedHashMap<>();
        for (CompleteRunAudioTrace.NativeSoundIdentity identity : Profile.IDENTITIES.values()) {
            result.put(identity, List.of(identity));
        }
        CompleteRunAudioTrace.NativeSoundIdentity ringRight = result.keySet().stream()
                .filter(value -> value.ownerClass() == CompleteRunAudioTrace.OwnerClass.SFX && value.nativeId() == 0xb5)
                .findFirst().orElseThrow();
        CompleteRunAudioTrace.NativeSoundIdentity ringLeft = result.keySet().stream()
                .filter(value -> value.ownerClass() == CompleteRunAudioTrace.OwnerClass.SFX && value.nativeId() == 0xce)
                .findFirst().orElseThrow();
        result.put(ringRight, List.of(ringRight, ringLeft));
        return Map.copyOf(result);
    }
}
