package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts property information from evidence chunks.
 * 
 * Identifies city names, addresses, and loan numbers from evidence text
 * to attribute mortgage/payment facts to specific community properties.
 */
@Service
public class PropertyExtractionService {
    private static final Logger logger = LoggerFactory.getLogger(PropertyExtractionService.class);
    
    // Pattern for "Property Address:" lines like "Property Address: 39586 S DARNER DR NEWARK, CA 94560"
    private static final Pattern PROPERTY_ADDRESS_PATTERN = Pattern.compile(
        "(?i)property\\s+address\\s*:\\s*(.+?)(?:\\n|$)"
    );
    
    // Pattern to extract city from address (word before CA/California)
    private static final Pattern CITY_PATTERN = Pattern.compile(
        "\\b([A-Z][A-Za-z]*)\\s*[,]?\\s*(CA|California)\\b"
    );
    
    // Pattern for loan/account numbers
    private static final Pattern LOAN_PATTERN = Pattern.compile(
        "(?i)(?:loan|lender|account)[\\s#:]*['\"]?([0-9]{8,})['\"]?"
    );
    
    /**
     * Extract property information from an evidence chunk.
     * 
     * @param chunk The evidence chunk to analyze
     * @return PropertyInfo with extracted details
     */
    public PropertyInfo extractPropertyInfo(EvidenceChunk chunk) {
        String text = chunk.text();
        String filename = chunk.filename();
        
        String city = extractCityFromText(text);
        String address = extractAddressFromText(text);
        String loanNumber = extractLoanNumberFromText(text);
        
        // If no city found in text, try filename
        if (city == null && filename != null) {
            city = extractCityFromText(filename);
        }
        
        return new PropertyInfo(city, address, loanNumber, filename);
    }
    
    /**
     * Property information extracted from evidence.
     */
    public static class PropertyInfo {
        public final String city;
        public final String address;
        public final String loanNumber;
        public final String sourceFilename;
        
        public PropertyInfo(String city, String address, String loanNumber, String sourceFilename) {
            this.city = city;
            this.address = address;
            this.loanNumber = loanNumber;
            this.sourceFilename = sourceFilename;
        }
        
        /**
         * Check if this PropertyInfo has any valid attributes.
         */
        public boolean hasAttributes() {
            return city != null || address != null || loanNumber != null;
        }
        
        /**
         * Get normalized property key for grouping.
         */
        public String getPropertyKey() {
            if (city != null && !city.isEmpty()) {
                return city.toLowerCase();
            }
            if (loanNumber != null && !loanNumber.isEmpty()) {
                return "loan_" + loanNumber;
            }
            return "unknown";
        }
        
        /**
         * Get human-readable property identifier.
         */
        public String getPropertyDisplay() {
            if (city != null) {
                if (address != null) {
                    return city + " (" + address + ")";
                }
                return city;
            }
            if (loanNumber != null) {
                return "Loan #" + loanNumber;
            }
            return "Unknown Property";
        }
        
        @Override
        public String toString() {
            return String.format("PropertyInfo{city=%s, address=%s, loan=%s}", city, address, loanNumber);
        }
    }
    
    /**
     * Extract city name from text.
     * Looks for city names before California/CA abbreviation in addresses.
     */
    private String extractCityFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        // First try to find city in Property Address line
        Matcher addressMatcher = PROPERTY_ADDRESS_PATTERN.matcher(text);
        if (addressMatcher.find()) {
            String addressLine = addressMatcher.group(1);
            return extractCityFromAddress(addressLine);
        }
        
        // Fall back to any CA/California pattern in the text
        return extractCityFromAddress(text);
    }
    
    /**
     * Extract city from an address string.
     */
    private String extractCityFromAddress(String addressLine) {
        if (addressLine == null || addressLine.isEmpty()) {
            return null;
        }
        
        Matcher cityMatcher = CITY_PATTERN.matcher(addressLine);
        if (cityMatcher.find()) {
            String city = cityMatcher.group(1);
            // Normalize: First letter uppercase, rest lowercase
            if (city.length() > 1 && !city.matches("^[A-Z]{1,2}$")) {
                return city.substring(0, 1).toUpperCase() + city.substring(1).toLowerCase();
            }
            return city;
        }
        return null;
    }
    
    /**
     * Extract full address from text.
     * Attempts to extract the first complete address line.
     */
    private String extractAddressFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        Matcher addressMatcher = PROPERTY_ADDRESS_PATTERN.matcher(text);
        if (addressMatcher.find()) {
            String addressLine = addressMatcher.group(1).trim();
            // Clean up: remove trailing content after state
            int stateIndex = addressLine.indexOf("CA");
            if (stateIndex > 0) {
                // Keep address up to state abbreviation
                return addressLine.substring(0, stateIndex).trim();
            }
            return addressLine;
        }
        return null;
    }
    
    /**
     * Extract loan or account number from text.
     */
    private String extractLoanNumberFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        Matcher loanMatcher = LOAN_PATTERN.matcher(text);
        if (loanMatcher.find()) {
            return loanMatcher.group(1);
        }
        return null;
    }
}
