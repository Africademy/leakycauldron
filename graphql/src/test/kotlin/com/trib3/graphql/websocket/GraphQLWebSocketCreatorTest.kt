package com.trib3.graphql.websocket

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.GraphQLConfigTest
import com.trib3.graphql.resources.GraphQLResourceContext
import com.trib3.testing.LeakyMock
import graphql.GraphQL
import org.easymock.EasyMock
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.testng.annotations.Test

class GraphQLWebSocketCreatorTest {
    @Test
    fun testSocketCreation() {
        val graphQL = LeakyMock.mock<GraphQL>()
        val mapper = ObjectMapper()
        val creatorFactory = GraphQLWebSocketCreatorFactory(graphQL, mapper, GraphQLConfig(ConfigLoader()))
        val creator = creatorFactory.getCreator(GraphQLResourceContext(null))
        assertThat(creator).isInstanceOf(GraphQLWebSocketCreator::class)
        assertThat((creator as GraphQLWebSocketCreator).graphQL).isEqualTo(graphQL)
        assertThat(creator.objectMapper).isEqualTo(mapper)
        assertThat(creator.graphQLConfig.keepAliveIntervalSeconds).isEqualTo(GraphQLConfigTest.DEFAULT_KEEPALIVE)
        assertThat(creator.context).isInstanceOf(GraphQLResourceContext::class)
        assertThat((creator.context as GraphQLResourceContext).principal).isNull()

        val request = LeakyMock.mock<ServletUpgradeRequest>()
        val response = LeakyMock.mock<ServletUpgradeResponse>()
        EasyMock.expect(response.setAcceptedSubProtocol("graphql-ws")).once()
        EasyMock.replay(graphQL, request, response)
        val socket = creator.createWebSocket(request, response)
        EasyMock.verify(response)
        assertThat(socket).isInstanceOf(GraphQLWebSocketAdapter::class)
        assertThat((socket as GraphQLWebSocketAdapter).objectMapper).isEqualTo(mapper)
        assertThat(socket.channel).isNotNull()
        // mapper writes without pretty printing, writer writes with pretty printing
        assertThat(socket.objectMapper.writeValueAsString(mapOf("a" to "b"))).isEqualTo("""{"a":"b"}""")
        assertThat(socket.objectWriter.writeValueAsString(mapOf("a" to "b"))).isEqualTo(
            """{
            |  "a" : "b"
            |}""".trimMargin()
        )
    }
}
