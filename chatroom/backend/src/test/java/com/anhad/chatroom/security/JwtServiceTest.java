package com.anhad.chatroom.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService service = new JwtService(
            new JwtProperties("unit-test-secret-unit-test-secret-1234", Duration.ofHours(1)));

    @Test
    void roundTrip() {
        String token = service.issue(42L, "alice");
        assertThat(service.parse(token)).contains(new AuthUser(42L, "alice"));
    }

    @Test
    void rejectsTamperedExpiredAndForeignTokens() {
        String token = service.issue(42L, "alice");
        assertThat(service.parse(token.substring(0, token.length() - 2) + "xx")).isEmpty();
        assertThat(service.parse("")).isEmpty();
        assertThat(service.parse(null)).isEmpty();

        var expired = new JwtService(new JwtProperties("unit-test-secret-unit-test-secret-1234", Duration.ofSeconds(-1)));
        assertThat(service.parse(expired.issue(1L, "bob"))).isEmpty();

        var other = new JwtService(new JwtProperties("a-completely-different-secret-value!!", Duration.ofHours(1)));
        assertThat(service.parse(other.issue(1L, "bob"))).isEmpty();
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", Duration.ofHours(1))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stripsBearerPrefix() {
        assertThat(JwtService.stripBearer("Bearer abc")).isEqualTo("abc");
        assertThat(JwtService.stripBearer("bearer abc")).isEqualTo("abc");
        assertThat(JwtService.stripBearer("abc")).isEqualTo("abc");
        assertThat(JwtService.stripBearer(null)).isNull();
    }

    @Test
    void onlyPerUserQueuesAndPresenceAreSubscribable() {
        assertThat(WebSocketAuthInterceptor.isAllowedSubscription("/user/queue/events")).isTrue();
        assertThat(WebSocketAuthInterceptor.isAllowedSubscription("/topic/presence")).isTrue();
        assertThat(WebSocketAuthInterceptor.isAllowedSubscription("/queue/events-user123")).isFalse();
        assertThat(WebSocketAuthInterceptor.isAllowedSubscription("/topic/anything-else")).isFalse();
        assertThat(WebSocketAuthInterceptor.isAllowedSubscription(null)).isFalse();
    }
}
