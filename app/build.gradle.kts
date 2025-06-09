plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.yinnho.upnpcast"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        
        // Version information setting
        version = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Add ProGuard consumer rules
        consumerProguardFiles("consumer-rules.pro")
    }

    // Test configuration
    testOptions {
        unitTests.all {
            it.enabled = true
            it.useJUnitPlatform()
        }
    }
    
    // Simplified lint configuration
    lint {
        abortOnError = false
    }

    // Simplified build type configuration
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }
    
    // Java version configuration
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    // Library doesn't need ViewBinding
    buildFeatures {
        buildConfig = false
    }

    // 简化的发布配置，适合JitPack
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Core dependencies, using Version Catalog
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // 添加RecyclerView支持，用于本地视频选择器
    implementation(libs.androidx.recyclerview)
    
    // Network related
    implementation(libs.okhttp)
    
    // JSON parsing
    implementation(libs.gson)
    
    // Local file server for local casting
    implementation(libs.nanohttpd)
    
    // Test dependencies
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core) 
    testImplementation(libs.mockito.junit.jupiter)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// 简化的发布配置 - JitPack会自动处理
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                
                groupId = "com.github.yinnho"
                artifactId = "UPnPCast" 
                version = "1.1.2"
            }
        }
    }
}