package com.docia.dto;

import java.util.List;

public record CpfCnpjExtractionResponse(
        String filename,
        String command,
        String status,
        int pageCount,
        List<CpfCnpjOccurrence> documents
) {
    public record CpfCnpjOccurrence(String type, String value, int pageNumber) {
    }
}
