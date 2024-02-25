import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.0-SNAPSHOT"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    id("org.jetbrains.dokka") version "1.9.10"
    id("maven-publish")
}

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:1.9.10")
    }
}

group = "shark"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-groovy-templates")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.3")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("de.undercouch:bson4jackson:2.15.0")
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.session:spring-session-core")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation("net.dv8tion:JDA:5.0.0-beta.20")
    implementation("club.minnced:jda-ktx:0.11.0-beta.20")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.github.StarWorldTeam:Kodash:967068cc6b")
    implementation("com.microsoft.playwright:playwright:1.41.0")
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = java.sourceCompatibility.toString()
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

configure<PublishingExtension> {
    publications.create<MavenPublication>("maven") {
        from(components.getByName("kotlin"))
        artifact(tasks.named("bootJar"))
        artifact(tasks.kotlinSourcesJar)
    }
}

tasks.withType<ProcessResources> {
    val resourceTargets = listOf("META-INF/shark.yml")
    val replaceProperties = mapOf(
        Pair(
            "gradle",
            mapOf(
                Pair("gradle", gradle),
                Pair("project", project)
            )
        )
    )
    filesMatching(resourceTargets) {
        expand(replaceProperties)
    }
}

tasks.dokkaHtml {
    pluginConfiguration<org.jetbrains.dokka.base.DokkaBase, org.jetbrains.dokka.base.DokkaBaseConfiguration> {
        footerMessage = "Copyright © StarWorld Team"
    }
}

tasks.bootJar {
    archiveClassifier.set("boot")
}

tasks.jar {
    archiveClassifier.set("")
}
