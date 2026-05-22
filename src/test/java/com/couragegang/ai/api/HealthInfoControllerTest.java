package com.couragegang.ai.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthInfoControllerTest {

    @Test
    void rootMapsEndpoints() {
        var map = new HealthInfoController().root();
        assertThat(map.get("service")).isEqualTo("ai-runtime");
        assertThat(map).containsKey("chat");
    }
}
