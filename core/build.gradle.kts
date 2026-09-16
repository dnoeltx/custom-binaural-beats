// The platform-neutral core (constitution VII). This module deliberately applies
// NO Android plugin and depends on NO Android artifact, so the rule is enforced by
// the build rather than by discipline. The check below fails the build if that ever
// stops being true.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// T008: fail the build if :core ever gains an Android dependency.
val verifyNoAndroidDependencies by tasks.registering {
    group = "verification"
    description = "Fails if :core depends on any Android artifact (constitution VII)."

    val configurationsToCheck = listOf("compileClasspath", "runtimeClasspath")
    val artifactIds = provider {
        configurationsToCheck.flatMap { name ->
            configurations.getByName(name).incoming.resolutionResult.allComponents
                .map { it.id.displayName }
        }
    }

    doLast {
        val offenders = artifactIds.get().filter {
            it.startsWith("com.android") || it.startsWith("androidx") || it.startsWith("com.google.android")
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Constitution VII violation: :core must contain no Android dependency, but found:\n" +
                    offenders.distinct().joinToString("\n") { "  $it" }
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyNoAndroidDependencies)
}
