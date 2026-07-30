pluginManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google/")
        maven(url = "https://maven.aliyun.com/repository/central/")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google/")
        maven(url = "https://maven.aliyun.com/repository/central/")
        mavenCentral()
        maven(url = "https://api.xposed.info/")
    }
}

rootProject.name = "XiaoAiOperitBridge"
include(":app")