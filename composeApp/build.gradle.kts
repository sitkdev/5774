import com.github.megatronking.stringfog.plugin.StringFogExtension
import com.github.megatronking.stringfog.plugin.StringFogMode
import com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator
import com.sqwerty.core.utils.ifNotExist
import com.sqwerty.res_guard.configureResGuardPlugin
import com.sqwerty.res_guard.extensions.ResGuardExtensions
import com.sqwerty.res_guard.utils.ResType
import io.github.valacuz.proguard.dictionary.DictionaryGeneratorPluginExtension
import io.github.valacuz.proguard.dictionary.tasks.generate.strategy.ObfuscationStrategy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("stringfog")
    id("sq.res-guard")
    id("io.github.valacuz.proguard-dictionary-generator")
    id("ru.cleverpumpkin.proguard-dictionaries-generator")

    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val appName = rootProject.extra["app_name"] as String
val appId = rootProject.extra["bundle"] as String
val isMinifyEnabledConfig = rootProject.extra["isMinifyEnabled"] as Boolean
val isShrinkResourcesConfig = rootProject.extra["isShrinkResources"] as Boolean
val minSdkConfig = rootProject.extra["minSdk"] as Int
val targetSdkConfig = rootProject.extra["targetSdk"] as Int
val compileSdkConfig = rootProject.extra["compileSdk"] as Int
val javaVersionConfig = JavaVersion.valueOf(rootProject.extra["javaVersion"] as String)
val jvmTargetConfig = rootProject.extra["jvmTarget"] as String
val versionCodeConfig = rootProject.extra["versionCode"] as Int
val versionNameConfig = rootProject.extra["versionName"] as String

fun writeWithYellow(text: String) = logger.lifecycle("[33m$text[0m")

val setupValues = file("$projectDir/setup.txt").ifNotExist {
    createNewFile()
    writeText(
        "Resource obfuscation: ${Random.nextInt(0, 2)}${System.lineSeparator()}" +
                "Strings obfuscation: ${Random.nextInt(0, 2)}${System.lineSeparator()}" +
                "Code obfuscation: ${Random.nextInt(0, 3)}"
    )
}.readLines().map { it.split(": ")[1] }

val (resObfuscation, stringsObfuscation, codeObfuscation) = setupValues

android {
    namespace = appId
    compileSdk = compileSdkConfig

    defaultConfig {
        applicationId = appId
        minSdk = minSdkConfig
        targetSdk = targetSdkConfig
        versionCode = versionCodeConfig
        versionName = versionNameConfig
        resValue("string", "app_name", appName)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = isMinifyEnabledConfig
            isShrinkResources = isShrinkResourcesConfig
            when (codeObfuscation) {
                "0" -> proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
                "1" -> proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro", "valacuz.pro"
                )
                "2" -> proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro", "pumpkin.pro"
                )
            }
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = javaVersionConfig
        targetCompatibility = javaVersionConfig
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            // Teach the kotlin-parcelize compiler plugin to treat our
            // multiplatform-safe @CommonParcelize annotation (which actual-aliases
            // to @kotlinx.parcelize.Parcelize on Android) as a Parcelize trigger,
            // so Circuit Screen `data object`s get a generated Parcelable impl.
            freeCompilerArgs.add(
                "-P",
            )
            freeCompilerArgs.add(
                "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=" +
                    "com.trid.test.kmpsample.navigation.CommonParcelize",
            )
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.stringfog.xor)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.messaging)

            implementation(libs.core.splashscreen)

            implementation(libs.koin.android)

            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.kermit)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.multiplatform.settings.no.arg)

            implementation(libs.circuit)

            implementation(libs.ktor.client.core)

            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.coil.compose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.multiplatform.settings.no.arg)
        }
    }
}

when (codeObfuscation) {
    "1" -> {
        writeWithYellow("Code obfuscation: valacuz")
        extensions.configure(DictionaryGeneratorPluginExtension::class) {
            createConfigFile = true
            configFilePath = DictionaryGeneratorPluginExtension.DEFAULT_CONFIG_FILE_PATH
            fieldMethodObfuscationStrategy = ObfuscationStrategy.RANDOM_CHARACTERS
            classObfuscationStrategy = ObfuscationStrategy.RANDOM_CHARACTERS
            packageObfuscationStrategy = ObfuscationStrategy.RANDOM_CHARACTERS
            variantNameFilter = null
        }
    }
    "2" -> {
        writeWithYellow("Code obfuscation: CleverPumpkin")
        proguardDictionaries {
            dictionaryNames = listOf(
                "build/class-dictionary",
                "build/package-dictionary",
                "build/obfuscation-dictionary"
            )
            minLineLength = 20
            maxLineLength = 50
            linesCountInDictionary = 80000
        }
    }
    else -> writeWithYellow("Code obfuscation: none")
}

