plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.bluepad"
    compileSdk = 36
    defaultConfig { applicationId = "com.bluepad"; minSdk = 28; targetSdk = 36; versionCode = 5; versionName = "1.3.0" }
    buildFeatures { buildConfig = true }
    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "VERBOSE_DIAGNOSTICS", providers.gradleProperty("bluepadDiagnostics").map { (it == "true").toString() }.getOrElse("false"))
        }
        getByName("release") { buildConfigField("boolean", "VERBOSE_DIAGNOSTICS", "false") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { testImplementation("junit:junit:4.13.2") }
