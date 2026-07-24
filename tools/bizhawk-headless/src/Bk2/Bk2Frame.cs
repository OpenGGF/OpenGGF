namespace OpenGGF.BizHawk.Headless
{
    public sealed class Bk2Frame
    {
        public bool Power { get; internal set; }
        public bool Reset { get; internal set; }
        public bool P1Up { get; internal set; }
        public bool P1Down { get; internal set; }
        public bool P1Left { get; internal set; }
        public bool P1Right { get; internal set; }
        public bool P1A { get; internal set; }
        public bool P1B { get; internal set; }
        public bool P1C { get; internal set; }
        public bool P1Start { get; internal set; }
        public bool P2Active { get; internal set; }
        public ushort OpenGgfInputMask { get; internal set; }
    }
}
