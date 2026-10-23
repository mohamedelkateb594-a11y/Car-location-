plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// لو رفعت ic_launcher.png في جذر الريبو بتتستخدم كأيقونة للتطبيق
val iconFile = rootProject.file("ic_launcher.png")
val genRes = layout.buildDirectory.get().asFile.resolve("generated/geomode_res")
if (iconFile.exists()) {
    val out = genRes.resolve("mipmap-xxxhdpi")
    out.mkdirs()
    iconFile.copyTo(out.resolve("ic_launcher.png"), overwrite = true)
}

android {
    namespace = "com.carwatch.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.geomode.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"
        manifestPlaceholders["appIcon"] =
            if (iconFile.exists()) "@mipmap/ic_launcher" else "@android:drawable/sym_def_app_icon"
    }

    sourceSets {
        getByName("main") {
            res.srcDir(genRes)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
