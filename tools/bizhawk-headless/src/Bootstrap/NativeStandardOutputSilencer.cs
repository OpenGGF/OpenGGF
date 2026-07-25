using System;
using System.IO;
using System.Runtime.InteropServices;

namespace BizHawk.Headless.Gpgx
{
    internal interface INativeDescriptorApi
    {
        int Duplicate(int descriptor);
        int OpenNull();
        int Redirect(int oldDescriptor, int newDescriptor);
        int Close(int descriptor);
        int FlushAll();
    }

    internal sealed class LibcNativeDescriptorApi : INativeDescriptorApi
    {
        private const int WriteOnly = 1;

        public static readonly LibcNativeDescriptorApi Instance =
            new LibcNativeDescriptorApi();

        private LibcNativeDescriptorApi()
        {
        }

        public int Duplicate(int descriptor)
        {
            return Dup(descriptor);
        }

        public int OpenNull()
        {
            return Open("/dev/null", WriteOnly);
        }

        public int Redirect(int oldDescriptor, int newDescriptor)
        {
            return Dup2(oldDescriptor, newDescriptor);
        }

        public int Close(int descriptor)
        {
            return CloseDescriptor(descriptor);
        }

        public int FlushAll()
        {
            return FFlush(IntPtr.Zero);
        }

        [DllImport("libc", EntryPoint = "dup", SetLastError = true)]
        private static extern int Dup(int oldDescriptor);

        [DllImport("libc", EntryPoint = "dup2", SetLastError = true)]
        private static extern int Dup2(
            int oldDescriptor,
            int newDescriptor);

        [DllImport(
            "libc",
            EntryPoint = "open",
            CharSet = CharSet.Ansi,
            SetLastError = true)]
        private static extern int Open(string path, int flags);

        [DllImport("libc", EntryPoint = "close", SetLastError = true)]
        private static extern int CloseDescriptor(int descriptor);

        [DllImport("libc", EntryPoint = "fflush", SetLastError = true)]
        private static extern int FFlush(IntPtr stream);
    }

    internal sealed class NativeStandardOutputSilencer : IDisposable
    {
        private const int StandardOutput = 1;
        private const int StandardError = 2;

        private readonly INativeDescriptorApi api;
        private int savedStandardOutput = -1;
        private int savedStandardError = -1;
        private int nullDescriptor = -1;
        private bool disposed;

        public NativeStandardOutputSilencer()
            : this(LibcNativeDescriptorApi.Instance)
        {
        }

        internal NativeStandardOutputSilencer(
            INativeDescriptorApi api)
        {
            if (api == null)
            {
                throw new ArgumentNullException("api");
            }
            this.api = api;

            Console.Out.Flush();
            Console.Error.Flush();
            savedStandardOutput = api.Duplicate(StandardOutput);
            if (savedStandardOutput < 0)
            {
                throw new IOException(
                    "Unable to preserve standard output.");
            }

            savedStandardError = api.Duplicate(StandardError);
            if (savedStandardError < 0)
            {
                CloseOpenedDescriptors();
                throw new IOException(
                    "Unable to preserve standard error.");
            }

            nullDescriptor = api.OpenNull();
            if (nullDescriptor < 0)
            {
                CloseOpenedDescriptors();
                throw new IOException(
                    "Unable to open /dev/null for GPGX console output.");
            }

            if (api.Redirect(nullDescriptor, StandardOutput) < 0)
            {
                CloseOpenedDescriptors();
                throw new IOException(
                    "Unable to suppress GPGX standard output.");
            }

            if (api.Redirect(nullDescriptor, StandardError) < 0)
            {
                RestoreWithRetry(
                    savedStandardOutput,
                    StandardOutput);
                CloseOpenedDescriptors();
                throw new IOException(
                    "Unable to suppress GPGX standard error.");
            }
        }

        public void Dispose()
        {
            if (disposed)
            {
                return;
            }
            disposed = true;
            api.FlushAll();

            bool errorRestored = api.Redirect(
                savedStandardError,
                StandardError) >= 0;
            bool outputRestored = api.Redirect(
                savedStandardOutput,
                StandardOutput) >= 0;
            bool restoreFailed = !errorRestored || !outputRestored;

            if (!errorRestored)
            {
                errorRestored = RestoreWithRetry(
                    savedStandardError,
                    StandardError);
            }
            if (!outputRestored)
            {
                outputRestored = RestoreWithRetry(
                    savedStandardOutput,
                    StandardOutput);
            }
            if (!errorRestored && outputRestored)
            {
                api.Redirect(savedStandardOutput, StandardError);
            }

            CloseOpenedDescriptors();
            if (restoreFailed)
            {
                throw new IOException(
                    "Unable to restore console output cleanly.");
            }
        }

        private bool RestoreWithRetry(
            int savedDescriptor,
            int targetDescriptor)
        {
            return api.Redirect(savedDescriptor, targetDescriptor) >= 0
                || api.Redirect(savedDescriptor, targetDescriptor) >= 0;
        }

        private void CloseOpenedDescriptors()
        {
            CloseIfOpen(ref nullDescriptor);
            CloseIfOpen(ref savedStandardOutput);
            CloseIfOpen(ref savedStandardError);
        }

        private void CloseIfOpen(ref int descriptor)
        {
            if (descriptor < 0)
            {
                return;
            }
            api.Close(descriptor);
            descriptor = -1;
        }
    }
}
