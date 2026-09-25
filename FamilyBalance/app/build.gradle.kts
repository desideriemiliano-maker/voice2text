plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/**
 * Esegue git nella cartella del progetto (FamilyBalance/); stringa vuota se git non è disponibile
 * o non è un repository.
 */
fun runGit(vararg args: String): String = try {
    val processo = ProcessBuilder("git", *args)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = processo.inputStream.bufferedReader().readText().trim()
    processo.waitFor()
    if (processo.exitValue() == 0) output else ""
} catch (e: Exception) {
    ""
}

// Il progetto vive in una sottocartella di un repository che contiene anche altre app: il
// pathspec limitano conteggio e changelog ai soli commit che toccano questa app (FamilyBalance/,
// e SpeseFamiglia/ dove si trovava prima di essere rinominata).
val PATHSPEC_APP = arrayOf("--", ".", ":(top)SpeseFamiglia")
val gitCommitCount: Int = runGit("rev-list", "--count", "HEAD", *PATHSPEC_APP).toIntOrNull()?.coerceAtLeast(1) ?: 1
val gitHeadCommit: String = runGit("rev-parse", "HEAD")

fun escapeKotlin(testo: String): String = testo.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

/**
 * Genera il sorgente Kotlin `Changelog.kt` con una voce per ogni commit git (versionCode =
 * posizione nella storia, coerente con `gitCommitCount`), da mostrare nella voce "Versioni" del
 * menu. Stesso approccio usato in WorkoutAnalyzer e Voice2Text.
 */
fun generaSorgenteChangelog(): String {
    val log = runGit("log", "--reverse", "--date=short", "--pretty=format:%ad@@@%s", *PATHSPEC_APP)
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
        |package com.desideri.familybalance.changelog
        |
        |data class VoceChangelog(val versionCode: Int, val data: String, val messaggio: String)
        |
        |val CHANGELOG: List<VoceChangelog> = listOf(
        |$voci)
        |
    """.trimMargin()
}

val changelogGeneratoDir = layout.buildDirectory.dir("generated/changelog")

val generaChangelog = tasks.register("generaChangelog") {
    // L'HEAD commit come input forza la rigenerazione a ogni nuovo commit.
    inputs.property("gitHeadCommit", gitHeadCommit)
    val outputDir = changelogGeneratoDir
    outputs.dir(outputDir)
    doLast {
        val pacchettoDir = File(outputDir.get().asFile, "com/desideri/familybalance/changelog")
        pacchettoDir.mkdirs()
        File(pacchettoDir, "Changelog.kt").writeText(generaSorgenteChangelog())
    }
}

android {
    namespace = "com.desideri.familybalance"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.desideri.familybalance"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "1.0.$gitCommitCount"

        // Chiave Gemini per l'import degli estratti conto: da local.properties (mai committato),
        // scritto in CI dal secret GEMINI_API_KEY del repository, come in Voice2Text.
        val proprietaLocali = java.util.Properties()
        val fileProprieta = rootProject.file("local.properties")
        if (fileProprieta.exists()) fileProprieta.inputStream().use { proprietaLocali.load(it) }
        buildConfigField("String", "GEMINI_API_KEY", "\"${proprietaLocali.getProperty("GEMINI_API_KEY", "")}\"")
    }

    // Keystore di debug condiviso e committato: build CI e locali firmano allo stesso modo, così
    // l'apk si installa sempre come aggiornamento. Lo SHA-1 di questo keystore è quello da
    // registrare nel client OAuth Android di Google Cloud per il backup su Drive.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "FamilyBalance.apk"
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
    // --- ANDROIDX & COMPOSE ---
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Blocco biometrico all'apertura (BiometricPrompt richiede una FragmentActivity).
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // --- DATABASE ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- GEMINI (import estratti conto, stessa libreria di Voice2Text) ---
    implementation("com.google.genai:google-genai:1.63.0")

    // --- BACKUP SU GOOGLE DRIVE (stesse versioni di WorkoutAnalyzer) ---
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    constraints {
        implementation("com.google.guava:guava:32.1.2-android")
    }

    // --- TEST ---
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
