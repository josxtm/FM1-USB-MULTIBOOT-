plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = providers.gradleProperty("fm1Build").map(String::toInt)
val signingPath = providers.environmentVariable("FM1_SIGNING_PATH").orNull

android {
    namespace = "com.josxtm.fm1screen"
    compileSdk = 35
    sourceSets["main"].assets.srcDir(rootProject.file("assets"))
    val approvedIcon = file("src/officialIcon/AndroidManifest.xml")
    if (approvedIcon.isFile) {
        sourceSets["main"].res.srcDir("src/officialIcon/res")
        for (variant in listOf("debug", "release")) {
            sourceSets[variant].manifest.srcFile(approvedIcon)
        }
    }
    defaultConfig {
        applicationId = "com.josxtm.fm1screen"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber.get()
        versionName = "1.0"
        buildConfigField("int", "COMPILATION", buildNumber.get().toString())
    }
    if (signingPath != null) {
        signingConfigs {
            create("distribution") {
                storeFile = file(signingPath)
                storePassword = providers.environmentVariable("FM1_SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("FM1_SIGNING_ALIAS").get()
                keyPassword = providers.environmentVariable("FM1_SIGNING_KEY_PASSWORD").get()
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("distribution")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin {
    jvmToolchain(17)
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
}
