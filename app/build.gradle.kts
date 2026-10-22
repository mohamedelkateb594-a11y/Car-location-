plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.carwatch.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.carwatch.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"

        // مفتاح Google Maps: بيتقرأ من متغير البيئة MAPS_API_KEY (GitHub Secret) أو من gradle.properties
        val mapsKey = System.getenv("MAPS_API_KEY")
            ?: (project.findProperty("MAPS_API_KEY") as String?)
            ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
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
    implementation("com.google.android.gms:play-services-maps:18.2.0")
}
