package com.example.hitmodel.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpcWriteCommandTests {

    @Test
    void parseFloatCommand() {
        OpcWriteCommand command = OpcWriteCommand.parse("工大OPC.AO.HIT_11115_SP,88.5,float");

        assertThat(command.getTag()).isEqualTo("工大OPC.AO.HIT_11115_SP");
        assertThat(command.getValue()).isInstanceOf(Float.class).isEqualTo(88.5f);
    }

    @Test
    void parseDefaultDoubleCommand() {
        OpcWriteCommand command = OpcWriteCommand.parse("tag1,12.25");

        assertThat(command.getValue()).isInstanceOf(Double.class).isEqualTo(12.25d);
    }

    @Test
    void parseIntAndBoolCommands() {
        assertThat(OpcWriteCommand.parse("tag1,12,int").getValue()).isEqualTo(12);
        assertThat(OpcWriteCommand.parse("tag2,on,bool").getValue()).isEqualTo(true);
    }

    @Test
    void rejectBadFormat() {
        assertThatThrownBy(() -> OpcWriteCommand.parse("tag-only"))
                .isInstanceOf(OpcWriteCommand.BadFormatException.class);
    }
}
