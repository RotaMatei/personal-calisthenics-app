package org.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Minimal stand-in for JUnit 4's @Test so the pure-Kotlin core tests can be compiled and run in
 * environments where Maven Central is not reachable. Real builds (Gradle/CI) use the real JUnit 4.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {
}
