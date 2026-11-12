package com.docia.dto;

import java.util.List;

public record PhoneExtractionResponse(
        String filename,
        String command,
        String status,
        int pageCount,
        List<PhoneOccurrence> phones
) {
    public record PhoneOccurrence(String phone, int pageNumber) {
    }
}
