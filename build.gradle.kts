plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // Generates the Room database code; its version must match the Kotlin version above.
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
