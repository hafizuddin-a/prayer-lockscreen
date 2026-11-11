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

// Whether this build can update itself from the app's GitHub releases (the default, for the build people download there), or not.
// Google Play does not allow an app to update itself any other way than through Play, so a Play build is made with -PselfUpdate=false:
// the permission to install apps is left out of the merged manifest and the update screens and daily job are off.
val selfUpdate = (findProperty("selfUpdate") as String?)?.toBooleanStrictOrNull() ?: true

/** Removes permissions from the merged manifest, whichever manifest they were merged in from. */
abstract class RemovePermissionsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val permissions: ListProperty<String>

    @get:OutputFile
    abstract val updatedManifest: RegularFileProperty

    @TaskAction
    fun run() {
        var text = mergedManifest.get().asFile.readText()
        for (name in permissions.get()) {
            text = text.replace(Regex("""[ \t]*<uses-permission[^>]*android:name="${Regex.escape(name)}"[^>]*/>[ \t]*\r?\n?"""), "")
        }
        updatedManifest.get().asFile.writeText(text)
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
        versionCode = 13
        versionName = "1.8.4"
        buildConfigField("boolean", "EXACT_ALARMS", exactAlarms.toString())
        buildConfigField("boolean", "SELF_UPDATE", selfUpdate.toString())
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
        val unwanted = buildList {
            if (!exactAlarms) add("android.permission.SCHEDULE_EXACT_ALARM")
            if (!selfUpdate) add("android.permission.REQUEST_INSTALL_PACKAGES")
        }
        if (unwanted.isNotEmpty()) {
            val remove = tasks.register<RemovePermissionsTask>("${variant.name}RemoveUnwantedPermissions") { permissions.set(unwanted) }
            variant.artifacts.use(remove)
                .wiredWithFiles(RemovePermissionsTask::mergedManifest, RemovePermissionsTask::updatedManifest)
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
