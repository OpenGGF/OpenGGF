using System;
using System.IO;
using System.Reflection;
using BizHawk.Common.PathExtensions;

namespace OpenGGF.BizHawk.Headless
{
    public sealed class BizHawkInstallation
    {
        private static readonly Version RequiredVersion = new Version(2, 11, 0, 0);

        private static readonly string[] ManagedAssemblies =
        {
            "BizHawk.Common.dll",
            "BizHawk.Emulation.Common.dll",
            "BizHawk.Emulation.Cores.dll",
            "BizHawk.Emulation.DiscSystem.dll",
            "BizHawk.BizInvoke.dll"
        };

        private static readonly string[] RequiredFiles =
        {
            "gpgx.wbx.zst",
            "libwaterboxhost.so",
            "BizHawk.Common.dll",
            "BizHawk.Emulation.Common.dll",
            "BizHawk.Emulation.Cores.dll",
            "BizHawk.Emulation.DiscSystem.dll",
            "BizHawk.BizInvoke.dll",
            "Newtonsoft.Json.dll"
        };

        private BizHawkInstallation(string dllDirectory, Version managedVersion)
        {
            DllDirectory = dllDirectory;
            ManagedVersion = managedVersion;
        }

        public string DllDirectory { get; private set; }
        public Version ManagedVersion { get; private set; }

        public static BizHawkInstallation Validate(string root)
        {
            if (string.IsNullOrEmpty(root))
            {
                throw new InvalidOperationException("BIZHAWK_HOME is not set.");
            }

            string fullRoot = Path.GetFullPath(root);
            string dllDirectory = Path.Combine(fullRoot, "dll");
            foreach (string fileName in RequiredFiles)
            {
                string path = Path.Combine(dllDirectory, fileName);
                if (!File.Exists(path))
                {
                    throw new InvalidOperationException(
                        "Required BizHawk file is missing: " + path);
                }
            }

            foreach (string fileName in ManagedAssemblies)
            {
                string path = Path.Combine(dllDirectory, fileName);
                Version actualVersion = AssemblyName.GetAssemblyName(path).Version;
                if (!RequiredVersion.Equals(actualVersion))
                {
                    throw new InvalidOperationException(
                        fileName + " has version " + actualVersion
                        + "; expected " + RequiredVersion + ".");
                }
            }

            string resolvedDllDirectory = Path.GetFullPath(PathUtils.DllDirectoryPath);
            string expectedDllDirectory = Path.GetFullPath(dllDirectory);
            if (!string.Equals(
                resolvedDllDirectory,
                expectedDllDirectory,
                StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    "BizHawk DLL directory resolved to " + resolvedDllDirectory
                    + "; expected " + expectedDllDirectory + ".");
            }

            return new BizHawkInstallation(dllDirectory, RequiredVersion);
        }
    }
}
