package com.adminpro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    @Value("${onlyoffice.document-server-url:https://onlinedocs.onlyoffice.com/}")
    private String onlyOfficeUrl;

    @ModelAttribute("onlyOfficeUrl")
    public String getOnlyOfficeUrl() {
        if (onlyOfficeUrl == null || onlyOfficeUrl.isBlank() || onlyOfficeUrl.equals("${ONLYOFFICE_URL}")) {
            return null;
        }
        if (onlyOfficeUrl.endsWith("/")) {
            return onlyOfficeUrl.substring(0, onlyOfficeUrl.length() - 1);
        }
        return onlyOfficeUrl;
    }
}
