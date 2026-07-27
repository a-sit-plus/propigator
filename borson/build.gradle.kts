import at.asitplus.gradle.*
import at.asitplus.gradle.modulator.carrier

plugins {
    id("at.asitplus.propigator.buildlogic")
    alias(libs.plugins.modulator)
}

propigatorConventions {
    android("at.asitplus.propigator.borson")
    mavenPublish(
        name = "Propigator Borson",
        description = "JSON and CBOR multi-format backing"
    )
}

kotlin {
    propigatorTargets()

    sourceSets {
        commonMain.dependencies {
            api(project(":multi"))
            carrier(project(":json"))
            carrier(project(":cbor"))
        }
        commonTest.dependencies {
            implementation(libs.awesn1.kxs)
        }
    }
}
