plugins {
    java
}

group = "de.momokli"
version = "1.0.0"
description = "CityPost - One-way Item-Package-System (World 1/2 -> City)"

java {
    toolchain {
        // Paper 1.26.x setzt Java 25 voraus (paper-api ist mit class file v69 gebaut).
        // Der Quellcode bleibt Java-21-kompatibel (keine 22+-Sprachfeatures).
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApi = "io.papermc.paper:paper-api:26.2.build.119-stable"

dependencies {
    compileOnly(paperApi)
    // SnakeYAML nur für den Testlauf; zur Laufzeit stellt Paper es bereit
    compileOnly("org.yaml:snakeyaml:2.4")

    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")

    testImplementation(paperApi)
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("org.yaml:snakeyaml:2.4")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.16")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// Fat-Jar: HikariCP + MariaDB-Treiber einpacken, slf4j wird vom Server gestellt
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("citypost-${project.version}.jar")
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("module-info.class", "META-INF/versions/**/module-info.class")
        exclude("org/slf4j/**")
    }
}
