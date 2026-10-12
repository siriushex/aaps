import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

val localGoogleApiProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val googleMapsApiKey = providers.environmentVariable("AAPS_GOOGLE_MAPS_API_KEY")
    .orElse(localGoogleApiProperties.getProperty("googleMapsApiKey", ""))

android {
    namespace = "app.aaps.plugins.automation"
    defaultConfig {
        resValue("string", "key_google_apis_android", googleMapsApiKey.get())
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":shared:impl"))
    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:main"))
    testImplementation(project(":pump:virtual"))

    api(libs.androidx.constraintlayout)
    api(libs.com.google.android.gms.playservices.location)
    api(libs.kotlin.reflect)
    // Places SDK
    api(libs.com.google.android.places)
    api(libs.com.github.rtchagas.pingplacepicker)
    api(libs.com.google.firebase.config)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}