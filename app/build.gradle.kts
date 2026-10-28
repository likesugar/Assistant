plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ziyuan.Assistant"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ziyuan.Assistant"
        minSdk = 24
        targetSdk = 36
        versionCode = 489
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17"; freeCompilerArgs += listOf("-Xskip-metadata-version-check") }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("ci/vendor/lib")
        }
    }
    packaging {
        resources.excludes += "META-INF/*"
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to rootProject.file("ci/vendor/ydl"), "include" to listOf("*.jar"), "exclude" to listOf("annotation-jvm.jar", "collection-jvm.jar", "kotlin-stdlib.jar"))))
    implementation(fileTree(mapOf("dir" to rootProject.file("ci/vendor"), "include" to listOf("ffk_classes.jar"))))
    implementation(fileTree(mapOf("dir" to rootProject.file("app/libs"), "include" to listOf("*.jar"), "exclude" to listOf("annotation-jvm.jar", "collection-jvm.jar", "kotlin-stdlib.jar"))))
    implementation(fileTree(mapOf("dir" to rootProject.file("ci/vendor/ffk_sex"), "include" to listOf("*.jar"))))
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
}
