import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.unpawse"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.unpawse"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Generates BuildConfig, which the Settings "Version" row reads so the shipped version can
        // never drift from defaultConfig above.
        buildConfig = true
    }
}

// The privacy policy promises no network access, and a dependency bump can silently merge it back
// in, so every variant's merged manifest is checked before it can be assembled or linted.
androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        val guard = tasks.register<CheckNoNetworkTask>("checkNoNetwork$suffix") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/noNetwork/${variant.name}.txt"))
        }
        tasks.matching { it.name == "assemble$suffix" || it.name == "lint$suffix" }
            .configureEach { dependsOn(guard) }
    }
}

abstract class CheckNoNetworkTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val forbiddenPermissions = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
        )
        val forbiddenComponents = setOf(
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
        )
        val androidNs = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)
        fun names(tag: String) = doc.getElementsByTagName(tag).let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }
        }
        val found = (names("uses-permission") + names("uses-permission-sdk-23"))
            .filter { it in forbiddenPermissions } +
            names("service").filter { it in forbiddenComponents }
        if (found.isNotEmpty()) {
            throw GradleException(
                "Merged manifest reintroduces network access: ${found.distinct().joinToString()}. " +
                    "Add a tools:node=\"remove\" entry to AndroidManifest.xml, or update the " +
                    "privacy policy before allowing it."
            )
        }
        report.get().asFile.writeText("No network permissions or transport backend.\n")
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Lifecycle — ViewModel + Compose state collection
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // CameraX — capture pipeline
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit — on-device cat identification
    implementation(libs.mlkit.image.labeling)

    // Room — capture metadata persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coil — image loading for captured photos
    implementation(libs.coil.compose)

    // DataStore — persistence for scalar settings toggles
    implementation(libs.androidx.datastore.preferences)

    // WorkManager — periodic backstop that re-arms monitoring if the service is ever killed
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
