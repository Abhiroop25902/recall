package com.abhiroop.recall.dto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SaveMemoryRequestDtoTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankAppId(String appId) {
        assertThrows(IllegalArgumentException.class, () -> new SaveMemoryRequestDto(appId, "text"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankText(String text) {
        assertThrows(IllegalArgumentException.class, () -> new SaveMemoryRequestDto("app", text));
    }
}
