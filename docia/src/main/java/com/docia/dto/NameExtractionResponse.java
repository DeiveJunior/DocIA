package com.docia.dto;

import java.util.List;

public record NameExtractionResponse(
        String filename,
        String command,
        String status,
        int pageCount,
        List<NameOccurrence> names
) {
    public record NameOccurrence(String name, int pageNumber) {
    }
}
