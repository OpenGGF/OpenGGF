using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// One finished run segment with its output file contents, materialised
    /// in memory for assertions. Production never does this — the S1/S2 run
    /// runners stream their segments straight into staged files — so this
    /// lives on the test side, where holding a synthetic segment's few
    /// hundred bytes is free.
    /// </summary>
    internal sealed class RunSegmentOutput
    {
        internal RunSegmentOutput(
            RunManifestSegment manifestEntry,
            string physicsCsv,
            string auxStateJsonl,
            string metadataJson)
        {
            ManifestEntry = manifestEntry;
            PhysicsCsv = physicsCsv;
            AuxStateJsonl = auxStateJsonl;
            MetadataJson = metadataJson;
        }

        internal RunManifestSegment ManifestEntry { get; private set; }

        internal string DirToken
        {
            get { return ManifestEntry.Dir; }
        }

        internal string PhysicsCsv { get; private set; }
        internal string AuxStateJsonl { get; private set; }
        internal string MetadataJson { get; private set; }
    }

    /// <summary>
    /// An <see cref="IRunSegmentSink"/> that keeps every segment's bytes,
    /// so the runner tests can assert on the exact stream contents the
    /// production sink would have written to disk.
    ///
    /// It also pins the sink protocol the production
    /// <c>StagedRunSegmentSink</c> depends on: exactly one BeginSegment per
    /// EndSegment, never nested, and the dir token the runner allocated is
    /// the one the finalized manifest entry carries.
    /// </summary>
    internal sealed class RunSegmentCollector : IRunSegmentSink
    {
        private readonly List<RunSegmentOutput> segments =
            new List<RunSegmentOutput>();

        private StringWriter physics;
        private StringWriter aux;
        private string openDirToken;

        internal IList<RunSegmentOutput> Segments
        {
            get { return segments; }
        }

        public RunSegmentStreams BeginSegment(string dirToken)
        {
            if (string.IsNullOrEmpty(dirToken))
            {
                throw new ArgumentException(
                    "A segment directory token is required.",
                    "dirToken");
            }
            if (openDirToken != null)
            {
                throw new InvalidOperationException(
                    "Segment " + openDirToken + " is still open.");
            }
            openDirToken = dirToken;
            physics = new StringWriter();
            aux = new StringWriter();
            return new RunSegmentStreams(physics, aux);
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
            if (openDirToken == null)
            {
                throw new InvalidOperationException(
                    "No segment is open for " + entry.Dir + ".");
            }
            if (entry.Dir != openDirToken)
            {
                throw new InvalidOperationException(
                    "Segment " + openDirToken + " finalized as "
                    + entry.Dir + ".");
            }
            segments.Add(new RunSegmentOutput(
                entry,
                physics.ToString(),
                aux.ToString(),
                metadataJson));
            physics = null;
            aux = null;
            openDirToken = null;
        }
    }

    /// <summary>
    /// An S2 run capture's result with the collected segment contents
    /// folded back in, so the S2 run-mode cases can assert on manifest
    /// fields and file bytes through one object.
    /// </summary>
    internal sealed class CollectedRunCapture
    {
        internal CollectedRunCapture(
            IList<RunSegmentOutput> segments,
            IList<RunManifestTransition> transitions,
            IList<DynamicArtGapTransition> dynamicArtGapTransitions,
            string runManifestJson)
        {
            Segments = segments;
            Transitions = transitions;
            DynamicArtGapTransitions = dynamicArtGapTransitions;
            RunManifestJson = runManifestJson;
        }

        internal IList<RunSegmentOutput> Segments { get; private set; }
        internal IList<RunManifestTransition> Transitions
        {
            get; private set;
        }

        internal IList<DynamicArtGapTransition> DynamicArtGapTransitions
        {
            get;
            private set;
        }

        internal string RunManifestJson { get; private set; }

        /// <summary>
        /// Runs an S2 run-mode capture against a collecting sink. The
        /// argument list mirrors
        /// <see cref="S2RunCaptureRunner.Capture"/> minus the sink.
        /// </summary>
        internal static CollectedRunCapture CaptureS2(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            string sourceBk2,
            string recordingDate,
            int effectiveMovieLength,
            byte[] dynamicArtRom = null)
        {
            var collector = new RunSegmentCollector();
            S2RunCaptureResult result = dynamicArtRom == null
                ? S2RunCaptureRunner.CaptureScratchLegacy(
                    movie,
                    host,
                    runId,
                    sourceBk2,
                    recordingDate,
                    effectiveMovieLength,
                    collector)
                : S2RunCaptureRunner.Capture(
                    movie,
                    host,
                    runId,
                    sourceBk2,
                    recordingDate,
                    effectiveMovieLength,
                    collector,
                    dynamicArtRom);
            AssertEx.Equal(result.Segments.Count, collector.Segments.Count);
            return new CollectedRunCapture(
                collector.Segments,
                result.Transitions,
                result.DynamicArtGapTransitions,
                result.RunManifestJson);
        }
    }
}
