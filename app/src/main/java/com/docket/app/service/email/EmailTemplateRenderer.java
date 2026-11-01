package com.docket.app.service.email;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

// Turns a template and its variables into the HTML and plain-text bodies of an email.
@Component
public class EmailTemplateRenderer {
    private final TemplateEngine htmlEngine = engine(".html", TemplateMode.HTML);
    private final TemplateEngine textEngine = engine(".txt", TemplateMode.TEXT);

    public Rendered render(EmailTemplate template, Map<String, Object> variables) {
        Context context = new Context(Locale.ENGLISH, variables);
        return new Rendered(
                htmlEngine.process(template.getTemplateName(), context),
                textEngine.process(template.getTemplateName(), context));
    }

    private static TemplateEngine engine(String suffix, TemplateMode mode) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    public record Rendered(String html, String text) {
    }
}
