package com.payflow.routing.iso8583;

/**
 * ISO 8583 field encoding types.
 * 
 * FIXED: Always exactly N characters (padded if shorter)
 * LLVAR: 2-digit length prefix + variable data (max 99 chars)
 * LLLVAR: 3-digit length prefix + variable data (max 999 chars)
 */
public enum FieldType {
    FIXED,
    LLVAR,
    LLLVAR
}
