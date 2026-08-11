package com.example.hitmodel.service;

import com.example.hitmodel.config.OpcConfig;
import com.example.hitmodel.opc.da.OPCDAClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpcWriteSocketServerServiceTests {

    @Test
    void handleLineWritesOpcAndReturnsOk() {
        OPCDAClient opcClient = mock(OPCDAClient.class);
        when(opcClient.write("tag1", 12.5f)).thenReturn(true);
        OpcWriteSocketServerService service = new OpcWriteSocketServerService(new OpcConfig(), opcClient);

        assertThat(service.handleLine("tag1,12.5,float")).isEqualTo("OK");
        verify(opcClient).write("tag1", 12.5f);
    }

    @Test
    void handleLineReturnsBadFormat() {
        OpcWriteSocketServerService service =
                new OpcWriteSocketServerService(new OpcConfig(), mock(OPCDAClient.class));

        assertThat(service.handleLine("tag-only")).isEqualTo("FAIL:BAD_FORMAT");
    }
}
