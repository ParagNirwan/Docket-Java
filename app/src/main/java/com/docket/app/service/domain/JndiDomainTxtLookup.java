package com.docket.app.service.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

// Uses the JDK's built-in DNS provider, so no extra dependency is needed.
@Slf4j
@Component
public class JndiDomainTxtLookup implements DomainTxtLookup {

    @Override
    public List<String> txtRecords(String host) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "2");

        DirContext context = null;
        try {
            context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes(host, new String[]{"TXT"});
            Attribute txt = attributes.get("TXT");
            if (txt == null) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            NamingEnumeration<?> all = txt.getAll();
            while (all.hasMore()) {
                values.add(unquote(String.valueOf(all.next())));
            }
            return values;
        } catch (NamingException exception) {
            log.debug("TXT lookup for {} failed: {}", host, exception.getMessage());
            return List.of();
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                    // nothing to clean up
                }
            }
        }
    }

    // JNDI returns long TXT records as quoted chunks: "abc" "def" -> abcdef.
    static String unquote(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.contains("\"")) {
            return trimmed;
        }
        StringBuilder out = new StringBuilder();
        boolean inQuotes = false;
        for (char c : trimmed.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (inQuotes) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
