using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class GpgxAudioObserverSourceLockTests
    {
        private const string ObserverDirectory = "native/gpgx-audio-observer";
        private const uint InOpen = 0x00000020;

        [DllImport("libc", SetLastError = true)]
        private static extern int inotify_init1(int flags);

        [DllImport("libc", SetLastError = true)]
        private static extern int inotify_add_watch(int fd, string pathname, uint mask);

        [DllImport("libc", SetLastError = true)]
        private static extern IntPtr read(int fd, byte[] buffer, UIntPtr count);

        [DllImport("libc", SetLastError = true)]
        private static extern int close(int fd);

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
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverSourceLockTests secure runtime rejects ambient overrides",
                SecureRuntimeRejectsAmbientOverrides));
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverSourceLockTests canonical recipe and managed inputs are complete",
                CanonicalRecipeAndManagedInputsAreComplete));
            tests.Add(new TestMain.TestCase(
                "GpgxAudioObserverSourceLockTests slow real stock pair gate",
                SlowRealStockPairGate));
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
                "57ea87848e924904cc3463e6a8b59c80eea62e22fe19f1c0d2c82c7bce33260a",
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
                "36dde84c81429343b2f4425ff66c04f8fbdf54bcaf42a2459e68c52f95e9a0d4",
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
                "verify-inputs.sh", "reproduce-stock-core.sh", "reproduce-stock-managed.sh",
                "prepare-managed-inputs.sh", "reproduce-stock-pair.sh", "secure-runtime.sh" })
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
                "b609fa7cf733755415b9b878e53ea25e72cc55dca92a645e9a788f3b8e19ce86",
                (string)managed["nuget"]["canonical_manifest_sha256"]);
            AssertEx.Equal(
                "e0afe65b153f1f3cbaed03c8e3987542322a9ea1a220cac3696bc7ba59c42290",
                (string)managed["nuget"]["package_tree_sha256"]);
            AssertEx.Equal(
                "efadaf168670ce0ae5f8f5dc7705ddaa94e898bce96134b9ebc86c31ceb6d6d2",
                (string)managed["reproduction"]["pre_recipe_audit_identity_sha256"]);
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
                "verify-inputs.sh", "reproduce-stock-core.sh", "reproduce-stock-managed.sh",
                "prepare-managed-inputs.sh", "reproduce-stock-pair.sh", "secure-runtime.sh" })
            {
                string scriptText = File.ReadAllText(Path.Combine(observerRoot, script));
                AssertEx.Equal(false, scriptText.Contains("--untracked-files=no"));
            }
            foreach (string script in new[] { "fetch-source.sh", "prepare-toolchain.sh",
                "reproduce-stock-core.sh", "reproduce-stock-managed.sh",
                "prepare-managed-inputs.sh", "reproduce-stock-pair.sh" })
            {
                string scriptText = File.ReadAllText(Path.Combine(observerRoot, script));
                AssertEx.Equal(false, scriptText.Contains("-exec mv -t"));
                AssertEx.Equal(false, scriptText.Contains("mkdir -- \"$target\""));
                AssertEx.Equal(true, scriptText.Contains("secure_publish_create_new"));
            }
            AssertEx.Equal(true, prepareScript.Contains("source_dir=$stage/work-source"));
            AssertEx.Equal(true, prepareScript.Contains("packages_dir=$stage/package-input"));
            string coreScript = File.ReadAllText(Path.Combine(observerRoot, "reproduce-stock-core.sh"));
            AssertEx.Equal(true, coreScript.Contains("source_dir=$stage/build-source"));
            AssertEx.Equal(true, coreScript.Contains("toolchain_dir=$stage/toolchain-input"));
            AssertEx.Equal(true, coreScript.Contains("verified_input_identity_sha256"));
            AssertEx.Equal(true, coreScript.Contains("build_recipe_sha256"));
            AssertEx.Equal(true, coreScript.Contains("complete_toolchain_tree_sha256"));
            string managedScript = File.ReadAllText(Path.Combine(observerRoot, "reproduce-stock-managed.sh"));
            AssertEx.Equal(true, managedScript.Contains("source_dir=$stage/source"));
            AssertEx.Equal(true, managedScript.Contains("managed_inputs=$stage/managed-input-tree"));
            AssertEx.Equal(true, managedScript.Contains("sdk_archive=$managed_inputs/dotnet-sdk-8.0.414-linux-x64.tar.gz"));

            string root = TestScratch.CreateRootPath("gpgx-source-lock");
            try
            {
                Directory.CreateDirectory(root);
                string existing = Path.Combine(root, "existing");
                Directory.CreateDirectory(existing);
                File.WriteAllText(Path.Combine(existing, "sentinel"), "keep");
                foreach (string script in new[] { "fetch-source.sh", "prepare-toolchain.sh",
                    "reproduce-stock-core.sh", "reproduce-stock-managed.sh",
                    "prepare-managed-inputs.sh", "reproduce-stock-pair.sh" })
                {
                    ProcessResult result = Run(script, "--output", existing);
                    AssertEx.Equal(false, result.ExitCode == 0);
                    AssertEx.Equal(true, result.Stderr.Contains("output already exists"));
                    AssertEx.Equal("keep", File.ReadAllText(Path.Combine(existing, "sentinel")));
                }

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

        private static void SecureRuntimeRejectsAmbientOverrides()
        {
            string root = TestScratch.CreateRootPath("gpgx-secure-runtime");
            try
            {
                Directory.CreateDirectory(root);
                string fakeBin = Path.Combine(root, "fake-bin");
                Directory.CreateDirectory(fakeBin);
                string marker = Path.Combine(root, "ambient-tool-ran");
                foreach (string tool in new[] { "git", "sha256sum", "cmp" })
                {
                    string fake = Path.Combine(fakeBin, tool);
                    File.WriteAllText(fake, "#!/usr/bin/bash\nprintf '%s\\n' invoked >> '"
                        + marker.Replace("'", "'\\''") + "'\nexit 0\n");
                    RunProgram("/usr/bin/chmod", "0755", fake);
                }
                string left = Path.Combine(root, "left");
                string right = Path.Combine(root, "right");
                File.WriteAllText(left, "same");
                File.WriteAllText(right, "same");
                ProcessResult safe = RunWithEnvironment("secure-runtime.sh",
                    new Dictionary<string, string> { { "PATH", fakeBin } },
                    "equal-files", left, right);
                AssertEx.Equal(0, safe.ExitCode);
                AssertEx.Equal(false, File.Exists(marker));
                string repository = Path.Combine(root, "repository");
                Directory.CreateDirectory(repository);
                AssertEx.Equal(0, RunProgram("/usr/bin/git", "-C", repository, "init", "-q").ExitCode);
                File.WriteAllText(Path.Combine(repository, "locked"), "value");
                AssertEx.Equal(0, RunProgram("/usr/bin/git", "-C", repository, "add", "locked").ExitCode);
                AssertEx.Equal(0, RunProgram("/usr/bin/git", "-c", "user.name=Task6",
                    "-c", "user.email=task6@example.invalid", "-C", repository,
                    "commit", "-q", "-m", "locked").ExitCode);
                ProcessResult safeGit = RunWithEnvironment("secure-runtime.sh",
                    new Dictionary<string, string> { { "PATH", fakeBin } },
                    "git-head", repository);
                AssertEx.Equal(0, safeGit.ExitCode);
                AssertEx.Equal(40, safeGit.Stdout.Trim().Length);
                AssertEx.Equal(false, File.Exists(marker));

                string hostileStartup = Path.Combine(root, "hostile-bash-env");
                File.WriteAllText(hostileStartup, "#!/usr/bin/bash\nprintf hostile > '"
                    + marker.Replace("'", "'\\''") + "'\n");
                string hostileGitConfig = Path.Combine(root, "hostile-git-config");
                File.WriteAllText(hostileGitConfig,
                    "[alias]\n\trev-parse = !printf hostile > " + marker + "\n");
                foreach (KeyValuePair<string, string> variable in new[]
                {
                    new KeyValuePair<string, string>("BASH_ENV", hostileStartup),
                    new KeyValuePair<string, string>("GIT_CONFIG_GLOBAL", hostileGitConfig)
                })
                {
                    ProcessResult rejected = RunWithEnvironment("secure-runtime.sh",
                        new Dictionary<string, string> { { variable.Key, variable.Value } },
                        "equal-files", left, right);
                    AssertEx.Equal(false, rejected.ExitCode == 0);
                    AssertEx.Equal(true, rejected.Stderr.Contains("forbidden ambient variable"));
                    AssertEx.Equal(false, File.Exists(marker));
                }

                string stage = Path.Combine(root, "stage");
                string output = Path.Combine(root, "output");
                Directory.CreateDirectory(stage);
                File.WriteAllText(Path.Combine(stage, "complete"), "yes");
                AssertEx.Equal(0, Run("secure-runtime.sh", "publish-create-new", stage, output).ExitCode);
                AssertEx.Equal("yes", File.ReadAllText(Path.Combine(output, "complete")));

                string racing = Path.Combine(root, "racing");
                string raceOutput = Path.Combine(root, "race-output");
                Directory.CreateDirectory(racing);
                File.WriteAllText(Path.Combine(racing, "complete"), "yes");
                int inotify = inotify_init1(0);
                AssertEx.Equal(true, inotify >= 0);
                try
                {
                    AssertEx.Equal(true,
                        inotify_add_watch(inotify, "/usr/bin/sha256sum", InOpen) >= 0);
                    string secureRuntime = Path.Combine(
                        EndToEndTests.ToolDirectory, ObserverDirectory, "secure-runtime.sh");
                    using (Process race = StartProgramWithEnvironment("/usr/bin/bash", null,
                        "-p", secureRuntime, "publish-create-new", racing, raceOutput))
                    {
                        var eventBytes = new byte[4096];
                        AssertEx.Equal(true,
                            read(inotify, eventBytes, new UIntPtr((uint)eventBytes.Length)).ToInt64() >= 16);
                        Directory.CreateDirectory(raceOutput);
                        string raceStdout = race.StandardOutput.ReadToEnd();
                        string raceStderr = race.StandardError.ReadToEnd();
                        race.WaitForExit();
                        AssertEx.Equal(false, race.ExitCode == 0);
                        AssertEx.Equal(true, raceStderr.Contains("publication target appeared concurrently"));
                        AssertEx.Equal(string.Empty, raceStdout);
                    }
                }
                finally
                {
                    close(inotify);
                }
                AssertEx.Equal(true, Directory.Exists(racing));
                AssertEx.Equal(0, Directory.GetFileSystemEntries(raceOutput).Length);

                string partial = Path.Combine(root, "partial-output");
                string crossDevice = Path.Combine("/dev/shm",
                    "openggf-task6-" + Guid.NewGuid().ToString("N"));
                try
                {
                    Directory.CreateDirectory(crossDevice);
                    File.WriteAllText(Path.Combine(crossDevice, "complete"), "yes");
                    ProcessResult failedMove = Run(
                        "secure-runtime.sh", "publish-create-new", crossDevice, partial);
                    AssertEx.Equal(false, failedMove.ExitCode == 0);
                    AssertEx.Equal(true, Directory.Exists(crossDevice));
                    AssertEx.Equal(false, Directory.Exists(partial));
                }
                finally
                {
                    if (Directory.Exists(crossDevice))
                    {
                        Directory.Delete(crossDevice, true);
                    }
                }

                string mutable = Path.Combine(root, "mutable");
                string snapshot = Path.Combine(root, "snapshot");
                Directory.CreateDirectory(mutable);
                File.WriteAllText(Path.Combine(mutable, "value"), "before");
                AssertEx.Equal(0, Run("secure-runtime.sh", "snapshot-tree", mutable, snapshot).ExitCode);
                File.WriteAllText(Path.Combine(mutable, "value"), "after");
                AssertEx.Equal("before", File.ReadAllText(Path.Combine(snapshot, "value")));
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static void CanonicalRecipeAndManagedInputsAreComplete()
        {
            string root = Path.Combine(EndToEndTests.ToolDirectory, ObserverDirectory);
            string recipePath = Path.Combine(root, "build-recipe.json");
            string manifestPath = Path.Combine(root, "managed-nuget-manifest.json");
            JObject recipe = JObject.Parse(File.ReadAllText(recipePath));
            JObject toolchain = ReadLock("toolchain-lock.json");
            AssertEx.Equal(Sha256(recipePath), (string)toolchain["build_recipe"]["sha256"]);
            AssertEx.Equal("openggf.gpgx-stock-build-recipe.v1", (string)recipe["schema"]);
            AssertEx.Equal(true, ((JObject)recipe["versioned_inputs"]).Count >= 9);
            JArray packages = (JArray)JObject.Parse(File.ReadAllText(manifestPath))["packages"];
            AssertEx.Equal(114, packages.Count);
            AssertEx.Equal(114, new HashSet<string>(packages.Values<string>("path"),
                StringComparer.Ordinal).Count);
            AssertEx.Equal(0, Run("secure-runtime.sh", "verify-recipe", root).ExitCode);

            string scratch = TestScratch.CreateRootPath("gpgx-recipe-mutation");
            try
            {
                Directory.CreateDirectory(scratch);
                foreach (string file in Directory.GetFiles(root))
                {
                    File.Copy(file, Path.Combine(scratch, Path.GetFileName(file)));
                }
                File.AppendAllText(Path.Combine(scratch, "verify-inputs.sh"), "# mutation\n");
                AssertEx.Equal(false,
                    Run("secure-runtime.sh", "verify-recipe", scratch).ExitCode == 0);
            }
            finally
            {
                if (Directory.Exists(scratch))
                {
                    Directory.Delete(scratch, true);
                }
            }
        }

        private static void SlowRealStockPairGate()
        {
            string packages = Environment.GetEnvironmentVariable("OPENGGF_TASK6_PACKAGES");
            string sdk = Environment.GetEnvironmentVariable("OPENGGF_TASK6_SDK_ARCHIVE");
            string nuget = Environment.GetEnvironmentVariable("OPENGGF_TASK6_NUGET_PACKAGES");
            string stock = Environment.GetEnvironmentVariable("OPENGGF_TASK6_STOCK");
            string output = Environment.GetEnvironmentVariable("OPENGGF_TASK6_PAIR_OUTPUT");
            if (string.IsNullOrEmpty(packages) || string.IsNullOrEmpty(sdk)
                || string.IsNullOrEmpty(nuget) || string.IsNullOrEmpty(stock)
                || string.IsNullOrEmpty(output))
            {
                throw new TestMain.SkipTestException(
                    "set OPENGGF_TASK6_PACKAGES, OPENGGF_TASK6_SDK_ARCHIVE, "
                    + "OPENGGF_TASK6_NUGET_PACKAGES, OPENGGF_TASK6_STOCK, and "
                    + "OPENGGF_TASK6_PAIR_OUTPUT to run the full offline/HTTPS reproduction");
            }
            ProcessResult result = Run("reproduce-stock-pair.sh",
                "--packages", packages, "--sdk-archive", sdk,
                "--nuget-packages", nuget, "--stock", stock, "--output", output);
            AssertEx.Equal(0, result.ExitCode);
            AssertEx.Equal(true, File.Exists(Path.Combine(output, "identity.json")));
        }

        private static string Sha256(string path)
        {
            using (SHA256 sha = SHA256.Create())
            using (FileStream stream = File.OpenRead(path))
            {
                var value = new StringBuilder();
                foreach (byte item in sha.ComputeHash(stream))
                {
                    value.Append(item.ToString("x2"));
                }
                return value.ToString();
            }
        }

        private static ProcessResult Run(string script, params string[] arguments)
        {
            string path = Path.Combine(EndToEndTests.ToolDirectory, ObserverDirectory, script);
            var allArguments = new string[arguments.Length + 1];
            allArguments[0] = path;
            Array.Copy(arguments, 0, allArguments, 1, arguments.Length);
            return RunProgram("/usr/bin/bash", Prepend("-p", allArguments));
        }

        private static ProcessResult RunWithEnvironment(string script,
            IDictionary<string, string> environment, params string[] arguments)
        {
            string path = Path.Combine(EndToEndTests.ToolDirectory, ObserverDirectory, script);
            var allArguments = new string[arguments.Length + 2];
            allArguments[0] = "-p";
            allArguments[1] = path;
            Array.Copy(arguments, 0, allArguments, 2, arguments.Length);
            return RunProgramWithEnvironment("/usr/bin/bash", environment, allArguments);
        }

        private static string[] Prepend(string value, string[] values)
        {
            var result = new string[values.Length + 1];
            result[0] = value;
            Array.Copy(values, 0, result, 1, values.Length);
            return result;
        }

        private static ProcessResult RunProgram(string program, params string[] arguments)
        {
            return RunProgramWithEnvironment(program, null, arguments);
        }

        private static ProcessResult RunProgramWithEnvironment(string program,
            IDictionary<string, string> environment, params string[] arguments)
        {
            using (Process process = StartProgramWithEnvironment(program, environment, arguments))
            {
                string stdout = process.StandardOutput.ReadToEnd();
                string stderr = process.StandardError.ReadToEnd();
                process.WaitForExit();
                return new ProcessResult(process.ExitCode, stdout, stderr);
            }
        }

        private static Process StartProgramWithEnvironment(string program,
            IDictionary<string, string> environment, params string[] arguments)
        {
            var info = new ProcessStartInfo(program)
            {
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                Arguments = string.Empty
            };
            var inheritedNames = new List<string>();
            foreach (System.Collections.DictionaryEntry item in info.EnvironmentVariables)
            {
                inheritedNames.Add((string)item.Key);
            }
            foreach (string name in inheritedNames)
            {
                if (IsForbiddenBuildEnvironment(name))
                {
                    info.EnvironmentVariables.Remove(name);
                }
            }
            if (environment != null)
            {
                foreach (KeyValuePair<string, string> item in environment)
                {
                    info.EnvironmentVariables[item.Key] = item.Value;
                }
            }
            foreach (string argument in arguments)
            {
                info.Arguments += " " + Quote(argument);
            }
            return Process.Start(info);
        }

        private static bool IsForbiddenBuildEnvironment(string name)
        {
            return name == "BASH_ENV" || name == "ENV" || name == "SHELLOPTS"
                || name == "CDPATH" || name == "GLOBIGNORE"
                || name.StartsWith("JAVA_", StringComparison.Ordinal)
                || name.StartsWith("JDK_", StringComparison.Ordinal)
                || name.StartsWith("_JAVA_", StringComparison.Ordinal)
                || name.StartsWith("MONO_", StringComparison.Ordinal)
                || name.StartsWith("DOTNET_", StringComparison.Ordinal)
                || name.StartsWith("NUGET_", StringComparison.Ordinal)
                || name.StartsWith("MSBUILD", StringComparison.OrdinalIgnoreCase)
                || name.StartsWith("GIT_", StringComparison.Ordinal)
                || name.StartsWith("SSH_", StringComparison.Ordinal)
                || name.StartsWith("LD_", StringComparison.Ordinal)
                || name == "CC" || name == "CXX" || name == "CPP"
                || name == "CFLAGS" || name == "CXXFLAGS" || name == "CPPFLAGS"
                || name == "LDFLAGS" || name == "MAKEFLAGS" || name == "MFLAGS";
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
