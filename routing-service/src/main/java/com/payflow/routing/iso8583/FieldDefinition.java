package com.payflow.routing.iso8583;

/**
 * Definition of a single ISO 8583 field.
 * 
 * @param number The field number (2-64)
 * @param name Human-readable name
 * @param type FIXED, LLVAR, or LLLVAR
 * @param length Max length (or exact length for FIXED)
 */
public record FieldDefinition(int number, String name, FieldType type, int length) {
}
