import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

val googleServicesFile = file("google-services.json")

if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("app/google-services.json not found; non-release builds will not include Firebase resources.")
}

val hasReleaseGoogleServicesConfig = googleServicesFile.isFile &&
    googleServicesFile.length() > 0L &&
    Regex("""\"package_name\"\s*:\s*\"com\.example\.pokemonalertsv2\"""")
        .containsMatchIn(googleServicesFile.takeIf { it.isFile }?.readText().orEmpty())

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun releaseSigningProperty(name: String): String? =
    keystoreProperties.getProperty(name)
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }

val releaseSigningRequiredProperties = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword"
)

val releaseSigningMissingProperties = releaseSigningRequiredProperties
    .filter { releaseSigningProperty(it) == null }

val hasReleaseSigningConfig = releaseSigningMissingProperties.isEmpty()

val googleMapsApiKey: String =
    localProperties.getProperty("GOOGLE_MAPS_API_KEY")?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable("GOOGLE_MAPS_API_KEY").orNull?.takeIf { it.isNotBlank() }
        ?: "YOUR_API_KEY_HERE"

val alertsApiBaseUrl: String =
    localProperties.getProperty("ALERTS_API_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable("ALERTS_API_BASE_URL").orNull?.takeIf { it.isNotBlank() }
        ?: "http://api.alsbach-scanner.uk/"

val osmTileUrl: String =
    localProperties.getProperty("OSM_TILE_URL")?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable("OSM_TILE_URL").orNull?.takeIf { it.isNotBlank() }
        ?: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

android {
    namespace = "com.example.pokemonalertsv2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.pokemonalertsv2"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "maps_api_key", googleMapsApiKey)
        buildConfigField("String", "ALERTS_API_BASE_URL", "\"${alertsApiBaseUrl.trimEnd('/')}/\"")
        buildConfigField("String", "OSM_TILE_URL", "\"$osmTileUrl\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = rootProject.file(releaseSigningProperty("storeFile")!!)
                storePassword = releaseSigningProperty("storePassword")
                keyAlias = releaseSigningProperty("keyAlias")
                keyPassword = releaseSigningProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.matching {
    it.name == "preReleaseBuild" || it.name == "assembleRelease" || it.name == "bundleRelease"
}.configureEach {
    doFirst {
        check(hasReleaseGoogleServicesConfig) {
            "Release Firebase configuration is missing or does not target com.example.pokemonalertsv2. " +
                "Provide app/google-services.json before building a release."
        }
        check(hasReleaseSigningConfig) {
            "Release signing is not configured. Fill keystore.properties. Missing values: " +
                releaseSigningMissingProperties.joinToString()
        }
    }
}

dependencies {

    baselineProfile(project(":baselineprofile"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
        implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.core)
    implementation(libs.google.play.services.maps)
    implementation(libs.google.maps.compose)
    implementation(libs.maplibre.android)
    implementation(libs.google.play.services.location)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
