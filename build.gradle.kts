buildscript {
    val kotlinVersion = "2.1.20"
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath(kotlin("gradle-plugin", version = kotlinVersion))
    }
}
