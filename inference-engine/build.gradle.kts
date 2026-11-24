plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
}

android {
    namespace = "io.github.localai.inference"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-Xskip-metadata-version-check"
        )
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "io.github.localai"
                artifactId = "android-inference-engine"
                version = "1.0.0"

                pom {
                    name.set("android-inference-engine")
                    description.set("Standalone high-performance on-device AI inference runtime for Android supporting llama.cpp GGUF, LiteRT NPU/GPU, and Gemini Nano AICore.")
                    url.set("https://github.com/local-ai-mobile/android-inference-engine")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("local-ai-mobile")
                            name.set("Local AI Mobile Team")
                            organization.set("local-ai-mobile")
                            organizationUrl.set("https://github.com/local-ai-mobile")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/local-ai-mobile/android-inference-engine.git")
                        developerConnection.set("scm:git:ssh://github.com/local-ai-mobile/android-inference-engine.git")
                        url.set("https://github.com/local-ai-mobile/android-inference-engine")
                    }
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // llama.cpp GGUF Native Bindings (precompiled for arm64-v8a, armeabi-v7a, x86_64)
    implementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")

    // LiteRT Google Play Services runtime & GPU acceleration
    implementation("com.google.android.gms:play-services-tflite-java:16.4.0")
    implementation("com.google.android.gms:play-services-tflite-gpu:16.4.0")

    // Google Gemini Nano via ML Kit GenAI & Android AICore
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta5")
    implementation("com.google.mlkit:genai-agent:1.0.0-alpha1")

    // Automated Unit Testing (Robolectric & JUnit)
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
