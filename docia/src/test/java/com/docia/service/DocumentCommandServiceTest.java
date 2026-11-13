package com.docia.service;

import com.docia.dto.AddressExtractionResponse;
import com.docia.dto.CpfCnpjExtractionResponse;
import com.docia.dto.DocumentCommandResponse;
import com.docia.dto.DocumentCommandResponse.PageText;
import com.docia.dto.EmailExtractionResponse;
import com.docia.dto.GeneralAnswerResponse;
import com.docia.dto.NameExtractionResponse;
import com.docia.dto.PhoneExtractionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentCommandServiceTest {

    @Test
    void returnsStructuredEmailResponseAndExtractsPdfOnce() {
        AtomicInteger extractionCount = new AtomicInteger();
        PdfExtractionService pdf = new PdfExtractionService() {
            @Override
            public ExtractedDocument extract(org.springframework.web.multipart.MultipartFile file) {
                extractionCount.incrementAndGet();
                return extracted("a@example.com");
            }
        };
        Fixture fixture = fixture(pdf, CommandInterpreterService.Intent.EMAIL);
        when(fixture.email().extract(anyList())).thenReturn(List.of(
                new EmailExtractionService.EmailOccurrence("a@example.com", 1)));

        Object response = fixture.service().process("sample.pdf", "find emails", multipart());

        EmailExtractionResponse emailResponse = assertInstanceOf(EmailExtractionResponse.class, response);
        assertEquals(1, emailResponse.emails().size());
        assertEquals(1, extractionCount.get());
        verify(fixture.general(), never()).answer(anyString(), anyList(), any());
    }

    @Test
    void preservesExistingAddressAndIdentifierPhoneEmailAndFullTextRoutes() {
        Fixture address = fixture(
                fixedExtraction(extracted("Rua A, 1; CPF 111.444.777-35; (48) 99999-1234; a@example.com")),
                CommandInterpreterService.Intent.ADDRESS);
        when(address.address().extract(anyList())).thenReturn(List.of(
                new AddressExtractionResponse.AddressOccurrence(
                        "Rua A", "1", null, "Centro", "Florianópolis", "SC", null, 1)));
        assertInstanceOf(AddressExtractionResponse.class,
                address.service().process("sample.pdf", "endereços, cpf, telefones e e-mails", multipart()));
        verify(address.general(), never()).answer(anyString(), anyList(), any());

        Fixture cpf = fixture(
                fixedExtraction(extracted("CPF 111.444.777-35")),
                CommandInterpreterService.Intent.CPF_CNPJ);
        when(cpf.cpf().extract(anyList())).thenReturn(List.of(
                new CpfCnpjExtractionService.CpfCnpjOccurrence("CPF", "111.444.777-35", 1)));
        assertInstanceOf(CpfCnpjExtractionResponse.class,
                cpf.service().process("sample.pdf", "cpf", multipart()));
        verify(cpf.general(), never()).answer(anyString(), anyList(), any());

        Fixture phone = fixture(
                fixedExtraction(extracted("Telefone (48) 99999-1234")),
                CommandInterpreterService.Intent.PHONE);
        when(phone.phone().extract(anyList())).thenReturn(List.of(
                new PhoneExtractionService.PhoneOccurrence("(48) 99999-1234", 1)));
        assertInstanceOf(PhoneExtractionResponse.class,
                phone.service().process("sample.pdf", "telefones", multipart()));
        verify(phone.general(), never()).answer(anyString(), anyList(), any());

        Fixture email = fixture(
                fixedExtraction(extracted("a@example.com")),
                CommandInterpreterService.Intent.EMAIL);
        when(email.email().extract(anyList())).thenReturn(List.of(
                new EmailExtractionService.EmailOccurrence("a@example.com", 1)));
        assertInstanceOf(EmailExtractionResponse.class,
                email.service().process("sample.pdf", "e-mails", multipart()));
        verify(email.general(), never()).answer(anyString(), anyList(), any());

        Fixture fullText = fixture(
                fixedExtraction(extracted("document text")),
                CommandInterpreterService.Intent.FULL_TEXT);
        DocumentCommandResponse response = assertInstanceOf(DocumentCommandResponse.class,
                fullText.service().process("sample.pdf", "read full text", multipart()));
        assertEquals("document text", response.pages().get(0).text());
        verify(fullText.general(), never()).answer(anyString(), anyList(), any());
    }

    @Test
    void complementsPartialAddressAndSendsOriginalCommandTextAndJavaResultsToGeneralService() {
        String command = "Qual é meu endereço completo?";
        String pageText = "Moro na rua Anélio, bairro Potecas, na cidade de São José.";
        Fixture fixture = fixture(
                fixedExtraction(extracted(pageText)),
                CommandInterpreterService.Intent.ADDRESS);
        when(fixture.address().extract(anyList())).thenReturn(List.of(
                new AddressExtractionResponse.AddressOccurrence(
                        "Rua Anélio", null, null, null, null, null, null, 1)));
        when(fixture.general().answer(eq(command), anyList(), any()))
                .thenReturn(new GeneralDocumentAnswerService.AnswerResult(
                        "ANSWERED", "Rua Anélio, bairro Potecas, São José.", List.of(1)));

        GeneralAnswerResponse response = assertInstanceOf(GeneralAnswerResponse.class,
                fixture.service().process("sample.pdf", command, multipart()));

        assertEquals("ANSWERED", response.answerStatus());
        assertEquals("Rua Anélio, bairro Potecas, São José.", response.answer());
        assertInstanceOf(AddressExtractionResponse.class, response.deterministicResults());
        verify(fixture.general()).answer(
                eq(command),
                argThat(pages -> pages.size() == 1
                        && pages.get(0).pageNumber() == 1
                        && pages.get(0).text().equals(pageText)),
                argThat(results -> results instanceof AddressExtractionResponse address
                        && address.addresses().size() == 1
                        && "Rua Anélio".equals(address.addresses().get(0).street())));
    }

    @Test
    void selectsPersonalEmailFromTwoJavaResults() {
        String command = "Qual é meu e-mail pessoal?";
        Fixture fixture = fixture(
                fixedExtraction(extracted("Pessoal: ana@gmail.com. Corporativo: ana@empresa.com.")),
                CommandInterpreterService.Intent.EMAIL);
        when(fixture.email().extract(anyList())).thenReturn(List.of(
                new EmailExtractionService.EmailOccurrence("ana@gmail.com", 1),
                new EmailExtractionService.EmailOccurrence("ana@empresa.com", 1)));
        when(fixture.general().answer(eq(command), anyList(), any()))
                .thenReturn(new GeneralDocumentAnswerService.AnswerResult(
                        "ANSWERED", "ana@gmail.com", List.of(1)));

        GeneralAnswerResponse response = assertInstanceOf(GeneralAnswerResponse.class,
                fixture.service().process("sample.pdf", command, multipart()));

        assertEquals("ana@gmail.com", response.answer());
        EmailExtractionResponse javaResults = assertInstanceOf(
                EmailExtractionResponse.class, response.deterministicResults());
        assertEquals(2, javaResults.emails().size());
    }

    @Test
    void returnsAllJavaNamesWithoutUnnecessaryAiCall() {
        Fixture fixture = fixture(
                fixedExtraction(extracted("Nome: Ana Silva. Nome: João Souza.")),
                CommandInterpreterService.Intent.NAME);
        when(fixture.names().extract(anyList())).thenReturn(List.of(
                new NameExtractionService.NameOccurrence("Ana Silva", 1),
                new NameExtractionService.NameOccurrence("João Souza", 1)));

        NameExtractionResponse response = assertInstanceOf(NameExtractionResponse.class,
                fixture.service().process(
                        "sample.pdf", "Quais são os nomes das pessoas mencionadas?", multipart()));

        assertEquals(2, response.names().size());
        verify(fixture.general(), never()).answer(anyString(), anyList(), any());
    }

    @Test
    void usesGeneralAiWhenNameAppearsInAnInformalSentence() {
        String command = "Qual é o meu nome?";
        Fixture fixture = fixture(
                fixedExtraction(extracted("Meu nome é Juliano Alcione de Souza.")),
                CommandInterpreterService.Intent.NAME);
        when(fixture.names().extract(anyList())).thenReturn(List.of());
        when(fixture.general().answer(eq(command), anyList(), any()))
                .thenReturn(new GeneralDocumentAnswerService.AnswerResult(
                        "ANSWERED", "Juliano Alcione de Souza", List.of(1)));

        GeneralAnswerResponse response = assertInstanceOf(GeneralAnswerResponse.class,
                fixture.service().process("sample.pdf", command, multipart()));

        assertEquals("Juliano Alcione de Souza", response.answer());
        assertEquals(List.of(new GeneralAnswerResponse.PageSource(1)), response.sources());
    }

    @Test
    void routesGeneralSummaryToAiWithNoJavaResults() {
        String command = "Faça um resumo do documento.";
        Fixture fixture = fixture(
                fixedExtraction(extracted("Contrato de prestação de serviços entre as partes.")),
                CommandInterpreterService.Intent.UNKNOWN);
        when(fixture.general().answer(eq(command), anyList(), isNull()))
                .thenReturn(new GeneralDocumentAnswerService.AnswerResult(
                        "ANSWERED",
                        "O documento é um contrato de prestação de serviços.",
                        List.of(1)));

        GeneralAnswerResponse response = assertInstanceOf(GeneralAnswerResponse.class,
                fixture.service().process("sample.pdf", command, multipart()));

        assertEquals("GENERAL_ANSWER", response.responseType());
        assertEquals("ANSWERED", response.answerStatus());
        assertEquals(null, response.deterministicResults());
        verify(fixture.general()).answer(eq(command), anyList(), isNull());
    }

    @Test
    void distinguishesMissingInformationFromOllamaFailureAndPreservesJavaData() {
        Fixture missing = fixture(
                fixedExtraction(extracted("Documento sem informação de endereço.")),
                CommandInterpreterService.Intent.ADDRESS);
        when(missing.address().extract(anyList())).thenReturn(List.of());
        when(missing.general().answer(anyString(), anyList(), any()))
                .thenReturn(new GeneralDocumentAnswerService.AnswerResult(
                        "NOT_FOUND", "Informação não encontrada no documento.", List.of()));

        GeneralAnswerResponse notFound = assertInstanceOf(GeneralAnswerResponse.class,
                missing.service().process("sample.pdf", "Qual é o endereço?", multipart()));
        assertEquals("NOT_FOUND", notFound.answerStatus());
        assertInstanceOf(AddressExtractionResponse.class, notFound.deterministicResults());

        Fixture unavailable = fixture(
                fixedExtraction(extracted("Rua A, bairro Centro, cidade São José.")),
                CommandInterpreterService.Intent.ADDRESS);
        when(unavailable.address().extract(anyList())).thenReturn(List.of(
                new AddressExtractionResponse.AddressOccurrence(
                        "Rua A", null, null, null, null, null, null, 1)));
        when(unavailable.general().answer(anyString(), anyList(), any()))
                .thenReturn(GeneralDocumentAnswerService.AnswerResult.unavailable());

        GeneralAnswerResponse aiUnavailable = assertInstanceOf(GeneralAnswerResponse.class,
                unavailable.service().process(
                        "sample.pdf", "Qual é o endereço completo?", multipart()));
        assertEquals("AI_UNAVAILABLE", aiUnavailable.answerStatus());
        AddressExtractionResponse preserved = assertInstanceOf(
                AddressExtractionResponse.class, aiUnavailable.deterministicResults());
        assertEquals(1, preserved.addresses().size());
    }

    @Test
    void companyNameRequestsDoNotUsePersonNameExtractorWhenIntentIsName() {
        Fixture fixture = fixture(
                fixedExtraction(extracted("Nome: Acme Ltda. Razão social: Acme Serviços Ltda.")),
                CommandInterpreterService.Intent.NAME);

        for (String command : List.of(
                "Qual é o nome da empresa?",
                "Informe a razão social",
                "Qual é o nome fantasia?",
                "Qual é o nome da instituição?")) {
            assertInstanceOf(GeneralAnswerResponse.class,
                    fixture.service().process("sample.pdf", command, multipart()));
        }

        verify(fixture.names(), never()).extract(anyList());
    }

    @Test
    void stillExtractsPersonNamesWhenCommandMentionsCompany() {
        Fixture fixture = fixture(
                fixedExtraction(extracted("Ana Silva e João Souza trabalham na empresa.")),
                CommandInterpreterService.Intent.NAME);
        when(fixture.names().extract(anyList())).thenReturn(List.of(
                new NameExtractionService.NameOccurrence("Ana Silva", 1),
                new NameExtractionService.NameOccurrence("João Souza", 1)));

        NameExtractionResponse response = assertInstanceOf(NameExtractionResponse.class,
                fixture.service().process(
                        "sample.pdf",
                        "Quais são os nomes das pessoas que trabalham na empresa?",
                        multipart()));

        assertEquals(2, response.names().size());
        verify(fixture.names()).extract(anyList());
        verify(fixture.general(), never()).answer(anyString(), anyList(), any());
    }

    private Fixture fixture(
            PdfExtractionService pdfExtractionService,
            CommandInterpreterService.Intent intent) {
        CommandInterpreterService interpreter = mock(CommandInterpreterService.class);
        when(interpreter.interpret(anyString())).thenReturn(intent);

        EmailExtractionService email = mock(EmailExtractionService.class);
        PhoneExtractionService phone = mock(PhoneExtractionService.class);
        CpfCnpjExtractionService cpf = mock(CpfCnpjExtractionService.class);
        AddressExtractionService address = mock(AddressExtractionService.class);
        NameExtractionService names = mock(NameExtractionService.class);
        GeneralDocumentAnswerService general = mock(GeneralDocumentAnswerService.class);
        when(general.answer(anyString(), anyList(), nullable(Object.class)))
                .thenReturn(GeneralDocumentAnswerService.AnswerResult.unavailable());

        DocumentCommandService service = new DocumentCommandService(
                pdfExtractionService,
                email,
                phone,
                cpf,
                address,
                names,
                interpreter,
                general);
        return new Fixture(service, email, phone, cpf, address, names, general);
    }

    private PdfExtractionService fixedExtraction(PdfExtractionService.ExtractedDocument extracted) {
        return new PdfExtractionService() {
            @Override
            public ExtractedDocument extract(org.springframework.web.multipart.MultipartFile file) {
                return extracted;
            }
        };
    }

    private PdfExtractionService.ExtractedDocument extracted(String text) {
        return new PdfExtractionService.ExtractedDocument(1, List.of(new PageText(1, text)));
    }

    private MockMultipartFile multipart() {
        return new MockMultipartFile("document", "sample.pdf", "application/pdf", new byte[]{1});
    }

    private record Fixture(
            DocumentCommandService service,
            EmailExtractionService email,
            PhoneExtractionService phone,
            CpfCnpjExtractionService cpf,
            AddressExtractionService address,
            NameExtractionService names,
            GeneralDocumentAnswerService general) {
    }
}
