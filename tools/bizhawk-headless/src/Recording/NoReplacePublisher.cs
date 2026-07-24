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
        private const string SmokeFileName = "smoke.csv";

        private readonly ILinkOperation linkOperation;
        private readonly Action<string> deleteFile;

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
            Action<string> deleteFile)
        {
            if (linkOperation == null)
            {
                throw new ArgumentNullException("linkOperation");
            }
            if (deleteFile == null)
            {
                throw new ArgumentNullException("deleteFile");
            }
            this.linkOperation = linkOperation;
            this.deleteFile = deleteFile;
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
            if (write == null)
            {
                throw new ArgumentNullException("write");
            }
            return StageAll(
                outputDirectory,
                new[] { SmokeFileName },
                writers => write(writers[0])).DetachSingle();
        }

        /// <summary>
        /// Stages one temporary file per name in
        /// <paramref name="fileNames"/> (all writers open at once, in the
        /// same order), then returns a set whose Publish() links every file
        /// into place with the same no-replace link(2) semantics as the
        /// single-file path. Names may carry relative subdirectory paths
        /// (e.g. "seg1_ehz1/physics.csv"); each parent directory is created
        /// at staging time and every temporary lives next to its final so
        /// link(2) never crosses a filesystem boundary. On any staging
        /// failure all temporaries are removed and no final path is
        /// touched; on a partial multi-file publication failure the
        /// already-linked finals are revoked (no partial finals).
        /// </summary>
        internal StagedPublicationSet StageAll(
            string outputDirectory,
            string[] fileNames,
            Action<TextWriter[]> write)
        {
            if (string.IsNullOrEmpty(outputDirectory))
            {
                throw new ArgumentException(
                    "An output directory is required.",
                    "outputDirectory");
            }
            if (fileNames == null || fileNames.Length == 0)
            {
                throw new ArgumentException(
                    "At least one output file name is required.",
                    "fileNames");
            }
            if (write == null)
            {
                throw new ArgumentNullException("write");
            }

            string fullOutputDirectory =
                Path.GetFullPath(outputDirectory);
            Directory.CreateDirectory(fullOutputDirectory);
            var temporaryPaths = new string[fileNames.Length];
            var staged = new StagedPublication[fileNames.Length];
            for (var index = 0; index < fileNames.Length; index++)
            {
                string finalPath = Path.Combine(
                    fullOutputDirectory,
                    fileNames[index]);
                string finalDirectory = Path.GetDirectoryName(finalPath);
                Directory.CreateDirectory(finalDirectory);
                temporaryPaths[index] = Path.Combine(
                    finalDirectory,
                    CreateTemporaryName(Path.GetFileName(fileNames[index])));
                staged[index] = new StagedPublication(
                    temporaryPaths[index],
                    finalPath,
                    linkOperation,
                    deleteFile);
            }

            try
            {
                WriteTemporaryFiles(temporaryPaths, write);
                return new StagedPublicationSet(staged);
            }
            catch
            {
                foreach (string temporaryPath in temporaryPaths)
                {
                    try
                    {
                        deleteFile(temporaryPath);
                    }
                    catch (Exception)
                    {
                        // Preserve the staging failure. A leftover temporary
                        // file is safer than obscuring why capture failed.
                    }
                }
                throw;
            }
        }

        private static void WriteTemporaryFiles(
            string[] temporaryPaths,
            Action<TextWriter[]> write)
        {
            var streams = new FileStream[temporaryPaths.Length];
            var writers = new StreamWriter[temporaryPaths.Length];
            try
            {
                for (var index = 0; index < temporaryPaths.Length; index++)
                {
                    streams[index] = new FileStream(
                        temporaryPaths[index],
                        FileMode.CreateNew,
                        FileAccess.Write,
                        FileShare.None);
                    writers[index] = new StreamWriter(
                        streams[index],
                        new UTF8Encoding(false),
                        1024,
                        true);
                    writers[index].NewLine = "\n";
                }

                write(writers);

                for (var index = 0; index < temporaryPaths.Length; index++)
                {
                    writers[index].Flush();
                    streams[index].Flush(true);
                }
            }
            finally
            {
                for (var index = 0; index < temporaryPaths.Length; index++)
                {
                    TryDispose(writers[index]);
                    TryDispose(streams[index]);
                }
            }
        }

        private static void TryDispose(IDisposable disposable)
        {
            if (disposable == null)
            {
                return;
            }
            try
            {
                disposable.Dispose();
            }
            catch (Exception)
            {
                // Content durability is established by the explicit flushes
                // on the success path; a disposal failure must not mask the
                // original staging exception on the failure path.
            }
        }

        internal sealed class StagedPublication : IDisposable
        {
            private readonly string temporaryPath;
            private readonly string finalPath;
            private readonly ILinkOperation linkOperation;
            private readonly Action<string> deleteFile;
            private bool finished;

            internal StagedPublication(
                string temporaryPath,
                string finalPath,
                ILinkOperation linkOperation,
                Action<string> deleteFile)
            {
                this.temporaryPath = temporaryPath;
                this.finalPath = finalPath;
                this.linkOperation = linkOperation;
                this.deleteFile = deleteFile;
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

            internal void RevokeFinal()
            {
                try
                {
                    deleteFile(finalPath);
                }
                catch (Exception)
                {
                    // Rollback is best-effort; the original multi-file
                    // publication failure is what gets reported.
                }
            }

            private void TryDeleteTemporary()
            {
                try
                {
                    deleteFile(temporaryPath);
                }
                catch (Exception)
                {
                    // Publication is the final commit point. Cleanup cannot
                    // turn a committed result into a reported failure.
                }
            }
        }

        internal sealed class StagedPublicationSet : IDisposable
        {
            private readonly StagedPublication[] staged;
            private bool finished;

            internal StagedPublicationSet(StagedPublication[] staged)
            {
                this.staged = staged;
            }

            internal StagedPublication DetachSingle()
            {
                if (staged.Length != 1)
                {
                    throw new InvalidOperationException(
                        "DetachSingle requires exactly one staged file.");
                }
                finished = true;
                return staged[0];
            }

            public void Publish()
            {
                if (finished)
                {
                    throw new InvalidOperationException(
                        "The staged outputs are already finalized.");
                }
                finished = true;

                var publishedCount = 0;
                try
                {
                    while (publishedCount < staged.Length)
                    {
                        staged[publishedCount].Publish();
                        publishedCount++;
                    }
                }
                catch
                {
                    // No partial finals: revoke the files that already
                    // linked and discard the unpublished temporaries so a
                    // failed multi-file publication leaves behind only a
                    // competing writer's own files.
                    for (var index = 0; index < staged.Length; index++)
                    {
                        if (index < publishedCount)
                        {
                            staged[index].RevokeFinal();
                        }
                        else
                        {
                            staged[index].Dispose();
                        }
                    }
                    throw;
                }
            }

            public void Dispose()
            {
                if (finished)
                {
                    return;
                }
                finished = true;
                foreach (StagedPublication file in staged)
                {
                    file.Dispose();
                }
            }
        }

        private static string CreateTemporaryName(string fileName)
        {
            var nonce = new byte[16];
            using (RandomNumberGenerator random =
                RandomNumberGenerator.Create())
            {
                random.GetBytes(nonce);
            }
            return fileName
                + ".tmp."
                + Process.GetCurrentProcess().Id
                + "."
                + BitConverter.ToString(nonce).Replace("-", "");
        }
    }
}
