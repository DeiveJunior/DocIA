package com.docia.service;

import com.docia.dto.DocumentCommandResponse.PageText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpfCnpjExtractionServiceTest {

    private final CpfCnpjExtractionService service = new CpfCnpjExtractionService();

    @Test
    void extractsValidCpfFormattedAndUnformatted() {
        var result = service.extract(List.of(new PageText(1, "CPF: 111.444.777-35 e 11144477735")));

        assertEquals(1, result.size());
        assertEquals("CPF", result.get(0).type());
        assertEquals("111.444.777-35", result.get(0).value());
        assertEquals(1, result.get(0).pageNumber());
    }

    @Test
    void extractsValidCnpjFormattedAndUnformatted() {
        var result = service.extract(List.of(new PageText(2, "CNPJ: 11.222.333/0001-81 e 11222333000181")));

        assertEquals(1, result.size());
        assertEquals("CNPJ", result.get(0).type());
        assertEquals("11.222.333/0001-81", result.get(0).value());
        assertEquals(2, result.get(0).pageNumber());
    }

    @Test
    void rejectsInvalidCpfAndCnpj() {
        var cpfResult = service.extract(List.of(new PageText(1, "CPF: 111.444.777-36")));
        var cnpjResult = service.extract(List.of(new PageText(1, "CNPJ: 11.222.333/0001-82")));

        assertTrue(cpfResult.isEmpty());
        assertTrue(cnpjResult.isEmpty());
    }

    @Test
    void ignoresDuplicatesOnSamePageWithDifferentFormatting() {
        var result = service.extract(List.of(new PageText(1, "111.444.777-35 11144477735")));

        assertEquals(1, result.size());
        assertEquals("111.444.777-35", result.get(0).value());
    }

    @Test
    void detectsCpfAndCnpjAcrossDifferentPages() {
        var result = service.extract(List.of(
                new PageText(1, "CPF 111.444.777-35"),
                new PageText(2, "CNPJ 11.222.333/0001-81")));

        assertEquals(2, result.size());
    }

    @Test
    void ignoresPhoneLikeValue() {
        var result = service.extract(List.of(new PageText(1, "Telefone: (48) 99999-1234")));

        assertTrue(result.isEmpty());
    }
}
