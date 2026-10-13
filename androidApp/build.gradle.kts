import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Claves de Last.fm opcionales: ponlas en local.properties
//   lastfm.apiKey=xxxxxxxx
//   lastfm.secret=xxxxxxxx
// (también se pueden escribir dentro de la app, en Ajustes).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val lastfmKey: String = localProps.getProperty("lastfm.apiKey") ?: System.getenv("LASTFM_API_KEY") ?: ""
val lastfmSecret: String = localProps.getProperty("lastfm.secret") ?: System.getenv("LASTFM_SECRET") ?: ""

android {
    // El detector NullSafeMutableLiveData de lifecycle 2.9 hace fallar el lint de AGP 8.7.
    lint {
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = false
    }
    namespace = "dev.surco"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.surco"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 1
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastfmSecret\"")
    }

    // Clave de firma FIJA (guardada en el repositorio). Antes se firmaba con la clave de depuración,
    // que GitHub genera nueva en cada compilación: Android no deja actualizar una app firmada con
    // otra clave y había que desinstalar (perdiendo favoritas, listas e historial).
    signingConfigs {
        create("surco") {
            storeFile = rootProject.file("signing/surco.jks")
            storePassword = "surco-android"
            keyAlias = "surco"
            keyPassword = "surco-android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("surco")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "3g"
            }
        }
    }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/**", "module-info.class")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(project(":shared"))
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Lectura/escritura de etiquetas (ID3, FLAC, MP4, Vorbis…)
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Capturas de pantalla en CI (Robolectric + Roborazzi)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Pruebas en un Android real (emulador en CI)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
