package com.abhiroop.recall.utils;

import lombok.Getter;

@Getter
public enum ErrorStrings {
    TEXT_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK("text must be present and must not be blank"),
    APP_ID_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK("appId must be present and must not be blank"),
    ID_MUST_BE_PRESENT_AND_MUST_NOT_BE_BLANK("id must be present and must not be blank"),
    UNABLE_TO_PROCESS_MEMORY_AT_THIS_TIME_PLEASE_TRY_AGAIN_LATER("Unable to process memory at this time. Please try again later");

    private final String errorString;

    ErrorStrings(String errorString) {
        this.errorString = errorString;
    }
}
