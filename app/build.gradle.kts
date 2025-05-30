plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.yinnho.upnpcast"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        
        // 版本信息设置
        version = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 添加ProGuard消费者规则
        consumerProguardFiles("consumer-rules.pro")
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
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }
    
    // Java版本配置
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
    
    // 库不需要ViewBinding
    buildFeatures {
        buildConfig = false
    }

    // 添加源码和文档打包
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
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

// Maven发布配置
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.yinnho"
            artifactId = "upnpcast"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("UPnPCast")
                description.set("现代化的Android DLNA/UPnP投屏库，作为停止维护的Cling项目的替代品")
                url.set("https://github.com/yinnho/UPnPCast")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("yinnho")
                        name.set("UPnPCast Team")
                        email.set("dev@upnpcast.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/yinnho/UPnPCast.git")
                    developerConnection.set("scm:git:ssh://github.com/yinnho/UPnPCast.git")
                    url.set("https://github.com/yinnho/UPnPCast/tree/main")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/yinnho/UPnPCast")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}

// 签名配置（用于Maven Central）
signing {
    sign(publishing.publications["release"])
}