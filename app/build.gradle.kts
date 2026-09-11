import java.io.ByteArrayOutputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Esegue git nella root del progetto; stringa vuota se git non è disponibile o non è un repository. */
fun runGit(vararg args: String): String = try {
    val out = ByteArrayOutputStream()
    project.exec {
        workingDir = rootProject.projectDir
        commandLine(listOf("git") + args.toList())
        standardOutput = out
        isIgnoreExitValue = true
    }
    out.toString(Charsets.UTF_8.name()).trim()
} catch (e: Exception) {
    ""
}

val gitCommitCount: Int = runGit("rev-list", "--count", "HEAD").toIntOrNull()?.coerceAtLeast(1) ?: 1

fun escapeKotlin(testo: String): String = testo.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

/**
 * Genera il sorgente Kotlin `Changelog.kt` con una voce per ogni commit git (versionCode = posizione
 * nella storia, coerente con `gitCommitCount`/`versionCode` dell'app), da mostrare nella voce
 * "Versioni" del menu. Se non c'è storia git, la lista risulta vuota: la UI lo gestisce senza errori.
 * Stesso approccio usato in WorkoutAnalyzer e Calendario++.
 */
fun generaSorgenteChangelog(): String {
    val log = runGit("log", "--reverse", "--date=short", "--pretty=format:%ad@@@%s")
    val voci = StringBuilder()
    if (log.isNotBlank()) {
        log.lines().forEachIndexed { indice, riga ->
            val parti = riga.split("@@@", limit = 2)
            if (parti.size == 2) {
                voci.append("    VoceChangelog(versionCode = ${indice + 1}, data = \"${parti[0]}\", messaggio = \"${escapeKotlin(parti[1])}\"),\n")
            }
        }
    }
    return """
        |package com.desideri.voice2text.changelog
        |
        |data class VoceChangelog(val versionCode: Int, val data: String, val messaggio: String)
        |
        |val CHANGELOG: List<VoceChangelog> = listOf(
        |$voci)
        |
    """.trimMargin()
}

val changelogGeneratoDir = layout.buildDirectory.dir("generated/changelog")
val gitHeadCommit: String = runGit("rev-parse", "HEAD")

val generaChangelog = tasks.register("generaChangelog") {
    // Senza un input dichiarato, Gradle non avrebbe modo di accorgersi che la storia git è
    // cambiata da una build all'altra e considererebbe il task sempre up-to-date dopo la prima
    // esecuzione: l'HEAD commit forza la rigenerazione a ogni nuovo commit.
    inputs.property("gitHeadCommit", gitHeadCommit)
    val outputDir = changelogGeneratoDir
    outputs.dir(outputDir)
    doLast {
        val pacchettoDir = File(outputDir.get().asFile, "com/desideri/voice2text/changelog")
        pacchettoDir.mkdirs()
        File(pacchettoDir, "Changelog.kt").writeText(generaSorgenteChangelog())
    }
}

android {
    namespace = "com.desideri.voice2text"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.desideri.voice2text"
        minSdk = 24
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "1.0.$gitCommitCount"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }

        buildConfigField("String", "GEMINI_API_KEY", "\"${properties.getProperty("GEMINI_API_KEY", "")}\"")
        android.buildFeatures.buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    // Nome fisso dell'apk generato (invece di "app-debug.apk"/"app-release.apk"), comodo
    // per condividerlo manualmente da installare sul telefono.
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "Voice2Text.apk"
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir(changelogGeneratoDir)
        }
    }
}

tasks.matching { it.name.contains("Kotlin") }.configureEach {
    dependsOn(generaChangelog)
}

dependencies {
    // --- GEMINI ---
    implementation("com.google.genai:google-genai:1.63.0")

    // Risoluzione conflitti Guava
    constraints {
        implementation("com.google.guava:guava:32.1.2-android")
    }

    // --- ANDROIDX & COMPOSE ---
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // --- TESTING ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
