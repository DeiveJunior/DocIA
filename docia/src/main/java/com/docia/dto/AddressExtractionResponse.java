package com.docia.dto;

import java.util.List;

public record AddressExtractionResponse(
        String filename,
        String command,
        String status,
        int pageCount,
        List<AddressOccurrence> addresses
) {
    public record AddressOccurrence(
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String zipCode,
            int pageNumber
    ) {
    }
}
