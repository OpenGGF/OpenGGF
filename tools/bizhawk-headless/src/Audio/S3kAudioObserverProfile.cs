using System;
using System.IO;
using System.Security.Cryptography;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Fixed locked-on S3K complete-run audio observer profile. The reviewed
    /// Task 8 service manifest remains the hook/source authority; this adapter
    /// enables ABI v2 prepublication only for the SndDrvInit proof chain so
    /// observation can begin at power-on and publication can begin at row 810.
    /// </summary>
    internal static class S3kAudioObserverProfile
    {
        internal const string RomSha1 =
            "cfbf98c36c776677290a872547ac47c53d2761d6";
        internal const string MovieSha256 =
            "aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc";
        internal const string MovieHeaderHash =
            "C5B1C655C19F462ADE0AC4E17A844D10";
        internal const string ManifestSha256 =
            "ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0";
        internal const int FirstRow = 810;
        internal const int ExclusiveEnd = 434417;
        internal const int DriverStateStart = 0x1C00;
        internal const int DriverStateExclusiveEnd = 0x2000;

        private const ushort UploadBeginToken = 7;
        private const ushort UploadCompletionToken = 8;
        private const byte ArmProofCompletion = 1;
        private const byte PrearmPermitted = 2;

        internal static CompleteRunAudioObserver CreateObserver(
            string manifestPath, IGpgxAudioTraceApi api)
        {
            if (string.IsNullOrEmpty(manifestPath)
                || !Path.IsPathRooted(manifestPath))
            {
                throw new ArgumentException(
                    "The S3K audio service-manifest path must be absolute.",
                    "manifestPath");
            }
            if (api == null) throw new ArgumentNullException("api");
            if (!File.Exists(manifestPath))
                throw new FileNotFoundException(
                    "The S3K audio service manifest is absent.", manifestPath);
            string actual = Sha256(manifestPath);
            if (!string.Equals(actual, ManifestSha256,
                StringComparison.Ordinal))
            {
                throw new InvalidDataException(
                    "The S3K audio service-manifest SHA-256 was " + actual
                    + "; expected " + ManifestSha256 + ".");
            }
            return GpgxAudioServiceManifest.Load(
                manifestPath, "s3k", new PrepublicationApi(api));
        }

        internal static Bk2Movie OpenMovie(string moviePath)
        {
            if (string.IsNullOrEmpty(moviePath)
                || !Path.IsPathRooted(moviePath))
            {
                throw new ArgumentException(
                    "The S3K complete audio movie path must be absolute.",
                    "moviePath");
            }
            if (!File.Exists(moviePath))
                throw new FileNotFoundException(
                    "The S3K complete audio movie is absent.", moviePath);
            string actual = Sha256(moviePath);
            if (!string.Equals(actual, MovieSha256, StringComparison.Ordinal))
                throw new InvalidDataException(
                    "The S3K complete audio movie SHA-256 was " + actual
                    + "; expected " + MovieSha256 + ".");
            Bk2Movie movie = Bk2Reader.Read(moviePath);
            if (movie.FrameCount != ExclusiveEnd
                || !string.Equals(movie.Sha1, MovieHeaderHash,
                    StringComparison.Ordinal))
            {
                throw new InvalidDataException(
                    "The S3K complete audio movie header identity is not exact.");
            }
            return movie;
        }

        internal static void ValidateRom(string romPath)
        {
            if (string.IsNullOrEmpty(romPath) || !Path.IsPathRooted(romPath))
                throw new ArgumentException(
                    "The locked-on S3K ROM path must be absolute.", "romPath");
            if (!File.Exists(romPath))
                throw new FileNotFoundException(
                    "The locked-on S3K ROM is absent.", romPath);
            string actual;
            try
            {
                actual = RomIdentity.ValidateSonic3kLockOn(
                    File.ReadAllBytes(romPath)).ToLowerInvariant();
            }
            catch (Exception error)
            {
                throw new InvalidDataException(
                    "The locked-on S3K ROM identity is not exact.", error);
            }
            if (!string.Equals(actual, RomSha1, StringComparison.Ordinal))
                throw new InvalidDataException(
                    "The locked-on S3K ROM SHA-1 was " + actual
                    + "; expected " + RomSha1 + ".");
        }

        private static string Sha256(string path)
        {
            byte[] digest;
            using (SHA256 sha = SHA256.Create())
            using (FileStream input = File.OpenRead(path))
                digest = sha.ComputeHash(input);
            char[] value = new char[digest.Length * 2];
            const string alphabet = "0123456789abcdef";
            for (int index = 0; index < digest.Length; index++)
            {
                value[index * 2] = alphabet[digest[index] >> 4];
                value[index * 2 + 1] = alphabet[digest[index] & 15];
            }
            return new string(value);
        }

        private sealed class PrepublicationApi : IGpgxAudioTraceApi
        {
            private readonly IGpgxAudioTraceApi inner;

            internal PrepublicationApi(IGpgxAudioTraceApi innerApi)
            {
                inner = innerApi;
            }

            public uint AbiVersion { get { return inner.AbiVersion; } }
            public uint EventSize { get { return inner.EventSize; } }
            public uint Capacity { get { return inner.Capacity; } }

            public int Configure(ref GpgxAudioObserverAdapter.Config config,
                byte[] mask, GpgxAudioObserverAdapter.ServiceKind[] kinds,
                GpgxAudioObserverAdapter.ServiceHook[] hooks,
                GpgxAudioObserverAdapter.SnapshotRange[] ranges)
            {
                if (config.AbiVersion != 1 || config.Flags != 0
                    || config.MaxContinuationFrames != 4)
                {
                    throw new InvalidDataException(
                        "The reviewed S3K manifest did not produce its legacy exact configuration.");
                }
                bool beginSeen = false;
                bool completionSeen = false;
                for (int index = 0; index < hooks.Length; index++)
                {
                    GpgxAudioObserverAdapter.ServiceHook hook = hooks[index];
                    if (hook.HookToken == UploadBeginToken)
                    {
                        if (beginSeen || hook.Action != 1 || hook.Cpu != 2
                            || hook.Pc != 0x12CE || hook.ServiceKindId != 2
                            || hook.ExpectedActiveKind != 0 || hook.Flags != 0)
                            throw InvalidUploadChain();
                        hook.Flags = PrearmPermitted;
                        beginSeen = true;
                    }
                    else if (hook.HookToken == UploadCompletionToken)
                    {
                        if (completionSeen || hook.Action != 2 || hook.Cpu != 2
                            || hook.Pc != 0x1346 || hook.ServiceKindId != 0
                            || hook.ExpectedActiveKind != 2
                            || hook.Flags != ArmProofCompletion)
                            throw InvalidUploadChain();
                        hook.Flags = ArmProofCompletion | PrearmPermitted;
                        completionSeen = true;
                    }
                    hooks[index] = hook;
                }
                if (!beginSeen || !completionSeen) throw InvalidUploadChain();
                config.AbiVersion = 2;
                config.Flags = 1;
                return inner.Configure(ref config, mask, kinds, hooks, ranges);
            }

            private static InvalidDataException InvalidUploadChain()
            {
                return new InvalidDataException(
                    "The reviewed S3K SndDrvInit proof chain changed.");
            }

            public int BeginFrame() { return inner.BeginFrame(); }
            public int EndFrame() { return inner.EndFrame(); }
            public int EventCount(out uint count, out uint overflow)
            { return inner.EventCount(out count, out overflow); }
            public int Drain(GpgxAudioTraceEvent[] events, uint capacity,
                out uint count) { return inner.Drain(events, capacity, out count); }
            public int GetFirstFault(out GpgxAudioObserverAdapter.FirstFault fault)
            { return inner.GetFirstFault(out fault); }
            public int BeginPublicationEpoch()
            { return inner.BeginPublicationEpoch(); }
            public int AbortFrame() { return inner.AbortFrame(); }
            public int Disable() { return inner.Disable(); }
        }
    }
}
