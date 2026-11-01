package com.docket.app.service.ai;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Turns the XHTML events Tika emits into segments that remember their page and heading.
// PDF pages arrive as <div class="page">, and DOCX heading styles arrive as h1 to h6.
class StructuredTextHandler extends DefaultHandler {

    private static final int MAX_HEADING_LENGTH = 500;
    private static final Set<String> TRACKED_HEADINGS = Set.of("h1", "h2", "h3");
    private static final Set<String> LINE_ENDING_ELEMENTS = Set.of("p", "div", "li", "tr", "h4", "h5", "h6");

    private final boolean trackPages;
    private final int maxChars;

    private final List<TextSegment> segments = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();
    private final StringBuilder headingText = new StringBuilder();

    private boolean inBody;
    private boolean inHeading;
    private boolean limitReached;
    private int page;
    private int totalChars;
    private int visibleChars;
    private String heading;

    StructuredTextHandler(boolean trackPages, int maxChars) {
        this.trackPages = trackPages;
        this.maxChars = maxChars;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        String element = elementName(localName, qName);

        if (element.equals("body")) {
            inBody = true;
        } else if (trackPages && element.equals("div") && "page".equals(attributes.getValue("class"))) {
            flush();
            page++;
        } else if (TRACKED_HEADINGS.contains(element)) {
            flush();
            inHeading = true;
            headingText.setLength(0);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        String element = elementName(localName, qName);

        if (element.equals("body")) {
            inBody = false;
        } else if (TRACKED_HEADINGS.contains(element)) {
            inHeading = false;
            String text = headingText.toString().strip();
            if (!text.isEmpty()) {
                heading = text.length() > MAX_HEADING_LENGTH ? text.substring(0, MAX_HEADING_LENGTH) : text;
            }
            current.append('\n');
        } else if (LINE_ENDING_ELEMENTS.contains(element)) {
            current.append('\n');
        } else if (element.equals("td") || element.equals("th")) {
            current.append('\t');
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (!inBody) {
            return;
        }
        if (totalChars + length > maxChars) {
            limitReached = true;
            throw new SAXException("Extracted text limit reached");
        }

        totalChars += length;
        current.append(ch, start, length);
        if (inHeading) {
            headingText.append(ch, start, length);
        }
        for (int i = start; i < start + length; i++) {
            if (!Character.isWhitespace(ch[i])) {
                visibleChars++;
            }
        }
    }

    List<TextSegment> segments() {
        flush();
        return List.copyOf(segments);
    }

    int pageCount() {
        return page;
    }

    int visibleChars() {
        return visibleChars;
    }

    boolean limitReached() {
        return limitReached;
    }

    private void flush() {
        String text = current.toString().strip();
        if (!text.isEmpty()) {
            segments.add(new TextSegment(text, page > 0 ? page : null, heading));
        }
        current.setLength(0);
    }

    private String elementName(String localName, String qName) {
        String name = localName == null || localName.isEmpty() ? qName : localName;
        return name.toLowerCase(Locale.ROOT);
    }
}
