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
        
        // Version information setting
        version = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Add ProGuard consumer rules
        consumerProguardFiles("consumer-rules.pro")
    }

    // Test configuration
    testOptions {
        unitTests.all {
            it.enabled = true
        }
    }
    
    // Simplified lint configuration
    lint {
        abortOnError = false
    }

    // Simplified build type configuration
    buildTypes {
        release {
            isMinifyEnabled = true
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
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
    
    // Library doesn't need ViewBinding
    buildFeatures {
        buildConfig = false
    }

    // Add sources and documentation packaging
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // Core dependencies, using Version Catalog
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Network related
    implementation(libs.okhttp)
    
    // JSON parsing
    implementation(libs.gson)
    
    // Test dependencies - JUnit 5 and Mockito
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params) // Parameterized test support
    testImplementation(libs.mockito.core) 
    testImplementation(libs.mockito.junit.jupiter) // Mockito's JUnit5 support
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Maven release configuration
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
                description.set("Modern Android DLNA/UPnP screen casting library, as a replacement for the deprecated Cling project")
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

// Signing configuration (for Maven Central)
signing {
    sign(publishing.publications["release"])
}