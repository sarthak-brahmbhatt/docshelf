// Document types (matches document.doc_type CHECK constraint in V1__init.sql)
package com.docshelf.extract;

public enum DocType {
    INSURANCE_POLICY, MF_CAS, AADHAAR, PASSPORT, DRIVING_LICENCE, PAN_CARD,
    PRESCRIPTION, MEDICAL_REPORT, BILL_RECEIPT, VOICE_NOTE, NOTE, OTHER, UNKNOWN;

    /** Types whose fields land in id_document. */
    public boolean isIdentity() {
        return this == AADHAAR || this == PASSPORT || this == DRIVING_LICENCE || this == PAN_CARD;
    }
}
