using System;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// ROM-owned Sonic 1 ending-demo identities. The runner only observes
    /// these values; it never writes a selected entry into RAM.
    /// </summary>
    public sealed class S1CreditsDemoDefinition
    {
        internal S1CreditsDemoDefinition(
            int index, string slug, int zoneActWord, int timerFrames)
        {
            Index = index;
            Slug = slug;
            ZoneActWord = zoneActWord;
            TimerFrames = timerFrames;
        }

        public int Index { get; private set; }
        public string Slug { get; private set; }
        public int ZoneActWord { get; private set; }
        public int TimerFrames { get; private set; }
    }

    public static class S1CreditsDemoCatalog
    {
        private static readonly S1CreditsDemoDefinition[] definitions =
        {
            new S1CreditsDemoDefinition(0, "ghz1_credits_demo_1", 0x0000, 540),
            new S1CreditsDemoDefinition(1, "mz2_credits_demo", 0x0201, 540),
            new S1CreditsDemoDefinition(2, "syz3_credits_demo", 0x0402, 540),
            new S1CreditsDemoDefinition(3, "lz3_credits_demo", 0x0102, 510),
            new S1CreditsDemoDefinition(4, "slz3_credits_demo", 0x0302, 540),
            new S1CreditsDemoDefinition(5, "sbz1_credits_demo", 0x0500, 540),
            new S1CreditsDemoDefinition(6, "sbz2_credits_demo", 0x0501, 540),
            new S1CreditsDemoDefinition(7, "ghz1_credits_demo_2", 0x0000, 540)
        };

        public static S1CreditsDemoDefinition[] All()
        {
            return (S1CreditsDemoDefinition[])definitions.Clone();
        }

        public static S1CreditsDemoDefinition Get(int index)
        {
            if (index < 0 || index >= definitions.Length)
            {
                throw new ArgumentOutOfRangeException(
                    "index", "Credits demo index must be between 0 and 7.");
            }
            return definitions[index];
        }
    }
}
