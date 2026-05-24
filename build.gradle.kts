plugins {
    id("io.micronaut.application") version "4.5.4"
    id("com.gradleup.shadow") version "8.3.7"
    jacoco
}

extra["jacocoCoverageExcludes"] = listOf(
    "**/api/dto/**",
    "**/repo/**",
    "**/Application.class",
    "**/integration/**",
    "**/api/ChatController.class",
    "**/api/ConversationsController.class",
    "**/api/InternalOrchestratorController.class",
    "**/api/HealthInfoController.class",
    "**/security/**",
    "**/metrics/**",
    "**/config/AiProperties.class",
    "**/service/NotionToolArguments.class",
    "**/service/ChatService.class",
    "**/service/OrchestratorRouterService.class",
    "**/service/OrchestratorToolCatalog.class",
    "**/service/HitlPromptFormatter.class",
    "**/service/OrchestratorService.class",
)
apply(from = rootDir.resolve("gradle/jacoco-coverage.gradle.kts"))

version = "0.1.0-SNAPSHOT"
group = "com.couragegang.ai"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.aliyun.com/repository/public") }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

micronaut {
    version("4.7.6")
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        module("com.couragegang.ai")
    }
}

dependencies {
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")

    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.projectreactor:reactor-core")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.micronaut.validation:micronaut-validation")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.yaml:snakeyaml")
    runtimeOnly("ch.qos.logback:logback-classic")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClass.set("com.couragegang.ai.Application")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
