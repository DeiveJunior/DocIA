package com.docia.dto;

import java.util.List;

public record EmailExtractionResponse(
        String filename,
        String command,
        String status,
        int pageCount,
        List<EmailOccurrence> emails
) {
    public record EmailOccurrence(String email, int pageNumber) {
    }
}
