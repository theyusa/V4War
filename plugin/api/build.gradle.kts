plugins {
    id("com.android.library")
    id("kotlin-parcelize")
}

setupKotlinCommon()
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "tr.theyusa.v4war.plugin"
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
