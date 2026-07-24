using System;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class AssertEx
    {
        public static void Equal<T>(T expected, T actual)
        {
            if (!object.Equals(expected, actual))
            {
                throw new InvalidOperationException(
                    "Expected <" + expected + "> but was <" + actual + ">.");
            }
        }

        public static void Throws<TException>(Action action, string messageFragment)
            where TException : Exception
        {
            try
            {
                action();
            }
            catch (TException exception)
            {
                if (exception.Message.IndexOf(messageFragment, StringComparison.Ordinal) < 0)
                {
                    throw new InvalidOperationException(
                        "Expected exception message to contain <" + messageFragment
                        + "> but was <" + exception.Message + ">.");
                }
                return;
            }

            throw new InvalidOperationException(
                "Expected exception of type " + typeof(TException).FullName + ".");
        }
    }
}
