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
        private const string PhysicsFileName = "physics.csv";
        private const string AuxStateFileName = "aux_state.jsonl";
        private const string HardwareTimingFileName =
            "hardware_timing.jsonl";
        private const string MetadataFileName = "metadata.json";

        private readonly NoReplacePublisher.IncrementalStagingSession session;
        private NoReplacePublisher.StagedStream physics;
        private NoReplacePublisher.StagedStream aux;
        private NoReplacePublisher.StagedStream hardwareTiming;
        private string dirToken;

        internal S3KStagedSegmentSink(
            NoReplacePublisher.IncrementalStagingSession session)
        {
            if (session == null)
            {
                throw new ArgumentNullException("session");
            }
            this.session = session;
        }

        public S3KSegmentStreams BeginSegment(S3KSegmentArm arm)
        {
            if (arm == null)
            {
                throw new ArgumentNullException("arm");
            }
            if (physics != null || aux != null || hardwareTiming != null)
            {
                throw new InvalidOperationException(
                    "Segment " + dirToken + " is still open.");
            }
            dirToken = arm.DirToken;
            physics = session.OpenFile(
                dirToken + "/" + PhysicsFileName);
            try
            {
                aux = session.OpenFile(
                    dirToken + "/" + AuxStateFileName);
                hardwareTiming = session.OpenFile(
                    dirToken + "/" + HardwareTimingFileName);
            }
            catch
            {
                DisposeOpenStreams();
                throw;
            }
            return new S3KSegmentStreams(
                physics.Writer,
                aux.Writer,
                hardwareTiming.Writer);
        }

        public void EndSegment(RunManifestSegment entry, string metadataJson)
        {
            if (entry == null)
            {
                throw new ArgumentNullException("entry");
            }
            if (metadataJson == null)
            {
                throw new ArgumentNullException("metadataJson");
            }
            if (physics == null || aux == null || hardwareTiming == null)
            {
                throw new InvalidOperationException(
                    "No segment is open for " + entry.Dir + ".");
            }
            physics.Complete();
            physics = null;
            aux.Complete();
            aux = null;
            hardwareTiming.Complete();
            hardwareTiming = null;
            session.StageFile(
                entry.Dir + "/" + MetadataFileName,
                metadataJson);
            dirToken = null;
        }

        /// <summary>
        /// Discards a half-written segment (a capture that threw
        /// mid-segment). The session itself owns everything already
        /// completed and revokes it on its own Dispose.
        /// </summary>
        public void Dispose()
        {
            DisposeOpenStreams();
        }

        private void DisposeOpenStreams()
        {
            if (physics != null)
            {
                physics.Dispose();
                physics = null;
            }
            if (aux != null)
            {
                aux.Dispose();
                aux = null;
            }
            if (hardwareTiming != null)
            {
                hardwareTiming.Dispose();
                hardwareTiming = null;
            }
            dirToken = null;
        }
    }
}
