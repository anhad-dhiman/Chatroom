package com.anhad.chatroom.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private WebSocketStompClient stomp;

    @AfterEach
    void stop() {
        if (stomp != null) {
            stomp.stop();
        }
    }

    @Test
    void membersReceiveMessagesInRealTime() throws Exception {
        JsonNode alice = register("alice");
        JsonNode bob = register("bob");
        long dmId = post("/api/direct/" + bob.at("/user/id").asLong(), alice, null).get("id").asLong();

        BlockingQueue<JsonNode> bobEvents = new LinkedBlockingQueue<>();
        StompSession bobSession = connect(bob.get("token").asText());
        subscribe(bobSession, "/user/queue/events", bobEvents);

        // Send over REST: Bob should get it pushed over his socket.
        post("/api/conversations/" + dmId + "/messages", alice, Map.of("content", "hi bob"));
        JsonNode event = bobEvents.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type").asText()).isEqualTo(ChatEvent.MESSAGE_CREATED);
        assertThat(event.get("conversationId").asLong()).isEqualTo(dmId);
        assertThat(event.at("/payload/content").asText()).isEqualTo("hi bob");

        // Send over the socket itself: Bob gets his own message echoed back too.
        bobSession.send("/app/conversations/" + dmId + "/send", Map.of("content", "hey alice"));
        JsonNode echoed = bobEvents.poll(5, TimeUnit.SECONDS);
        assertThat(echoed).isNotNull();
        assertThat(echoed.at("/payload/content").asText()).isEqualTo("hey alice");
        assertThat(echoed.at("/payload/sender/username").asText()).isEqualTo(bob.at("/user/username").asText());
    }

    @Test
    void outsidersDoNotReceiveEvents() throws Exception {
        JsonNode alice = register("alice");
        JsonNode bob = register("bob");
        JsonNode eve = register("eve");
        long dmId = post("/api/direct/" + bob.at("/user/id").asLong(), alice, null).get("id").asLong();

        BlockingQueue<JsonNode> eveEvents = new LinkedBlockingQueue<>();
        subscribe(connect(eve.get("token").asText()), "/user/queue/events", eveEvents);

        post("/api/conversations/" + dmId + "/messages", alice, Map.of("content", "secret"));
        assertThat(eveEvents.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void connectWithoutValidTokenFails() {
        assertThatThrownBy(() -> connect("garbage")).isInstanceOf(Exception.class);
    }

    // ------------------------------------------------------------------ helpers

    private StompSession connect(String token) throws Exception {
        if (stomp == null) {
            stomp = new WebSocketStompClient(new StandardWebSocketClient());
            MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
            converter.setObjectMapper(new ObjectMapper().registerModule(new JavaTimeModule()));
            stomp.setMessageConverter(converter);
        }
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    private void subscribe(StompSession session, String destination, BlockingQueue<JsonNode> sink) throws Exception {
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add((JsonNode) payload);
            }
        });
        // SUBSCRIBE is fire-and-forget; give the broker a moment to register it.
        Thread.sleep(200);
    }

    private JsonNode register(String prefix) {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        return rest.postForObject("/api/auth/register",
                Map.of("username", username, "password", "password123"), JsonNode.class);
    }

    private JsonNode post(String url, JsonNode as, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(as.get("token").asText());
        headers.setAccept(List.of(org.springframework.http.MediaType.APPLICATION_JSON));
        return rest.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
