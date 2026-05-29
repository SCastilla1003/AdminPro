package com.adminpro.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;

@RestController
@RequestMapping("/oo-proxy")
public class OnlyOfficeProxyController {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeProxyController.class);

    private static final List<String> HOP_BY_HOP_HEADERS = List.of(
            "host", "connection", "proxy-connection", "transfer-encoding",
            "upgrade", "keep-alive", "te", "trailer"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${onlyoffice.document-server-url:https://onlinedocs.onlyoffice.com/}")
    private String documentServerUrl;

    @Value("${onlyoffice.internal-url:#{null}}")
    private String internalUrl;

    @RequestMapping("/**")
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = requestUri.substring(contextPath.length() + "/oo-proxy".length());
        if (path.isEmpty()) { path = "/"; }

        String baseUrl = (internalUrl != null && !internalUrl.isBlank()) ? internalUrl : documentServerUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String queryString = request.getQueryString();
        String targetUrl = baseUrl + path + (queryString != null ? "?" + queryString : "");

        log.info("Proxying: {} -> {} (base: {})", requestUri, targetUrl, baseUrl);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(30));

            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase())) {
                    continue;
                }
                Enumeration<String> headerValues = request.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    requestBuilder.header(headerName, headerValues.nextElement());
                }
            }

            HttpRequest proxyRequest = requestBuilder.GET().build();
            HttpResponse<InputStream> proxyResponse = httpClient.send(proxyRequest, HttpResponse.BodyHandlers.ofInputStream());

            response.setStatus(proxyResponse.statusCode());

            proxyResponse.headers().map().forEach((name, values) -> {
                if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                    values.forEach(value -> response.addHeader(name, value));
                }
            });

            try (InputStream inputStream = proxyResponse.body();
                 OutputStream outputStream = response.getOutputStream()) {
                inputStream.transferTo(outputStream);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Proxy interrupted for {}: {}", request.getRequestURI(), e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Upstream server unreachable");
        } catch (IOException e) {
            log.error("Proxy IO error for {} -> {}: {}", request.getRequestURI(), documentServerUrl, e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Upstream server unreachable: " + e.getMessage());
        } catch (Exception e) {
            log.error("Proxy error for {}: {}", request.getRequestURI(), e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy error: " + e.getMessage());
        }
    }
}
