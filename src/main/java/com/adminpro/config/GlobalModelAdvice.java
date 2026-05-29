package com.adminpro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalModelAdvice.class);

    @Value("${onlyoffice.document-server-url:#{null}}")
    private String onlyOfficeUrl;

    @ModelAttribute("onlyOfficeUrl")
    public String getOnlyOfficeUrl() {
        log.info("ONLYOFFICE URL from @Value: '{}'", onlyOfficeUrl);
        if (onlyOfficeUrl == null || onlyOfficeUrl.isBlank()) {
            log.warn("ONLYOFFICE URL is null/blank, DocsAPI script will not be loaded");
            return null;
        }
        String url = onlyOfficeUrl.trim();
        if (url.startsWith("${") && url.endsWith("}")) {
            log.warn("ONLYOFFICE URL is unresolved placeholder: {}", url);
            return null;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        log.info("ONLYOFFICE URL resolved to: {}", url);
        return url;
    }
}
