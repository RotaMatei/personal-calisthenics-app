plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin module: no Android imports. Domain logic, seed data, timer engine and the
// 3D body-rig math live here so they can be unit-tested on a plain JVM.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
