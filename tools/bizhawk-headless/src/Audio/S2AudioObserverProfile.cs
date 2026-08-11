using System;
using System.Globalization;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Closed Sonic 2 REV01 view of the reviewed shared native observer
    /// manifest and its already-proven complete-run capability evidence.
    /// This class selects and validates data; the shared collector remains the
    /// sole owner of native ABI and service reconstruction semantics.
    /// </summary>
    internal static class S2AudioObserverProfile
    {
        internal const string Game = "s2";
        internal const string RomSha1 =
            "8bca5dcef1af3e00098666fd892dc1c2a76333f9";
        internal const string MovieName =
            "sonic-2-sonic-tails-complete-emeralds.bk2";
        internal const string MovieSha256 =
            "e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5";
        internal const string ServiceManifestSha256 =
            "ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0";
        internal const string CapabilityTemplateSha256Expected =
            "97b800c1421a5a15d4dc53acd99fa853399a57a9c46c7b79a3eff1032eb7f098";
        internal const string ObserverIdentitySha256 =
            "b8023a7a80cb961d97c80bcb3835480aca9a78f3eb1ede5490c9295e2ca9bd60";
        internal const string CompleteEventDigestSha256 =
            "c2b2f82374aaa16144b6bf121df051dcd5b4ba095431c16cf6224adc633de41d";
        internal const string TerminalZ80Sha256 =
            "355ee9b9b697e674482dd8fe9329e0216d5d32a136454a223f79e784b74ccc02";
        internal const string FrontierDigestSha256 =
            "2afa645a9471a7e084fa4273a9cfa0978868fe7be4f9a33f72f73de2ca907804";

        private const string CompressedCoreSha256 =
            "93be2835112aeb73bd38cd467cfa0a55f38e3b6ceb7bed642033eb73656cc453";
        private const string DecompressedCoreSha256 =
            "c29a3631c5aa6b4566dd80f2dcca5138426adaa624dbb7c450cdaead09cd4bd6";
        private const string ManagedCoreSha256 =
            "0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7";
        private const string ManagedCommonSha256 =
            "f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4";
        private const string WaterboxHostSha256 =
            "d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78";

        internal sealed class Capability
        {
            internal Capability(int frames, long events,
                int maximumFrameOccupancy, int openServicesAtCutoff,
                int pendingServicesAtCutoff, string eventDigestSha256)
            {
                Frames = frames;
                Events = events;
                MaximumFrameOccupancy = maximumFrameOccupancy;
                OpenServicesAtCutoff = openServicesAtCutoff;
                PendingServicesAtCutoff = pendingServicesAtCutoff;
                EventDigestSha256 = eventDigestSha256;
            }

            internal int Frames { get; private set; }
            internal long Events { get; private set; }
            internal int MaximumFrameOccupancy { get; private set; }
            internal int OpenServicesAtCutoff { get; private set; }
            internal int PendingServicesAtCutoff { get; private set; }
            internal string EventDigestSha256 { get; private set; }
        }

        internal sealed class InstallationIdentity
        {
            internal InstallationIdentity(string installationId,
                string coreId, int abiVersion, string buildId)
            {
                InstallationId = installationId;
                CoreId = coreId;
                AbiVersion = abiVersion;
                BuildId = buildId;
            }

            internal string InstallationId { get; private set; }
            internal string CoreId { get; private set; }
            internal int AbiVersion { get; private set; }
            internal string BuildId { get; private set; }
        }

        internal static CompleteRunAudioObserver CreateObserver(
            string serviceManifestPath, string capabilityPath,
            IGpgxAudioTraceApi api)
        {
            if (api == null) throw new ArgumentNullException("api");
            LoadCapability(serviceManifestPath, capabilityPath);
            return GpgxAudioServiceManifest.Load(
                serviceManifestPath, Game, api);
        }

        internal static Capability LoadCapability(
            string serviceManifestPath, string capabilityPath)
        {
            RequireAbsoluteFile(serviceManifestPath, "service manifest");
            RequireAbsoluteFile(capabilityPath, "capability");
            RequireEqual(ServiceManifestSha256,
                Sha256File(serviceManifestPath), "service manifest identity");
            RequireEqual(CapabilityTemplateSha256Expected,
                CapabilityTemplateSha256(capabilityPath),
                "capability template identity");

            JObject root = JObject.Parse(File.ReadAllText(capabilityPath));
            RequireEqual(Sha256File(typeof(GpgxHost).Assembly.Location),
                RequiredString(root,"task8_harness_executable_sha256"),
                "capability executable identity");
            RequireEqual("openggf.gpgx-audio-capability.v1",
                RequiredString(root, "schema"), "capability schema");
            RequireEqual(ServiceManifestSha256,
                RequiredString(root, "service_manifest_sha256"),
                "capability service-manifest identity");
            RequireEqual(ObserverIdentitySha256,
                RequiredString(root, "observer_identity_sha256"),
                "capability observer identity");

            JObject run = RequiredObject(RequiredObject(root, "runs"), Game);
            JObject movie = RequiredObject(run, "movie");
            if (RequiredString(movie, "name") != MovieName
                || RequiredString(movie, "sha256") != MovieSha256
                || RequiredInt(movie, "rows") != 259590)
            {
                throw new InvalidDataException("S2 movie identity changed.");
            }

            JObject complete = RequiredObject(run, "complete");
            int frames = RequiredInt(complete, "frames");
            long events = RequiredLong(complete, "events");
            int occupancy = RequiredInt(complete, "maximum_frame_occupancy");
            int open = RequiredInt(complete, "open_services_at_cutoff");
            int pending = RequiredInt(complete, "pending_services_at_cutoff");
            string frontier = RequiredString(
                complete, "frontier_digest_sha256");
            string terminal = RequiredString(complete, "terminal_z80_sha256");
            string digest = RequiredString(complete, "event_digest_sha256");
            if (frames != 259590 || events != 169986419L)
                throw new InvalidDataException(
                    "S2 complete event count or frame count changed.");
            if (occupancy != 1825 || open != 0 || pending != 0)
                throw new InvalidDataException(
                    "S2 complete cutoff capability changed.");
            RequireEqual(FrontierDigestSha256, frontier,
                "S2 cutoff frontier identity");
            RequireEqual(TerminalZ80Sha256, terminal,
                "S2 terminal Z80 identity");
            RequireEqual(CompleteEventDigestSha256, digest,
                "S2 complete event identity");
            return new Capability(frames, events, occupancy, open, pending,
                digest);
        }

        internal static InstallationIdentity VerifyInstallation(string home)
        {
            if (string.IsNullOrEmpty(home) || !Path.IsPathRooted(home)
                || !Directory.Exists(home)
                || LinuxPathEntry.IsSymbolicLink(home))
                throw new InvalidDataException(
                    "Observer installation must be an existing absolute directory.");
            string identityPath = PlainFile(home,
                "gpgx-audio-observer-source/identity.json");
            RequireEqual(ObserverIdentitySha256, Sha256File(identityPath),
                "observer build identity");
            RequireFileHash(home, "dll/gpgx.wbx.zst", CompressedCoreSha256);
            RequireFileHash(home, "gpgx-audio-observer-source/gpgx.wbx",
                DecompressedCoreSha256);
            RequireFileHash(home, "dll/BizHawk.Emulation.Cores.dll",
                ManagedCoreSha256);
            RequireFileHash(home, "dll/BizHawk.Emulation.Common.dll",
                ManagedCommonSha256);
            RequireFileHash(home, "dll/libwaterboxhost.so",
                WaterboxHostSha256);

            JObject identity = JObject.Parse(File.ReadAllText(identityPath));
            RequireEqual("openggf.gpgx-audio-observer-build.v1",
                RequiredString(identity, "schema"), "observer identity schema");
            RequireEqual(CompressedCoreSha256,
                RequiredString(identity, "compressed_sha256"),
                "installed compressed core identity");
            RequireEqual(DecompressedCoreSha256,
                RequiredString(identity, "decompressed_sha256"),
                "installed decompressed core identity");
            int abi = RequiredInt(identity, "abi_version");
            if (abi != 3 || RequiredInt(identity, "event_size") != 32
                || RequiredInt(identity, "capacity") != 65536)
                throw new InvalidDataException(
                    "Installed observer ABI identity changed.");
            return new InstallationIdentity(
                RequiredString(identity, "installation_id"),
                RequiredString(identity, "core_id"), abi,
                RequiredString(identity, "build_id"));
        }

        private static void RequireFileHash(
            string home, string relative, string expected)
        {
            RequireEqual(expected, Sha256File(PlainFile(home, relative)),
                "installed artifact " + relative);
        }

        private static string PlainFile(string root, string relative)
        {
            string rootFull = Path.GetFullPath(root);
            string path = Path.GetFullPath(Path.Combine(rootFull,
                relative.Replace('/', Path.DirectorySeparatorChar)));
            string prefix = rootFull.EndsWith(Path.DirectorySeparatorChar.ToString(),
                StringComparison.Ordinal)
                ? rootFull : rootFull + Path.DirectorySeparatorChar;
            if (!path.StartsWith(prefix, StringComparison.Ordinal)
                || !File.Exists(path))
                throw new InvalidDataException(
                    "Missing observer installation artifact: " + relative + ".");
            if (LinuxPathEntry.IsSymbolicLink(path))
                throw new InvalidDataException(
                    "Observer installation artifact is a symbolic link: "
                    + relative + ".");
            return path;
        }

        private static void RequireAbsoluteFile(string path, string label)
        {
            if (string.IsNullOrEmpty(path) || !Path.IsPathRooted(path)
                || !File.Exists(path))
                throw new InvalidDataException(
                    "S2 " + label + " must be an existing absolute file.");
        }

        private static string Sha256File(string path)
        {
            using (FileStream input = File.OpenRead(path))
            using (SHA256 digest = SHA256.Create())
                return ToHex(digest.ComputeHash(input));
        }

        internal static string CapabilityTemplateSha256(string path)
        {
            RequireAbsoluteFile(path,"capability");
            byte[] raw=File.ReadAllBytes(path);
            byte[] name=Encoding.ASCII.GetBytes(
                "\"task8_harness_executable_sha256\"");
            byte[] prefix=Encoding.ASCII.GetBytes(
                "\"task8_harness_executable_sha256\": \"");
            int occurrences=0,start=-1;
            for(int i=0;i<=raw.Length-name.Length;i++)
                if(Matches(raw,i,name)) occurrences++;
            for(int i=0;i<=raw.Length-prefix.Length;i++)
                if(Matches(raw,i,prefix))
                {if(start!=-1)throw new InvalidDataException(
                    "Capability must contain exactly one executable identity field.");
                    start=i+prefix.Length;}
            if(occurrences!=1||start<0||start+64>=raw.Length||raw[start+64]!='\"')
                throw new InvalidDataException(
                    "Capability must contain exactly one executable identity field.");
            byte[] normalized=(byte[])raw.Clone();
            for(int i=0;i<64;i++)
            {
                byte value=raw[start+i];
                if(!((value>='0'&&value<='9')||(value>='a'&&value<='f')))
                    throw new InvalidDataException(
                        "Capability executable identity must be lowercase hexadecimal.");
                normalized[start+i]=(byte)'0';
            }
            using(SHA256 digest=SHA256.Create())
                return ToHex(digest.ComputeHash(normalized));
        }

        private static bool Matches(byte[] value,int offset,byte[] expected)
        {
            for(int i=0;i<expected.Length;i++)
                if(value[offset+i]!=expected[i])return false;
            return true;
        }

        private static string ToHex(byte[] value)
        {
            char[] result = new char[value.Length * 2];
            const string hex = "0123456789abcdef";
            for (int index = 0; index < value.Length; index++)
            {
                result[index * 2] = hex[value[index] >> 4];
                result[index * 2 + 1] = hex[value[index] & 15];
            }
            return new string(result);
        }

        private static JObject RequiredObject(JObject value, string name)
        {
            JObject result = value[name] as JObject;
            if (result == null)
                throw new InvalidDataException("Missing object: " + name + ".");
            return result;
        }

        private static string RequiredString(JObject value, string name)
        {
            string result = (string)value[name];
            if (string.IsNullOrEmpty(result))
                throw new InvalidDataException("Missing string: " + name + ".");
            return result;
        }

        private static int RequiredInt(JObject value, string name)
        {
            JToken token = value[name];
            int result;
            if (token == null || !int.TryParse(token.ToString(),
                NumberStyles.Integer, CultureInfo.InvariantCulture, out result))
                throw new InvalidDataException("Missing integer: " + name + ".");
            return result;
        }

        private static long RequiredLong(JObject value, string name)
        {
            JToken token = value[name];
            long result;
            if (token == null || !long.TryParse(token.ToString(),
                NumberStyles.Integer, CultureInfo.InvariantCulture, out result))
                throw new InvalidDataException("Missing integer: " + name + ".");
            return result;
        }

        private static void RequireEqual(
            string expected, string actual, string label)
        {
            if (!string.Equals(expected, actual, StringComparison.Ordinal))
                throw new InvalidDataException(label + " changed.");
        }
    }
}
