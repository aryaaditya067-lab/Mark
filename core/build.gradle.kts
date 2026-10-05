import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// API keys stay out of source control: read from local.properties (gitignored),
// falling back to environment variables for CI. Missing values build as "".
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localConfig(name: String): String =
    (localProperties.getProperty(name) ?: System.getenv(name) ?: "").trim()

android {
    namespace = "com.example.mark.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "MIMO_API_KEY", "\"${localConfig("MIMO_API_KEY")}\"")
        buildConfigField("String", "MIMO_MODEL", "\"${localConfig("MIMO_MODEL").ifEmpty { "mimo-v2.6-flash" }}\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"${localConfig("WEATHER_API_KEY")}\"")
        buildConfigField("String", "TAVILY_API_KEY", "\"${localConfig("TAVILY_API_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
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

    // Network — LLM (MiMo) and weather. Used only inside core's network package.
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