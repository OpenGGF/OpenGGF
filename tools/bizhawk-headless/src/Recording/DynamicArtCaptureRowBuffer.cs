using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Holds one complete stored row so callbacks observed while the capture
    /// decides whether that row is terminal can still be forwarded into its
    /// single dynamic-art heartbeat. The whole row is delayed, preserving
    /// monotonically ordered physics and aux streams.
    /// </summary>
    internal sealed class DynamicArtCaptureRowBuffer
    {
        private readonly TextWriter physics;
        private readonly TextWriter aux;
        private readonly string newline;
        private string pendingPhysics;
        private IList<string> pendingAux;
        private DynamicArtTransferEnvelope pendingDynamicArt;

        public DynamicArtCaptureRowBuffer(
            TextWriter physics,
            TextWriter aux,
            string newline)
        {
            if (physics == null) throw new ArgumentNullException("physics");
            if (aux == null) throw new ArgumentNullException("aux");
            if (newline == null) throw new ArgumentNullException("newline");
            this.physics = physics;
            this.aux = aux;
            this.newline = newline;
        }

        public bool HasPendingRow
        {
            get { return pendingDynamicArt != null; }
        }

        public void Queue(
            string physicsLine,
            IList<string> auxLines,
            DynamicArtTransferEnvelope dynamicArt)
        {
            if (physicsLine == null)
                throw new ArgumentNullException("physicsLine");
            if (auxLines == null) throw new ArgumentNullException("auxLines");
            if (dynamicArt == null) throw new ArgumentNullException("dynamicArt");
            FlushPending();
            pendingPhysics = physicsLine;
            pendingAux = new List<string>(auxLines);
            pendingDynamicArt = dynamicArt;
        }

        public void FlushTerminal(DynamicArtTransferEnvelope terminal)
        {
            if (terminal == null) throw new ArgumentNullException("terminal");
            if (pendingDynamicArt == null)
            {
                if (terminal.Edges.Count != 0
                    || terminal.OutstandingTransferIds.Count != 0)
                {
                    throw new InvalidOperationException(
                        "dynamic-art callbacks have no stored row for terminal forwarding");
                }
                return;
            }
            if (terminal.Frame != pendingDynamicArt.Frame)
            {
                throw new InvalidOperationException(
                    "dynamic-art terminal frame does not match the buffered row");
            }
            var edges = new List<DynamicArtTransferEdge>(
                pendingDynamicArt.Edges.Count + terminal.Edges.Count);
            edges.AddRange(pendingDynamicArt.Edges);
            edges.AddRange(terminal.Edges);
            pendingDynamicArt = new DynamicArtTransferEnvelope(
                pendingDynamicArt.Frame,
                edges,
                terminal.OutstandingTransferIds);
            FlushPending();
        }

        private void FlushPending()
        {
            if (pendingDynamicArt == null)
            {
                return;
            }
            WriteLine(physics, pendingPhysics);
            for (int index = 0; index < pendingAux.Count; index++)
            {
                WriteLine(aux, pendingAux[index]);
            }
            WriteLine(aux, pendingDynamicArt.Format());
            pendingPhysics = null;
            pendingAux = null;
            pendingDynamicArt = null;
        }

        private void WriteLine(TextWriter writer, string value)
        {
            writer.Write(value);
            writer.Write(newline);
        }
    }
}
