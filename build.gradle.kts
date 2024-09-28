plugins {
    antlr
    application
    distribution
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.5.3")
    implementation("junit:junit:4.11")
    implementation("com.google.code.gson:gson:2.10")
    implementation("org.fusesource.jansi:jansi:2.4.0")
}

sourceSets {
    main {
        java {
            srcDir("src/main")
        }
    }

    test {
        java {
            srcDir("test")
            srcDir("test-private")
        }

        resources {
            srcDir("test")
            srcDir("test-private")
        }
    }
}

application {
    mainClass = "pt.up.fe.comp2024.Launcher"

    // On macOS, this prevents Java icon from being added to Dock
    // and stealing focus when "gradle run" is called
    applicationDefaultJvmArgs = listOf("-Dapple.awt.UIElement=true")
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
}

tasks {
    installDist {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint:deprecation"))
    }

    withType<Tar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    withType<Zip> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    test {
        // On macOS, this prevents Java icon from being added to Dock
        // and stealing focus when "gradle test" is called
        jvmArgs = listOf("-Dapple.awt.UIElement=true")

        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
}
