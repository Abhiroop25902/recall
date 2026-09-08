package com.abhiroop.recall.dto;

import com.abhiroop.recall.utils.ErrorStrings;

public record SaveMemoryRequestDto(String appId, String text) {

    public SaveMemoryRequestDto {
        if (appId == null || appId.isBlank())
            throw new IllegalArgumentException(
                    ErrorStrings.APP_ID_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK.getErrorString()
            );

        if (text == null || text.isBlank())
            throw new IllegalArgumentException(
                    ErrorStrings.TEXT_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK.getErrorString()
            );
    }
}
