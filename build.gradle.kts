plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    `java-library`
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version "1.7.20"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

group = "dev.vstrs"
version = "0.1.0-SNAPSHOT"
val githubOrg = "evestera"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.slf4j:slf4j-api:1.7.36")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")

    testImplementation("org.assertj:assertj-core:3.23.1")
}

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:1.7.20")
    }
}

val isRelease = System.getenv("MAVEN_RELEASE") == "true"
if (isRelease) {
    project.version = project.version.toString().replace("-SNAPSHOT", "")
    println("Building release version ${project.version}")
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.named<Jar>("javadocJar") {
    from(tasks.named("dokkaJavadoc"))
}

tasks.withType(org.jetbrains.dokka.gradle.DokkaTask::class).configureEach {
    dokkaSourceSets {
        named("main") {
            includes.from("module-docs.md")
        }
    }

    pluginConfiguration<org.jetbrains.dokka.base.DokkaBase, org.jetbrains.dokka.base.DokkaBaseConfiguration> {
        footerMessage = "Copyright © 2022 Erik Vesteraas"
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("${project.group}:${project.name}")
                description.set("Simple dependency resolution and injection for Kotlin classes")
                url.set("https://github.com/$githubOrg/${project.name}")
                scm {
                    url.set("scm:git:git@github.com:$githubOrg/${project.name}.git")
                    connection.set("https://github.com/$githubOrg/${project.name}")
                    developerConnection.set("scm:git:git://github.com/$githubOrg/${project.name}.git")
                }
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("evestera")
                        name.set("Erik Vesteraas")
                        email.set("erik@vestera.as")
                    }
                }
            }
        }
    }
}

signing {
    setRequired({ isRelease })
    sign(publishing.publications["maven"])
}
