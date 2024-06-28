plugins {
    java
    id("architectury-plugin") version ("3.4-SNAPSHOT")
    id("dev.architectury.loom") version ("1.6-SNAPSHOT") apply false
    kotlin("jvm") version "1.8.22"
}

architectury {
    minecraft = rootProject.property("minecraft_version").toString()
}

subprojects {
    apply(plugin = "dev.architectury.loom")

    //val loom = project.extensions.getByName<LoomGradleExtensionAPI>("loom")

    dependencies {
        "minecraft"("com.mojang:minecraft:${project.property("minecraft_version")}")
        // The following line declares the mojmap mappings, you may use other mappings as well
        // mappings loom.officialMojangMappings()
        // The following line declares the yarn mappings you may select this one as well.
        "mappings"("net.fabricmc:yarn:${project.property("yarn_version")}:v2")
    }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")

//    base.archivesName.set(rootProject.property("archives_base_name").toString())

    version = rootProject.property("mod_version").toString()
    group = rootProject.property("maven_group").toString()

    repositories {
        // Add repositories to retrieve artifacts from in here.
        // You should only use this when depending on other mods because
        // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
        // See https://docs.gradle.org/current/userguide/declaring_repositories.html
        // for more information about repositories.
//      mavenCentral()
        maven(url = "https://maven.impactdev.net/repository/development/")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    kotlin.target.compilations.all {
        kotlinOptions.jvmTarget = "17"
    }

    java {
        withSourcesJar()
    }
}
