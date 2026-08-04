using System;
using System.IO;
using System.Runtime.InteropServices;

namespace OpenGGF.BizHawk.Headless
{
    internal static class LinuxPathEntry
    {
        private const int ENoEnt = 2;
        private const int ENotDir = 20;

        public static bool Exists(string path)
        {
            if (File.Exists(path) || Directory.Exists(path))
            {
                return true;
            }

            var targetProbe = new byte[1];
            if (ReadLink(path, targetProbe, new UIntPtr(1)) >= 0)
            {
                return true;
            }

            int error = Marshal.GetLastWin32Error();
            return error != ENoEnt && error != ENotDir;
        }

        public static bool IsSymbolicLink(string path)
        {
            var targetProbe = new byte[1];
            return ReadLink(path, targetProbe, new UIntPtr(1)) >= 0;
        }

        /// <summary>
        /// Resolves symlinks in the deepest existing ancestor of a proposed
        /// output path. The caller may append not-yet-created children, but
        /// cannot escape through an alias in an existing parent directory.
        /// </summary>
        public static string ResolveExistingAncestor(string path)
        {
            string current = Path.GetFullPath(path);
            while (!Exists(current))
            {
                string parent = Path.GetDirectoryName(current);
                if (string.IsNullOrEmpty(parent) || parent == current)
                {
                    throw new IOException("No existing ancestor for " + path + ".");
                }
                current = parent;
            }
            IntPtr resolved = RealPath(current, IntPtr.Zero);
            if (resolved == IntPtr.Zero)
            {
                throw new IOException("realpath failed for " + current + ".");
            }
            try
            {
                return Marshal.PtrToStringAnsi(resolved);
            }
            finally
            {
                Free(resolved);
            }
        }

        /// <summary>
        /// Resolves every existing component and then appends the still
        /// absent suffix. This gives proposed outputs a stable identity for
        /// overlap checks instead of reducing sibling paths to their shared
        /// existing ancestor.
        /// </summary>
        public static string ResolveProposedPath(string path)
        {
            string current = Path.GetFullPath(path);
            var missing = new System.Collections.Generic.Stack<string>();
            while (!Exists(current))
            {
                missing.Push(Path.GetFileName(current));
                string parent = Path.GetDirectoryName(current);
                if (string.IsNullOrEmpty(parent) || parent == current)
                {
                    throw new IOException("No existing ancestor for " + path + ".");
                }
                current = parent;
            }
            string resolved = ResolveExistingAncestor(current);
            while (missing.Count != 0)
            {
                resolved = Path.Combine(resolved, missing.Pop());
            }
            return Path.GetFullPath(resolved);
        }

        [DllImport(
            "libc",
            EntryPoint = "readlink",
            CharSet = CharSet.Ansi,
            SetLastError = true)]
        private static extern long ReadLink(
            string path,
            byte[] buffer,
            UIntPtr bufferSize);

        [DllImport("libc", EntryPoint = "realpath", SetLastError = true)]
        private static extern IntPtr RealPath(string path, IntPtr resolvedPath);

        [DllImport("libc", EntryPoint = "free")]
        private static extern void Free(IntPtr pointer);
    }
}
