using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless
{
    public static class Bk2Reader
    {
        private const string HeaderEntryName = "Header.txt";
        private const string SyncEntryName = "SyncSettings.json";
        private const string InputEntryName = "Input Log.txt";
        private const string CanonicalMovieRelativePath =
            "src/test/resources/traces/s1/ghz1_fullrun/ghz1_fullrun.bk2";
        private const string CanonicalMovieSha256 =
            "dced61b2d3a3346b2ecd62254140497ef2827374c1de8597780f91e39ca0dcea";
        private const string SyncSettingsType =
            "BizHawk.Emulation.Cores.Consoles.Sega.gpgx.GPGX+"
            + "GPGXSyncSettings, BizHawk.Emulation.Cores";

        private static readonly string[] RequiredEntryNames =
        {
            HeaderEntryName,
            SyncEntryName,
            InputEntryName
        };

        private static readonly string CanonicalMoviePath = Path.GetFullPath(
            Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "..",
                "..",
                "..",
                "..",
                CanonicalMovieRelativePath));

        private static readonly string[] ExpectedSyncFields =
        {
            "$type",
            "UseSixButton",
            "ControlTypeLeft",
            "ControlTypeRight",
            "Region",
            "ForceVDP",
            "LoadBIOS",
            "Overscan",
            "GGExtra",
            "SMSFMSoundChip",
            "GenesisFMSoundChip",
            "Filter",
            "LowPassRange",
            "LowFreq",
            "HighFreq",
            "LowGain",
            "MidGain",
            "HighGain",
            "BackdropColor",
            "SpritesAlwaysOnTop"
        };

        private static readonly string[] ExpectedSystemButtons =
        {
            "Power",
            "Reset"
        };

        private static readonly string[] ExpectedP1Buttons =
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

        private static readonly string[] ExpectedP2Buttons =
        {
            "P2 Up",
            "P2 Down",
            "P2 Left",
            "P2 Right",
            "P2 A",
            "P2 B",
            "P2 C",
            "P2 Start"
        };

        public static Bk2Movie Read(string path)
        {
            if (string.IsNullOrEmpty(path))
            {
                throw new ArgumentException("A BK2 path is required.", "path");
            }

            string fullPath = Path.GetFullPath(path);
            VerifyCanonicalMovieHash(fullPath);

            Dictionary<string, string> headers;
            GPGX.GPGXSyncSettings syncSettings;
            int frameCount;
            using (FileStream stream = File.OpenRead(fullPath))
            using (var archive = new ZipArchive(
                stream,
                ZipArchiveMode.Read,
                false))
            {
                Dictionary<string, ZipArchiveEntry> required =
                    GetRequiredEntries(archive);
                headers = ParseHeader(ReadEntryText(required[HeaderEntryName]));
                syncSettings =
                    ParseSyncSettings(ReadEntryText(required[SyncEntryName]));
                using (Stream inputStream = required[InputEntryName].Open())
                using (var inputReader = new StreamReader(inputStream))
                {
                    frameCount = ReadInputFrames(inputReader).Count();
                }
            }

            var movie = new Bk2Movie(fullPath)
            {
                MovieVersion = GetHeader(headers, "MovieVersion"),
                Author = GetHeader(headers, "Author"),
                Core = headers["Core"],
                Platform = headers["Platform"],
                EmulatorVersion = GetHeader(headers, "emuVersion"),
                OriginalEmulatorVersion =
                    GetHeader(headers, "OriginalEmuVersion"),
                GameName = GetHeader(headers, "GameName"),
                Sha1 = GetHeader(headers, "SHA1"),
                FrameCount = frameCount,
                SyncSettings = syncSettings
            };
            return movie;
        }

        internal static IEnumerable<Bk2Frame> OpenFrameStream(string path)
        {
            return OpenFrameStreamIterator(path);
        }

        private static IEnumerable<Bk2Frame> OpenFrameStreamIterator(string path)
        {
            using (FileStream stream = File.OpenRead(path))
            using (var archive = new ZipArchive(
                stream,
                ZipArchiveMode.Read,
                false))
            {
                ZipArchiveEntry inputEntry =
                    GetRequiredEntries(archive)[InputEntryName];
                using (Stream inputStream = inputEntry.Open())
                using (var inputReader = new StreamReader(inputStream))
                {
                    foreach (Bk2Frame frame in ReadInputFrames(inputReader))
                    {
                        yield return frame;
                    }
                }
            }
        }

        private static Dictionary<string, ZipArchiveEntry> GetRequiredEntries(
            ZipArchive archive)
        {
            var result = new Dictionary<string, ZipArchiveEntry>(
                StringComparer.Ordinal);
            foreach (string name in RequiredEntryNames)
            {
                List<ZipArchiveEntry> matches = archive.Entries
                    .Where(entry => string.Equals(
                        entry.FullName,
                        name,
                        StringComparison.Ordinal))
                    .ToList();
                if (matches.Count == 0)
                {
                    throw new InvalidDataException(
                        "BK2 is missing required entry " + name + ".");
                }
                if (matches.Count != 1)
                {
                    throw new InvalidDataException(
                        "BK2 contains duplicate entry " + name + ".");
                }
                result.Add(name, matches[0]);
            }
            return result;
        }

        private static string ReadEntryText(ZipArchiveEntry entry)
        {
            using (Stream stream = entry.Open())
            using (var reader = new StreamReader(stream))
            {
                return reader.ReadToEnd();
            }
        }

        private static Dictionary<string, string> ParseHeader(string text)
        {
            var headers = new Dictionary<string, string>(
                StringComparer.Ordinal);
            using (var reader = new StringReader(text))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    if (line.Length == 0)
                    {
                        continue;
                    }

                    int separator = line.IndexOf(' ');
                    if (separator <= 0)
                    {
                        throw new InvalidDataException(
                            "Malformed BK2 Header.txt line: " + line);
                    }

                    string key = line.Substring(0, separator);
                    string value = line.Substring(separator + 1);
                    if (headers.ContainsKey(key))
                    {
                        throw new InvalidDataException(
                            "BK2 Header.txt contains duplicate " + key + ".");
                    }
                    headers.Add(key, value);
                }
            }

            RequireHeader(headers, "Core", "Genplus-gx");
            RequireHeader(headers, "Platform", "GEN");
            RequirePowerOnHeader(headers, "StartsFromSavestate");
            RequirePowerOnHeader(headers, "StartsFromSaveRam");
            return headers;
        }

        private static void RequireHeader(
            IDictionary<string, string> headers,
            string key,
            string expected)
        {
            string value;
            if (!headers.TryGetValue(key, out value))
            {
                throw new InvalidDataException(
                    "BK2 Header.txt is missing " + key + ".");
            }
            if (!string.Equals(value, expected, StringComparison.Ordinal))
            {
                throw new InvalidDataException(
                    "BK2 Header.txt " + key + " is " + value
                    + "; expected " + expected + ".");
            }
        }

        private static void RequirePowerOnHeader(
            IDictionary<string, string> headers,
            string key)
        {
            string value;
            if (!headers.TryGetValue(key, out value))
            {
                return;
            }

            bool parsed;
            if (!bool.TryParse(value, out parsed) || parsed)
            {
                throw new InvalidDataException(
                    "BK2 Header.txt " + key
                    + " must be absent or False.");
            }
        }

        private static string GetHeader(
            IDictionary<string, string> headers,
            string key)
        {
            string value;
            return headers.TryGetValue(key, out value) ? value : null;
        }

        private static GPGX.GPGXSyncSettings ParseSyncSettings(string json)
        {
            JObject root;
            try
            {
                root = JObject.Parse(
                    json,
                    new JsonLoadSettings
                    {
                        DuplicatePropertyNameHandling =
                            DuplicatePropertyNameHandling.Error
                    });
            }
            catch (JsonException exception)
            {
                throw new InvalidDataException(
                    "SyncSettings.json is not valid JSON or contains a duplicate property.",
                    exception);
            }

            ValidateExactFields(root, new[] { "o" }, "sync root");
            JObject settingsObject = root["o"] as JObject;
            if (settingsObject == null)
            {
                throw new InvalidDataException(
                    "SyncSettings.json field o must be an object.");
            }
            ValidateExactFields(
                settingsObject,
                ExpectedSyncFields,
                "sync");

            var serializer = new JsonSerializer
            {
                MetadataPropertyHandling = MetadataPropertyHandling.Ignore
            };
            SyncSettingsDto dto;
            try
            {
                dto = settingsObject.ToObject<SyncSettingsDto>(serializer);
            }
            catch (JsonException exception)
            {
                throw new InvalidDataException(
                    "SyncSettings.json contains an invalid field value.",
                    exception);
            }

            RequireSyncValue("$type", SyncSettingsType, dto.Type);
            RequireSyncValue("UseSixButton", false, dto.UseSixButton);
            RequireSyncValue("ControlTypeLeft", 1, dto.ControlTypeLeft);
            RequireSyncValue("ControlTypeRight", 1, dto.ControlTypeRight);
            RequireSyncValue("Region", 0, dto.Region);
            RequireSyncValue("ForceVDP", 0, dto.ForceVDP);
            RequireSyncValue("LoadBIOS", false, dto.LoadBIOS);
            RequireSyncValue("Overscan", 3, dto.Overscan);
            RequireSyncValue("GGExtra", false, dto.GGExtra);
            RequireSyncValue("SMSFMSoundChip", 1, dto.SMSFMSoundChip);
            RequireSyncValue("GenesisFMSoundChip", 0, dto.GenesisFMSoundChip);
            RequireSyncValue("Filter", 0, dto.Filter);
            RequireSyncValue("LowPassRange", (ushort)26214, dto.LowPassRange);
            RequireSyncValue("LowFreq", (short)880, dto.LowFreq);
            RequireSyncValue("HighFreq", (short)5000, dto.HighFreq);
            RequireSyncValue("LowGain", 1.0f, dto.LowGain);
            RequireSyncValue("MidGain", 1.0f, dto.MidGain);
            RequireSyncValue("HighGain", 1.0f, dto.HighGain);
            RequireSyncValue("BackdropColor", 4294902015u, dto.BackdropColor);
            RequireSyncValue(
                "SpritesAlwaysOnTop",
                false,
                dto.SpritesAlwaysOnTop);

            return new GPGX.GPGXSyncSettings
            {
                UseSixButton = dto.UseSixButton,
                ControlTypeLeft = (GPGX.ControlType)dto.ControlTypeLeft,
                ControlTypeRight = (GPGX.ControlType)dto.ControlTypeRight,
                Region = (LibGPGX.Region)dto.Region,
                ForceVDP = (LibGPGX.ForceVDP)dto.ForceVDP,
                LoadBIOS = dto.LoadBIOS,
                Overscan =
                    (LibGPGX.InitSettings.OverscanType)dto.Overscan,
                GGExtra = dto.GGExtra,
                SMSFMSoundChip =
                    (LibGPGX.InitSettings.SMSFMSoundChipType)
                    dto.SMSFMSoundChip,
                GenesisFMSoundChip =
                    (LibGPGX.InitSettings.GenesisFMSoundChipType)
                    dto.GenesisFMSoundChip,
                Filter = (LibGPGX.InitSettings.FilterType)dto.Filter,
                LowPassRange = dto.LowPassRange,
                LowFreq = dto.LowFreq,
                HighFreq = dto.HighFreq,
                LowGain = dto.LowGain,
                MidGain = dto.MidGain,
                HighGain = dto.HighGain,
                BackdropColor = dto.BackdropColor,
                SpritesAlwaysOnTop = dto.SpritesAlwaysOnTop
            };
        }

        private static void ValidateExactFields(
            JObject value,
            IEnumerable<string> expectedFields,
            string context)
        {
            var expected = new HashSet<string>(
                expectedFields,
                StringComparer.Ordinal);
            foreach (JProperty property in value.Properties())
            {
                if (!expected.Remove(property.Name))
                {
                    throw new InvalidDataException(
                        "SyncSettings.json contains unknown "
                        + context + " field " + property.Name + ".");
                }
            }
            if (expected.Count != 0)
            {
                string missing = expected.OrderBy(name => name).First();
                throw new InvalidDataException(
                    "SyncSettings.json is missing "
                    + context + " field " + missing + ".");
            }
        }

        private static void RequireSyncValue<T>(
            string field,
            T expected,
            T actual)
        {
            if (!EqualityComparer<T>.Default.Equals(expected, actual))
            {
                throw new InvalidDataException(
                    "SyncSettings.json field " + field + " is "
                    + Convert.ToString(actual, CultureInfo.InvariantCulture)
                    + "; expected "
                    + Convert.ToString(expected, CultureInfo.InvariantCulture)
                    + ".");
            }
        }

        private static IEnumerable<Bk2Frame> ReadInputFrames(TextReader reader)
        {
            bool seenInput = false;
            bool insideInput = false;
            bool seenEnd = false;
            InputLayout layout = null;
            string line;
            while ((line = reader.ReadLine()) != null)
            {
                if (line.Length == 0)
                {
                    continue;
                }

                if (string.Equals(line, "[Input]", StringComparison.Ordinal))
                {
                    if (seenInput)
                    {
                        throw new InvalidDataException(
                            "Input Log.txt contains multiple input sections.");
                    }
                    seenInput = true;
                    insideInput = true;
                    continue;
                }

                if (string.Equals(line, "[/Input]", StringComparison.Ordinal))
                {
                    if (!insideInput || layout == null)
                    {
                        throw new InvalidDataException(
                            "Input Log.txt contains a malformed input section.");
                    }
                    insideInput = false;
                    seenEnd = true;
                    continue;
                }

                if (!insideInput)
                {
                    throw new InvalidDataException(
                        "Input Log.txt contains content outside its input section.");
                }

                if (layout == null)
                {
                    if (!line.StartsWith("LogKey:", StringComparison.Ordinal))
                    {
                        throw new InvalidDataException(
                            "Input Log.txt input section is missing LogKey.");
                    }
                    layout = ParseLogKey(line);
                    continue;
                }

                if (line.StartsWith("LogKey:", StringComparison.Ordinal))
                {
                    throw new InvalidDataException(
                        "Input Log.txt contains duplicate LogKey.");
                }

                yield return ParseFrame(line, layout);
            }

            if (!seenInput)
            {
                throw new InvalidDataException(
                    "Input Log.txt is missing its input section.");
            }
            if (insideInput || !seenEnd)
            {
                throw new InvalidDataException(
                    "Input Log.txt input section is missing [/Input].");
            }
        }

        private static InputLayout ParseLogKey(string line)
        {
            string declaration = line.Substring("LogKey:".Length);
            string[] tokens = declaration.Split(
                new[] { '|' },
                StringSplitOptions.None);
            if (tokens.Length < 2
                || tokens[tokens.Length - 1].Length != 0)
            {
                throw new InvalidDataException(
                    "Input Log.txt contains a malformed LogKey.");
            }

            var groups = new List<InputGroup>();
            InputGroup current = null;
            for (var index = 0; index < tokens.Length - 1; index++)
            {
                string token = tokens[index];
                bool beginsGroup = token.StartsWith(
                    "#",
                    StringComparison.Ordinal);
                string button = beginsGroup ? token.Substring(1) : token;
                if (button.Length == 0)
                {
                    throw new InvalidDataException(
                        "Input Log.txt contains a malformed LogKey.");
                }

                string groupName = GetGroupName(button);
                if (beginsGroup)
                {
                    current = new InputGroup(groupName);
                    groups.Add(current);
                }
                else if (current == null
                    || !string.Equals(
                        current.Name,
                        groupName,
                        StringComparison.Ordinal))
                {
                    throw new InvalidDataException(
                        "Input Log.txt contains a malformed input group.");
                }
                current.Buttons.Add(button);
            }

            if (groups.Count != 3
                || groups[0].Name != "System"
                || groups[1].Name != "P1"
                || groups[2].Name != "P2")
            {
                throw new InvalidDataException(
                    "Input Log.txt contains an unknown input group.");
            }

            ValidateButtons(groups[0], ExpectedSystemButtons);
            ValidateButtons(groups[1], ExpectedP1Buttons);
            ValidateButtons(groups[2], ExpectedP2Buttons);
            return new InputLayout(
                groups[0].Buttons.ToArray(),
                groups[1].Buttons.ToArray(),
                groups[2].Buttons.ToArray());
        }

        private static string GetGroupName(string button)
        {
            if (button == "Power" || button == "Reset")
            {
                return "System";
            }
            if (button.StartsWith("P1 ", StringComparison.Ordinal))
            {
                return "P1";
            }
            if (button.StartsWith("P2 ", StringComparison.Ordinal))
            {
                return "P2";
            }
            if (button.StartsWith("P", StringComparison.Ordinal))
            {
                throw new InvalidDataException(
                    "Input Log.txt contains an unknown input group.");
            }
            throw new InvalidDataException(
                "Input Log.txt contains an unknown input button " + button + ".");
        }

        private static void ValidateButtons(
            InputGroup group,
            IEnumerable<string> expectedButtons)
        {
            var expected = new HashSet<string>(
                expectedButtons,
                StringComparer.Ordinal);
            foreach (string button in group.Buttons)
            {
                if (!expected.Remove(button))
                {
                    throw new InvalidDataException(
                        "Input Log.txt contains an unknown input button "
                        + button + ".");
                }
            }
            if (expected.Count != 0)
            {
                throw new InvalidDataException(
                    "Input Log.txt is missing input button "
                    + expected.OrderBy(name => name).First() + ".");
            }
        }

        private static Bk2Frame ParseFrame(string row, InputLayout layout)
        {
            string[] groups = row.Split(
                new[] { '|' },
                StringSplitOptions.None);
            if (groups.Length != 5
                || groups[0].Length != 0
                || groups[4].Length != 0)
            {
                throw new InvalidDataException(
                    "Input Log.txt contains a malformed input row.");
            }
            if (groups[1].Length != layout.SystemButtons.Length
                || groups[2].Length != layout.P1Buttons.Length
                || groups[3].Length != layout.P2Buttons.Length)
            {
                throw new InvalidDataException(
                    "Input Log.txt input row has an invalid group length.");
            }

            var frame = new Bk2Frame
            {
                Power = IsPressed(
                    groups[1],
                    layout.SystemButtons,
                    "Power"),
                Reset = IsPressed(
                    groups[1],
                    layout.SystemButtons,
                    "Reset"),
                P1Up = IsPressed(groups[2], layout.P1Buttons, "P1 Up"),
                P1Down = IsPressed(groups[2], layout.P1Buttons, "P1 Down"),
                P1Left = IsPressed(groups[2], layout.P1Buttons, "P1 Left"),
                P1Right = IsPressed(groups[2], layout.P1Buttons, "P1 Right"),
                P1A = IsPressed(groups[2], layout.P1Buttons, "P1 A"),
                P1B = IsPressed(groups[2], layout.P1Buttons, "P1 B"),
                P1C = IsPressed(groups[2], layout.P1Buttons, "P1 C"),
                P1Start =
                    IsPressed(groups[2], layout.P1Buttons, "P1 Start"),
                P2Active = groups[3].Any(marker => marker != '.')
            };
            if (frame.P2Active)
            {
                throw new InvalidDataException(
                    "P2 input is not supported by the GHZ1 BK2 reader.");
            }

            ushort mask = 0;
            if (frame.P1Up)
            {
                mask |= 0x0001;
            }
            if (frame.P1Down)
            {
                mask |= 0x0002;
            }
            if (frame.P1Left)
            {
                mask |= 0x0004;
            }
            if (frame.P1Right)
            {
                mask |= 0x0008;
            }
            if (frame.P1A)
            {
                mask |= 0x0010;
            }
            if (frame.P1B)
            {
                mask |= 0x0020;
            }
            if (frame.P1C)
            {
                mask |= 0x0040;
            }
            if (frame.P1Start)
            {
                mask |= 0x0080;
            }
            frame.OpenGgfInputMask = mask;
            return frame;
        }

        private static bool IsPressed(
            string state,
            string[] buttons,
            string button)
        {
            int index = Array.IndexOf(buttons, button);
            return state[index] != '.';
        }

        private static void VerifyCanonicalMovieHash(string path)
        {
            if (!string.Equals(
                path,
                CanonicalMoviePath,
                StringComparison.Ordinal))
            {
                return;
            }

            string actual;
            using (FileStream stream = File.OpenRead(path))
            using (SHA256 sha256 = SHA256.Create())
            {
                actual = BitConverter.ToString(sha256.ComputeHash(stream))
                    .Replace("-", "")
                    .ToLowerInvariant();
            }
            if (!string.Equals(
                actual,
                CanonicalMovieSha256,
                StringComparison.Ordinal))
            {
                throw new InvalidDataException(
                    "Canonical GHZ1 BK2 SHA-256 is " + actual
                    + "; expected " + CanonicalMovieSha256 + ".");
            }
        }

        private sealed class InputGroup
        {
            public InputGroup(string name)
            {
                Name = name;
                Buttons = new List<string>();
            }

            public string Name { get; private set; }
            public List<string> Buttons { get; private set; }
        }

        private sealed class InputLayout
        {
            public InputLayout(
                string[] systemButtons,
                string[] p1Buttons,
                string[] p2Buttons)
            {
                SystemButtons = systemButtons;
                P1Buttons = p1Buttons;
                P2Buttons = p2Buttons;
            }

            public string[] SystemButtons { get; private set; }
            public string[] P1Buttons { get; private set; }
            public string[] P2Buttons { get; private set; }
        }

        private sealed class SyncSettingsDto
        {
            [JsonProperty("$type")]
            public string Type { get; set; }

            public bool UseSixButton { get; set; }
            public int ControlTypeLeft { get; set; }
            public int ControlTypeRight { get; set; }
            public int Region { get; set; }
            public int ForceVDP { get; set; }
            public bool LoadBIOS { get; set; }
            public int Overscan { get; set; }
            public bool GGExtra { get; set; }
            public int SMSFMSoundChip { get; set; }
            public int GenesisFMSoundChip { get; set; }
            public int Filter { get; set; }
            public ushort LowPassRange { get; set; }
            public short LowFreq { get; set; }
            public short HighFreq { get; set; }
            public float LowGain { get; set; }
            public float MidGain { get; set; }
            public float HighGain { get; set; }
            public uint BackdropColor { get; set; }
            public bool SpritesAlwaysOnTop { get; set; }
        }
    }
}
