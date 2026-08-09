package com.openggf.tools.audio.timeline;

import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.HardwareRole.FM3;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.HardwareRole.FM4;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.OwnerClass.MUSIC;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.OwnerClass.NONE;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.OwnerClass.NORMAL_SFX;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.SoundClass.SFX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS1GameplayAudioTimelineComparator {
    @TempDir
    Path temp;

    @Test
    void reportsMetadataAndSideAwareCaptureFailures() throws Exception {
        Path reference = write("reference.jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE,
                Function.identity(), 0L);
        Path wrongSide = write("wrong-side.jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE,
                Function.identity(), 0L);

        assertEquals(S1GameplayAudioTimelineReport.Kind.METADATA_MISMATCH,
                S1GameplayAudioTimelineComparator.compare(reference, wrongSide).kind());

        Path partial = temp.resolve("partial.jsonl");
        Files.writeString(partial, Files.readString(reference).replaceFirst("\\n.*", "\\n"));
        S1GameplayAudioTimelineReport failure = S1GameplayAudioTimelineComparator.compare(partial,
                write("engine.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE, Function.identity(), 0L));
        assertEquals(S1GameplayAudioTimelineReport.Kind.CAPTURE_FAILURE, failure.kind());
        assertTrue(failure.detail().contains("reference"));
    }

    @Test
    void distinguishesOrderedRequestAndRoleArbitrationDifferences() throws Exception {
        assertKind(S1GameplayAudioTimelineReport.Kind.CAPTURE_FAILURE,
                frame -> frame.bk2Frame() == 900 ? empty(frame) : frame);
        assertKind(S1GameplayAudioTimelineReport.Kind.REQUEST_ORDINAL_MISMATCH,
                frame -> frame.bk2Frame() == 901 ? frameWith(frame, request(2, SFX, 0xA1, List.of(FM3), true,
                        owner(0xA0, 0), owner(0xA1, 2)), frame.owners()) : frame);
        assertKind(S1GameplayAudioTimelineReport.Kind.REQUEST_CLASS_MISMATCH,
                frame -> frame.bk2Frame() == 900 ? frameWith(frame, request(0,
                        S1GameplayAudioTimeline.SoundClass.SPECIAL_SFX, 0xD0, List.of(FM3), true,
                        music(), new S1GameplayAudioTimeline.OwnerRef(
                                S1GameplayAudioTimeline.OwnerClass.SPECIAL_SFX, 0xD0, 0)), frame.owners()) : frame);
        assertKind(S1GameplayAudioTimelineReport.Kind.REQUEST_ID_MISMATCH,
                frame -> frame.bk2Frame() == 900 ? frameWith(frame, request(0, SFX, 0xA1, List.of(FM3), true,
                        music(), owner(0xA1, 0)), frame.owners()) : frame);
        assertKind(S1GameplayAudioTimelineReport.Kind.REQUEST_ROLE_MISMATCH,
                frame -> frame.bk2Frame() == 900 ? frameWith(frame, request(0, SFX, 0xA0, List.of(FM4), true,
                        music(), owner(0xA0, 0)), frame.owners()) : frame);
        assertKind(S1GameplayAudioTimelineReport.Kind.CAPTURE_FAILURE,
                frame -> frame.bk2Frame() == 900 ? frameWith(frame, request(0, SFX, 0xA0, List.of(FM3), false,
                        music(), music()), frame.owners()) : frame);
        assertKind(S1GameplayAudioTimelineReport.Kind.CAPTURE_FAILURE,
                frame -> frame.bk2Frame() == 900 ? frameWith(frame, request(0, SFX, 0xA0, List.of(FM3), true,
                        none(), owner(0xA0, 0)), frame.owners()) : frame);
    }

    @Test
    void distinguishesPartialOwnershipFinalOwnerAndMusicRestoration() throws Exception {
        Path reference = write("reference.jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE,
                Function.identity(), 0L);
        Path partial = write("partial.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE, frame -> {
            if (frame.bk2Frame() != 900) {
                return frame;
            }
            S1GameplayAudioTimeline.Request request = new S1GameplayAudioTimeline.Request(0, SFX, 0xA0,
                    List.of(FM3, FM4), List.of(
                            new S1GameplayAudioTimeline.RoleArbitration(FM3, true, music(), owner(0xA0, 0)),
                            new S1GameplayAudioTimeline.RoleArbitration(FM4, false, music(), music())));
            return frameWith(frame, request, frame.owners());
        }, 0L);
        assertEquals(S1GameplayAudioTimelineReport.Kind.REQUEST_ROLE_MISMATCH,
                S1GameplayAudioTimelineComparator.compare(reference, partial).kind());

        Path unrecovered = write("unrecovered.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE, frame ->
                frame.bk2Frame() == 902 ? frameWith(frame, List.of(), owners(owner(0xA1, 1))) : frame, 0L);
        assertEquals(S1GameplayAudioTimelineReport.Kind.RESTORATION_MISMATCH,
                S1GameplayAudioTimelineComparator.compare(reference, unrecovered).kind());
    }

    @Test
    void ignoresDiagnosticsButRetainsOnlyEightRecordsEitherSideOfFirstMismatch() throws Exception {
        Path reference = write("reference.jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE,
                Function.identity(), 0L);
        Path diagnostics = write("diagnostics.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE,
                Function.identity(), 300L);
        assertTrue(S1GameplayAudioTimelineComparator.compare(reference, diagnostics).matches());

        Path changed = write("changed.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE, frame ->
                frame.bk2Frame() == 1000 ? frameWith(frame, request(2, SFX, 0xA1, List.of(FM3), true,
                        music(), owner(0xA1, 2)), frame.owners()) : frame, 0L);
        S1GameplayAudioTimelineReport report = S1GameplayAudioTimelineComparator.compare(reference, changed);
        assertEquals(S1GameplayAudioTimelineReport.Kind.REQUEST_EXTRA, report.kind());
        assertEquals(17, report.referenceContext().size());
        assertEquals(17, report.openGgfContext().size());
        assertTrue(report.referenceContext().getFirst().contains("992"));
        assertTrue(report.referenceContext().getLast().contains("1008"));
    }

    @Test
    void rejectsOtherwiseMatchingStreamsWithoutBothRequiredContentionClasses() throws Exception {
        Function<S1GameplayAudioTimeline.Frame, S1GameplayAudioTimeline.Frame> removeContention = frame ->
                frame.bk2Frame() == 900 || frame.bk2Frame() == 901 ? empty(frame) : frame;
        Path reference = write("reference-without-contention.jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE,
                removeContention, 0L);
        Path engine = write("engine-without-contention.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE,
                removeContention, 0L);

        S1GameplayAudioTimelineReport report = S1GameplayAudioTimelineComparator.compare(reference, engine);
        assertEquals(S1GameplayAudioTimelineReport.Kind.CAPTURE_FAILURE, report.kind());
        assertTrue(report.detail().contains("contention"));
    }

    @Test
    void validatesContentionEvidenceBeforeClassifyingAParityMismatch() throws Exception {
        Function<S1GameplayAudioTimeline.Frame, S1GameplayAudioTimeline.Frame> removeContention = frame ->
                frame.bk2Frame() == 900 || frame.bk2Frame() == 901 ? empty(frame) : frame;
        Path reference = write("reference-unproven.jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE,
                removeContention, 0L);
        Path engine = write("engine-unproven.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE, frame ->
                frame.bk2Frame() == 1000 ? frameWith(frame, request(0, SFX, 0xA2, List.of(FM3), true,
                        music(), owner(0xA2, 0)), frame.owners()) : removeContention.apply(frame), 0L);

        assertEquals(S1GameplayAudioTimelineReport.Kind.CAPTURE_FAILURE,
                S1GameplayAudioTimelineComparator.compare(reference, engine).kind());
    }

    @Test
    void rejectsClassOnlyMusicRestoreAndSelfDisplacingSfxAsContentionEvidence() throws Exception {
        Function<S1GameplayAudioTimeline.Frame, S1GameplayAudioTimeline.Frame> falsePositive = frame -> {
            if (frame.bk2Frame() == 901) {
                return frameWith(frame, request(1, SFX, 0xA1, List.of(FM3), true, owner(0xA1, 1), owner(0xA1, 1)),
                        frame.owners());
            }
            if (frame.bk2Frame() == 902) {
                return frameWith(frame, List.of(), owners(new S1GameplayAudioTimeline.OwnerRef(MUSIC, 0x82, 0)));
            }
            return frame;
        };
        Path reference = write("reference-false-positive.jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE,
                falsePositive, 0L);
        Path engine = write("engine-false-positive.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE,
                falsePositive, 0L);

        assertEquals(S1GameplayAudioTimelineReport.Kind.CAPTURE_FAILURE,
                S1GameplayAudioTimelineComparator.compare(reference, engine).kind());
    }

    @Test
    void reportsArbitrationRoleOrderBeforeComparingDecisionValues() throws Exception {
        Path reference = write("reference-roles.jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE, frame -> {
            if (frame.bk2Frame() != 900) {
                return frame;
            }
            return frameWith(frame, twoRoleRequest(List.of(FM3, FM4)), frame.owners());
        }, 0L);
        Path engine = write("engine-roles.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE, frame -> {
            if (frame.bk2Frame() != 900) {
                return frame;
            }
            return frameWith(frame, twoRoleRequest(List.of(FM4, FM3)), frame.owners());
        }, 0L);

        assertEquals(S1GameplayAudioTimelineReport.Kind.ROLE_ORDER_MISMATCH,
                S1GameplayAudioTimelineComparator.compare(reference, engine).kind());
    }

    @Test
    void rejectsSourceReplacementAfterCompleteValidation() throws Exception {
        Path reference = write("reference.jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE,
                Function.identity(), 0L);
        Path engine = write("engine.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE, Function.identity(), 0L);
        Path replacement = write("replacement.jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE, frame ->
                frame.bk2Frame() == 1000 ? frameWith(frame, request(2, SFX, 0xA1, List.of(FM3), true,
                        music(), owner(0xA1, 2)), frame.owners()) : frame, 0L);

        S1GameplayAudioTimelineReport report = S1GameplayAudioTimelineComparator.compare(reference, engine,
                () -> {
                    try {
                        Files.move(replacement, engine, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                });
        assertEquals(S1GameplayAudioTimelineReport.Kind.CAPTURE_FAILURE, report.kind());
        assertTrue(report.detail().contains("OpenGGF"));
    }

    private void assertKind(S1GameplayAudioTimelineReport.Kind expected,
            Function<S1GameplayAudioTimeline.Frame, S1GameplayAudioTimeline.Frame> mutate) throws Exception {
        String name = expected + "-" + java.util.UUID.randomUUID();
        Path reference = write("reference-" + name + ".jsonl", S1GameplayAudioTimeline.REFERENCE_CAPTURE,
                Function.identity(), 0L);
        Path engine = write("engine-" + name + ".jsonl", S1GameplayAudioTimeline.OPENGGF_CAPTURE, mutate, 0L);
        assertEquals(expected, S1GameplayAudioTimelineComparator.compare(reference, engine).kind());
    }

    private Path write(String name, String capture,
            Function<S1GameplayAudioTimeline.Frame, S1GameplayAudioTimeline.Frame> mutate, long tickOffset) {
        Path path = temp.resolve(name);
        List<S1GameplayAudioTimeline.TimelineRecord> records = new ArrayList<>();
        records.add(new S1GameplayAudioTimeline.Baseline(860, 0x81, null, owners(music())));
        long requests = 0;
        for (int bk2Frame = 860; bk2Frame < 4975; bk2Frame++) {
            List<S1GameplayAudioTimeline.Request> frameRequests = switch (bk2Frame) {
                case 900 -> List.of(request(0, SFX, 0xA0, List.of(FM3), true, music(), owner(0xA0, 0)));
                case 901 -> List.of(request(1, SFX, 0xA1, List.of(FM3), true, owner(0xA0, 0), owner(0xA1, 1)));
                default -> List.of();
            };
            S1GameplayAudioTimeline.OwnerVector frameOwners = switch (bk2Frame) {
                case 900 -> owners(owner(0xA0, 0));
                case 901 -> owners(owner(0xA1, 1));
                default -> owners(music());
            };
            S1GameplayAudioTimeline.Frame frame = mutate.apply(new S1GameplayAudioTimeline.Frame(bk2Frame,
                    tickOffset + (long) bk2Frame - 859, frameRequests, frameOwners));
            requests += frame.requests().size();
            records.add(frame);
        }
        records.add(new S1GameplayAudioTimeline.Terminal(4115, requests, 4115));
        S1GameplayAudioTimelineJsonl.writeNew(path, metadata(capture), records.iterator());
        return path;
    }

    private S1GameplayAudioTimeline.Frame empty(S1GameplayAudioTimeline.Frame frame) {
        return frameWith(frame, List.of(), owners(music()));
    }

    private S1GameplayAudioTimeline.Frame frameWith(S1GameplayAudioTimeline.Frame frame,
            S1GameplayAudioTimeline.Request request, S1GameplayAudioTimeline.OwnerVector owners) {
        return frameWith(frame, List.of(request), owners);
    }

    private S1GameplayAudioTimeline.Frame frameWith(S1GameplayAudioTimeline.Frame frame,
            List<S1GameplayAudioTimeline.Request> requests, S1GameplayAudioTimeline.OwnerVector owners) {
        return new S1GameplayAudioTimeline.Frame(frame.bk2Frame(), frame.diagnosticTick(), requests, owners);
    }

    private S1GameplayAudioTimeline.Request request(long ordinal, S1GameplayAudioTimeline.SoundClass soundClass,
            int soundId, List<S1GameplayAudioTimeline.HardwareRole> roles, boolean acquired,
            S1GameplayAudioTimeline.OwnerRef displaced, S1GameplayAudioTimeline.OwnerRef finalOwner) {
        return new S1GameplayAudioTimeline.Request(ordinal, soundClass, soundId, roles,
                roles.stream().map(role -> new S1GameplayAudioTimeline.RoleArbitration(role, acquired,
                        displaced, finalOwner)).toList());
    }

    private S1GameplayAudioTimeline.Request twoRoleRequest(List<S1GameplayAudioTimeline.HardwareRole> order) {
        return new S1GameplayAudioTimeline.Request(0, SFX, 0xA0, List.of(FM3, FM4), order.stream()
                .map(role -> new S1GameplayAudioTimeline.RoleArbitration(role, true, music(), owner(0xA0, 0))).toList());
    }

    private S1GameplayAudioTimeline.Metadata metadata(String capture) {
        return new S1GameplayAudioTimeline.Metadata(S1GameplayAudioTimeline.SCHEMA, capture,
                S1GameplayAudioTimeline.S1_REV01_SHA1, S1GameplayAudioTimeline.S1_REV01_CRC32,
                S1GameplayAudioTimeline.BK2_SHA256,
                S1GameplayAudioTimeline.REFERENCE_CAPTURE.equals(capture)
                        ? S1GameplayAudioTimeline.REFERENCE_PRODUCER : S1GameplayAudioTimeline.OPENGGF_PRODUCER,
                860, 4975, 4115);
    }

    private S1GameplayAudioTimeline.OwnerVector owners(S1GameplayAudioTimeline.OwnerRef fm3) {
        return new S1GameplayAudioTimeline.OwnerVector(fm3, music(), music(), none(), none(), none());
    }

    private S1GameplayAudioTimeline.OwnerRef music() {
        return new S1GameplayAudioTimeline.OwnerRef(MUSIC, 0x81, 0);
    }

    private S1GameplayAudioTimeline.OwnerRef none() {
        return new S1GameplayAudioTimeline.OwnerRef(NONE, 0, -1);
    }

    private S1GameplayAudioTimeline.OwnerRef owner(int id, long ordinal) {
        return new S1GameplayAudioTimeline.OwnerRef(NORMAL_SFX, id, ordinal);
    }
}
