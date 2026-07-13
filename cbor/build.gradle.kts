import at.asitplus.gradle.*
plugins {
    id("at.asitplus.propigator.buildlogic")
}

propigatorConventions {
    android("at.asitplus.propigator.cbor")
    mavenPublish(
        name = "Propigator for CBOR",
        description = "Typed properties over untamed CBOR data"
    )
}

kotlin {
    propigatorTargets()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(libs.kotlinx.serialization.cbor)
        }
        commonTest {
            kotlin.srcDir(project(":common").layout.projectDirectory.dir("src/commonTest/kotlin"))
        }
        commonTest.dependencies {
            implementation(libs.cosef)
        }
    }
}
