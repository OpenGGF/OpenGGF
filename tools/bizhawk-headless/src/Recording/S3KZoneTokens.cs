using System;
using System.Collections.Generic;
using System.Globalization;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// The S3K complete-run recorder's zone-id to directory/metadata token
    /// map (s3k_complete_run_recorder.lua ZONE_TOKEN / BONUS_TOKENS /
    /// zone_token_for, L439-664; spec
    /// tools/bizhawk-headless/docs/s3k-complete-run-behavior.md §5.5/§6.1).
    ///
    /// This is deliberately NOT <see cref="S3KRam.ZoneName"/>. The two
    /// tables DISAGREE for every id from 10 up — ZONE_NAMES[10] is "ssz"
    /// where ZONE_TOKEN[10] is "hpz", [11] is "dez" vs "ssz", [13] is
    /// "hpz" vs "ddz", ZONE_TOKEN has no [12] at all (so id 12 falls
    /// through to the "zone0c" hex form) and adds the disambiguated
    /// late-game slots [22] "hpz22" / [23] "dez23". The complete-run
    /// recorder never calls ZONE_NAMES, so reusing the STANDARD recorder's
    /// table here would silently mislabel every SOZ-and-later segment's
    /// directory AND its metadata "zone" field (spec §10.8).
    ///
    /// Bonus zones resolve to their bonus token BEFORE the zone table is
    /// consulted, which is why a gumball segment's metadata reads
    /// "zone": "gumball" alongside "zone_id": 19.
    /// </summary>
    public static class S3KZoneTokens
    {
        public const int GumballZoneId = 0x13;
        public const int PachinkoZoneId = 0x14;
        public const int SlotsZoneId = 0x15;

        /// <summary>The special-stage segments' fixed base token.</summary>
        public const string SpecialStageToken = "ss";

        /// <summary>
        /// BONUS_TOKENS lookup: the bonus token for 0x13/0x14/0x15, else
        /// null. The Lua's BONUS_ZONE_MIN/MAX (0x13/0x15) are documentation
        /// only — every live site uses the table, so an unmapped id inside
        /// the range would be treated as an ordinary zone (spec §5.4). Port
        /// the lookup, not a range check.
        /// </summary>
        public static string BonusToken(int zoneId)
        {
            switch (zoneId)
            {
                case GumballZoneId: return "gumball";
                case PachinkoZoneId: return "pachinko";
                case SlotsZoneId: return "slots";
                default: return null;
            }
        }

        /// <summary>
        /// ZONE_TOKEN lookup; null for an id the table omits (12 and
        /// everything from 14 up bar 22/23).
        /// </summary>
        public static string ZoneToken(int zoneId)
        {
            switch (zoneId)
            {
                case 0: return "aiz";
                case 1: return "hcz";
                case 2: return "mgz";
                case 3: return "cnz";
                case 4: return "fbz";
                case 5: return "icz";
                case 6: return "lbz";
                case 7: return "mhz";
                case 8: return "soz";
                case 9: return "lrz";
                case 10: return "hpz";
                case 11: return "ssz";
                case 13: return "ddz";
                case 22: return "hpz22";
                case 23: return "dez23";
                default: return null;
            }
        }

        /// <summary>
        /// zone_token_for: bonus token, else ZONE_TOKEN, else the Lua
        /// string.format("zone%02x", zone_id) fallback (LOWERCASE hex, two
        /// digits, from the u8 zone register).
        /// </summary>
        public static string ZoneTokenFor(int zoneId)
        {
            string bonus = BonusToken(zoneId);
            if (bonus != null)
            {
                return bonus;
            }
            string token = ZoneToken(zoneId);
            if (token != null)
            {
                return token;
            }
            return "zone" + (zoneId & 0xFF).ToString(
                "x2", CultureInfo.InvariantCulture);
        }

        /// <summary>
        /// The token set precreate_segment_dirs() creates under
        /// BASE_OUTPUT_DIR at load: every ZONE_TOKEN value, every
        /// BONUS_TOKENS value, and "ss". Directory precreation has no
        /// effect on output bytes (ensure_segment_dir covers whatever the
        /// list misses, including every "_2"-and-up suffix dir), so a
        /// native port only needs this to keep the same on-disk shape.
        /// </summary>
        public static IList<string> PrecreatedTokens()
        {
            var tokens = new List<string>();
            for (var zoneId = 0; zoneId <= 23; zoneId++)
            {
                string token = ZoneToken(zoneId);
                if (token != null)
                {
                    tokens.Add(token);
                }
            }
            tokens.Add(BonusToken(GumballZoneId));
            tokens.Add(BonusToken(PachinkoZoneId));
            tokens.Add(BonusToken(SlotsZoneId));
            tokens.Add(SpecialStageToken);
            return tokens;
        }
    }
}
