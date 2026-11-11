package com.docia.dto;

import java.util.List;

public record DocumentCommandResponse(
        String filename,
        String command,
        String status,
        int pageCount,
        List<PageText> pages
) {
    public record PageText(int pageNumber, String text) {
    }
}
