using System;
using System.Collections.Generic;
using BizHawk.Emulation.Common;

namespace OpenGGF.BizHawk.Headless
{
    internal sealed class MutableController : IController
    {
        private static readonly string[] ButtonNames =
        {
            "P1 Up",
            "P1 Down",
            "P1 Left",
            "P1 Right",
            "P1 A",
            "P1 B",
            "P1 C",
            "P1 Start",
            "Power",
            "Reset"
        };

        private static readonly IReadOnlyCollection<(string Name, int Strength)>
            EmptyHaptics =
                new (string Name, int Strength)[0];

        private readonly Dictionary<string, bool> buttons =
            new Dictionary<string, bool>(StringComparer.Ordinal);

        public MutableController(ControllerDefinition definition)
        {
            Definition = definition
                ?? throw new ArgumentNullException("definition");
            foreach (string buttonName in Definition.BoolButtons)
            {
                buttons.Add(buttonName, false);
            }
            foreach (string buttonName in ButtonNames)
            {
                if (!buttons.ContainsKey(buttonName))
                {
                    buttons.Add(buttonName, false);
                }
            }
        }

        public ControllerDefinition Definition { get; private set; }

        public bool IsPressed(string button)
        {
            bool pressed;
            if (!buttons.TryGetValue(button, out pressed))
            {
                return false;
            }
            return pressed;
        }

        public int AxisValue(string name)
        {
            return 0;
        }

        public IReadOnlyCollection<(string Name, int Strength)> GetHapticsSnapshot()
        {
            return EmptyHaptics;
        }

        public void SetHapticChannelStrength(string name, int strength)
        {
        }

        public void Clear()
        {
            foreach (string buttonName in new List<string>(buttons.Keys))
            {
                buttons[buttonName] = false;
            }
        }

        public void Set(string name, bool pressed)
        {
            if (!buttons.ContainsKey(name))
            {
                throw new ArgumentException(
                    "Unknown GPGX button: " + name,
                    "name");
            }
            buttons[name] = pressed;
        }
    }
}
