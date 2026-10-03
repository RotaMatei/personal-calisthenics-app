# Room, Compose and Coil ship their own consumer rules. Keep the pure-Kotlin core model names stable
# for readable crash traces.
-keep class com.personal.calisthenics.core.** { *; }
