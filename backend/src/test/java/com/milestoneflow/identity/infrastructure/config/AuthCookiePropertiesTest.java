package com.milestoneflow.identity.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthCookieProperties")
class AuthCookiePropertiesTest {

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("null access name defaults to MF_ACCESS")
        void accessNameDefaults() {
            var props = new AuthCookieProperties(null, "MF_REFRESH", "XSRF-TOKEN",
                    "/api/v1", "/api/v1/auth/refresh", "Lax", "Strict", false);
            assertThat(props.accessName()).isEqualTo("MF_ACCESS");
        }

        @Test
        @DisplayName("null refresh name defaults to MF_REFRESH")
        void refreshNameDefaults() {
            var props = new AuthCookieProperties("MF_ACCESS", null, "XSRF-TOKEN",
                    "/api/v1", "/api/v1/auth/refresh", "Lax", "Strict", false);
            assertThat(props.refreshName()).isEqualTo("MF_REFRESH");
        }

        @Test
        @DisplayName("null access path defaults to /api/v1")
        void accessPathDefaults() {
            var props = new AuthCookieProperties("MF_ACCESS", "MF_REFRESH", "XSRF-TOKEN",
                    null, "/api/v1/auth/refresh", "Lax", "Strict", false);
            assertThat(props.accessPath()).isEqualTo("/api/v1");
        }

        @Test
        @DisplayName("null refresh path defaults to /api/v1/auth/refresh")
        void refreshPathDefaults() {
            var props = new AuthCookieProperties("MF_ACCESS", "MF_REFRESH", "XSRF-TOKEN",
                    "/api/v1", null, "Lax", "Strict", false);
            assertThat(props.refreshPath()).isEqualTo("/api/v1/auth/refresh");
        }

        @Test
        @DisplayName("blank values fall back to defaults")
        void blankValuesFallBack() {
            var props = new AuthCookieProperties("  ", "  ", "  ",
                    " ", " ", "  ", " ", false);
            assertThat(props.accessName()).isEqualTo("MF_ACCESS");
            assertThat(props.refreshName()).isEqualTo("MF_REFRESH");
            assertThat(props.xsrfName()).isEqualTo("XSRF-TOKEN");
            assertThat(props.accessPath()).isEqualTo("/api/v1");
            assertThat(props.refreshPath()).isEqualTo("/api/v1/auth/refresh");
            assertThat(props.sameSiteAccess()).isEqualTo("Lax");
            assertThat(props.sameSiteRefresh()).isEqualTo("Strict");
        }
    }
}
