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
            api(project(":common"))
            api(libs.yamlkt)
        }
        commonTest {
            kotlin.srcDir(project(":common").layout.projectDirectory.dir("src/commonTest/kotlin"))
        }
    }
}
