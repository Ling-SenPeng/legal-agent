package com.agent.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Structured mortgage or loan payment record.
 * 
 * Represents a single payment entry extracted from mortgage statements.
 * All fields must be extracted from the same payment block to ensure accuracy.
 * 
 * Design principle: Only bind fields that are semantically co-located
 * in the source document. Never mix fields from unrelated lines.
 */
public class MortgagePaymentRecord {
    private LocalDate statementDate;      // Statement date
    private LocalDate paymentDueDate;     // When payment was due
    private LocalDate paymentDate;        // When payment was made
    private BigDecimal totalPayment;      // Total payment amount
    private BigDecimal principal;         // Principal portion
    private BigDecimal interest;          // Interest portion
    private String loanNumber;            // Loan/account number
    private String propertyAddress;       // Property address
    private String transactionDescription; // Description from transaction row
    
    /**
     * Private constructor - use builder for creation.
     */
    private MortgagePaymentRecord() {
    }
    
    /**
     * Get the statement date.
     */
    public LocalDate getStatementDate() {
        return statementDate;
    }
    
    /**
     * Get the payment due date.
     */
    public LocalDate getPaymentDueDate() {
        return paymentDueDate;
    }
    
    /**
     * Get the date payment was made.
     */
    public LocalDate getPaymentDate() {
        return paymentDate;
    }
    
    /**
     * Get the total payment amount.
     */
    public BigDecimal getTotalPayment() {
        return totalPayment;
    }
    
    /**
     * Get the principal portion.
     */
    public BigDecimal getPrincipal() {
        return principal;
    }
    
    /**
     * Get the interest portion.
     */
    public BigDecimal getInterest() {
        return interest;
    }
    
    /**
     * Get the loan number.
     */
    public String getLoanNumber() {
        return loanNumber;
    }
    
    /**
     * Get the property address.
     */
    public String getPropertyAddress() {
        return propertyAddress;
    }
    
    /**
     * Get the transaction description.
     */
    public String getTransactionDescription() {
        return transactionDescription;
    }
    
    /**
     * Check if this record has a valid payment date.
     */
    public boolean hasPaymentDate() {
        return paymentDate != null || paymentDueDate != null;
    }
    
    /**
     * Check if this record has a valid payment amount.
     */
    public boolean hasPaymentAmount() {
        return totalPayment != null && totalPayment.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Builder for fluent creation of MortgagePaymentRecord.
     */
    public static class Builder {
        private final MortgagePaymentRecord record = new MortgagePaymentRecord();
        
        public Builder staticementDate(LocalDate date) {
            this.record.statementDate = date;
            return this;
        }
        
        public Builder paymentDueDate(LocalDate date) {
            this.record.paymentDueDate = date;
            return this;
        }
        
        public Builder paymentDate(LocalDate date) {
            this.record.paymentDate = date;
            return this;
        }
        
        public Builder totalPayment(BigDecimal amount) {
            this.record.totalPayment = amount;
            return this;
        }
        
        public Builder principal(BigDecimal amount) {
            this.record.principal = amount;
            return this;
        }
        
        public Builder interest(BigDecimal amount) {
            this.record.interest = amount;
            return this;
        }
        
        public Builder loanNumber(String number) {
            this.record.loanNumber = number;
            return this;
        }
        
        public Builder propertyAddress(String address) {
            this.record.propertyAddress = address;
            return this;
        }
        
        public Builder transactionDescription(String description) {
            this.record.transactionDescription = description;
            return this;
        }
        
        public MortgagePaymentRecord build() {
            return this.record;
        }
    }
    
    @Override
    public String toString() {
        return "MortgagePaymentRecord{" +
                "statementDate=" + statementDate +
                ", paymentDate=" + paymentDate +
                ", totalPayment=" + totalPayment +
                ", loanNumber='" + loanNumber + '\'' +
                ", propertyAddress='" + propertyAddress + '\'' +
                '}';
    }
}
