using System;
using System.IO;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Routes <c>Console</c> writes to the buffer belonging to the test
    /// running on the calling thread, so a parallel run can emit each
    /// test's output in one uninterrupted block instead of interleaving
    /// several tests a line at a time.
    ///
    /// This is what keeps the output machine-readable under <c>--jobs</c>:
    /// the <c>PASS </c>/<c>FAIL </c>/<c>SKIP </c> lines and the incidental
    /// output around them stay in the same order, and in the same stream,
    /// as they appear in a sequential run.
    ///
    /// Writes from a thread with no test bound — a background thread a
    /// test started, or anything running outside the phase — fall through
    /// to the real console rather than being swallowed.
    /// </summary>
    internal sealed class TestConsoleRouter : TextWriter
    {
        [ThreadStatic]
        private static StringWriter currentOut;

        [ThreadStatic]
        private static StringWriter currentError;

        private readonly TextWriter fallback;
        private readonly bool isError;

        internal TestConsoleRouter(TextWriter fallback, bool isError)
        {
            this.fallback = fallback;
            this.isError = isError;
        }

        public override Encoding Encoding
        {
            get { return fallback.Encoding; }
        }

        /// <summary>
        /// Binds the calling thread's console output to the given
        /// buffers for the duration of one test.
        /// </summary>
        internal static void Begin(StringWriter output, StringWriter error)
        {
            currentOut = output;
            currentError = error;
        }

        /// <summary>
        /// Unbinds the calling thread. Subsequent writes fall through to
        /// the real console.
        /// </summary>
        internal static void End()
        {
            currentOut = null;
            currentError = null;
        }

        public override void Write(char value)
        {
            Target.Write(value);
        }

        public override void Write(string value)
        {
            Target.Write(value);
        }

        public override void Write(char[] buffer, int index, int count)
        {
            Target.Write(buffer, index, count);
        }

        public override void WriteLine()
        {
            Target.WriteLine();
        }

        public override void WriteLine(string value)
        {
            Target.WriteLine(value);
        }

        public override void Flush()
        {
            Target.Flush();
        }

        private TextWriter Target
        {
            get
            {
                StringWriter bound = isError ? currentError : currentOut;
                return bound != null ? (TextWriter)bound : fallback;
            }
        }
    }
}
