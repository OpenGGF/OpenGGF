using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class Bk2ReaderTests
    {
        private const string HeaderEntry = "Header.txt";
        private const string SyncEntry = "SyncSettings.json";
        private const string InputEntry = "Input Log.txt";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "Bk2Reader fixture sync payload has canonical SHA-256",
                FixtureSyncPayloadHasCanonicalSha256));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader reads the tracked GHZ1 prefix",
                ReadsTrackedGhz1Prefix));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader validates the canonical GHZ1 archive",
                ValidatesCanonicalGhz1Archive));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader maps every supported P1 input bit",
                MapsEverySupportedP1InputBit));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader maps Power and Reset independently",
                MapsPowerAndResetIndependently));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader derives button positions from LogKey",
                DerivesButtonPositionsFromLogKey));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader frame streams reopen the archive",
                FrameStreamsReopenArchive));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader only hashes the canonical archive identity",
                OnlyHashesCanonicalArchiveIdentity));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects duplicate and missing required entries",
                RejectsDuplicateAndMissingRequiredEntries));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects duplicate and missing Core or Platform",
                RejectsDuplicateAndMissingCoreOrPlatform));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects savestate and SaveRAM starts",
                RejectsSavestateAndSaveRamStarts));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader accepts explicit false power-on header flags",
                AcceptsExplicitFalsePowerOnFlags));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects wrong missing and extra sync fields",
                RejectsWrongMissingAndExtraSyncFields));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects duplicate sync JSON properties",
                RejectsDuplicateSyncJsonProperties));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects wrong sync JSON scalar token types",
                RejectsWrongSyncJsonScalarTokenTypes));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects six-button and controller changes",
                RejectsSixButtonAndControllerChanges));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects multiple input sections",
                RejectsMultipleInputSections));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects malformed input group lengths",
                RejectsMalformedInputGroupLengths));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects malformed input row delimiters",
                RejectsMalformedInputRowDelimiters));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects active P2",
                RejectsActiveP2));
            tests.Add(new TestMain.TestCase(
                "Bk2Reader rejects unknown groups and buttons",
                RejectsUnknownGroupsAndButtons));
        }

        private static void FixtureSyncPayloadHasCanonicalSha256()
        {
            byte[] payload = File.ReadAllBytes(FixturePath("ghz1-sync-settings.json"));
            string actual;
            using (SHA256 sha256 = SHA256.Create())
            {
                actual = ToLowerHex(sha256.ComputeHash(payload));
            }
            AssertEx.Equal(
                "8f4130ebee1f1593080371f1d257477fbb2cc68c1cb691620736639e768c97bc",
                actual);
        }

        private static void ReadsTrackedGhz1Prefix()
        {
            WithMovie(
                Fixture("ghz1-header.txt"),
                Fixture("ghz1-sync-settings.json"),
                Fixture("ghz1-input-prefix.txt"),
                path =>
                {
                    Bk2Movie movie = Bk2Reader.Read(path);
                    List<Bk2Frame> frames = movie.OpenFrameStream().ToList();

                    AssertEx.Equal("BizHawk v2.0.0", movie.MovieVersion);
                    AssertEx.Equal("Raiscan", movie.Author);
                    AssertEx.Equal("Genplus-gx", movie.Core);
                    AssertEx.Equal("GEN", movie.Platform);
                    AssertEx.Equal("Version 2.11", movie.EmulatorVersion);
                    AssertEx.Equal("Version 2.11", movie.OriginalEmulatorVersion);
                    AssertEx.Equal(
                        "Sonic The Hedgehog (W) (REV01) [!]",
                        movie.GameName);
                    AssertEx.Equal(
                        "09DADB5071EB35050067A32462E39C5F",
                        movie.Sha1);
                    AssertEx.Equal(3, movie.FrameCount);
                    AssertEx.Equal(3, frames.Count);
                    AssertEx.Equal((ushort)0, frames[0].OpenGgfInputMask);
                    AssertEx.Equal(false, frames[0].Power);
                    AssertEx.Equal(false, frames[0].Reset);
                    AssertEx.Equal(false, frames[0].P2Active);
                    AssertSyncSettings(movie.SyncSettings);
                });
        }

        private static void MapsEverySupportedP1InputBit()
        {
            var cases = new[]
            {
                new InputCase("P1 Up", 'U', 0x0001, frame => frame.P1Up),
                new InputCase("P1 Down", 'D', 0x0002, frame => frame.P1Down),
                new InputCase("P1 Left", 'L', 0x0004, frame => frame.P1Left),
                new InputCase("P1 Right", 'R', 0x0008, frame => frame.P1Right),
                new InputCase("P1 A", 'A', 0x0010, frame => frame.P1A),
                new InputCase("P1 B", 'B', 0x0020, frame => frame.P1B),
                new InputCase("P1 C", 'C', 0x0040, frame => frame.P1C),
                new InputCase("P1 Start", 'S', 0x0080, frame => frame.P1Start)
            };

            foreach (InputCase inputCase in cases)
            {
                string input = OneFrameInput(
                    StandardLogKey(),
                    P1Row(inputCase.ButtonName, inputCase.Marker));
                WithMovie(
                    Fixture("ghz1-header.txt"),
                    Fixture("ghz1-sync-settings.json"),
                    input,
                    path =>
                    {
                        Bk2Frame frame =
                            Bk2Reader.Read(path).OpenFrameStream().Single();
                        AssertEx.Equal(
                            (ushort)inputCase.ExpectedMask,
                            frame.OpenGgfInputMask);
                        AssertEx.Equal(true, inputCase.IsPressed(frame));
                        AssertEx.Equal(false, frame.P2Active);
                    });
            }
        }

        private static void ValidatesCanonicalGhz1Archive()
        {
            string path = Path.GetFullPath(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "..",
                "..",
                "..",
                "..",
                "src",
                "test",
                "resources",
                "traces",
                "s1",
                "ghz1_fullrun",
                "ghz1_fullrun.bk2"));
            Bk2Movie movie = Bk2Reader.Read(path);
            AssertEx.Equal(4806, movie.FrameCount);
            AssertEx.Equal(
                4806,
                movie.OpenFrameStream().Count());
        }

        private static void MapsPowerAndResetIndependently()
        {
            AssertSystemRow("|P.|........|........|", true, false);
            AssertSystemRow("|.R|........|........|", false, true);
        }

        private static void DerivesButtonPositionsFromLogKey()
        {
            const string reorderedLogKey =
                "LogKey:#Reset|Power|"
                + "#P1 Start|P1 C|P1 B|P1 A|P1 Right|P1 Left|P1 Down|P1 Up|"
                + "#P2 Start|P2 C|P2 B|P2 A|P2 Right|P2 Left|P2 Down|P2 Up|";
            string input = OneFrameInput(
                reorderedLogKey,
                "|.P|.......U|........|");
            WithMovie(
                Fixture("ghz1-header.txt"),
                Fixture("ghz1-sync-settings.json"),
                input,
                path =>
                {
                    Bk2Frame frame =
                        Bk2Reader.Read(path).OpenFrameStream().Single();
                    AssertEx.Equal(true, frame.Power);
                    AssertEx.Equal(false, frame.Reset);
                    AssertEx.Equal(true, frame.P1Up);
                    AssertEx.Equal((ushort)0x0001, frame.OpenGgfInputMask);
                });
        }

        private static void FrameStreamsReopenArchive()
        {
            string directory = CreateTempDirectory();
            string path = Path.Combine(directory, "synthetic.bk2");
            try
            {
                WriteMovie(
                    path,
                    Entries(
                        Fixture("ghz1-header.txt"),
                        Fixture("ghz1-sync-settings.json"),
                        OneFrameInput(
                            StandardLogKey(),
                            P1Row("P1 Up", 'U'))));
                Bk2Movie movie = Bk2Reader.Read(path);
                AssertEx.Equal(
                    (ushort)0x0001,
                    movie.OpenFrameStream().Single().OpenGgfInputMask);

                File.Delete(path);
                WriteMovie(
                    path,
                    Entries(
                        Fixture("ghz1-header.txt"),
                        Fixture("ghz1-sync-settings.json"),
                        OneFrameInput(
                            StandardLogKey(),
                            P1Row("P1 Right", 'R'))));
                AssertEx.Equal(
                    (ushort)0x0008,
                    movie.OpenFrameStream().Single().OpenGgfInputMask);
            }
            finally
            {
                Directory.Delete(directory, true);
            }
        }

        private static void OnlyHashesCanonicalArchiveIdentity()
        {
            string directory = CreateTempDirectory();
            string path = Path.Combine(
                directory,
                "copy",
                "src",
                "test",
                "resources",
                "traces",
                "s1",
                "ghz1_fullrun",
                "ghz1_fullrun.bk2");
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(path));
                WriteMovie(
                    path,
                    Entries(
                        Fixture("ghz1-header.txt"),
                        Fixture("ghz1-sync-settings.json"),
                        Fixture("ghz1-input-prefix.txt")));

                AssertEx.Equal(3, Bk2Reader.Read(path).FrameCount);
            }
            finally
            {
                Directory.Delete(directory, true);
            }
        }

        private static void RejectsDuplicateAndMissingRequiredEntries()
        {
            string[] required = { HeaderEntry, SyncEntry, InputEntry };
            foreach (string missing in required)
            {
                List<ArchiveEntry> entries = Entries(
                    Fixture("ghz1-header.txt"),
                    Fixture("ghz1-sync-settings.json"),
                    Fixture("ghz1-input-prefix.txt"));
                entries.RemoveAll(entry => entry.Name == missing);
                AssertInvalid(entries, "missing required entry " + missing);
            }

            foreach (string duplicate in required)
            {
                List<ArchiveEntry> entries = Entries(
                    Fixture("ghz1-header.txt"),
                    Fixture("ghz1-sync-settings.json"),
                    Fixture("ghz1-input-prefix.txt"));
                ArchiveEntry original =
                    entries.Single(entry => entry.Name == duplicate);
                entries.Add(new ArchiveEntry(original.Name, original.Content));
                AssertInvalid(entries, "duplicate entry " + duplicate);
            }
        }

        private static void RejectsDuplicateAndMissingCoreOrPlatform()
        {
            string header = Fixture("ghz1-header.txt");
            AssertHeaderInvalid(
                header.Replace("Core Genplus-gx\r\n", string.Empty)
                    .Replace("Core Genplus-gx\n", string.Empty),
                "missing Core");
            AssertHeaderInvalid(header + "Core Genplus-gx\r\n", "duplicate Core");
            AssertHeaderInvalid(
                header.Replace("Core Genplus-gx", "Core OtherCore"),
                "Core");
            AssertHeaderInvalid(
                header.Replace("Platform GEN\r\n", string.Empty)
                    .Replace("Platform GEN\n", string.Empty),
                "missing Platform");
            AssertHeaderInvalid(header + "Platform GEN\r\n", "duplicate Platform");
            AssertHeaderInvalid(
                header.Replace("Platform GEN", "Platform SMS"),
                "Platform");
        }

        private static void RejectsSavestateAndSaveRamStarts()
        {
            string header = Fixture("ghz1-header.txt");
            AssertHeaderInvalid(
                header + "StartsFromSavestate True\r\n",
                "StartsFromSavestate");
            AssertHeaderInvalid(
                header + "StartsFromSaveRam True\r\n",
                "StartsFromSaveRam");
        }

        private static void AcceptsExplicitFalsePowerOnFlags()
        {
            string header = Fixture("ghz1-header.txt")
                + "StartsFromSavestate False\r\n"
                + "StartsFromSaveRam False\r\n";
            WithMovie(
                header,
                Fixture("ghz1-sync-settings.json"),
                Fixture("ghz1-input-prefix.txt"),
                path => AssertEx.Equal(3, Bk2Reader.Read(path).FrameCount));
        }

        private static void RejectsWrongMissingAndExtraSyncFields()
        {
            string sync = Fixture("ghz1-sync-settings.json");
            AssertSyncInvalid(
                sync.Replace("\"Region\":0", "\"Region\":2"),
                "Region");
            AssertSyncInvalid(
                sync.Replace("\"Region\":0,", string.Empty),
                "missing sync field Region");
            AssertSyncInvalid(
                sync.Replace(
                    "\"SpritesAlwaysOnTop\":false",
                    "\"SpritesAlwaysOnTop\":false,\"Unexpected\":0"),
                "unknown sync field Unexpected");
        }

        private static void RejectsDuplicateSyncJsonProperties()
        {
            string sync = Fixture("ghz1-sync-settings.json");
            AssertSyncInvalid(
                sync.Replace("\"Region\":0,", "\"Region\":0,\"Region\":2,"),
                "duplicate");
            AssertSyncInvalid(
                sync.Replace("\"Region\":0,", "\"Region\":0,\"Region\":0,"),
                "duplicate");

            string settings = sync.Substring(
                "{\"o\":".Length,
                sync.Length - "{\"o\":".Length - 1);
            AssertSyncInvalid(
                "{\"o\":" + settings + ",\"o\":" + settings + "}",
                "duplicate");
        }

        private static void RejectsWrongSyncJsonScalarTokenTypes()
        {
            string sync = Fixture("ghz1-sync-settings.json");
            AssertSyncInvalid(
                sync.Replace("\"UseSixButton\":false", "\"UseSixButton\":\"false\""),
                "field UseSixButton must be a JSON boolean");
            AssertSyncInvalid(
                sync.Replace("\"UseSixButton\":false", "\"UseSixButton\":0"),
                "field UseSixButton must be a JSON boolean");
            AssertSyncInvalid(
                sync.Replace("\"Region\":0", "\"Region\":\"0\""),
                "field Region must be a JSON integer");
            AssertSyncInvalid(
                sync.Replace("\"Region\":0", "\"Region\":0.0"),
                "field Region must be a JSON integer");
            AssertSyncInvalid(
                sync.Replace("\"LowGain\":1.0", "\"LowGain\":\"1.0\""),
                "field LowGain must be a JSON floating-point number");
            AssertSyncInvalid(
                sync.Replace(
                    "\"$type\":\"BizHawk.Emulation.Cores.Consoles.Sega.gpgx."
                        + "GPGX+GPGXSyncSettings, BizHawk.Emulation.Cores\"",
                    "\"$type\":1"),
                "field $type must be a JSON string");
        }

        private static void RejectsSixButtonAndControllerChanges()
        {
            string sync = Fixture("ghz1-sync-settings.json");
            AssertSyncInvalid(
                sync.Replace("\"UseSixButton\":false", "\"UseSixButton\":true"),
                "UseSixButton");
            AssertSyncInvalid(
                sync.Replace("\"ControlTypeLeft\":1", "\"ControlTypeLeft\":0"),
                "ControlTypeLeft");
            AssertSyncInvalid(
                sync.Replace("\"ControlTypeRight\":1", "\"ControlTypeRight\":0"),
                "ControlTypeRight");
        }

        private static void RejectsMultipleInputSections()
        {
            string section = Fixture("ghz1-input-prefix.txt");
            AssertInputInvalid(section + section, "multiple input sections");
        }

        private static void RejectsMalformedInputGroupLengths()
        {
            string[] rows =
            {
                "|.|........|........|",
                "|..|.......|........|",
                "|..|.........|........|",
                "|..|........|.......|",
                "|..|........|.........|"
            };
            foreach (string row in rows)
            {
                AssertInputInvalid(
                    OneFrameInput(StandardLogKey(), row),
                    "group length");
            }
        }

        private static void RejectsMalformedInputRowDelimiters()
        {
            string[] rows =
            {
                "..|........|........|",
                "|..|........|........",
                "|..||........|........|",
                "|..|........||........|"
            };
            foreach (string row in rows)
            {
                AssertInputInvalid(
                    OneFrameInput(StandardLogKey(), row),
                    "malformed input row");
            }
        }

        private static void RejectsActiveP2()
        {
            AssertInputInvalid(
                OneFrameInput(
                    StandardLogKey(),
                    "|..|........|U.......|"),
                "P2 input is not supported");
        }

        private static void RejectsUnknownGroupsAndButtons()
        {
            AssertInputInvalid(
                Fixture("ghz1-input-prefix.txt").Replace("#P2 Up", "#P3 Up"),
                "unknown input group");
            AssertInputInvalid(
                Fixture("ghz1-input-prefix.txt").Replace("P1 Up", "P1 X"),
                "unknown input button");
        }

        private static void AssertSystemRow(
            string row,
            bool expectedPower,
            bool expectedReset)
        {
            WithMovie(
                Fixture("ghz1-header.txt"),
                Fixture("ghz1-sync-settings.json"),
                OneFrameInput(StandardLogKey(), row),
                path =>
                {
                    Bk2Frame frame =
                        Bk2Reader.Read(path).OpenFrameStream().Single();
                    AssertEx.Equal(expectedPower, frame.Power);
                    AssertEx.Equal(expectedReset, frame.Reset);
                    AssertEx.Equal((ushort)0, frame.OpenGgfInputMask);
                    AssertEx.Equal(false, frame.P2Active);
                });
        }

        private static void AssertHeaderInvalid(string header, string message)
        {
            AssertInvalid(
                Entries(
                    header,
                    Fixture("ghz1-sync-settings.json"),
                    Fixture("ghz1-input-prefix.txt")),
                message);
        }

        private static void AssertSyncInvalid(string sync, string message)
        {
            AssertInvalid(
                Entries(
                    Fixture("ghz1-header.txt"),
                    sync,
                    Fixture("ghz1-input-prefix.txt")),
                message);
        }

        private static void AssertInputInvalid(string input, string message)
        {
            AssertInvalid(
                Entries(
                    Fixture("ghz1-header.txt"),
                    Fixture("ghz1-sync-settings.json"),
                    input),
                message);
        }

        private static void AssertInvalid(
            IList<ArchiveEntry> entries,
            string messageFragment)
        {
            string directory = CreateTempDirectory();
            string path = Path.Combine(directory, "invalid.bk2");
            try
            {
                WriteMovie(path, entries);
                AssertEx.Throws<InvalidDataException>(
                    () => Bk2Reader.Read(path),
                    messageFragment);
            }
            finally
            {
                Directory.Delete(directory, true);
            }
        }

        private static void WithMovie(
            string header,
            string sync,
            string input,
            Action<string> body)
        {
            string directory = CreateTempDirectory();
            string path = Path.Combine(directory, "synthetic.bk2");
            try
            {
                WriteMovie(path, Entries(header, sync, input));
                body(path);
            }
            finally
            {
                Directory.Delete(directory, true);
            }
        }

        private static List<ArchiveEntry> Entries(
            string header,
            string sync,
            string input)
        {
            return new List<ArchiveEntry>
            {
                new ArchiveEntry(HeaderEntry, header),
                new ArchiveEntry(SyncEntry, sync),
                new ArchiveEntry(InputEntry, input)
            };
        }

        private static void WriteMovie(
            string path,
            IEnumerable<ArchiveEntry> entries)
        {
            using (var stream = File.Create(path))
            using (var archive = new ZipArchive(
                stream,
                ZipArchiveMode.Create,
                false))
            {
                foreach (ArchiveEntry item in entries)
                {
                    ZipArchiveEntry entry =
                        archive.CreateEntry(item.Name, CompressionLevel.NoCompression);
                    using (Stream entryStream = entry.Open())
                    using (var writer = new StreamWriter(
                        entryStream,
                        new UTF8Encoding(false)))
                    {
                        writer.Write(item.Content);
                    }
                }
            }
        }

        private static string OneFrameInput(string logKey, string row)
        {
            return "[Input]\r\n"
                + logKey + "\r\n"
                + row + "\r\n"
                + "[/Input]\r\n";
        }

        private static string StandardLogKey()
        {
            string input = Fixture("ghz1-input-prefix.txt");
            using (var reader = new StringReader(input))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    if (line.StartsWith("LogKey:", StringComparison.Ordinal))
                    {
                        return line;
                    }
                }
            }
            throw new InvalidOperationException("Fixture LogKey was not found.");
        }

        private static string P1Row(string buttonName, char marker)
        {
            string[] buttons =
            {
                "P1 Up",
                "P1 Down",
                "P1 Left",
                "P1 Right",
                "P1 A",
                "P1 B",
                "P1 C",
                "P1 Start"
            };
            char[] state = "........".ToCharArray();
            int index = Array.IndexOf(buttons, buttonName);
            state[index] = marker;
            return "|..|" + new string(state) + "|........|";
        }

        private static string Fixture(string name)
        {
            return File.ReadAllText(FixturePath(name));
        }

        private static string FixturePath(string name)
        {
            return Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "fixtures",
                name);
        }

        private static string CreateTempDirectory()
        {
            string path = Path.Combine(
                Path.GetTempPath(),
                "openggf-bk2-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(path);
            return path;
        }

        private static string ToLowerHex(byte[] value)
        {
            return BitConverter.ToString(value).Replace("-", "").ToLowerInvariant();
        }

        private static void AssertSyncSettings(GPGX.GPGXSyncSettings settings)
        {
            AssertEx.Equal(false, settings.UseSixButton);
            AssertEx.Equal(GPGX.ControlType.Normal, settings.ControlTypeLeft);
            AssertEx.Equal(GPGX.ControlType.Normal, settings.ControlTypeRight);
            AssertEx.Equal(LibGPGX.Region.Autodetect, settings.Region);
            AssertEx.Equal(LibGPGX.ForceVDP.Disabled, settings.ForceVDP);
            AssertEx.Equal(false, settings.LoadBIOS);
            AssertEx.Equal(
                LibGPGX.InitSettings.OverscanType.All,
                settings.Overscan);
            AssertEx.Equal(false, settings.GGExtra);
            AssertEx.Equal(
                LibGPGX.InitSettings.SMSFMSoundChipType.YM2413_MAME,
                settings.SMSFMSoundChip);
            AssertEx.Equal(
                LibGPGX.InitSettings.GenesisFMSoundChipType.MAME_YM2612,
                settings.GenesisFMSoundChip);
            AssertEx.Equal(
                LibGPGX.InitSettings.FilterType.None,
                settings.Filter);
            AssertEx.Equal((ushort)26214, settings.LowPassRange);
            AssertEx.Equal((short)880, settings.LowFreq);
            AssertEx.Equal((short)5000, settings.HighFreq);
            AssertEx.Equal(1.0f, settings.LowGain);
            AssertEx.Equal(1.0f, settings.MidGain);
            AssertEx.Equal(1.0f, settings.HighGain);
            AssertEx.Equal(4294902015u, settings.BackdropColor);
            AssertEx.Equal(false, settings.SpritesAlwaysOnTop);
        }

        private sealed class InputCase
        {
            public InputCase(
                string buttonName,
                char marker,
                int expectedMask,
                Func<Bk2Frame, bool> isPressed)
            {
                ButtonName = buttonName;
                Marker = marker;
                ExpectedMask = expectedMask;
                IsPressed = isPressed;
            }

            public string ButtonName { get; private set; }
            public char Marker { get; private set; }
            public int ExpectedMask { get; private set; }
            public Func<Bk2Frame, bool> IsPressed { get; private set; }
        }

        private sealed class ArchiveEntry
        {
            public ArchiveEntry(string name, string content)
            {
                Name = name;
                Content = content;
            }

            public string Name { get; private set; }
            public string Content { get; private set; }
        }
    }
}
