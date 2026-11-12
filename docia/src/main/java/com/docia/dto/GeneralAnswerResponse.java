package com.docia.dto;

import java.util.List;

public record GeneralAnswerResponse(
        String filename,
        String command,
        String status,
        int pageCount,
        String responseType,
        String answerStatus,
        String answer,
        List<PageSource> sources,
        Object deterministicResults
) {
    public record PageSource(int pageNumber) {
    }
}
