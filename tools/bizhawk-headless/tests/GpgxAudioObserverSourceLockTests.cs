using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class GpgxAudioObserverSourceLockTests
    {
        private const string ObserverDirectory = "native/gpgx-audio-observer";

        internal static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverSourceLockTests pin exact native inputs",
                PinsExactNativeInputs));
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverSourceLockTests reject mutable source identity",
                RejectsMutableSourceIdentity));
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverSourceLockTests lock reflection after managed mismatch",
                LocksReflectionAfterManagedMismatch));
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverSourceLockTests scripts are create-new and fail closed",
                ScriptsAreCreateNewAndFailClosed));
        }

        private static void PinsExactNativeInputs()
        {
            JObject source = ReadLock("source-lock.json");
            JObject toolchain = ReadLock("toolchain-lock.json");
            AssertEx.Equal(
                "427556b5ef3ac437eba754d90c5e7e9096c9a8df",
                (string)source["bizhawk"]["commit"]);
            AssertEx.Equal(
                "051d430d3d1b54625f9900c8f152d7f232e06daf",
                (string)source["gpgx"]["commit"]);
            AssertEx.Equal(
                "2063abc4e16c84218757b1db10d3cdf9f36ef3f8",
                (string)source["musl"]["commit"]);
            AssertEx.Equal(
                "ada57e3ac045bb324397c6d269dbad56a0b0f3608c89d321d1fed38206570ff5",
                (string)toolchain["packages"]["libclang-common-16-dev"]["sha256"]);
            AssertEx.Equal(
                "fc06187ae45bcedeea4f76f33868ccb05a8c80831d5dce19adbd5eee6e6e06e1",
                (string)toolchain["waterbox"]["sysroot_tree_sha256"]);
            AssertEx.Equal("16.0.6-15", (string)toolchain["versions"]["clang"]);
            AssertEx.Equal("1.5.5", (string)toolchain["versions"]["zstd"]);
            AssertEx.Equal(
                "7bc75866617449d384679bd29298a222a458ff0daea0fc4c221122b5513cf307",
                (string)toolchain["zstd"]["executable_sha256"]);
            AssertEx.Equal(
                "fdc7dc98b5a218256c991d712a2909ca244ad482e8996737ea49569cd8643563",
                (string)toolchain["build_recipe"]["sha256"]);
            AssertEx.Equal(
                "c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12",
                (string)source["stock"]["gpgx_compressed_sha256"]);
            AssertEx.Equal(
                "b4cc6dabc069a6f1b87790212d80f665d216e603aa4990955cc816d5bf98d218",
                (string)source["stock"]["gpgx_decompressed_sha256"]);
            AssertEx.Equal("7696adca7ad14b79", (string)source["stock"]["gpgx_build_id"]);
            AssertEx.Equal(
                "c328932fde7df37ce21759045b5b90f13170b9df88b1798e064c35a34b8fbb1f",
                (string)source["critical_files"]["waterbox/common.mak"]);
            AssertEx.Equal(
                "7281227ed2f3b89c0962b2792b28539e35361c6b",
                (string)source["bizhawk"]["tree"]);
            AssertEx.Equal(
                "1bb96ca74d660d383e70d9cd56b88906a0773519",
                (string)source["gpgx"]["tree"]);
            AssertEx.Equal(
                "a9969a63cd1780cdcc4c09745a8789206a72b8b4",
                (string)source["musl"]["tree"]);
            AssertEx.Equal(39558192, (int)source["stock"]["gpgx_decompressed_size"]);
            AssertEx.Equal(400161, (int)source["stock"]["gpgx_compressed_size"]);
            AssertEx.Equal(
                "0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7",
                (string)source["stock"]["cores_sha256"]);
            AssertEx.Equal(
                "f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4",
                (string)source["stock"]["common_sha256"]);
            AssertEx.Equal(
                "d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78",
                (string)source["stock"]["waterbox_host_sha256"]);
            AssertEx.Equal(
                "2f686f6d652f66656f732f7368617265732f73686172652f42697a4861776b",
                (string)source["historical_paths_utf8_hex"]["build_root"]);
            AssertEx.Equal(
                "2f686f6d652f66656f732f7368617265732f73686172652f42697a4861776b2f7761746572626f782f737973726f6f742f7379736c69622f6c642d6d75736c2d7761746572626f782e736f2e31",
                (string)source["historical_paths_utf8_hex"]["interpreter"]);

            var sourceDigests = new Dictionary<string, string>
            {
                { "waterbox/emulibc/emulibc.c", "4b86754f2c5d8ebe759efa90f9e74a985098492ad011bfe197ba23a93e1173fd" },
                { "waterbox/emulibc/emulibc.h", "90eadc83d089550dfbbfe012839ba5804cbe46628c3264b0b7bbea1b0ccabb89" },
                { "waterbox/emulibc/waterboxcore.h", "b4be11bda3c1e608fd5d38be48d70a4d506f92c32249e64ca9338c26a06810f3" },
                { "waterbox/emulibc/Makefile", "0524d95e1e350a42ef8f3676b6d59b06d959a72574a18261edf9fa0c8d029a9a" },
                { "waterbox/linkscript.T", "3a5f16e86596f0bb4b254b0fa0c4ba68effbf8a438ef34bbfbe7b179692cd536" },
                { "waterbox/gpgx/Makefile", "c92fd9b2cbce52c75b580bf91d357cc028c5ea5c935475b042cce7110ef4caaa" },
                { "waterbox/musl/configure", "1c6b2127d864cdc912645e7130debcd55e47ba1ce63e8e004ea3cd08fed71b22" },
                { "waterbox/musl/wbox_configure.sh", "34abbf5b7c115b3c8c1cf58cc4b2efef87d20a175dccfef1739b0444a022662c" },
                { "waterbox/musl/wbox_build.sh", "ef7b3279e8be2e1b519f73812a217f4027bd97c8f09dd6a691f1061766d2af2b" }
            };
            foreach (KeyValuePair<string, string> expected in sourceDigests)
            {
                AssertEx.Equal(expected.Value, (string)source["critical_files"][expected.Key]);
            }

            AssertEx.Equal("16.0.6", (string)toolchain["versions"]["lld"]);
            AssertEx.Equal("4.4.1", (string)toolchain["versions"]["make"]);
            AssertEx.Equal("2.47", (string)toolchain["versions"]["binutils"]);
            var packageDigests = new Dictionary<string, string>
            {
                { "clang-16", "b9cd4d27a5d1b6c429fccf56a4ac1c4ac5baf2cb9b5a53e2a20fcd6593153e5a" },
                { "libclang-cpp16", "39eb3e73119ef0180489c7e594d29398152b3a2d7eec2361cf87d367032f466a" },
                { "libllvm16", "3353bbe1910cfc99a8ef96e1cd7df45c65e2aaebefcfc801bcb7587bab819a15" },
                { "llvm-16-linker-tools", "39f6c47b5ecc04c064899a99d224650b2d932e7f27ac02246073395fc8bd1300" },
                { "libclang-common-16-dev", "ada57e3ac045bb324397c6d269dbad56a0b0f3608c89d321d1fed38206570ff5" },
                { "lld-16", "e75a2e784d2da2e3d90a31d7b8002892ac58b90e53073a14c7db1a8d80172204" },
                { "libclang-rt-16-dev", "20f3b1a105d5b8fba261a03bd6ad531e09a87c929f33f54e5dd4db78f980dda2" },
                { "libedit2", "d1c26768f5e108c97d9520c8a19356ddf5a1967222af4f38efb1f5af21da46b5" },
                { "libxml2", "7c4d4ec04145f854bb824cb72fb34233c99f7db3eaafaa3d2049bd82800c0f85" },
                { "libicu72", "3db0831a7a8da3c8d878fdbc4644d4131ed914b22c8a0cffbcabe68a2c3f6ec4" },
                { "zstd-source", "9c4396cc829cfae319a6e2615202e82aad41372073482fce286fac78646d3ee4" }
            };
            foreach (KeyValuePair<string, string> expected in packageDigests)
            {
                AssertEx.Equal(expected.Value, (string)toolchain["packages"][expected.Key]["sha256"]);
            }
            AssertEx.Equal(
                "bb6556bdcdeb00dca0c758da9966a9982542a23ddcaffa784a2de9344ede3fc0",
                (string)toolchain["executables"]["clang-16"]);
            AssertEx.Equal(
                "f8d0601bf957a1b063e29c3c43613a5b76482f6c14664b9fcac4d596871e14df",
                (string)toolchain["executables"]["ld.lld-16"]);
            AssertEx.Equal(
                "4dc8719b3b60a5e03b3720f3060415a8dd3b564b74319539b2a0dc52bc50c0df",
                (string)toolchain["executables"]["mv"]);
            AssertEx.Equal(
                "55f9e1b3c3b98853fc31787414064de36a22cc23f870962b45832fc904c498a2",
                (string)toolchain["libraries"]["libLLVM-16.so.1"]);
            AssertEx.Equal(
                "f9bf97848329b4d444c8c8791b9f8a584b58016852a6ba4b55db164726623ac7",
                (string)toolchain["libraries"]["libclang-cpp.so.16"]);
            AssertEx.Equal(
                "2f257b223dbee10ea0415e5f95385a71dc05bb94505a21a4be1d22ce733e624d",
                (string)toolchain["waterbox"]["compiler_rt_builtins_sha256"]);
            AssertEx.Equal(
                "9b8f89ee3105aad8b2a18805362677b6d983721e9d3706629359ddf7c9ec837b",
                (string)toolchain["waterbox"]["libc_archive_sha256"]);
            AssertEx.Equal(
                "409b9debb122dd5e5d0719874e99d0f3d3f71c25cf8731bfa1ec61462d0c295b",
                (string)toolchain["build_recipe"]["verified_input_identity_sha256"]);
            AssertEx.Equal(
                "9caa5c02dcd2d9c01e5d0196956787a0f31760195c6544a2ceafcb771f469521",
                (string)toolchain["build_recipe"]["complete_toolchain_tree_sha256"]);
        }

        private static void RejectsMutableSourceIdentity()
        {
            JObject source = ReadLock("source-lock.json");
            string all = source.ToString(Newtonsoft.Json.Formatting.None);
            AssertEx.Equal(false, all.Contains("bdddf4a58aa1a022afb11dc73294a81a5aa7bbd5"));
            AssertEx.Equal(false, all.Contains("latest"));
            AssertEx.Equal(false, all.Contains("refs/heads"));
            AssertEx.Equal(false, all.Contains("/tmp/"));
            AssertEx.Equal(false, all.Contains("/home/feos"));
            foreach (string script in new[] { "fetch-source.sh", "prepare-toolchain.sh",
                "verify-inputs.sh", "reproduce-stock-core.sh", "reproduce-stock-managed.sh" })
            {
                string scriptText = File.ReadAllText(Path.Combine(
                    EndToEndTests.ToolDirectory, ObserverDirectory, script));
                AssertEx.Equal(false, scriptText.Contains("/home/feos"));
            }
        }

        private static void LocksReflectionAfterManagedMismatch()
        {
            JObject managed = ReadLock("managed-toolchain-lock.json");
            AssertEx.Equal("8.0.414", (string)managed["sdk"]["version"]);
            AssertEx.Equal("8.0.20", (string)managed["sdk"]["runtime_version"]);
            AssertEx.Equal("17.11.41+18f1ecf82", (string)managed["sdk"]["msbuild_version"]);
            AssertEx.Equal(
                "7786bbe5093e3a5d354a1ffa56083b6a32ad12837a83170f1f3b51ad7df28516",
                (string)managed["sdk"]["archive_sha256"]);
            AssertEx.Equal("REFLECTION", (string)managed["selected_adapter"]);
            AssertEx.Equal(false, (bool)managed["patched_managed_dll_permitted"]);
            AssertEx.Equal("BYTE_MISMATCH", (string)managed["reproduction"]["status"]);
            AssertEx.Equal(
                "0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7",
                (string)managed["stock"]["cores_sha256"]);
            AssertEx.Equal(
                "f7e7ea11f05adb7bcdc1f55c09810f873abfe06debdc3f3b100185f20a69c031",
                (string)managed["reproduction"]["observed_cores_sha256"]);
            AssertEx.Equal(
                "f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4",
                (string)managed["stock"]["common_sha256"]);
            AssertEx.Equal(
                "96f494af9be13f52dc63ab3d430b15641fc142cf469339a8bf013e67b99b757e",
                (string)managed["reproduction"]["observed_common_sha256"]);
            AssertEx.Equal(false, (bool)managed["reproduction"]["stock_cmp"]);
            AssertEx.Equal(114, (int)managed["nuget"]["package_count"]);
            AssertEx.Equal(
                "e0afe65b153f1f3cbaed03c8e3987542322a9ea1a220cac3696bc7ba59c42290",
                (string)managed["nuget"]["canonical_manifest_sha256"]);
            AssertEx.Equal(
                "efadaf168670ce0ae5f8f5dc7705ddaa94e898bce96134b9ebc86c31ceb6d6d2",
                (string)managed["reproduction"]["canonical_identity_sha256"]);
        }

        private static void ScriptsAreCreateNewAndFailClosed()
        {
            string observerRoot = Path.Combine(EndToEndTests.ToolDirectory, ObserverDirectory);
            string prepareScript = File.ReadAllText(Path.Combine(observerRoot, "prepare-toolchain.sh"));
            string verifyScript = File.ReadAllText(Path.Combine(observerRoot, "verify-inputs.sh"));
            const string completeToolchainTree =
                "9caa5c02dcd2d9c01e5d0196956787a0f31760195c6544a2ceafcb771f469521";
            AssertEx.Equal(true, prepareScript.Contains(completeToolchainTree));
            AssertEx.Equal(true, verifyScript.Contains(completeToolchainTree));
            foreach (string script in new[] { "fetch-source.sh", "prepare-toolchain.sh",
                "verify-inputs.sh", "reproduce-stock-core.sh", "reproduce-stock-managed.sh" })
            {
                string scriptText = File.ReadAllText(Path.Combine(observerRoot, script));
                AssertEx.Equal(false, scriptText.Contains("--untracked-files=no"));
            }
            foreach (string script in new[] { "fetch-source.sh", "prepare-toolchain.sh",
                "reproduce-stock-core.sh", "reproduce-stock-managed.sh" })
            {
                string scriptText = File.ReadAllText(Path.Combine(observerRoot, script));
                AssertEx.Equal(false, scriptText.Contains("-exec mv -t"));
                AssertEx.Equal(false, scriptText.Contains("mkdir -- \"$target\""));
                AssertEx.Equal(true, scriptText.Contains(
                    "/usr/bin/mv -T --no-copy --no-clobber -- \"$source\" \"$target\""));
            }
            AssertEx.Equal(true, prepareScript.Contains("source_dir=$stage/work-source"));
            AssertEx.Equal(true, prepareScript.Contains("packages_dir=$stage/package-input"));
            string coreScript = File.ReadAllText(Path.Combine(observerRoot, "reproduce-stock-core.sh"));
            AssertEx.Equal(true, coreScript.Contains("source_dir=$stage/build-source"));
            AssertEx.Equal(true, coreScript.Contains("toolchain_dir=$stage/toolchain-input"));
            AssertEx.Equal(true, coreScript.Contains("verified_input_identity_sha256"));
            AssertEx.Equal(true, coreScript.Contains("complete_toolchain_tree_sha256"));
            string managedScript = File.ReadAllText(Path.Combine(observerRoot, "reproduce-stock-managed.sh"));
            AssertEx.Equal(true, managedScript.Contains("source_dir=$stage/source"));
            AssertEx.Equal(true, managedScript.Contains("nuget_dir=$stage/nuget-input-tree"));
            AssertEx.Equal(true, managedScript.Contains("sdk_archive=$stage/sdk-archive.tar.gz"));

            string root = TestScratch.CreateRootPath("gpgx-source-lock");
            try
            {
                Directory.CreateDirectory(root);
                string existing = Path.Combine(root, "existing");
                Directory.CreateDirectory(existing);
                File.WriteAllText(Path.Combine(existing, "sentinel"), "keep");
                foreach (string script in new[] { "fetch-source.sh", "prepare-toolchain.sh",
                    "reproduce-stock-core.sh", "reproduce-stock-managed.sh" })
                {
                    ProcessResult result = Run(script, "--output", existing);
                    AssertEx.Equal(false, result.ExitCode == 0);
                    AssertEx.Equal(true, result.Stderr.Contains("output already exists"));
                    AssertEx.Equal("keep", File.ReadAllText(Path.Combine(existing, "sentinel")));
                }

                string staged = Path.Combine(root, "publication-stage");
                string racingEmptyTarget = Path.Combine(root, "racing-empty-target");
                Directory.CreateDirectory(staged);
                Directory.CreateDirectory(racingEmptyTarget);
                File.WriteAllText(Path.Combine(staged, "complete"), "complete");
                ProcessResult noReplace = RunProgram("/usr/bin/mv", "-T", "--no-copy",
                    "--no-clobber", "--", staged, racingEmptyTarget);
                AssertEx.Equal(0, noReplace.ExitCode);
                AssertEx.Equal(true, Directory.Exists(staged));
                AssertEx.Equal(true, File.Exists(Path.Combine(staged, "complete")));
                AssertEx.Equal(0, Directory.GetFileSystemEntries(racingEmptyTarget).Length);

                string absentTarget = Path.Combine(root, "absent-target");
                ProcessResult atomicPublish = RunProgram("/usr/bin/mv", "-T", "--no-copy",
                    "--no-clobber", "--", staged, absentTarget);
                AssertEx.Equal(0, atomicPublish.ExitCode);
                AssertEx.Equal(false, Directory.Exists(staged));
                AssertEx.Equal("complete", File.ReadAllText(Path.Combine(absentTarget, "complete")));

                string uninitialized = Path.Combine(root, "bizhawk-2.11.1-uninitialized-cache");
                string emptyToolchain = Path.Combine(root, "empty-toolchain");
                Directory.CreateDirectory(uninitialized);
                Directory.CreateDirectory(emptyToolchain);
                ProcessResult rejected = Run("verify-inputs.sh", "--source", uninitialized,
                    "--toolchain", emptyToolchain);
                AssertEx.Equal(false, rejected.ExitCode == 0);
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static JObject ReadLock(string name)
        {
            string path = Path.Combine(EndToEndTests.ToolDirectory, ObserverDirectory, name);
            return JObject.Parse(File.ReadAllText(path));
        }

        private static ProcessResult Run(string script, params string[] arguments)
        {
            string path = Path.Combine(EndToEndTests.ToolDirectory, ObserverDirectory, script);
            var allArguments = new string[arguments.Length + 1];
            allArguments[0] = path;
            Array.Copy(arguments, 0, allArguments, 1, arguments.Length);
            return RunProgram("bash", allArguments);
        }

        private static ProcessResult RunProgram(string program, params string[] arguments)
        {
            var info = new ProcessStartInfo(program)
            {
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                Arguments = string.Empty
            };
            foreach (string argument in arguments)
            {
                info.Arguments += " " + Quote(argument);
            }
            using (Process process = Process.Start(info))
            {
                string stdout = process.StandardOutput.ReadToEnd();
                string stderr = process.StandardError.ReadToEnd();
                process.WaitForExit();
                return new ProcessResult(process.ExitCode, stdout, stderr);
            }
        }

        private static string Quote(string value)
        {
            return "\"" + value.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"";
        }

        private sealed class ProcessResult
        {
            internal ProcessResult(int exitCode, string stdout, string stderr)
            {
                ExitCode = exitCode;
                Stdout = stdout;
                Stderr = stderr;
            }

            internal int ExitCode { get; private set; }
            internal string Stdout { get; private set; }
            internal string Stderr { get; private set; }
        }
    }
}
