pluginManagement {
    repositories {
        google()
        // Google-hosted Maven Central mirror first: avoids 429s from repo.maven.apache.org.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google-hosted Maven Central mirror first: avoids 429s from repo.maven.apache.org.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "Budilnik"
include(":app")
