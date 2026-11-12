package com.docia.service;

import com.docia.dto.DocumentCommandResponse.PageText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AddressExtractionServiceTest {

    private final AddressExtractionService service = new AddressExtractionService();

    @Test
    void extractsAddressFieldsAndPageNumber() {
        var result = service.extract(List.of(new PageText(2,
                "Rua das Flores, 123, Apto 4, bairro: Centro, cidade: Florianópolis, estado: SC, CEP: 88000-000")));

        assertEquals(1, result.size());
        var address = result.get(0);
        assertEquals("Rua das Flores", address.street());
        assertEquals("123", address.number());
        assertEquals("Apto 4", address.complement());
        assertEquals("Centro", address.neighborhood());
        assertEquals("Florianópolis", address.city());
        assertEquals("SC", address.state());
        assertEquals("88000-000", address.zipCode());
        assertEquals(2, address.pageNumber());
    }

    @Test
    void recognizesDifferentBrazilianStreetTypesAndOptionalFields() {
        var result = service.extract(List.of(new PageText(1,
                "Av. Brasil, 50, Rio de Janeiro - RJ, 20000-000\nTravessa do Sol")));

        assertEquals(2, result.size());
        assertEquals("Av. Brasil", result.get(0).street());
        assertEquals("50", result.get(0).number());
        assertEquals("Rio de Janeiro", result.get(0).city());
        assertEquals("RJ", result.get(0).state());
        assertEquals("20000-000", result.get(0).zipCode());
        assertEquals("Travessa do Sol", result.get(1).street());
        assertNull(result.get(1).number());
    }

    @Test
    void deduplicatesSameAddressOnOnePageIgnoringCaseAndSpacing() {
        var result = service.extract(List.of(new PageText(1,
                "Rua das Flores, 123, Centro\nrua das flores, 123, Centro")));

        assertEquals(1, result.size());
    }

    @Test
    void keepsSameAddressOnDifferentPages() {
        var result = service.extract(List.of(
                new PageText(1, "Rodovia BR-101, 100"),
                new PageText(2, "Rodovia BR-101, 100")));

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).pageNumber());
        assertEquals(2, result.get(1).pageNumber());
    }

    @Test
    void returnsEmptyListWhenNoAddressIsFound() {
        assertEquals(List.of(), service.extract(List.of(new PageText(1, "Sem endereço neste texto."))));
    }
}
