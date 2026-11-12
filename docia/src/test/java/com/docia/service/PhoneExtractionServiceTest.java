package com.docia.service;

import com.docia.dto.DocumentCommandResponse.PageText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhoneExtractionServiceTest {

    private final PhoneExtractionService service = new PhoneExtractionService();

    @Test
    void extractsBrazilianMobileFixedAndUnformattedNumbersPreservingOriginalText() {
        var result = service.extract(List.of(new PageText(1,
                "Celular (48) 99999-1234; fixo (48) 3333-2020; compacto 48999991234.")));

        assertEquals(List.of("(48) 99999-1234", "(48) 3333-2020", "48999991234"),
                result.stream().map(phone -> phone.phone()).toList());
        assertEquals(List.of(1, 1, 1), result.stream().map(phone -> phone.pageNumber()).toList());
    }

    @Test
    void extractsMultiplePhonesOnOnePage() {
        var result = service.extract(List.of(new PageText(1,
                "Ligue (11) 99999-1111 ou (21) 3333-2222.")));

        assertEquals(2, result.size());
    }

    @Test
    void reportsPhonesOnTheirRespectivePages() {
        var result = service.extract(List.of(
                new PageText(1, "(48) 99999-1234"),
                new PageText(2, "(48) 3333-2020")));

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).pageNumber());
        assertEquals(2, result.get(1).pageNumber());
    }

    @Test
    void ignoresDuplicatesWithDifferentFormattingOnSamePage() {
        var result = service.extract(List.of(new PageText(1,
                "(48) 99999-1234, 48 999991234 e 48999991234.")));

        assertEquals(1, result.size());
        assertEquals("(48) 99999-1234", result.get(0).phone());
    }

    @Test
    void doesNotExtractFormattedCpfAsPhone() {
        var result = service.extract(List.of(new PageText(1, "CPF: 11.999.999-99")));

        assertEquals(List.of(), result);
    }

    @Test
    void doesNotExtractFormattedCnpjAsPhone() {
        var result = service.extract(List.of(new PageText(1, "CNPJ: 11.999.999/9999-99")));

        assertEquals(List.of(), result);
    }

    @Test
    void returnsEmptyListWhenNoPhoneIsFound() {
        assertEquals(List.of(), service.extract(List.of(new PageText(1, "Sem telefone neste documento."))));
    }
}
