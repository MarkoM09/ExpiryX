// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kapt)
    alias(libs.plugins.parcelize)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.expiryx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.expiryx.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-beta" // version shown in Settings

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // FIXED: Use a literal string or access from project properties if needed, 
        // but ensuring it's not null here.
        resValue("string", "app_version_name", "1.0.0-beta")
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Enable 16KB page alignment
            // https://developer.android.com/guide/practices/page-sizes#check-alignment
            packaging {
                jniLibs {
                    useLegacyPackaging = false
                }
            }
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
        viewBinding = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}



dependencies {
    // AndroidX core UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Room
    implementation(libs.room.runtime)
    kapt(libs.room.compiler)
    implementation(libs.room.ktx)

    // Google Sign-In & Firebase
    implementation(libs.play.services.auth)
    implementation(libs.play.services.base)
    
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Glide
    implementation(libs.glide)
    kapt(libs.glide.compiler)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit
    implementation(libs.mlkit.barcode.scanning)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.json)

    // Guava (Fix for ListenableFuture issue)
    implementation(libs.guava)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Charts
    implementation(libs.mpandroidchart)

    // Apache POI (Excel export)


    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
