import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from keystore.properties (git-ignored), so the upload key never enters the repo.
// Without that file the release build is unsigned, which is what Play App Signing wants uploaded
// anyway after you sign the bundle with your own upload key.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

// Optional data.gov.sg API key. Read for debug builds only; the release build always gets "".
val debugApiKey = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}.getProperty("datagov.apiKey", "")

// Whether this build declares the exact alarm permission and sets exact alarms (the default), or only approximate ones. Google Play
// restricts the exact alarm permission, and its review of a prayer reminder app is not known, so a build with -PexactAlarms=false
// leaves the permission out of the merged manifest entirely and everything else is the same: reminders then arrive approximately.
val exactAlarms = (findProperty("exactAlarms") as String?)?.toBooleanStrictOrNull() ?: true

/** Removes the exact alarm permission from the merged manifest, whichever manifest it was merged in from. */
abstract class RemoveExactAlarmPermissionTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val updatedManifest: RegularFileProperty

    @TaskAction
    fun run() {
        val declaration = Regex("""[ \t]*<uses-permission[^>]*android:name="android\.permission\.SCHEDULE_EXACT_ALARM"[^>]*/>[ \t]*\r?\n?""")
        updatedManifest.get().asFile.writeText(mergedManifest.get().asFile.readText().replace(declaration, ""))
    }
}

android {
    namespace = "com.prayerwallpaper.app"
    // Spec asks for 36. Only the API 37 platform is installed locally; compileSdk only affects
    // which APIs are visible at compile time. Runtime behaviour is governed by targetSdk (36).
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.prayerwallpaper.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 9
        versionName = "1.8.0"
        buildConfigField("boolean", "EXACT_ALARMS", exactAlarms.toString())
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "DATA_GOV_API_KEY", "\"$debugApiKey\"")
        }
        release {
            buildConfigField("String", "DATA_GOV_API_KEY", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        if (!exactAlarms) {
            val remove = tasks.register<RemoveExactAlarmPermissionTask>("${variant.name}RemoveExactAlarmPermission")
            variant.artifacts.use(remove)
                .wiredWithFiles(RemoveExactAlarmPermissionTask::mergedManifest, RemoveExactAlarmPermissionTask::updatedManifest)
                .toTransform(SingleArtifact.MERGED_MANIFEST)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.adhan)

    testImplementation(libs.junit)
}
