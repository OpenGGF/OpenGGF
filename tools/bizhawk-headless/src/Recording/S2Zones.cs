using System.Globalization;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// S2 recorder zone identity maps, ported verbatim from
    /// tools/bizhawk/s2_trace_recorder.lua (ZONE_NAMES,
    /// ROM_ZONE_TO_ENGINE_ZONE, apparent_act_for). Shared by the aux event
    /// engine (zone_act_state/checkpoint naming, CNZ gate) and the S2
    /// metadata writer.
    /// </summary>
    public static class S2Zones
    {
        /// <summary>
        /// CNZ's raw ROM zone id; recordings armed in CNZ emit the
        /// per-frame cnz_slot_machine_state diagnostic.
        /// </summary>
        public const int CnzRomZoneId = 0x0C;

        /// <summary>
        /// MTZ's alternate zone id: acts render as actual_act + 2
        /// (apparent_act_for).
        /// </summary>
        public const int MtzAlternateRomZoneId = 0x05;

        /// <summary>
        /// ZONE_NAMES: raw u8 Current_Zone to recorder zone name; unmapped
        /// ids fall back to "unknown_%02x" (lowercase hex).
        /// </summary>
        public static string ZoneName(int romZoneId)
        {
            switch (romZoneId)
            {
                case 0x00: return "ehz";
                case 0x01: return "unknown_01";
                case 0x02: return "wz";
                case 0x03: return "unknown_03";
                case 0x04: return "mtz";
                case 0x05: return "mtz";
                case 0x06: return "wfz";
                case 0x07: return "htz";
                case 0x08: return "hpz";
                case 0x09: return "unknown_09";
                case 0x0A: return "ooz";
                case 0x0B: return "mcz";
                case 0x0C: return "cnz";
                case 0x0D: return "cpz";
                case 0x0E: return "dez";
                case 0x0F: return "arz";
                case 0x10: return "scz";
                default:
                    return "unknown_"
                        + romZoneId.ToString("x2", CultureInfo.InvariantCulture);
            }
        }

        /// <summary>
        /// ROM_ZONE_TO_ENGINE_ZONE: raw zone id to engine progression id;
        /// unmapped ids pass through unchanged.
        /// </summary>
        public static int EngineZoneId(int romZoneId)
        {
            switch (romZoneId)
            {
                case 0x00: return 0;   // EHZ
                case 0x0D: return 1;   // CPZ
                case 0x0F: return 2;   // ARZ
                case 0x0C: return 3;   // CNZ
                case 0x07: return 4;   // HTZ
                case 0x0B: return 5;   // MCZ
                case 0x0A: return 6;   // OOZ
                case 0x04: return 7;   // MTZ
                case 0x05: return 7;   // MTZ alternate act id
                case 0x10: return 8;   // SCZ
                case 0x06: return 9;   // WFZ
                case 0x0E: return 10;  // DEZ
                default: return romZoneId;
            }
        }

        /// <summary>
        /// apparent_act_for: the MTZ alternate zone id renders acts shifted
        /// by +2; every other zone reports the actual act unchanged.
        /// </summary>
        public static int ApparentAct(int romZoneId, int actualAct)
        {
            return romZoneId == MtzAlternateRomZoneId
                ? actualAct + 2
                : actualAct;
        }
    }
}
