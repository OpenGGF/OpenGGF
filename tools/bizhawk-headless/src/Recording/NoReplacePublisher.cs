using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;

[assembly: InternalsVisibleTo("BizHawk.Headless.Gpgx.Tests")]

namespace OpenGGF.BizHawk.Headless
{
    internal interface ILinkOperation
    {
        void Create(string temporary, string finalPath);
    }

    internal sealed class LibcLinkOperation : ILinkOperation
    {
        private const int EExist = 17;

        public static readonly LibcLinkOperation Instance =
            new LibcLinkOperation();

        private LibcLinkOperation()
        {
        }

        public void Create(string temporary, string finalPath)
        {
            if (Link(temporary, finalPath) == 0)
            {
                return;
            }

            int error = Marshal.GetLastWin32Error();
            if (error == EExist)
            {
                throw new IOException(
                    "Final output already exists and will not be replaced: "
                    + finalPath);
            }
            throw new IOException(
                "Unable to publish " + finalPath
                + ": link(2) failed with errno " + error + ".");
        }

        [DllImport(
            "libc",
            EntryPoint = "link",
            CharSet = CharSet.Ansi,
            SetLastError = true)]
        private static extern int Link(string oldPath, string newPath);
    }

    public sealed class NoReplacePublisher
    {
        private readonly ILinkOperation linkOperation;
        private readonly Action<string> deleteTemporary;

        public NoReplacePublisher()
            : this(LibcLinkOperation.Instance, File.Delete)
        {
        }

        internal NoReplacePublisher(ILinkOperation linkOperation)
            : this(linkOperation, File.Delete)
        {
        }

        internal NoReplacePublisher(
            ILinkOperation linkOperation,
            Action<string> deleteTemporary)
        {
            if (linkOperation == null)
            {
                throw new ArgumentNullException("linkOperation");
            }
            if (deleteTemporary == null)
            {
                throw new ArgumentNullException("deleteTemporary");
            }
            this.linkOperation = linkOperation;
            this.deleteTemporary = deleteTemporary;
        }

        public void Publish(
            string outputDirectory,
            Action<TextWriter> write)
        {
            using (StagedPublication staged = Stage(
                outputDirectory,
                write))
            {
                staged.Publish();
            }
        }

        internal StagedPublication Stage(
            string outputDirectory,
            Action<TextWriter> write)
        {
            if (string.IsNullOrEmpty(outputDirectory))
            {
                throw new ArgumentException(
                    "An output directory is required.",
                    "outputDirectory");
            }
            if (write == null)
            {
                throw new ArgumentNullException("write");
            }

            string fullOutputDirectory =
                Path.GetFullPath(outputDirectory);
            Directory.CreateDirectory(fullOutputDirectory);
            string finalPath = Path.Combine(
                fullOutputDirectory,
                "smoke.csv");
            string temporaryPath = Path.Combine(
                fullOutputDirectory,
                CreateTemporaryName());

            try
            {
                using (var stream = new FileStream(
                    temporaryPath,
                    FileMode.CreateNew,
                    FileAccess.Write,
                    FileShare.None))
                using (var writer = new StreamWriter(
                    stream,
                    new UTF8Encoding(false),
                    1024,
                    true))
                {
                    writer.NewLine = "\n";
                    write(writer);
                    writer.Flush();
                    stream.Flush(true);
                }

                return new StagedPublication(
                    temporaryPath,
                    finalPath,
                    linkOperation,
                    deleteTemporary);
            }
            catch
            {
                try
                {
                    deleteTemporary(temporaryPath);
                }
                catch (Exception)
                {
                    // Preserve the staging failure. A leftover temporary file
                    // is safer than obscuring why capture did not complete.
                }
                throw;
            }
        }

        internal sealed class StagedPublication : IDisposable
        {
            private readonly string temporaryPath;
            private readonly string finalPath;
            private readonly ILinkOperation linkOperation;
            private readonly Action<string> deleteTemporary;
            private bool finished;

            internal StagedPublication(
                string temporaryPath,
                string finalPath,
                ILinkOperation linkOperation,
                Action<string> deleteTemporary)
            {
                this.temporaryPath = temporaryPath;
                this.finalPath = finalPath;
                this.linkOperation = linkOperation;
                this.deleteTemporary = deleteTemporary;
            }

            public void Publish()
            {
                if (finished)
                {
                    throw new InvalidOperationException(
                        "The staged output is already finalized.");
                }

                linkOperation.Create(temporaryPath, finalPath);
                finished = true;
                TryDeleteTemporary();
            }

            public void Dispose()
            {
                if (finished)
                {
                    return;
                }
                finished = true;
                TryDeleteTemporary();
            }

            private void TryDeleteTemporary()
            {
                try
                {
                    deleteTemporary(temporaryPath);
                }
                catch (Exception)
                {
                    // Publication is the final commit point. Cleanup cannot
                    // turn a committed result into a reported failure.
                }
            }
        }

        private static string CreateTemporaryName()
        {
            var nonce = new byte[16];
            using (RandomNumberGenerator random =
                RandomNumberGenerator.Create())
            {
                random.GetBytes(nonce);
            }
            return "smoke.csv.tmp."
                + Process.GetCurrentProcess().Id
                + "."
                + BitConverter.ToString(nonce).Replace("-", "");
        }
    }
}
