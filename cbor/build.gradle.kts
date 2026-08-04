import at.asitplus.gradle.*

plugins {
    id("at.asitplus.propigator.buildlogic")
}

propigatorConventions {
    android("at.asitplus.propigator.cbor")
    mavenPublish(
        name = "Propigator CBOR",
        description = "CBOR-backed typed properties over untamed data"
    )
}

kotlin {
    propigatorTargets()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.kotlinx.serialization.cbor)
        }
    }
}
