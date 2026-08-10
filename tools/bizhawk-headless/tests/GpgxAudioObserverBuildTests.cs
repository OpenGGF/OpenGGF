using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Security.Cryptography;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class GpgxAudioObserverBuildTests
    {
        private const string ObserverDirectory = "native/gpgx-audio-observer";

        internal static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverBuildTests publish a frozen native ABI recipe",
                PublishesFrozenNativeAbiRecipe));
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverBuildTests keep reflection as the sole managed adapter",
                KeepsReflectionAsSoleManagedAdapter));
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverBuildTests lock exact observer seams and provenance",
                LocksObserverSeamsAndProvenance));
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverBuildTests reproduce and install twice from locked inputs",
                ReproducesAndInstallsTwice,
                kind: TestKind.Gate,
                serial: true,
                estimatedSeconds: 180.0));
        }

        private static void PublishesFrozenNativeAbiRecipe()
        {
            string root = Path.Combine(
                EndToEndTests.ToolDirectory, ObserverDirectory);
            AssertEx.Equal(true, File.Exists(Path.Combine(
                root, "0001-buffer-z80-audio-events.patch")));
            AssertEx.Equal(true, File.Exists(Path.Combine(root, "build-core.sh")));
            AssertEx.Equal(true, File.Exists(Path.Combine(root, "install-core.sh")));
            AssertEx.Equal(true, File.Exists(Path.Combine(root, "README.md")));

            JObject artifact = JObject.Parse(File.ReadAllText(Path.Combine(
                root, "artifact-lock.json")));
            AssertEx.Equal(
                "openggf.gpgx-audio-observer-artifact-lock.v1",
                (string)artifact["schema"]);
            AssertEx.Equal(1, (int)artifact["abi"]["version"]);
            AssertEx.Equal(64, (int)artifact["abi"]["config_size"]);
            AssertEx.Equal(16, (int)artifact["abi"]["kind_size"]);
            AssertEx.Equal(32, (int)artifact["abi"]["hook_size"]);
            AssertEx.Equal(16, (int)artifact["abi"]["range_size"]);
            AssertEx.Equal(32, (int)artifact["abi"]["event_size"]);
            AssertEx.Equal(65536, (int)artifact["abi"]["capacity"]);
            AssertEx.Equal("little-endian", (string)artifact["abi"]["byte_order"]);
            AssertEx.Equal(41718744, (int)artifact["core"]["decompressed_size"]);
            AssertEx.Equal(409653, (int)artifact["core"]["compressed_size"]);
            AssertEx.Equal(32, (int)artifact["core"]["invis_alignment"]);
        }

        private static void KeepsReflectionAsSoleManagedAdapter()
        {
            string root = Path.Combine(
                EndToEndTests.ToolDirectory, ObserverDirectory);
            JObject artifact = JObject.Parse(File.ReadAllText(Path.Combine(
                root, "artifact-lock.json")));
            AssertEx.Equal("REFLECTION", (string)artifact["managed_adapter"]);
            AssertEx.Equal(false, (bool)artifact["patched_managed_dll_permitted"]);
            AssertEx.Equal(false, File.Exists(Path.Combine(
                root, "0002-first-class-managed-adapter.patch")));
        }

        private static void LocksObserverSeamsAndProvenance()
        {
            string root = Path.Combine(EndToEndTests.ToolDirectory, ObserverDirectory);
            JObject artifact = JObject.Parse(File.ReadAllText(Path.Combine(root, "artifact-lock.json")));
            JObject recipe = JObject.Parse(File.ReadAllText(Path.Combine(root, "task7-build-recipe.json")));
            string patchPath = Path.Combine(root, "0001-buffer-z80-audio-events.patch");
            string patch = File.ReadAllText(patchPath);
            AssertEx.Equal((string)artifact["native_patch"]["sha256"], Sha256(patchPath));
            AssertEx.Equal((string)artifact["build_recipe"]["sha256"], Sha256(Path.Combine(root, "task7-build-recipe.json")));
            AssertEx.Equal(Sha256(Path.Combine(root, "build-core.sh")), (string)recipe["versioned_inputs"]["build-core.sh"]);
            AssertEx.Equal(true, patch.Contains("gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80, PCD);"));
            AssertEx.Equal(true, patch.Contains("gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K, REG_PC);"));
            AssertEx.Equal(true, patch.Contains("GPGX_AUDIO_TRACE_INSTRUCTION(GPGX_AUDIO_TRACE_CPU_M68K, REG_PC)"));
            AssertEx.Equal(true, patch.Contains("static void (*fm_write_impl)"));
            AssertEx.Equal(true, patch.Contains("gpgx_audio_trace_fm_write(address, data)"));
            AssertEx.Equal(true, patch.Contains("gpgx_audio_trace_psg_write(data)"));
            AssertEx.Equal(true, patch.Contains("gpgx_audio_trace_reset_begin"));
            AssertEx.Equal(true, patch.Contains("ECL_INVISIBLE static struct gpgx_audio_trace_event"));
            AssertEx.Equal(true, patch.Contains("STATIC_ASSERT(event_size"));
        }

        private static void ReproducesAndInstallsTwice()
        {
            string source = Environment.GetEnvironmentVariable("OPENGGF_GPGX_OBSERVER_SOURCE");
            string toolchainA = Environment.GetEnvironmentVariable("OPENGGF_GPGX_OBSERVER_TOOLCHAIN_A");
            string toolchainB = Environment.GetEnvironmentVariable("OPENGGF_GPGX_OBSERVER_TOOLCHAIN_B");
            string stock = Environment.GetEnvironmentVariable("OPENGGF_GPGX_OBSERVER_STOCK");
            if (string.IsNullOrEmpty(source) || string.IsNullOrEmpty(toolchainA)
                || string.IsNullOrEmpty(toolchainB) || string.IsNullOrEmpty(stock))
            {
                throw new TestMain.SkipTestException(
                    "Set OPENGGF_GPGX_OBSERVER_SOURCE, _TOOLCHAIN_A, _TOOLCHAIN_B, and _STOCK for the real reproduction gate.");
            }
            string scratch = TestScratch.CreateRootPath("gpgx-observer-build");
            Directory.CreateDirectory(scratch);
            try
            {
                string root = Path.Combine(EndToEndTests.ToolDirectory, ObserverDirectory);
                string stockCore = Path.Combine(stock, "dll", "gpgx.wbx.zst");
                string stockBefore = Sha256(stockCore);
                string a = Path.Combine(scratch, "build-a");
                string b = Path.Combine(scratch, "build-b");
                Run("/usr/bin/bash", "-p", Path.Combine(root, "build-core.sh"), "--source", source,
                    "--toolchain", toolchainA, "--stock", stock, "--output", a);
                Run("/usr/bin/bash", "-p", Path.Combine(root, "build-core.sh"), "--source", source,
                    "--toolchain", toolchainB, "--stock", stock, "--output", b);
                foreach (string file in new[] { "gpgx.wbx", "gpgx.wbx.zst", "source-bundle.tar.zst", "identity.json" })
                {
                    AssertEx.Equal(Sha256(Path.Combine(a, file)), Sha256(Path.Combine(b, file)));
                }
                string install = Path.Combine(scratch, "install-a");
                string installB = Path.Combine(scratch, "install-b");
                Run("/usr/bin/bash", "-p", Path.Combine(root, "install-core.sh"), "--build", a,
                    "--stock", stock, "--output", install);
                Run("/usr/bin/bash", "-p", Path.Combine(root, "install-core.sh"), "--build", b,
                    "--stock", stock, "--output", installB);
                AssertEx.Equal(stockBefore, Sha256(stockCore));
                AssertEx.Equal(true, File.Exists(Path.Combine(install, "dll", "gpgx.wbx.zst")));
                AssertEx.Equal(true, File.Exists(Path.Combine(install, "gpgx-audio-observer-source", "source-bundle.tar.zst")));
                AssertEx.Equal(Sha256(Path.Combine(install, "dll", "gpgx.wbx.zst")),
                    Sha256(Path.Combine(installB, "dll", "gpgx.wbx.zst")));
                AssertEx.Equal(Sha256(Path.Combine(install, "gpgx-audio-observer-source", "identity.json")),
                    Sha256(Path.Combine(installB, "gpgx-audio-observer-source", "identity.json")));
                AssertEx.Equal(false, (File.GetAttributes(Path.Combine(install, "dll", "gpgx.wbx.zst"))
                    & FileAttributes.ReparsePoint) != 0);
                AssertEx.Equal(false, (File.GetAttributes(Path.Combine(install,
                    "gpgx-audio-observer-source", "source-bundle.tar.zst"))
                    & FileAttributes.ReparsePoint) != 0);
                AssertEx.Equal(false, RunCapture("/usr/bin/stat", "-c", "%d:%i", stockCore).Trim()
                    == RunCapture("/usr/bin/stat", "-c", "%d:%i", Path.Combine(install, "dll", "gpgx.wbx.zst")).Trim());
                foreach (string relative in new[] { "EmuHawk.exe", "dll/BizHawk.Emulation.Cores.dll",
                    "dll/BizHawk.Emulation.Common.dll", "dll/BizHawk.BizInvoke.dll",
                    "dll/BizHawk.Common.dll", "dll/libwaterboxhost.so" })
                {
                    string installed = Path.Combine(install, relative.Replace('/', Path.DirectorySeparatorChar));
                    string original = Path.Combine(stock, relative.Replace('/', Path.DirectorySeparatorChar));
                    AssertEx.Equal(false, (File.GetAttributes(installed) & FileAttributes.ReparsePoint) != 0);
                    AssertEx.Equal(false, RunCapture("/usr/bin/stat", "-c", "%d:%i", original).Trim()
                        == RunCapture("/usr/bin/stat", "-c", "%d:%i", installed).Trim());
                    AssertEx.Equal(Sha256(original), Sha256(installed));
                }
                string evidence = Path.Combine(install, "gpgx-audio-observer-source");
                foreach (string file in new[] {
                    "source-bundle.tar", "source-bundle.tar.zst", "source-bundle.paths",
                    "source-bundle.path-modes", "identity.json", "0001-buffer-z80-audio-events.patch",
                    "artifact-lock.json", "task7-build-recipe.json", "build-core.sh", "install-core.sh",
                    "native-selftest.log", "elf-proof.txt", "callgraph-proof.txt", "build.log",
                    "BizHawk-LICENSE", "GPGX-LICENSE.txt", "musl-COPYRIGHT", "zstd-LICENSE",
                    "GpgxAudioObserverAdapter.cs", "GpgxHost.cs" })
                {
                    AssertEx.Equal(true, File.Exists(Path.Combine(evidence, file)));
                }
                AssertEx.Equal(true, Directory.Exists(Path.Combine(evidence, "llvm-debian-notices")));
                string gpgxLicense = File.ReadAllText(Path.Combine(evidence, "GPGX-LICENSE.txt"));
                AssertEx.Equal(true, gpgxLicense.Contains(
                    "Redistributions may not be sold, nor may they be used in a commercial\nproduct or activity."));
                AssertEx.Equal(true, gpgxLicense.Contains(
                    "complete source code, including the source code for all components used by a\nbinary built from the modified sources"));
                AssertEx.Equal(true, gpgxLicense.Contains("THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\""));
                AssertEx.Equal(true, gpgxLicense.Contains("Nuked OPN2 core is distributed under the following license:"));
                AssertEx.Equal(true, gpgxLicense.Contains("TREMOR library is distributed under the following license:"));
                AssertEx.Equal(true, gpgxLicense.Contains("MINIMP3 library is distributed under the following license:"));
                foreach (string component in new[] { "LIBCHDR", "DR_FLAC", "ZLIB", "ZSTD", "LZMA", "NTSC", "BLIP" })
                    AssertEx.Equal(true, gpgxLicense.ToUpperInvariant().Contains(component));
                AssertEx.Throws<InvalidOperationException>(() => Run("/usr/bin/bash", "-p",
                    Path.Combine(root, "install-core.sh"), "--build", a, "--stock", stock, "--output", install),
                    "output already exists");
                string outside = Path.Combine(Path.GetTempPath(), "openggf-task7-outside-" + Guid.NewGuid().ToString("N"));
                AssertEx.Throws<InvalidOperationException>(() => Run("/usr/bin/bash", "-p",
                    Path.Combine(root, "install-core.sh"), "--build", a, "--stock", stock, "--output", outside),
                    "output must be beneath an ignored audio-parity target");
                string binaryOnly = Path.Combine(scratch, "binary-only-build");
                Directory.CreateDirectory(binaryOnly);
                File.Copy(Path.Combine(a, "gpgx.wbx"), Path.Combine(binaryOnly, "gpgx.wbx"));
                File.Copy(Path.Combine(a, "gpgx.wbx.zst"), Path.Combine(binaryOnly, "gpgx.wbx.zst"));
                AssertEx.Throws<InvalidOperationException>(() => Run("/usr/bin/bash", "-p",
                    Path.Combine(root, "install-core.sh"), "--build", binaryOnly, "--stock", stock,
                    "--output", Path.Combine(scratch, "binary-only-install")), "missing source-bundle");
                string summarized = Path.Combine(scratch, "summarized-license-build");
                Run("/usr/bin/cp", "-al", a, summarized);
                string summarizedLicense = Path.Combine(summarized, "GPGX-LICENSE.txt");
                File.Delete(summarizedLicense);
                File.WriteAllText(summarizedLicense, "Genesis Plus GX license summary only.\n");
                AssertEx.Throws<InvalidOperationException>(() => Run("/usr/bin/bash", "-p",
                    Path.Combine(root, "install-core.sh"), "--build", summarized, "--stock", stock,
                    "--output", Path.Combine(scratch, "summarized-license-install")), "GPGX-LICENSE.txt");
                AssertEx.Throws<InvalidOperationException>(() => Run("/usr/bin/bash", "-p",
                    Path.Combine(root, "build-core.sh"), "--source", source,
                    "--toolchain", toolchainA, "--stock", stock, "--output", a),
                    "output already exists");
                AssertEx.Equal(stockBefore, Sha256(stockCore));
                string badSource = Path.Combine(scratch, "bad-source");
                Directory.CreateDirectory(badSource);
                AssertEx.Throws<InvalidOperationException>(() => Run("/usr/bin/bash", "-p",
                    Path.Combine(root, "build-core.sh"), "--source", badSource,
                    "--toolchain", toolchainA, "--stock", stock,
                    "--output", Path.Combine(scratch, "failed-build")), "wrong BizHawk commit");
                AssertEx.Equal(stockBefore, Sha256(stockCore));
            }
            finally
            {
                if (Directory.Exists(scratch)) Directory.Delete(scratch, true);
            }
        }

        private static void Run(string program, params string[] arguments)
        {
            RunCapture(program, arguments);
        }

        private static string RunCapture(string program, params string[] arguments)
        {
            var info = new ProcessStartInfo(program) { UseShellExecute = false, RedirectStandardOutput = true, RedirectStandardError = true };
            info.EnvironmentVariables.Clear();
            info.EnvironmentVariables["HOME"] = "/nonexistent";
            info.EnvironmentVariables["PATH"] = "/usr/bin:/bin";
            info.Arguments = string.Empty;
            foreach (string argument in arguments) info.Arguments += " \"" + argument.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"";
            using (Process process = Process.Start(info))
            {
                string stdout = process.StandardOutput.ReadToEnd();
                string stderr = process.StandardError.ReadToEnd();
                process.WaitForExit();
                if (process.ExitCode != 0) throw new InvalidOperationException(stdout + stderr);
                return stdout;
            }
        }

        private static string Sha256(string path)
        {
            using (SHA256 sha = SHA256.Create())
            using (FileStream stream = File.OpenRead(path))
            {
                return BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", string.Empty).ToLowerInvariant();
            }
        }
    }
}
