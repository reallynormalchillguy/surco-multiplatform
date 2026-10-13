plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
}

/**
 * Código común de Surco para Android e iOS: textos, letras, protocolo de Soulseek…
 * Poco a poco irá absorbiendo también la red, la base de datos y la interfaz.
 */
val ktor = "3.1.3"
val room = "2.7.1"
val coil = "3.2.0"

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)
            api(compose.animation)
            implementation(compose.components.resources)
            api("org.jetbrains.androidx.navigation:navigation-compose:2.9.0-beta03")
            api("io.coil-kt.coil3:coil-compose:$coil")
            implementation("io.coil-kt.coil3:coil-network-ktor3:$coil")
            implementation("com.squareup.okio:okio:3.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            api("io.ktor:ktor-client-core:$ktor")
            implementation("io.ktor:ktor-network:$ktor")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            api("androidx.room:room-runtime:$room")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktor")
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("androidx.palette:palette-ktx:1.0.0")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktor")
            implementation("androidx.sqlite:sqlite-bundled:2.5.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:$ktor")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "dev.surco.resources"
    generateResClass = always
}

android {
    // El detector NullSafeMutableLiveData de lifecycle 2.9 hace fallar el lint de AGP 8.7.
    lint {
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = false
    }
    namespace = "dev.surco.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Room genera el código de la base de datos para cada plataforma.
dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, "androidx.room:room-compiler:$room")
    }
}
