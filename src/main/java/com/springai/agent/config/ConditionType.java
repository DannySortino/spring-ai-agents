package com.springai.agent.config;

/**
 * Enum representing the different types of conditions that can be evaluated in workflows.
 * Each condition type defines how field values are compared against expected values.
 */
public enum ConditionType {
    
    /**
     * Exact string equality comparison.
     * Compares the field value exactly with the expected value.
     * Supports case-sensitive and case-insensitive comparison via ignoreCase flag.
     * 
     * Example: field="input", value="urgent", ignoreCase=true
     * Will match if input contains exactly "urgent" (case-insensitive)
     */
    EQUALS,
    
    /**
     * Substring containment check.
     * Checks if the field value contains the expected value as a substring.
     * Supports case-sensitive and case-insensitive comparison via ignoreCase flag.
     * 
     * Example: field="input", value="invoice", ignoreCase=true
     * Will match if input contains "invoice" anywhere within it
     */
    CONTAINS,
    
    /**
     * Regular expression pattern matching.
     * Uses Java regex patterns to match against the field value.
     * The ignoreCase flag is not used for regex - use regex flags instead.
     * 
     * Example: field="input", value=".*invoice.*|.*bill.*|.*payment.*"
     * Will match if input contains "invoice", "bill", or "payment"
     */
    REGEX,
    
    /**
     * Field existence check.
     * Checks if the field exists and has a non-null, non-empty value.
     * The expected value is ignored for this condition type.
     * 
     * Example: field="context.userId", value="" (ignored)
     * Will match if context.userId exists and is not empty
     */
    EXISTS,
    
    /**
     * Field emptiness check.
     * Checks if the field is null or empty.
     * The expected value is ignored for this condition type.
     * 
     * Example: field="previousResult", value="" (ignored)
     * Will match if previousResult is null or empty
     */
    EMPTY
}
