pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // osmdroid utilise directement mavenCentral, pas de repo tiers nécessaire
        maven { url = uri("https://jitpack.io") } // requis pour MPAndroidChart
    }
}

rootProject.name = "RandoGPX"
include(":app")
