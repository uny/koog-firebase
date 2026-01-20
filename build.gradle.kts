import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.publish)
}

group = "dev.ynagai.koog.firebase"

kotlin {
    androidLibrary {
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        namespace = "dev.ynagai.koog.firebase"
    }
    iosArm64()
    iosSimulatorArm64()
    withSourcesJar(publish = true)
    sourceSets {
        commonMain.dependencies {
            implementation(libs.firebase.ai)
            implementation(libs.firebase.app)
            implementation(libs.koog.agents)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name.set("Koog Firebase")
        description.set("Firebase Vertex AI integration for Koog Agent Framework")
        url.set("https://github.com/uny/koog-firebase")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("uny")
                name.set("Yuki Nagai")
                url.set("https://github.com/uny")
            }
        }
        scm {
            url.set("https://github.com/uny/koog-firebase")
            connection.set("scm:git:https://github.com/uny/koog-firebase.git")
            developerConnection.set("scm:git:https://github.com/uny/koog-firebase.git")
        }
    }
}
