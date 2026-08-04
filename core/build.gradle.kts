import at.asitplus.gradle.*
plugins {
    id("at.asitplus.propigator.buildlogic")
}

propigatorConventions {
    android("at.asitplus.propigator.core")
    mavenPublish(
        name = "Propigator Core",
        description = "Format-neutral typed properties over untamed data"
    )
}

kotlin {
    propigatorTargets()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.core)
        }
    }
}
