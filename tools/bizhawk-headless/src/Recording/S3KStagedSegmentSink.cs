using System;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Production sink for
    /// <see cref="S3KCompleteRunCaptureRunner"/>: the shared
    /// <see cref="StagedRunSegmentSink"/> behind the S3K arm object, so the
    /// staging, streaming and finalize-order guarantees are literally the
    /// same code the S1/S2 run-mode captures use — physics.csv and
    /// aux_state.jsonl streamed (a single complete-run segment reaches
    /// 266 MB of aux), metadata.json staged as a string at finalize, which
    /// is also the only moment its bytes are known (trace_frame_count,
    /// recording date and Player_mode are finalize-time samples).
    ///
    /// Line endings are the one genuine S3K delta: the content is written
    /// verbatim, because the S3K complete-run recorder publishes LF in BOTH
    /// plain and run mode (docs/s3k-run-publication.md §6). The S1/S2
    /// run-mode CRLF expansion must never be applied here — it would
    /// corrupt the (C) gate, itself a run-mode capture with LF output.
    /// </summary>
    internal sealed class S3KStagedSegmentSink
        : IS3KCompleteRunSegmentSink, IDisposable
    {
        private readonly StagedRunSegmentSink inner;

        internal S3KStagedSegmentSink(
            NoReplacePublisher.IncrementalStagingSession session)
        {
            // false = no line-ending rewrite, which is the whole S3K
            // delta from the S1/S2 run-mode publication.
            inner = new StagedRunSegmentSink(session, false);
        }

        public S3KSegmentStreams BeginSegment(S3KSegmentArm arm)
        {
            if (arm == null)
            {
                throw new ArgumentNullException("arm");
            }
            RunSegmentStreams streams = inner.BeginSegment(arm.DirToken);
            return new S3KSegmentStreams(
                streams.PhysicsCsv, streams.AuxStateJsonl);
        }

        public void EndSegment(RunManifestSegment entry, string metadataJson)
        {
            inner.EndSegment(entry, metadataJson);
        }

        /// <summary>
        /// Discards a half-written segment (a capture that threw
        /// mid-segment). The session itself owns everything already
        /// completed and revokes it on its own Dispose.
        /// </summary>
        public void Dispose()
        {
            inner.Dispose();
        }
    }
}
