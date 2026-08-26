using System;
using System.IO;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// A writer that does not open its target until the first character is
    /// written, and reports whether that ever happened.
    ///
    /// The run-level interstitial hardware-timing stream needs this: the
    /// runner has to hand a writer to the observer on every unrepresented
    /// frame, but the overwhelming majority of those frames emit nothing,
    /// and a capture that emitted nothing must publish no file at all. An
    /// eagerly opened stream would add a 0-byte file to the inventory of
    /// every S3K run capture ever taken, which is a fixture change dressed
    /// up as a no-op.
    /// </summary>
    internal sealed class LazyOpenTextWriter : TextWriter
    {
        private readonly Func<TextWriter> factory;
        private TextWriter target;

        internal LazyOpenTextWriter(Func<TextWriter> factory)
        {
            if (factory == null)
            {
                throw new ArgumentNullException("factory");
            }
            this.factory = factory;
        }

        /// <summary>
        /// True once the target has been opened, i.e. once at least one
        /// character has been written.
        /// </summary>
        internal bool Opened
        {
            get { return target != null; }
        }

        public override Encoding Encoding
        {
            get { return Encoding.UTF8; }
        }

        public override void Write(char value)
        {
            Target().Write(value);
        }

        public override void Write(string value)
        {
            if (value == null)
            {
                return;
            }
            Target().Write(value);
        }

        public override void Flush()
        {
            if (target != null)
            {
                target.Flush();
            }
        }

        private TextWriter Target()
        {
            if (target == null)
            {
                target = factory();
                if (target == null)
                {
                    throw new InvalidOperationException(
                        "The lazy writer factory returned no writer.");
                }
            }
            return target;
        }
    }
}
