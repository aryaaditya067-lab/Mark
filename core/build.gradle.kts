plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.mark.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.testLogging {
                showStandardStreams = true
            }
        }
    }
}

// NOTE on api vs implementation:
// This does NOT shrink the APK or speed up startup — both keep the library on the
// runtime classpath. What it does: keeps them off the consumers' COMPILE classpath,
// so :app and :wear cannot accidentally start using Firestore or Retrofit directly,
// and incremental builds get faster. Actual size/startup wins come from R8.
//
// Rule used below: `api` only where :app or :wear imports the library's types
// directly. Everything else is an internal detail of :core.
dependencies {
    implementation(libs.androidx.core.ktx)

    // Network — Groq. Used only inside core's network package.
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Firebase.
    // bom stays api so version alignment is inherited by consumers.
    // firebase-auth stays api: FirebaseApp.initializeApp() is called in
    // WearApplication and MarkApplication, and FirebaseApp ships in
    // firebase-common which auth pulls in.
    // firestore is used ONLY by core's repositories — the watch never touches it.
    api(platform(libs.firebase.bom))
    api(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // DataStore — core's SettingsRepository only
    implementation(libs.androidx.datastore.preferences)

    // Coroutines — used everywhere, stays api
    api(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Play Services
    // wearable stays api: MarkWearableListenerService in :app extends
    // WearableListenerService directly.
    // auth stays api: the sign-in screens use GoogleSignIn types.
    // location is used only by core's PlayLocationProvider.
    api(libs.play.services.wearable)
    api(libs.play.services.auth)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.junit)
}