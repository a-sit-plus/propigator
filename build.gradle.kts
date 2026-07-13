plugins {
    val kotlinVer = System.getenv("KOTLIN_VERSION_ENV")?.ifBlank { null } ?: libs.versions.kotlin.get()
    val testballoonVer = System.getenv("TESTBALLOON_VERSION_OVERRIDE")?.ifBlank { null } ?: libs.versions.testballoon.get()


    alias(libs.plugins.asp)
    alias(libs.plugins.sbombastic)
    alias(libs.plugins.spotless)
    kotlin("multiplatform") version kotlinVer apply false
    kotlin("plugin.serialization") version kotlinVer apply false
    id("com.android.kotlin.multiplatform.library") version libs.versions.agp.get() apply false
    id("de.infix.testBalloon") version testballoonVer apply false
}


//work around nexus publish bug
val propigatorVersion: String by extra
group = "at.asitplus.propigator"
version = propigatorVersion
//end work around nexus publish bug


val dokkaDir = rootProject.layout.buildDirectory.dir("docs")
dokka {
    dokkaPublications.html{
        outputDirectory.set(dokkaDir)
    }
}
subprojects {
    rootProject.dependencies.add("dokka", this)
}

allprojects {
    apply(plugin = "org.jetbrains.dokka")
    group = rootProject.group
}

val spdxHeaderLines = listOf(
    "// SPDX-FileCopyrightText: Copyright (c) A-SIT Plus GmbH",
    "// SPDX-License-Identifier: Apache-2.0",
)
val spdxHeader = spdxHeaderLines.joinToString(separator = "\n", postfix = "\n\n")
val spdxHeaderExtensions = listOf("kt", "java", "kts", "js", "ts", "swift", "c", "h")
val spdxHeaderSourcePatterns = spdxHeaderExtensions.flatMap { extension ->
    listOf(
        "**/src/main/**/*.$extension",
        "**/src/*Main/**/*.$extension",
    )
}
val spdxHeaderSources = fileTree(rootDir) {
    include(spdxHeaderSourcePatterns)
    exclude("docs/**", "repo/**", "**/build/**", ".gradle/**")
}

allprojects {
    repositories {
        mavenLocal ()
    }
}
spotless {
    format("mainSourceHeaders") {
        target(spdxHeaderSources)
        licenseHeader(
            spdxHeader,
            "^(package|@file:|import|plugins|pluginManagement|dependencyResolutionManagement|rootProject|include|buildscript)",
        )
    }
}

tasks.named("spotlessCheck") {
    dependsOn(subprojects.map { "${it.path}:cyclonedxPublishedBom" })
}
