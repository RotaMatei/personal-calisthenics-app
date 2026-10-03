package org.junit;

import java.util.Objects;

/** Minimal stand-in for org.junit.Assert mirroring the overload set of JUnit 4.13 that the tests use. */
public final class Assert {
    private Assert() {}

    public static void fail(String message) {
        throw new AssertionError(message == null ? "" : message);
    }

    public static void assertTrue(String message, boolean condition) {
        if (!condition) fail(message);
    }

    public static void assertTrue(boolean condition) {
        assertTrue(null, condition);
    }

    public static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    public static void assertFalse(boolean condition) {
        assertFalse(null, condition);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            fail((message == null ? "" : message + " ") + "expected:<" + expected + "> but was:<" + actual + ">");
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(String message, long expected, long actual) {
        if (expected != actual) {
            fail((message == null ? "" : message + " ") + "expected:<" + expected + "> but was:<" + actual + ">");
        }
    }

    public static void assertEquals(long expected, long actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(String message, double expected, double actual, double delta) {
        if (Double.compare(expected, actual) == 0) return;
        if (!(Math.abs(expected - actual) <= delta)) {
            fail((message == null ? "" : message + " ") + "expected:<" + expected + "> but was:<" + actual + ">");
        }
    }

    public static void assertEquals(double expected, double actual, double delta) {
        assertEquals(null, expected, actual, delta);
    }

    public static void assertEquals(String message, float expected, float actual, float delta) {
        assertEquals(message, (double) expected, (double) actual, (double) delta);
    }

    public static void assertEquals(float expected, float actual, float delta) {
        assertEquals(null, expected, actual, delta);
    }

    public static void assertNotEquals(Object unexpected, Object actual) {
        if (Objects.equals(unexpected, actual)) fail("Values should be different. Actual: " + actual);
    }

    public static void assertNull(String message, Object object) {
        if (object != null) fail((message == null ? "" : message + " ") + "expected null but was:<" + object + ">");
    }

    public static void assertNull(Object object) {
        assertNull(null, object);
    }

    public static void assertNotNull(String message, Object object) {
        if (object == null) fail((message == null ? "" : message + " ") + "expected not null");
    }

    public static void assertNotNull(Object object) {
        assertNotNull(null, object);
    }

    public static void assertSame(Object expected, Object actual) {
        if (expected != actual) fail("expected same:<" + expected + "> was not:<" + actual + ">");
    }
}
