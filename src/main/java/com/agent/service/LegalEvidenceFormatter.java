package com.agent.service;

import com.agent.model.LegalEvidenceLine;
import com.agent.model.PaymentRecord;
import com.agent.model.PaymentSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formats payment records into legal evidence text.
 * 
 * Converts raw payment data into citation-ready format
 * suitable for embedding in legal analysis answers.
 */
@Service
public class LegalEvidenceFormatter {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    /**
     * Format a single payment record as evidence.
     * 
     * @param record Payment record to format
     * @return Formatted evidence line
     */
    public LegalEvidenceLine formatPaymentRecord(PaymentRecord record) {
        if (record == null) {
            return null;
        }

        String amount = formatAmount(record.getTotalAmount());
        String propertyRef = formatPropertyReference(record.getPropertyAddress(), record.getPropertyCity());
        String dateStr = formatDate(record.getPaymentDate());
        String sourceRef = formatSourceReference(record.getSourcePage());

        // Build evidence text with breakdown of components
        StringBuilder evidenceText = new StringBuilder();
        evidenceText.append("Payment of ").append(amount);
        if (dateStr != null) {
            evidenceText.append(" on ").append(dateStr);
        }
        if (propertyRef != null) {
            evidenceText.append(" for property at ").append(propertyRef);
        }

        // Add breakdown if available
        if (hasBreakdown(record)) {
            evidenceText.append(" (");
            List<String> components = new ArrayList<>();
            if (record.getPrincipalAmount() != null && record.getPrincipalAmount().compareTo(BigDecimal.ZERO) > 0) {
                components.add("P: " + formatAmount(record.getPrincipalAmount()));
            }
            if (record.getInterestAmount() != null && record.getInterestAmount().compareTo(BigDecimal.ZERO) > 0) {
                components.add("I: " + formatAmount(record.getInterestAmount()));
            }
            if (record.getEscrowAmount() != null && record.getEscrowAmount().compareTo(BigDecimal.ZERO) > 0) {
                components.add("E: " + formatAmount(record.getEscrowAmount()));
            }
            evidenceText.append(String.join(", ", components));
            evidenceText.append(")");
        }

        if (record.getLoanNumber() != null) {
            evidenceText.append(" (Loan: ").append(record.getLoanNumber()).append(")");
        }

        return new LegalEvidenceLine(
            evidenceText.toString(),
            propertyRef,
            dateStr,
            amount,
            sourceRef,
            record.getSourcePage(),
            record.getConfidence()
        );
    }

    /**
     * Format a payment summary as evidence.
     * 
     * @param summary Payment summary to format
     * @return Formatted evidence line
     */
    public LegalEvidenceLine formatPaymentSummary(PaymentSummary summary) {
        if (summary == null) {
            return null;
        }

        String amount = formatAmount(summary.getTotalPayments());
        String propertyRef = formatPropertyReference(summary.getPropertyAddress(), summary.getPropertyCity());
        String dateRange = formatDateRange(summary.getPeriodStart(), summary.getPeriodEnd());
        String sourceRef = summary.getRecordCount() + " payment records";

        StringBuilder evidenceText = new StringBuilder();
        evidenceText.append("Total payments of ").append(amount);
        if (dateRange != null) {
            evidenceText.append(" from ").append(dateRange);
        }
        if (propertyRef != null) {
            evidenceText.append(" for ").append(propertyRef);
        }

        // Add breakdown summary if available
        if (hasSummaryBreakdown(summary)) {
            evidenceText.append(" (");
            List<String> components = new ArrayList<>();
            if (summary.getTotalPrincipal() != null && summary.getTotalPrincipal().compareTo(BigDecimal.ZERO) > 0) {
                components.add("Principal: " + formatAmount(summary.getTotalPrincipal()));
            }
            if (summary.getTotalInterest() != null && summary.getTotalInterest().compareTo(BigDecimal.ZERO) > 0) {
                components.add("Interest: " + formatAmount(summary.getTotalInterest()));
            }
            if (summary.getTotalEscrow() != null && summary.getTotalEscrow().compareTo(BigDecimal.ZERO) > 0) {
                components.add("Escrow: " + formatAmount(summary.getTotalEscrow()));
            }
            evidenceText.append(String.join(", ", components));
            evidenceText.append(")");
        }

        evidenceText.append(" across ").append(summary.getRecordCount()).append(" payments");

        return new LegalEvidenceLine(
            evidenceText.toString(),
            propertyRef,
            dateRange,
            amount,
            sourceRef,
            null,
            summary.getAverageConfidence()
        );
    }

    /**
     * Format a list of payment records as evidence lines.
     * 
     * @param records Payment records to format
     * @return List of formatted evidence lines
     */
    public List<LegalEvidenceLine> formatMultipleRecords(List<PaymentRecord> records) {
        List<LegalEvidenceLine> evidenceLines = new ArrayList<>();
        for (PaymentRecord record : records) {
            LegalEvidenceLine line = formatPaymentRecord(record);
            if (line != null) {
                evidenceLines.add(line);
            }
        }
        return evidenceLines;
    }

    // Helper methods
    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "$0.00";
        }
        return CURRENCY_FORMAT.format(amount);
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DATE_FORMATTER);
    }

    private String formatDateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return null;
        }
        if (start == null) {
            return "through " + formatDate(end);
        }
        if (end == null) {
            return "from " + formatDate(start);
        }
        return formatDate(start) + " to " + formatDate(end);
    }

    private String formatPropertyReference(String address, String city) {
        if (address == null && city == null) {
            return null;
        }
        if (address != null && city != null) {
            return address + ", " + city;
        }
        return address != null ? address : city;
    }

    private String formatSourceReference(Integer page) {
        if (page == null) {
            return "Mortgage Statement";
        }
        return "Mortgage Statement, Page " + page;
    }

    private boolean hasBreakdown(PaymentRecord record) {
        return (record.getPrincipalAmount() != null && record.getPrincipalAmount().compareTo(BigDecimal.ZERO) > 0) ||
               (record.getInterestAmount() != null && record.getInterestAmount().compareTo(BigDecimal.ZERO) > 0) ||
               (record.getEscrowAmount() != null && record.getEscrowAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    private boolean hasSummaryBreakdown(PaymentSummary summary) {
        return (summary.getTotalPrincipal() != null && summary.getTotalPrincipal().compareTo(BigDecimal.ZERO) > 0) ||
               (summary.getTotalInterest() != null && summary.getTotalInterest().compareTo(BigDecimal.ZERO) > 0) ||
               (summary.getTotalEscrow() != null && summary.getTotalEscrow().compareTo(BigDecimal.ZERO) > 0);
    }
}
