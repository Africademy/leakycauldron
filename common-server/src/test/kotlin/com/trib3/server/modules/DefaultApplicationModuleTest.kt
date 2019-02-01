package com.trib3.server.modules

import assertk.all

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.logging.RequestIdFilter
import io.dropwizard.Configuration
import io.dropwizard.configuration.ConfigurationFactoryFactory
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject
import javax.inject.Named

@Guice(modules = [DefaultApplicationModule::class])
class DefaultApplicationModuleTest
@Inject constructor(
    val configurationFactoryFactory: ConfigurationFactoryFactory<Configuration>,
    val servletFilterConfigs: Set<@JvmSuppressWildcards ServletFilterConfig>,
    val objectMapper: ObjectMapper,
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val resources: Set<@JvmSuppressWildcards Any>
) {
    @Test
    fun testBindings() {
        assertThat(resources).isEmpty()
        assertThat(configurationFactoryFactory).isInstanceOf(HoconConfigurationFactoryFactory::class)
        assertThat(servletFilterConfigs.map { it.filterClass }).all {
            contains(RequestIdFilter::class.java)
            contains(CrossOriginFilter::class.java)
        }
        assertThat(servletFilterConfigs.map { it.name }).all {
            contains(RequestIdFilter::class.simpleName)
            contains(CrossOriginFilter::class.simpleName)
        }
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse()
    }
}