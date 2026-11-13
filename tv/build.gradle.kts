import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val lastfmApiKey: String = (
    localProps.getProperty("LASTFM_API_KEY")
        ?: System.getenv("LASTFM_API_KEY")
        ?: ""
    ).trim()
val lastfmSecret: String = (
    localProps.getProperty("LASTFM_SECRET")
        ?: System.getenv("LASTFM_SECRET")
        ?: ""
    ).trim()

val listenTogetherServer: String = (
    localProps.getProperty("LISTEN_TOGETHER_SERVER")
        ?: System.getenv("LISTEN_TOGETHER_SERVER")
        ?: "https://bitchord-listen-together.onrender.com"
    ).trim().trimEnd('/')

val betaSuffix = ""

val prepareTvRes = tasks.register<Sync>("prepareTvRes") {
    from("../app/src/main/res") { exclude("mipmap-anydpi/**") }
    from("../app/src/main/res/mipmap-anydpi") { into("mipmap-anydpi-v26") }
    into(layout.buildDirectory.dir("tvRes"))
}
tasks.named("preBuild") { dependsOn(prepareTvRes) }

android {
    namespace = "com.music.bitchord"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.music.bitchord.tv"
        minSdk = 25
        targetSdk = 36
        versionCode = 39
        versionName = "1.9.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "LASTFM_API_KEY", "\"${lastfmApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "LASTFM_SECRET", "\"${lastfmSecret.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField(
            "String",
            "LISTEN_TOGETHER_SERVER",
            "\"${listenTogetherServer.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        buildConfigField("String", "ADDON_AUTH_URL", "\"\"")
        buildConfigField("String", "UPDATE_APK_PREFIX", "\"BitChordTV-\"")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("../app/src/main/java")
            res.srcDir(prepareTvRes)
            assets.srcDir("../app/src/main/assets")
            jniLibs.srcDir("../app/src/main/jniLibs")
        }
        getByName("debug") { java.srcDir("../app/src/debug/java") }
        configureEach { if (name == "dev") manifest.srcFile("src/dev/AndroidManifest.xml") }
    }

    externalNativeBuild {
        cmake {
            path = file("../app/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationId = "com.dev.bitchord.tv"
            resValue("string", "app_name", "BitChord TV")
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "BitChord TV")
        }
    }

    signingConfigs {
        val store = signing.getProperty("storeFile")?.let { rootProject.file(it) }
        if (store != null && store.exists()) {
            create("release") {
                storeFile = store
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (betaSuffix.isNotEmpty()) versionNameSuffix = "-$betaSuffix"
        }
        release {
            if (betaSuffix.isNotEmpty()) versionNameSuffix = "-$betaSuffix"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "../app/proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".benchmark"
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val newPipeExtractorRaw: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}
dependencies {
    newPipeExtractorRaw("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
}
val newPipeExtractorStripped = tasks.register<org.gradle.api.tasks.bundling.Jar>(
    "stripNewPipeExtractorUtils"
) {
    archiveFileName.set("NewPipeExtractor-v0.26.3-noutils.jar")
    destinationDirectory.set(layout.buildDirectory.dir("stripped-libs"))
    from(provider { newPipeExtractorRaw.map { zipTree(it) } }) {
        exclude("org/schabi/newpipe/extractor/utils/Utils.class")
        exclude("org/schabi/newpipe/extractor/utils/Utils\$*.class")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(":shared"))
    implementation(project(":sharedUi"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha12")
    implementation("androidx.startup:startup-runtime:1.2.0")

    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.media3:media3-common:1.11.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("io.coil-kt.coil3:coil-svg:3.0.4")
    implementation("com.github.cosmictaserdev-creator.Jetpack_SF_Symbols:sfsymbols-android:1.1.1") {
        isTransitive = false
    }
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("dev.chrisbanes.haze:haze-materials:1.3.1")

    implementation("com.google.zxing:core:3.5.3")

    implementation("com.halilibo.compose-richtext:richtext-ui-material3:0.20.0")
    implementation("com.halilibo.compose-richtext:richtext-commonmark:0.20.0")

    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("io.ktor:ktor-client-websockets:3.5.2")

    implementation("com.github.MetrolistGroup.innertubex:innertubex-android:v0.7.0")

    implementation(files(newPipeExtractorStripped))
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.protobuf:protobuf-javalite:4.35.0")
    implementation("org.mozilla:rhino:1.8.1")
    implementation("org.mozilla:rhino-engine:1.8.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("io.github.dokar3:quickjs-kt-android:1.0.14")

    implementation("com.hierynomus:smbj:0.15.0")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")

    implementation("com.google.android.gms:play-services-cast-framework:22.2.0")
    implementation("androidx.mediarouter:mediarouter:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

val verifyDevInstall = tasks.register("verifyDevInstall") {
    group = "install"
    description = "Pre-verifies the installed dev build on every connected device."
    val adb = androidComponents.sdkComponents.adb
    doLast {
        val adbPath = adb.get().asFile.absolutePath
        val serials = ProcessBuilder(adbPath, "devices").start()
            .inputStream.bufferedReader().readLines()
            .drop(1)
            .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 && it[1] == "device" }?.get(0) }
        serials.forEach { serial ->
            logger.lifecycle("verifyDevInstall: compiling com.dev.bitchord on $serial")
            ProcessBuilder(
                adbPath, "-s", serial, "shell", "cmd", "package", "compile",
                "-m", "verify", "-f", "com.dev.bitchord",
            ).inheritIO().start().waitFor()
        }
    }
}
tasks.matching { it.name == "installDevDebug" }.configureEach { finalizedBy(verifyDevInstall) }

val phoneJava = file("../app/src/main/java")
val tvJava = file("src/main/java")
val replacedByTv: Set<String> = fileTree(tvJava) { include("**/*.kt") }.files
    .map { it.relativeTo(tvJava).invariantSeparatorsPath }
    .filter { File(phoneJava, it).isFile }
    .toSet()
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude { el ->
        el.file.startsWith(phoneJava) && el.relativePath.pathString in replacedByTv
    }
}
