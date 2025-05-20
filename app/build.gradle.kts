plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yinnho.upnpcast"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        
        // 版本信息设置
        version = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 测试配置
    testOptions {
        unitTests.all {
            it.enabled = true
        }
    }
    
    // 简化lint配置
    lint {
        abortOnError = false
    }

    // 简化构建类型配置
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    // Java版本配置
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // 核心依赖，使用Version Catalog
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // 网络相关
    implementation(libs.okhttp)
    
    // JSON解析
    implementation(libs.gson)
    
    // 测试依赖 - JUnit 5和Mockito
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1") // 参数化测试支持
    testImplementation("org.mockito:mockito-core:5.10.0") 
    testImplementation("org.mockito:mockito-junit-jupiter:5.10.0") // Mockito的JUnit5支持
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}