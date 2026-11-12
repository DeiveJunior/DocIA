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
public class PhoneExtractionService {

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\([1-9]\\d\\)|[1-9]\\d)[\\s.-]*(?:9\\d{4}|[2-5]\\d{3})[\\s.-]?\\d{4}(?!\\d)");
    private static final Pattern FORMATTED_CPF_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}(?!\\d)");
    private static final Pattern FORMATTED_CNPJ_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}(?!\\d)");

    public List<PhoneOccurrence> extract(List<PageText> pages) {
        List<PhoneOccurrence> occurrences = new ArrayList<>();
        for (PageText page : pages) {
            String text = page.text();
            List<int[]> taxIdRanges = findTaxIdRanges(text);
            Set<String> seenOnPage = new HashSet<>();
            Matcher matcher = PHONE_PATTERN.matcher(text);
            while (matcher.find()) {
                if (overlapsTaxId(matcher.start(), matcher.end(), taxIdRanges)) {
                    continue;
                }
                String phone = matcher.group();
                String normalizedPhone = phone.replaceAll("\\D", "");
                if (seenOnPage.add(normalizedPhone)) {
                    occurrences.add(new PhoneOccurrence(phone, page.pageNumber()));
                }
            }
        }
        return List.copyOf(occurrences);
    }

    private List<int[]> findTaxIdRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        addRanges(text, FORMATTED_CPF_PATTERN, ranges);
        addRanges(text, FORMATTED_CNPJ_PATTERN, ranges);
        return ranges;
    }

    private void addRanges(String text, Pattern pattern, List<int[]> ranges) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
        }
    }

    private boolean overlapsTaxId(int start, int end, List<int[]> ranges) {
        return ranges.stream().anyMatch(range -> start < range[1] && end > range[0]);
    }

    public record PhoneOccurrence(String phone, int pageNumber) {
    }
}
