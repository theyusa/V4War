plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "tr.theyusa.v4war.plugin.hysteria2"
    }
    namespace = "tr.theyusa.v4war.plugin.hysteria2"
}

setupPlugin("hysteria2")
