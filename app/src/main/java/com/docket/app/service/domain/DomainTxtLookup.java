package com.docket.app.service.domain;

import java.util.List;

// Reads DNS TXT records. An interface so domain verification can be tested without real DNS.
public interface DomainTxtLookup {

    // Returns every TXT record value for the host, or an empty list if there are none
    // or the lookup fails. Never throws for "not found".
    List<String> txtRecords(String host);
}