configureResGuardPlugin<ResGuardExtensions> {
    enabled = (resObfuscation != "0").also {
        writeWithYellow("Resources obfuscation: $it")
    }
    maxNameLength = 255
    minNameLength = 16
    resTypes = listOf(ResType.DRAWABLE)
    outputMappingPath = projectDir.path
}

configure<StringFogExtension> {
    enable = (stringsObfuscation != "0").also {
        writeWithYellow("Strings obfuscation: $it")
    }
    implementation = "com.github.megatronking.stringfog.xor.StringFogImpl"
    fogPackages = arrayOf(appId)
    kg = RandomKeyGenerator()
    mode = StringFogMode.base64
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

afterEvaluate {
    try {
        tasks.named("uploadCrashlyticsMappingFileRelease").configure { enabled = false }
    } catch (_: Exception) {}

    tasks.named("preBuild") {
        finalizedBy("rebundle")
    }

    if (System.getenv("CI_BUILD") != "true") {
        tasks.named("bundleRelease").configure {
            finalizedBy("removeProguardMap")
        }
    }
}

tasks.register("rebundle") {
    doLast {
        val newBundle = rootProject.extra["bundle"] as String
        val javaDir = file("${projectDir}/src/main/java")
        if (!javaDir.exists()) return@doLast
        val oldBundle = getCurrentBundleViaRecursion(javaDir).replace("java.", "")
        if (newBundle == oldBundle) return@doLast
        changeBundleInManifest(oldBundle, newBundle)
        val newFolder = file("${projectDir}/src/main/java/${newBundle.replace(".", "/")}")
        val currentFolder = file("${projectDir}/src/main/java/${oldBundle.replace(".", "/")}")
        currentFolder.copyRecursively(newFolder)
        currentFolder.apply {
            deleteRecursively()
            deleteOldFiles(this)
        }
        getAllProjectFilesViaRecursion(newFolder).forEach { file ->
            file.inputStream().use { fis ->
                val reBundled = fis.readBytes().decodeToString().replace(oldBundle, newBundle)
                file.outputStream().use { fos ->
                    fos.write(reBundled.encodeToByteArray())
                }
            }
        }
    }
}

tasks.register("removeProguardMap") {
    doLast {
        val generatedAabPath = "${projectDir}/release"
        val aabFile = file("${generatedAabPath}/composeApp-release.aab")
        val zipFile = file("${generatedAabPath}/composeApp-release.zip")
        val savedProguardMapFile = file("${generatedAabPath}/proguard.map")
        val tempZipFilePath = file("${generatedAabPath}/composeApp-release-temp.zip")
        val targetFilePath = "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"
        if (!aabFile.exists()) return@doLast
        aabFile.renameTo(zipFile)
        val zf = ZipFile(zipFile)
        val zos = ZipOutputStream(tempZipFilePath.outputStream())
        try {
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() as ZipEntry
                if (entry.name != targetFilePath) {
                    zos.putNextEntry(ZipEntry(entry.name))
                    zf.getInputStream(entry).use { it.copyTo(zos) }
                    zos.closeEntry()
                } else {
                    zf.getInputStream(entry).use { input ->
                        savedProguardMapFile.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        } finally {
            zos.close()
            zf.close()
        }
        zipFile.delete()
        tempZipFilePath.renameTo(aabFile)
    }
}

fun changeBundleInManifest(oldBundle: String, newBundle: String) {
    file("${projectDir}/src/main/AndroidManifest.xml").apply {
        inputStream().use { fis ->
            val reBundled = fis.readBytes().decodeToString().replace(oldBundle, newBundle)
            outputStream().use { fos ->
                fos.write(reBundled.encodeToByteArray())
            }
        }
    }
}

fun getCurrentBundleViaRecursion(file: File): String {
    return if ((file.listFiles()?.count() ?: 0) > 1) file.name
    else file.name + "." + getCurrentBundleViaRecursion(file.listFiles()!![0])
}

fun getAllProjectFilesViaRecursion(file: File): List<File> {
    return if (file.listFiles() == null) listOf(file)
    else file.listFiles()!!.flatMap { getAllProjectFilesViaRecursion(it) }
}

fun deleteOldFiles(file: File) {
    if ((file.listFiles()?.count() ?: 0) == 0) {
        file.delete()
        deleteOldFiles(file.parentFile)
    }
}