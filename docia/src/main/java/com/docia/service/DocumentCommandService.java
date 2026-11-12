package com.docia.service;

import com.docia.dto.AddressExtractionResponse;
import com.docia.dto.CpfCnpjExtractionResponse;
import com.docia.dto.DocumentCommandResponse;
import com.docia.dto.EmailExtractionResponse;
import com.docia.dto.PhoneExtractionResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class DocumentCommandService {

    private final PdfExtractionService pdfExtractionService;
    private final EmailExtractionService emailExtractionService;
    private final PhoneExtractionService phoneExtractionService;
    private final CpfCnpjExtractionService cpfCnpjExtractionService;
    private final AddressExtractionService addressExtractionService;
    private final CommandInterpreterService commandInterpreterService;

    public DocumentCommandService(PdfExtractionService pdfExtractionService,
                                  EmailExtractionService emailExtractionService,
                                  PhoneExtractionService phoneExtractionService,
                                  CpfCnpjExtractionService cpfCnpjExtractionService,
                                  AddressExtractionService addressExtractionService,
                                  CommandInterpreterService commandInterpreterService) {
        this.pdfExtractionService = pdfExtractionService;
        this.emailExtractionService = emailExtractionService;
        this.phoneExtractionService = phoneExtractionService;
        this.cpfCnpjExtractionService = cpfCnpjExtractionService;
        this.addressExtractionService = addressExtractionService;
        this.commandInterpreterService = commandInterpreterService;
    }

    public Object process(String filename, String command, MultipartFile document) {
        CommandInterpreterService.Intent intent = commandInterpreterService.interpret(command);
        if (intent == CommandInterpreterService.Intent.UNKNOWN) {
            intent = intentFromKeywords(command);
        }
        if (intent == CommandInterpreterService.Intent.UNKNOWN) {
            return new DocumentCommandResponse(filename, command, "UNSUPPORTED", 0, List.of());
        }

        PdfExtractionService.ExtractedDocument extracted = pdfExtractionService.extract(document);
        List<DocumentCommandResponse.PageText> pages = extracted.pages();

        return switch (intent) {
            case ADDRESS -> new AddressExtractionResponse(
                    filename, command, "EXTRACTED", extracted.pageCount(),
                    addressExtractionService.extract(pages));
            case CPF_CNPJ -> {
                List<CpfCnpjExtractionResponse.CpfCnpjOccurrence> documents = cpfCnpjExtractionService.extract(pages)
                        .stream()
                        .map(item -> new CpfCnpjExtractionResponse.CpfCnpjOccurrence(
                                item.type(), item.value(), item.pageNumber()))
                        .toList();
                yield new CpfCnpjExtractionResponse(
                        filename, command, "EXTRACTED", extracted.pageCount(), documents);
            }
            case PHONE -> {
                List<PhoneExtractionResponse.PhoneOccurrence> phones = phoneExtractionService.extract(pages)
                        .stream()
                        .map(item -> new PhoneExtractionResponse.PhoneOccurrence(
                                item.phone(), item.pageNumber()))
                        .toList();
                yield new PhoneExtractionResponse(
                        filename, command, "EXTRACTED", extracted.pageCount(), phones);
            }
            case EMAIL -> new EmailExtractionResponse(
                    filename, command, "EXTRACTED", extracted.pageCount(),
                    emailExtractionService.extract(pages));
            case FULL_TEXT -> new DocumentCommandResponse(
                    filename, command, "EXTRACTED", extracted.pageCount(), pages);
            case UNKNOWN -> new DocumentCommandResponse(filename, command, "UNSUPPORTED", 0, List.of());
        };
    }

    private CommandInterpreterService.Intent intentFromKeywords(String command) {
        if (requestsAddressExtraction(command)) {
            return CommandInterpreterService.Intent.ADDRESS;
        }
        if (requestsCpfCnpjExtraction(command)) {
            return CommandInterpreterService.Intent.CPF_CNPJ;
        }
        if (requestsPhoneExtraction(command)) {
            return CommandInterpreterService.Intent.PHONE;
        }
        if (requestsEmailExtraction(command)) {
            return CommandInterpreterService.Intent.EMAIL;
        }
        return CommandInterpreterService.Intent.UNKNOWN;
    }

    private boolean requestsAddressExtraction(String command) {
        return command.matches("(?iu).*\\b(?:endereços?|endereco|logradouros?)\\b.*");
    }

    private boolean requestsCpfCnpjExtraction(String command) {
        return command.matches("(?iu).*\\b(?:cpfs?|cnpjs?)\\b.*");
    }

    private boolean requestsPhoneExtraction(String command) {
        return command.matches("(?iu).*\\b(?:telefones?|celulares?)\\b.*");
    }

    private boolean requestsEmailExtraction(String command) {
        return command.matches("(?iu).*\\b(?:e[\\s-]?mails?)\\b.*");
    }
}
