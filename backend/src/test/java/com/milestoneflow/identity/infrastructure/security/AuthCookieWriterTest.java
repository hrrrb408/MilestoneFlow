package com.milestoneflow.identity.infrastructure.security;

import com.milestoneflow.identity.infrastructure.config.AuthCookieProperties;
import com.milestoneflow.identity.infrastructure.config.AuthTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthCookieWriter")
class AuthCookieWriterTest {

    private AuthCookieWriter cookieWriter;

    @BeforeEach
    void setUp() {
        var cookieProps = new AuthCookieProperties(
                "MF_ACCESS", "MF_REFRESH", "XSRF-TOKEN",
                "/api/v1", "/api/v1/auth/refresh", "Lax", "Strict", false);
        var tokenProps = new AuthTokenProperties(Duration.ofMinutes(15), Duration.ofDays(30));
        cookieWriter = new AuthCookieWriter(cookieProps, tokenProps);
    }

    @Nested
    @DisplayName("MF_ACCESS cookie")
    class AccessCookie {

        @Test
        @DisplayName("has correct name")
        void name() {
            var cookie = cookieWriter.buildAccessCookie("test-token");
            assertThat(cookie.getName()).isEqualTo("MF_ACCESS");
        }

        @Test
        @DisplayName("is HttpOnly")
        void httpOnly() {
            var cookie = cookieWriter.buildAccessCookie("test-token");
            assertThat(cookie.isHttpOnly()).isTrue();
        }

        @Test
        @DisplayName("has SameSite=Lax")
        void sameSite() {
            var cookie = cookieWriter.buildAccessCookie("test-token");
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
        }

        @Test
        @DisplayName("has correct path")
        void path() {
            var cookie = cookieWriter.buildAccessCookie("test-token");
            assertThat(cookie.getPath()).isEqualTo("/api/v1");
        }

        @Test
        @DisplayName("has correct Max-Age")
        void maxAge() {
            var cookie = cookieWriter.buildAccessCookie("test-token");
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(15));
        }

        @Test
        @DisplayName("has correct value")
        void value() {
            var cookie = cookieWriter.buildAccessCookie("my-raw-token");
            assertThat(cookie.getValue()).isEqualTo("my-raw-token");
        }
    }

    @Nested
    @DisplayName("MF_REFRESH cookie")
    class RefreshCookie {

        @Test
        @DisplayName("has correct name")
        void name() {
            var cookie = cookieWriter.buildRefreshCookie("test-token");
            assertThat(cookie.getName()).isEqualTo("MF_REFRESH");
        }

        @Test
        @DisplayName("is HttpOnly")
        void httpOnly() {
            var cookie = cookieWriter.buildRefreshCookie("test-token");
            assertThat(cookie.isHttpOnly()).isTrue();
        }

        @Test
        @DisplayName("has SameSite=Strict")
        void sameSite() {
            var cookie = cookieWriter.buildRefreshCookie("test-token");
            assertThat(cookie.getSameSite()).isEqualTo("Strict");
        }

        @Test
        @DisplayName("has correct path matching future refresh endpoint")
        void path() {
            var cookie = cookieWriter.buildRefreshCookie("test-token");
            assertThat(cookie.getPath()).isEqualTo("/api/v1/auth/refresh");
        }

        @Test
        @DisplayName("has correct Max-Age")
        void maxAge() {
            var cookie = cookieWriter.buildRefreshCookie("test-token");
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(30));
        }
    }

    @Nested
    @DisplayName("XSRF-TOKEN cookie")
    class XsrfCookie {

        @Test
        @DisplayName("is NOT HttpOnly")
        void notHttpOnly() {
            var cookie = cookieWriter.buildXsrfCookie("xsrf-value");
            assertThat(cookie.isHttpOnly()).isFalse();
        }

        @Test
        @DisplayName("has SameSite=Lax")
        void sameSite() {
            var cookie = cookieWriter.buildXsrfCookie("xsrf-value");
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
        }

        @Test
        @DisplayName("has correct path")
        void path() {
            var cookie = cookieWriter.buildXsrfCookie("xsrf-value");
            assertThat(cookie.getPath()).isEqualTo("/api/v1");
        }
    }

    @Nested
    @DisplayName("Secure attribute")
    class SecureAttribute {

        @Test
        @DisplayName("secure=false for local/test")
        void localNotSecure() {
            var props = new AuthCookieProperties(
                    "MF_ACCESS", "MF_REFRESH", "XSRF-TOKEN",
                    "/api/v1", "/api/v1/auth/refresh", "Lax", "Strict", false);
            var writer = new AuthCookieWriter(props,
                    new AuthTokenProperties(Duration.ofMinutes(15), Duration.ofDays(30)));

            assertThat(writer.buildAccessCookie("t").isSecure()).isFalse();
            assertThat(writer.buildRefreshCookie("t").isSecure()).isFalse();
        }

        @Test
        @DisplayName("secure=true for production")
        void prodSecure() {
            var props = new AuthCookieProperties(
                    "MF_ACCESS", "MF_REFRESH", "XSRF-TOKEN",
                    "/api/v1", "/api/v1/auth/refresh", "Lax", "Strict", true);
            var writer = new AuthCookieWriter(props,
                    new AuthTokenProperties(Duration.ofMinutes(15), Duration.ofDays(30)));

            assertThat(writer.buildAccessCookie("t").isSecure()).isTrue();
            assertThat(writer.buildRefreshCookie("t").isSecure()).isTrue();
            assertThat(writer.buildXsrfCookie("t").isSecure()).isTrue();
        }
    }

    @Nested
    @DisplayName("Clear cookies")
    class ClearCookies {

        @Test
        @DisplayName("clear access has Max-Age=0")
        void clearAccessMaxAge() {
            var cookie = cookieWriter.buildClearAccessCookie();
            assertThat(cookie.getMaxAge()).isZero();
        }

        @Test
        @DisplayName("clear refresh has Max-Age=0")
        void clearRefreshMaxAge() {
            var cookie = cookieWriter.buildClearRefreshCookie();
            assertThat(cookie.getMaxAge()).isZero();
        }
    }

    @Test
    @DisplayName("toString does not contain cookie values")
    void toStringSecurity() {
        var str = cookieWriter.toString();
        assertThat(str).doesNotContain("test-token");
    }
}
