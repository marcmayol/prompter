import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma de release: los secretos salen de keystore.properties (gitignored) o de las
// variables PROMPTER_*. Sin ninguna de las dos se firma con el debug.keystore, que
// sirve para probar pero no para publicar (firma distinta = no actualiza encima).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

fun datoFirma(clave: String, entorno: String): String? =
    (keystoreProps[clave] as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(entorno)?.takeIf { it.isNotBlank() }

val storeFileRelease = datoFirma("storeFile", "PROMPTER_STORE_FILE")

android {
    namespace = "com.marcmayol.prompter"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.marcmayol.prompter"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 5
        versionName = "0.1.0"
        // Vosk trae librerías nativas para 4 arquitecturas; basta con el móvil (arm64) y el emulador (x86_64).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        create("release") {
            if (storeFileRelease != null) {
                storeFile = file(storeFileRelease)
                storePassword = datoFirma("storePassword", "PROMPTER_STORE_PASSWORD")
                keyAlias = datoFirma("keyAlias", "PROMPTER_KEY_ALIAS")
                keyPassword = datoFirma("keyPassword", "PROMPTER_KEY_PASSWORD")
            } else {
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // El modelo de Vosk va sin comprimir: se copia tal cual a disco al primer arranque.
    androidResources {
        noCompress += listOf("mdl", "fst", "int", "conf", "mat", "dubm", "ie", "stats")
    }
}

dependencies {
    implementation(project(":actualizador"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.video)

    implementation(libs.vosk)
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    testImplementation(libs.junit)
}
