import org.gradle.api.tasks.wrapper.Wrapper

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "8.9"
    distributionType = Wrapper.DistributionType.BIN
}
