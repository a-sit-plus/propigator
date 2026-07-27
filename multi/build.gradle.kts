import at.asitplus.gradle.*

plugins {
    id("at.asitplus.propigator.buildlogic")
}

propigatorConventions {
    android("at.asitplus.propigator.multi")
    mavenPublish(
        name = "Propigator Multi",
        description = "Experimental multi-format backing"
    )
}

kotlin {
    propigatorTargets()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
    }
}
