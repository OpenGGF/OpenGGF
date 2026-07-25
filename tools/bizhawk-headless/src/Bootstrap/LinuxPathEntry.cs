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

        [DllImport(
            "libc",
            EntryPoint = "readlink",
            CharSet = CharSet.Ansi,
            SetLastError = true)]
        private static extern long ReadLink(
            string path,
            byte[] buffer,
            UIntPtr bufferSize);
    }
}
