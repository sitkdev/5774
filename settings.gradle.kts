rootProject.name = "mic-kmp-sample"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "stringfog") {
                useModule("com.github.megatronking.stringfog:gradle-plugin:5.2.0")
            }
            if (requested.id.id == "sq.res-guard") {
                useModule("com.github.sq-dsl.SqGuard:sq.res-guard:0.0.1")
            }
            if (requested.id.id == "ru.cleverpumpkin.proguard-dictionaries-generator") {
                useModule("gradle.plugin.ru.cleverpumpkin.proguard-dictionaries-generator:plugin:1.0.8")
            }
            if (requested.id.id == "io.github.valacuz.proguard-dictionary-generator") {
                useModule("io.github.valacuz:proguard-dict-generator:1.0.1")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
    }
}

include(":composeApp")