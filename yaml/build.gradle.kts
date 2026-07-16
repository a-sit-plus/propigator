import at.asitplus.gradle.propigatorConventions
import at.asitplus.gradle.propigatorTargets

plugins {
    id("at.asitplus.propigator.buildlogic")
}

propigatorConventions {
    android("at.asitplus.propigator.yaml")
    mavenPublish(
        name = "Propigator for YAML",
        description = "Typed properties over untamed YAML data"
    )
}

kotlin {
    propigatorTargets(disableWasm = true)

    sourceSets {
        commonMain.dependencies {
            val previousSerializationVersion = libs.versions.serialization.previous.get()

            api(project(":common"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:$previousSerializationVersion") {
                version { strictly(previousSerializationVersion) }
            }
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:$previousSerializationVersion") {
                version { strictly(previousSerializationVersion) }
            }
            api("org.jetbrains.kotlinx:kotlinx-serialization-cbor:$previousSerializationVersion") {
                version { strictly(previousSerializationVersion) }
            }
            api(libs.yamlkt)
        }
        commonTest {
            kotlin.srcDir(project(":common").layout.projectDirectory.dir("src/commonTest/kotlin"))
            kotlin.exclude("**/MultiFormatTest.kt")
        }
    }
}
