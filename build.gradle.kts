plugins {
    id("com.android.application") version "8.8.2" apply false
    id("com.android.library") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10" apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            force(
                "androidx.core:core:1.15.0",
                "androidx.core:core-ktx:1.15.0",
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.10",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.10",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.10",
                "org.jetbrains.kotlin:kotlin-stdlib-common:2.1.10"
            )
        }
    }
}
