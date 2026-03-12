package com.agent.model.analysis;

/**
 * Enumeration of legal issues commonly found in family law cases.
 */
public enum LegalIssueType {
    /**
     * Financial reimbursement issues (e.g., post-separation mortgage payments, loan repayment)
     */
    REIMBURSEMENT,
    
    /**
     * Post-separation spousal or child support obligations
     */
    SUPPORT,
    
    /**
     * Characterization of community property vs. separate property
     */
    PROPERTY_CHARACTERIZATION,
    
    /**
     * Tracing of funds, down payment sources, contribution tracking
     */
    TRACING,
    
    /**
     * Exclusive use and occupancy of real property
     */
    EXCLUSIVE_USE,
    
    /**
     * Custody and visitation with minor children
     */
    CUSTODY,
    
    /**
     * Restraining orders, TRO, DVRO, protective orders
     */
    RESTRAINING_ORDER,
    
    /**
     * Generic/unclassified legal issue
     */
    OTHER
}
