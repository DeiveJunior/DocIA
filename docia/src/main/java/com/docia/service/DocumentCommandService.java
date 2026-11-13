package com.docia.service;

import com.docia.dto.AddressExtractionResponse;
import com.docia.dto.CpfCnpjExtractionResponse;
import com.docia.dto.DocumentCommandResponse;
import com.docia.dto.EmailExtractionResponse;
import com.docia.dto.GeneralAnswerResponse;
import com.docia.dto.NameExtractionResponse;
import com.docia.dto.PhoneExtractionResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class DocumentCommandService {

    private static final Pattern COMPANY_NAME_REQUEST_PATTERN = Pattern.compile(
            "(?iu)\\b(?:nome\\s+(?:da\\s+empresa|da\\s+institui[cç][aã]o|fantasia)|raz[aã]o\\s+social)\\b");
    private static final Pattern PERSONAL_EMAIL_REQUEST_PATTERN = Pattern.compile(
            "(?iu)\\b(?:pessoal|particular|corporativo|corporativa|profissional|de\\s+trabalho|do\\s+trabalho)\\b");
    private static final Pattern PERSONAL_NAME_REQUEST_PATTERN = Pattern.compile(
            "(?iu)\\b(?:meu\\s+nome|qual\\s+(?:é|e)\\s+(?:o\\s+)?nome|qual(?:\\s+é|\\s+e)?\\s+o\\s+meu\\s+nome)\\b");
    private static final Pattern ALL_NAMES_REQUEST_PATTERN = Pattern.compile(
            "(?iu)\\b(?:todos\\s+os\\s+nomes|nomes\\s+(?:das?|de)\\s+pessoas|quais\\s+s[aã]o\\s+os\\s+nomes|nomes\\s+mencionad[oa]s)\\b");
    private static final Pattern COMPLETE_ADDRESS_PATTERN = Pattern.compile(
            "(?iu)\\b(?:completo|completa|completos|completas)\\b");

    private final PdfExtractionService pdfExtractionService;
    private final EmailExtractionService emailExtractionService;
    private final PhoneExtractionService phoneExtractionService;
    private final CpfCnpjExtractionService cpfCnpjExtractionService;
    private final AddressExtractionService addressExtractionService;
    private final NameExtractionService nameExtractionService;
    private final CommandInterpreterService commandInterpreterService;
    private final GeneralDocumentAnswerService generalDocumentAnswerService;

    public DocumentCommandService(
            PdfExtractionService pdfExtractionService,
            EmailExtractionService emailExtractionService,
            PhoneExtractionService phoneExtractionService,
            CpfCnpjExtractionService cpfCnpjExtractionService,
            AddressExtractionService addressExtractionService,
            NameExtractionService nameExtractionService,
            CommandInterpreterService commandInterpreterService,
            GeneralDocumentAnswerService generalDocumentAnswerService) {
        this.pdfExtractionService = pdfExtractionService;
        this.emailExtractionService = emailExtractionService;
        this.phoneExtractionService = phoneExtractionService;
        this.cpfCnpjExtractionService = cpfCnpjExtractionService;
        this.addressExtractionService = addressExtractionService;
        this.nameExtractionService = nameExtractionService;
        this.commandInterpreterService = commandInterpreterService;
        this.generalDocumentAnswerService = generalDocumentAnswerService;
    }

    public Object process(String filename, String command, MultipartFile document) {
        CommandInterpreterService.Intent intent = commandInterpreterService.interpret(command);
        if (COMPANY_NAME_REQUEST_PATTERN.matcher(command).find()) {
            // Do not route company/institution name requests to the person-name extractor,
            // even when the intent classifier says NAME.
            intent = CommandInterpreterService.Intent.UNKNOWN;
        } else if (intent == CommandInterpreterService.Intent.UNKNOWN) {
            intent = intentFromKeywords(command);
        }

        PdfExtractionService.ExtractedDocument extracted = pdfExtractionService.extract(document);
        List<DocumentCommandResponse.PageText> pages = extracted.pages();
        Object deterministicResponse = extractDeterministically(filename, command, intent, extracted, pages);

        if (!needsGeneralAnswer(command, intent, deterministicResponse, pages)) {
            return deterministicResponse;
        }

        GeneralDocumentAnswerService.AnswerResult answer =
                generalDocumentAnswerService.answer(command, pages, deterministicResponse);
        List<GeneralAnswerResponse.PageSource> sources = answer.sourcePages().stream()
                .map(GeneralAnswerResponse.PageSource::new)
                .toList();

        return new GeneralAnswerResponse(
                filename,
                command,
                "EXTRACTED",
                extracted.pageCount(),
                "GENERAL_ANSWER",
                answer.status(),
                answer.answer(),
                sources,
                deterministicResponse);
    }

    private Object extractDeterministically(
            String filename,
            String command,
            CommandInterpreterService.Intent intent,
            PdfExtractionService.ExtractedDocument extracted,
            List<DocumentCommandResponse.PageText> pages) {
        return switch (intent) {
            case ADDRESS -> new AddressExtractionResponse(
                    filename, command, "EXTRACTED", extracted.pageCount(),
                    addressExtractionService.extract(pages));
            case CPF_CNPJ -> {
                List<CpfCnpjExtractionResponse.CpfCnpjOccurrence> documents =
                        cpfCnpjExtractionService.extract(pages).stream()
                                .map(item -> new CpfCnpjExtractionResponse.CpfCnpjOccurrence(
                                        item.type(), item.value(), item.pageNumber()))
                                .toList();
                yield new CpfCnpjExtractionResponse(
                        filename, command, "EXTRACTED", extracted.pageCount(), documents);
            }
            case PHONE -> {
                List<PhoneExtractionResponse.PhoneOccurrence> phones =
                        phoneExtractionService.extract(pages).stream()
                                .map(item -> new PhoneExtractionResponse.PhoneOccurrence(
                                        item.phone(), item.pageNumber()))
                                .toList();
                yield new PhoneExtractionResponse(
                        filename, command, "EXTRACTED", extracted.pageCount(), phones);
            }
            case EMAIL -> new EmailExtractionResponse(
                    filename, command, "EXTRACTED", extracted.pageCount(),
                    emailExtractionService.extract(pages));
            case NAME -> {
                List<NameExtractionResponse.NameOccurrence> names =
                        nameExtractionService.extract(pages).stream()
                                .map(item -> new NameExtractionResponse.NameOccurrence(
                                        item.name(), item.pageNumber()))
                                .toList();
                yield new NameExtractionResponse(
                        filename, command, "EXTRACTED", extracted.pageCount(), names);
            }
            case FULL_TEXT -> new DocumentCommandResponse(
                    filename, command, "EXTRACTED", extracted.pageCount(), pages);
            case UNKNOWN -> null;
        };
    }

    private boolean needsGeneralAnswer(
            String command,
            CommandInterpreterService.Intent intent,
            Object deterministicResponse,
            List<DocumentCommandResponse.PageText> pages) {
        if (intent == CommandInterpreterService.Intent.UNKNOWN) {
            return true;
        }
        if (intent == CommandInterpreterService.Intent.FULL_TEXT) {
            return false;
        }
        if (resultCount(deterministicResponse) == 0) {
            return true;
        }

        if (intent == CommandInterpreterService.Intent.EMAIL
                && PERSONAL_EMAIL_REQUEST_PATTERN.matcher(command).find()) {
            // The email extractor does not distinguish personal from corporate context.
            return true;
        }

        if (intent == CommandInterpreterService.Intent.NAME) {
            if (ALL_NAMES_REQUEST_PATTERN.matcher(command).find()) {
                return false;
            }
            if (PERSONAL_NAME_REQUEST_PATTERN.matcher(command).find()) {
                // A found name is not necessarily the speaker's name.
                return true;
            }
        }

        if (intent == CommandInterpreterService.Intent.ADDRESS
                && COMPLETE_ADDRESS_PATTERN.matcher(command).find()) {
            return addressNeedsCompletion(deterministicResponse, pages);
        }

        if (intent == CommandInterpreterService.Intent.CPF_CNPJ) {
            return !identifierRequestSatisfied(command, deterministicResponse);
        }

        return false;
    }

    private int resultCount(Object response) {
        if (response instanceof AddressExtractionResponse value) return value.addresses().size();
        if (response instanceof CpfCnpjExtractionResponse value) return value.documents().size();
        if (response instanceof EmailExtractionResponse value) return value.emails().size();
        if (response instanceof PhoneExtractionResponse value) return value.phones().size();
        if (response instanceof NameExtractionResponse value) return value.names().size();
        return 0;
    }

    private boolean addressNeedsCompletion(
            Object response,
            List<DocumentCommandResponse.PageText> pages) {
        if (!(response instanceof AddressExtractionResponse addresses)) {
            return true;
        }

        return addresses.addresses().stream().anyMatch(address -> {
            String pageText = pages.stream()
                    .filter(page -> page.pageNumber() == address.pageNumber())
                    .map(DocumentCommandResponse.PageText::text)
                    .findFirst()
                    .orElse("");

            boolean neighborhoodAvailable =
                    pageText.matches("(?isu).*\\b(?:bairro|vila|loteamento)\\b.*");
            boolean cityAvailable =
                    pageText.matches("(?isu).*\\b(?:cidade|munic[ií]pio)\\b.*");

            return !isPresent(address.street())
                    || (neighborhoodAvailable && !isPresent(address.neighborhood()))
                    || (cityAvailable && !isPresent(address.city()));
        });
    }

    private boolean identifierRequestSatisfied(String command, Object response) {
        if (!(response instanceof CpfCnpjExtractionResponse documents)) {
            return false;
        }

        boolean asksCpf = command.matches("(?iu).*\\bcpfs?\\b.*");
        boolean asksCnpj = command.matches("(?iu).*\\bcnpjs?\\b.*");
        if (asksCpf && !asksCnpj) {
            return documents.documents().stream()
                    .anyMatch(item -> "CPF".equalsIgnoreCase(item.type()));
        }
        if (asksCnpj && !asksCpf) {
            return documents.documents().stream()
                    .anyMatch(item -> "CNPJ".equalsIgnoreCase(item.type()));
        }
        return !documents.documents().isEmpty();
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private CommandInterpreterService.Intent intentFromKeywords(String command) {
        if (COMPANY_NAME_REQUEST_PATTERN.matcher(command).find()) {
            return CommandInterpreterService.Intent.UNKNOWN;
        }
        if (command.matches(
                "(?iu).*\\b(?:endereços?|endereco|logradouros?|onde\\s+(?:eu\\s+)?moro)\\b.*")) {
            return CommandInterpreterService.Intent.ADDRESS;
        }
        if (command.matches("(?iu).*\\b(?:cpfs?|cnpjs?)\\b.*")) {
            return CommandInterpreterService.Intent.CPF_CNPJ;
        }
        if (command.matches("(?iu).*\\b(?:telefones?|celulares?)\\b.*")) {
            return CommandInterpreterService.Intent.PHONE;
        }
        if (command.matches("(?iu).*\\b(?:e[\\s-]?mails?)\\b.*")) {
            return CommandInterpreterService.Intent.EMAIL;
        }
        if (command.matches(
                "(?iu).*\\b(?:nomes?|meu\\s+nome|qual\\s+(?:é|e)\\s+(?:o\\s+)?nome)\\b.*")) {
            return CommandInterpreterService.Intent.NAME;
        }
        return CommandInterpreterService.Intent.UNKNOWN;
    }
}
