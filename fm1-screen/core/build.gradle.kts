plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    jvmToolchain(17)
    sourceSets {
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}
