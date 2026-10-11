import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.wire)
}

kotlin {
    jvm()

    androidLibrary {
        namespace = "com.metitem.casapic.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)

            implementation(libs.sqldelight.coroutines)
            implementation(libs.paging.common)
            implementation(libs.wire.runtime)
        }

        jvmMain.dependencies {
            implementation(libs.sqldelight.jvm.driver)
            implementation(libs.metadata.extractor)
            implementation(libs.coil.compose)
            implementation(libs.onnxruntime)
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.mlkit.face.detection)
            implementation(libs.coil.compose)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(libs.junit5.api)
            implementation(libs.junit5.params)
            runtimeOnly(libs.junit5.engine)
            implementation(libs.kotest.assertions)
            implementation(libs.sqldelight.jvm.driver)
        }
    }
}

sqldelight {
    databases {
        create("CasaPicDatabase") {
            packageName.set("com.metitem.casapic.db")
        }
    }
}

wire {
    sourcePath {
        srcDir("src/commonMain/proto")
    }
    kotlin {
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

// Force skiko to the exact version bundled with Compose Multiplatform so the
// checkJvmMainComposeLibrariesCompatibility task sees a consistent version across
// all dependencies (Coil 3.5.0-beta01 declares 0.144.5; CMP 1.11.0 brings 0.144.6).
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.skiko") {
            useVersion("0.144.6")
            because("Align skiko with Compose Multiplatform 1.11.0")
        }
    }
}
