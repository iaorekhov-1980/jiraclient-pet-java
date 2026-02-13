package io.github.iaorekhov.jiraclient;

import java.net.URI;
import java.net.http.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class JiraClient {

    private final String baseUrl;
    private final String authHeader;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpClient createUnsafeHttpClient() {
        try {
            // Trust manager, который доверяет всем сертификатам
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] xcs, String string) {
                    }

                    public void checkServerTrusted(X509Certificate[] xcs, String string) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };

            // SSL context с нашим trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // Hostname verifier, который принимает все хосты
            HostnameVerifier allHostsValid = (hostname, session) -> true;

            // Создаем HttpClient с настройками
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(30))
                    .sslParameters(sslContext.getDefaultSSLParameters())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create HttpClient", e);
        }
    }

    public JiraClient(String baseUrl, String bearerToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authHeader = "Authorization";
        String value = "Bearer " + Objects.requireNonNull(bearerToken);
        this.http = createUnsafeHttpClient();
        this.defaultHeaders = Map.of(
                authHeader, value,
                "Accept", "application/json"
        );
    }

    private final Map<String, String> defaultHeaders;

    private HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header(authHeader, defaultHeaders.get(authHeader))
                .header("Accept", "application/json");
    }

    private <T> T ensure2xx(HttpResponse<String> resp, int... allowed) {
        int code = resp.statusCode();
        if (code >= 200 && code < 300) {
            return null;
        }
        for (int a : allowed) {
            if (code == a) {
                return null;
            }
        }
        throw new RuntimeException("HTTP " + code + ": " + resp.body());
    }

    public Map<String, Object> getMyself() throws Exception {
        HttpRequest r = req("/rest/api/2/myself").GET().build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        ensure2xx(resp);
        return mapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {
        });
    }

    public Map<String, Object> getIssue(String key, String fieldsCsv) throws Exception {
        String q = fieldsCsv == null ? "" : "?fields=" + fieldsCsv;
        HttpRequest r = req("/rest/api/2/issue/" + key + q).GET().build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        ensure2xx(resp);
        return mapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {
        });
    }

    public List<Map<String, Object>> searchJql(String jql, List<String> fields, int maxResults) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("jql", jql);
        body.put("startAt", 0);
        body.put("maxResults", maxResults);
        if (fields != null) {
            body.put("fields", fields);
        }
        String json = mapper.writeValueAsString(body);
        HttpRequest r = req("/rest/api/2/search")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        ensure2xx(resp);
        Map<String, Object> m = mapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {
        });
        Object issues = m.get("issues");
        if (issues instanceof List) {
            return (List<Map<String, Object>>) issues;
        }
        return List.of();
    }

    public String findEpicLinkFieldId() throws Exception {
        HttpRequest r = req("/rest/api/2/field").GET().build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        ensure2xx(resp);
        List<Map<String, Object>> arr = mapper.readValue(resp.body(), new TypeReference<List<Map<String, Object>>>() {
        });
        for (Map<String, Object> f : arr) {
            if ("Epic Link".equals(f.get("name"))) {
                return (String) f.get("id"); // e.g. customfield_10008
            }
        }
        throw new RuntimeException("Field 'Epic Link' not found");
    }

    public String createIssue(Map<String, Object> fields) throws Exception {
        Map<String, Object> root = Map.of("fields", fields);
        String json = mapper.writeValueAsString(root);
        HttpRequest r = req("/rest/api/2/issue")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 201) {
            throw new RuntimeException("Create issue failed: HTTP " + resp.statusCode() + " " + resp.body());
        }
        Map<String, Object> m = mapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {
        });
        return String.valueOf(m.get("key"));
    }

    public void linkCloners(String originalKey, String cloneKey, String typeName) throws Exception {
        Map<String, Object> body = Map.of(
                "type", Map.of("name", typeName), // "Cloners"
                "inwardIssue", Map.of("key", originalKey),
                "outwardIssue", Map.of("key", cloneKey)
        );
        String json = mapper.writeValueAsString(body);
        HttpRequest r = req("/rest/api/2/issueLink")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 201 && resp.statusCode() != 200 && resp.statusCode() != 204) {
            throw new RuntimeException("Create link failed: HTTP " + resp.statusCode() + " " + resp.body());
        }
    }

        public void addRemoteLinkConfluence(String issueKey, String url, String title, String relationship) throws Exception {
        String rel = (relationship == null || relationship.isBlank()) ? "Wiki Page" : relationship;
    
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("url", url);
        object.put("title", title);
        object.put("icon", Map.of("url16x16", "https://bwiki.beeline.ru/images/icons/favicon.png",
                          "title", "Confluence"));
    
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("object", object);
        root.put("relationship", rel);
        // Не добавляем "application" без globalId → избавляемся от "failed to load"
    
        String json = mapper.writeValueAsString(root);
        HttpRequest r = req("/rest/api/2/issue/" + issueKey + "/remotelink")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        ensure2xx(resp);
    }
}
