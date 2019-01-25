package com.trib3.server.config

import ch.qos.logback.classic.Level
import com.authzee.kotlinguice4.getInstance
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.trib3.config.ConfigLoader
import com.trib3.config.KMSStringSelectReader
import com.trib3.config.extract
import com.typesafe.config.Config
import io.dropwizard.logging.BootstrapLogging

/**
 * Application config object that exposes basic things about the main service and
 * provides a way to get a guice injector
 */
class TribeApplicationConfig {
    val env: String
    val appName: String
    val appModules: List<String>
    val corsDomain: String
    val appPort: Int
    val adminPort: Int

    init {
        val config = ConfigLoader.load()
        env = config.extract("env")
        appName = config.extract("application.name")
        appModules = config.extract("application.modules")
        corsDomain = config.extract("application.domain")
        appPort = config.extract<List<Config>>("server.applicationConnectors").first().getInt("port")
        adminPort = config.extract<List<Config>>("server.adminConnectors").first().getInt("port")
    }

    fun getInjector(builtinModules: List<AbstractModule>): Injector {
        // since we instantiate things before the Application's constructor gets called, bootstrap the
        // logging so that we don't log things we don't want to during instantiation
        BootstrapLogging.bootstrap(Level.WARN)
        System.setProperty("org.jooq.no-logo", "true")
        val appModules = appModules.map {
            Class.forName(it).getDeclaredConstructor().newInstance() as AbstractModule
        }
        val injector = Guice.createInjector(listOf(builtinModules, appModules).flatten())
        injector.getInstance<KMSStringSelectReader>() // force load of KMS
        return injector
    }
}
