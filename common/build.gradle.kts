import at.asitplus.gradle.*
plugins {
    id("at.asitplus.propigator.buildlogic")
}

propigatorConventions {
    android("at.asitplus.propigator.common")
    mavenPublish(
        name = "Propigator Commons",
        description = "Typed properties over untamed data (common abstractions)"
    )
}

kotlin {
    propigatorTargets()

    sourceSets {
        commonMain.dependencies {
            api(serialization("core"))
        }
    }
}
