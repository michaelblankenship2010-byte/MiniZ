plugins {
    java
}

group = "dev.miniz"
version = "1.0.0"
val pluginAuthor = "Redstoneskew"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Newest Paper 26.3 API build.
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    val props = mapOf("version" to project.version, "author" to pluginAuthor)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("MiniZ-${project.version}.jar")
    manifest {
        attributes(
            "Implementation-Title" to "MiniZ",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to pluginAuthor,
            "Built-By" to pluginAuthor,
        )
    }
    // Shown at the end of every build.
    doLast {
        println("")
        println("  MiniZ ${project.version} by $pluginAuthor")
        println("  -> build/libs/MiniZ-${project.version}.jar")
        println("")
    }
}
