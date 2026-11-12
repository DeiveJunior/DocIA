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

    public DocumentCommandService(PdfExtractionService pdfExtractionService,
                                  EmailExtractionService emailExtractionService,
                                  PhoneExtractionService phoneExtractionService,
                                  CpfCnpjExtractionService cpfCnpjExtractionService,
                                  AddressExtractionService addressExtractionService) {
        this.pdfExtractionService = pdfExtractionService;
        this.emailExtractionService = emailExtractionService;
        this.phoneExtractionService = phoneExtractionService;
        this.cpfCnpjExtractionService = cpfCnpjExtractionService;
        this.addressExtractionService = addressExtractionService;
    }

    public Object process(String filename, String command, MultipartFile document) {
        PdfExtractionService.ExtractedDocument extracted = pdfExtractionService.extract(document);
        List<DocumentCommandResponse.PageText> pages = extracted.pages();

        if (requestsAddressExtraction(command)) {
            return new AddressExtractionResponse(filename, command, "EXTRACTED", extracted.pageCount(),
                    addressExtractionService.extract(pages));
        }
        if (requestsCpfCnpjExtraction(command)) {
            List<CpfCnpjExtractionResponse.CpfCnpjOccurrence> documents = cpfCnpjExtractionService.extract(pages)
                    .stream()
                    .map(item -> new CpfCnpjExtractionResponse.CpfCnpjOccurrence(
                            item.type(), item.value(), item.pageNumber()))
                    .toList();
            return new CpfCnpjExtractionResponse(filename, command, "EXTRACTED", extracted.pageCount(), documents);
        }
        if (requestsPhoneExtraction(command)) {
            List<PhoneExtractionResponse.PhoneOccurrence> phones = phoneExtractionService.extract(pages)
                    .stream()
                    .map(item -> new PhoneExtractionResponse.PhoneOccurrence(item.phone(), item.pageNumber()))
                    .toList();
            return new PhoneExtractionResponse(filename, command, "EXTRACTED", extracted.pageCount(), phones);
        }
        if (requestsEmailExtraction(command)) {
            return new EmailExtractionResponse(filename, command, "EXTRACTED", extracted.pageCount(),
                    emailExtractionService.extract(pages));
        }
        return new DocumentCommandResponse(filename, command, "EXTRACTED", extracted.pageCount(), pages);
    }

    private boolean requestsAddressExtraction(String command) {
        return command.matches("(?iu).*\\b(?:endereços?|endereco|logradouros?)\\b.*");
    }

    private boolean requestsCpfCnpjExtraction(String command) {
        return command.matches("(?iu).*\\b(?:cpf|cnpj|documentos?)\\b.*");
    }

    private boolean requestsPhoneExtraction(String command) {
        return command.matches("(?iu).*\\b(?:telefones?|celulares?)\\b.*");
    }

    private boolean requestsEmailExtraction(String command) {
        return command.matches("(?iu).*\\b(?:e[\\s-]?mails?)\\b.*");
    }
}
