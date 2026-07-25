import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.notifilter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ibrahimege.notifilter"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            FileInputStream(localPropertiesFile).use { localProperties.load(it) }
        }
        val supabaseUrl = (localProperties.getProperty("SUPABASE_URL") ?: "").trim()
        val supabaseAnonKey = (localProperties.getProperty("SUPABASE_ANON_KEY") ?: "").trim()
        val billingProductId = (localProperties.getProperty("BILLING_PRODUCT_ID") ?: "").trim()
        val billingBasePlanId = (localProperties.getProperty("BILLING_BASE_PLAN_ID") ?: "").trim()
        val billingOfferId = (localProperties.getProperty("BILLING_OFFER_ID") ?: "").trim()
        val billingBypass = (localProperties.getProperty("BILLING_BYPASS") ?: "").trim()
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "BILLING_PRODUCT_ID", "\"$billingProductId\"")
        buildConfigField("String", "BILLING_BASE_PLAN_ID", "\"$billingBasePlanId\"")
        buildConfigField("String", "BILLING_OFFER_ID", "\"$billingOfferId\"")
        buildConfigField(
            "Boolean",
            "BILLING_BYPASS",
            "${billingBypass.equals("true", ignoreCase = true)}"
        )
    }

    val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        create("internal") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // OAuth (Custom Tabs)
    implementation("androidx.browser:browser:1.7.0")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")
}
