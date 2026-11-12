package com.docket.app.enums;

// ARCHIVED_AT counts from when a document was archived or filed under the policy.
// KEY_DATE counts from the date confirmed on the document, such as a contract's end date,
// and behaves like ARCHIVED_AT for a document that has none.
public enum ClockStart {
    UPLOADED_AT,
    ARCHIVED_AT,
    KEY_DATE
}
