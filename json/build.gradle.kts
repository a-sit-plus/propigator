import at.asitplus.gradle.*
plugins {
    id("at.asitplus.propigator.buildlogic")
}

propigatorConventions {
    android("at.asitplus.propigator.json")
    mavenPublish(
        name = "Propigator for JSON",
        description = "Typed properties over untamed JSON data"
    )
}

kotlin {
    propigatorTargets()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(serialization("json"))
        }
    }
}
