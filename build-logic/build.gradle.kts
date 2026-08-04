plugins {
    `kotlin-dsl`
}

group = "at.asitplus.propigator.buildlogic"

gradlePlugin {
    plugins {
        create("propigatorConventions") {
            id = "at.asitplus.propigator.buildlogic"
            implementationClass = "at.asitplus.gradle.PropigatorConventionsPlugin"
            displayName = "Propigator Build Logic Conventions"
            description = "Common build logic for Propigator"
        }
    }
}

dependencies {
    val kotlinVer = System.getenv("KOTLIN_VERSION_ENV")?.ifBlank { null } ?: libs.versions.kotlin.get()

    implementation("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:$kotlinVer")
    implementation(libs.agp)
    implementation(libs.asp)
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://raw.githubusercontent.com/a-sit-plus/gradle-conventions-plugin/mvn/repo")
        name = "aspConventions"
    }
    gradlePluginPortal()
    google()
    mavenCentral()
}
