plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "fr.pierre.randogpx"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.pierre.randogpx"
        minSdk = 26 // Android 8.0, nécessaire pour les Foreground Service types modernes
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // Suffixe d'applicationId indispensable pour pouvoir installer le debug et le
            // release en parallèle sur le même téléphone (sinon même package = la seconde
            // installation remplace la première au lieu de coexister).
            applicationIdSuffix = ".debug"
        }
        release {
            isDebuggable = true // TEMPORAIRE : retiré juste après l'import GPX en masse, voir commentaire plus bas
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signature avec la clé debug (auto-générée par AGP) uniquement pour pouvoir
            // installer ce build release localement à des fins de comparaison/test. À
            // remplacer par une vraie clé de release (gardée en lieu sûr, jamais dans le
            // dépôt) avant toute publication réelle — F-Droid signe de toute façon ses
            // propres builds reproductibles et ignore cette configuration.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // --- Core / AndroidX (libre, AOSP) ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // --- Jetpack Compose (libre) ---
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- Room (stockage local, libre) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // --- osmdroid (cartes libres, LGPL, cache tuiles offline natif) ---
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // --- MPAndroidChart (graphique élévation, libre Apache 2.0) ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // --- Test ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
