package com.docia.service;

import com.docia.dto.DocumentCommandResponse.PageText;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CpfCnpjExtractionService {

    private static final Pattern CPF_PATTERN = Pattern.compile("(?<!\\d)(\\d{11})(?!\\d)");
    private static final Pattern CNPJ_PATTERN = Pattern.compile("(?<!\\d)(\\d{14})(?!\\d)");
    private static final Pattern CPF_FORMATTED_PATTERN = Pattern.compile("(?<!\\d)(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})(?!\\d)");
    private static final Pattern CNPJ_FORMATTED_PATTERN = Pattern.compile("(?<!\\d)(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})(?!\\d)");

    public List<CpfCnpjOccurrence> extract(List<PageText> pages) {
        List<CpfCnpjOccurrence> occurrences = new ArrayList<>();
        for (PageText page : pages) {
            Set<String> seenOnPage = new HashSet<>();
            for (String candidate : candidates(page.text())) {
                String normalized = candidate.replaceAll("\\D", "");
                if (normalized.length() == 11 && isValidCpf(normalized)) {
                    if (seenOnPage.add(normalized)) {
                        occurrences.add(new CpfCnpjOccurrence("CPF", formatCpf(candidate), page.pageNumber()));
                    }
                } else if (normalized.length() == 14 && isValidCnpj(normalized)) {
                    if (seenOnPage.add(normalized)) {
                        occurrences.add(new CpfCnpjOccurrence("CNPJ", formatCnpj(candidate), page.pageNumber()));
                    }
                }
            }
        }
        return List.copyOf(occurrences);
    }

    private List<String> candidates(String text) {
        List<String> values = new ArrayList<>();
        addCandidates(text, CPF_FORMATTED_PATTERN, values);
        addCandidates(text, CNPJ_FORMATTED_PATTERN, values);
        addCandidates(text, CPF_PATTERN, values);
        addCandidates(text, CNPJ_PATTERN, values);
        return values;
    }

    private void addCandidates(String text, Pattern pattern, List<String> values) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
    }

    private boolean isValidCpf(String cpf) {
        if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(cpf.charAt(i)) * (10 - i);
        }
        int firstDigit = sum % 11;
        firstDigit = (firstDigit < 2) ? 0 : 11 - firstDigit;
        if (Character.getNumericValue(cpf.charAt(9)) != firstDigit) {
            return false;
        }

        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += Character.getNumericValue(cpf.charAt(i)) * (11 - i);
        }
        int secondDigit = sum % 11;
        secondDigit = (secondDigit < 2) ? 0 : 11 - secondDigit;
        return Character.getNumericValue(cpf.charAt(10)) == secondDigit;
    }

    private boolean isValidCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14 || cnpj.chars().distinct().count() == 1) {
            return false;
        }

        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += Character.getNumericValue(cnpj.charAt(i)) * weights1[i];
        }
        int firstDigit = sum % 11;
        firstDigit = firstDigit < 2 ? 0 : 11 - firstDigit;
        if (Character.getNumericValue(cnpj.charAt(12)) != firstDigit) {
            return false;
        }

        sum = 0;
        for (int i = 0; i < 13; i++) {
            sum += Character.getNumericValue(cnpj.charAt(i)) * weights2[i];
        }
        int secondDigit = sum % 11;
        secondDigit = secondDigit < 2 ? 0 : 11 - secondDigit;
        return Character.getNumericValue(cnpj.charAt(13)) == secondDigit;
    }

    private String formatCpf(String value) {
        String digits = value.replaceAll("\\D", "");
        return digits.substring(0, 3) + "." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-" + digits.substring(9);
    }

    private String formatCnpj(String value) {
        String digits = value.replaceAll("\\D", "");
        return digits.substring(0, 2) + "." + digits.substring(2, 5) + "." + digits.substring(5, 8) + "/" + digits.substring(8, 12) + "-" + digits.substring(12);
    }

    public record CpfCnpjOccurrence(String type, String value, int pageNumber) {
    }
}
