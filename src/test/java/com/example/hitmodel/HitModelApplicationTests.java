package com.example.hitmodel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "opc.heartbeat.enabled=false",
        "opc.realtime.enabled=false",
        "opc.write-server.enabled=false"
})
class HitModelApplicationTests {

    @Test
    void contextLoads() {
    }

}
