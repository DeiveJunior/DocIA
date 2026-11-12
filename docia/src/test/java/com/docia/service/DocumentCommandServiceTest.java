package com.docia.service;

import com.docia.dto.DocumentCommandResponse;
import com.docia.dto.EmailExtractionResponse;
import com.docia.dto.NameExtractionResponse;
import com.docia.dto.PhoneExtractionResponse;
import com.docia.dto.DocumentCommandResponse.PageText;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentCommandServiceTest {

    private static final List<String> NAME_COMMANDS = List.of(
            "Nome",
            "Me traga o nome",
            "Me traga o nome deste documento"
    );

    @Test
    void returnsStructuredEmailResponseAndExtractsPdfOnce() {
        AtomicInteger extractionCount = new AtomicInteger();
        var document = new PdfExtractionService() {
            @Override
            public ExtractedDocument extract(org.springframework.web.multipart.MultipartFile file) {
                extractionCount.incrementAndGet();
                return new ExtractedDocument(1, List.of(new PageText(1, "a@example.com")));
            }
        };
        var service = service(document);

        Object response = service.process("sample.pdf", "find emails",
                new MockMultipartFile("document", "sample.pdf", "application/pdf", new byte[]{1}));

        var emailResponse = assertInstanceOf(EmailExtractionResponse.class, response);
        assertEquals(1, emailResponse.emails().size());
        assertEquals(1, extractionCount.get());
    }

    @Test
    void returnsAddressResponseWithPriorityOverOtherRequests() {
        var service = service(fixedExtraction(new PdfExtractionService.ExtractedDocument(1,
                List.of(new PageText(1, "Rua A, 1; CPF 111.444.777-35; (48) 99999-1234; a@example.com")))));

        Object response = service.process("sample.pdf", "endereços, cpf, telefones e e-mails",
                multipart());

        assertInstanceOf(com.docia.dto.AddressExtractionResponse.class, response);
    }

    @Test
    void routesCpfPhoneAndEmailCommandsToTheirResponses() {
        var service = service(fixedExtraction(new PdfExtractionService.ExtractedDocument(1,
                List.of(new PageText(1, "CPF 111.444.777-35, telefone (48) 99999-1234, a@example.com")))));

        assertInstanceOf(com.docia.dto.CpfCnpjExtractionResponse.class,
                service.process("sample.pdf", "cpf", multipart()));
        assertInstanceOf(PhoneExtractionResponse.class,
                service.process("sample.pdf", "telefones", multipart()));
        assertInstanceOf(EmailExtractionResponse.class,
                service.process("sample.pdf", "e-mails", multipart()));
    }

    @Test
    void routesNameCommandsWhenInterpreterRecognizesNameIntent() {
        var service = nameService(CommandInterpreterService.Intent.NAME);

        for (String command : NAME_COMMANDS) {
            var response = assertInstanceOf(NameExtractionResponse.class,
                    service.process("sample.pdf", command, multipart()));
            assertEquals("Ana Silva", response.names().get(0).name());
            assertEquals(1, response.names().get(0).pageNumber());
        }
    }

    @Test
    void fallsBackToNameIntentForNameCommandsWhenInterpreterReturnsUnknown() {
        var service = nameService(CommandInterpreterService.Intent.UNKNOWN);

        for (String command : NAME_COMMANDS) {
            var response = assertInstanceOf(NameExtractionResponse.class,
                    service.process("sample.pdf", command, multipart()));
            assertEquals("Ana Silva", response.names().get(0).name());
        }
    }

    @Test
    void doesNotFallbackToNameIntentForCompanyNameRequests() {
        var service = nameService(CommandInterpreterService.Intent.UNKNOWN);

        assertInstanceOf(DocumentCommandResponse.class,
                service.process("sample.pdf", "nome da empresa", multipart()));
        assertInstanceOf(DocumentCommandResponse.class,
                service.process("sample.pdf", "razão social", multipart()));
        assertInstanceOf(DocumentCommandResponse.class,
                service.process("sample.pdf", "nome fantasia", multipart()));
    }

    @Test
    void preservesFullTextResponseForOtherCommands() {
        var extracted = new PdfExtractionService.ExtractedDocument(1,
                List.of(new PageText(1, "document text")));
        var service = service(fixedExtraction(extracted), CommandInterpreterService.Intent.FULL_TEXT);

        Object response = service.process("sample.pdf", "read full text", multipart());

        var textResponse = assertInstanceOf(DocumentCommandResponse.class, response);
        assertEquals("document text", textResponse.pages().get(0).text());
    }

    private DocumentCommandService nameService(CommandInterpreterService.Intent intent) {
        return service(fixedExtraction(new PdfExtractionService.ExtractedDocument(1,
                List.of(new PageText(1, "Nome: Ana Silva")))), intent);
    }

    private DocumentCommandService service(PdfExtractionService pdfExtractionService) {
        return service(pdfExtractionService, CommandInterpreterService.Intent.UNKNOWN);
    }

    private DocumentCommandService service(
            PdfExtractionService pdfExtractionService,
            CommandInterpreterService.Intent intent) {
        CommandInterpreterService commandInterpreterService = mock(CommandInterpreterService.class);
        when(commandInterpreterService.interpret(anyString())).thenReturn(intent);

        return new DocumentCommandService(
                pdfExtractionService,
                new EmailExtractionService(),
                new PhoneExtractionService(),
                new CpfCnpjExtractionService(),
                new AddressExtractionService(),
                new NameExtractionService(),
                commandInterpreterService);
    }

    private PdfExtractionService fixedExtraction(PdfExtractionService.ExtractedDocument extracted) {
        return new PdfExtractionService() {
            @Override
            public ExtractedDocument extract(org.springframework.web.multipart.MultipartFile file) {
                return extracted;
            }
        };
    }

    private MockMultipartFile multipart() {
        return new MockMultipartFile("document", "sample.pdf", "application/pdf", new byte[]{1});
    }
}
