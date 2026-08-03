using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Stages ending-demo directories as one no-replace transaction. The
    /// canonical fixture root is explicitly forbidden: candidates are only
    /// ever staged beneath a caller-owned scratch output root.
    /// </summary>
    internal sealed class S1CreditsDemoCollectionSink : IDisposable
    {
        private readonly NoReplacePublisher.IncrementalStagingSession session;
        private readonly HashSet<int> completed = new HashSet<int>();
        private NoReplacePublisher.StagedStream physics;
        private NoReplacePublisher.StagedStream aux;
        private S1CreditsDemoDefinition active;

        internal S1CreditsDemoCollectionSink(
            NoReplacePublisher.IncrementalStagingSession session)
        {
            if (session == null) throw new ArgumentNullException("session");
            this.session = session;
        }

        public TextWriter Begin(
            S1CreditsDemoDefinition demo, out TextWriter auxState)
        {
            if (demo == null) throw new ArgumentNullException("demo");
            if (active != null) throw new InvalidOperationException("A credits demo is already open.");
            if (completed.Contains(demo.Index)) throw new InvalidOperationException("Credits demo " + demo.Index + " was captured twice.");
            string directory = DirectoryName(demo);
            physics = session.OpenFile(directory + "/physics.csv");
            try { aux = session.OpenFile(directory + "/aux_state.jsonl"); }
            catch { physics.Dispose(); physics = null; throw; }
            active = demo;
            auxState = aux.Writer;
            return physics.Writer;
        }

        public void Complete(string metadataJson)
        {
            if (active == null) throw new InvalidOperationException("No credits demo is open.");
            if (metadataJson == null) throw new ArgumentNullException("metadataJson");
            string directory = DirectoryName(active);
            physics.Complete(); physics = null;
            aux.Complete(); aux = null;
            session.StageFile(directory + "/metadata.json", metadataJson);
            completed.Add(active.Index);
            active = null;
        }

        public bool IsComplete(int? requestedTarget)
        {
            return requestedTarget.HasValue
                ? completed.Contains(requestedTarget.Value)
                : completed.Count == 8;
        }

        public static string DirectoryName(S1CreditsDemoDefinition demo)
        {
            return demo.Index.ToString("D2") + "_" + demo.Slug;
        }

        public void Dispose()
        {
            if (physics != null) { physics.Dispose(); physics = null; }
            if (aux != null) { aux.Dispose(); aux = null; }
        }
    }
}
