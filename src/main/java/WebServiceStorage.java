import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;


public class WebServiceStorage implements Storage<ExchangeRateList> {

    private final String name = "WebService";
    private final String appId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebServiceStorage() {
        this(resolveAppId(null));
    }

    public WebServiceStorage(String appId) {
        this.appId = resolveAppId(appId);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static String resolveAppId(String explicitAppId) {
        if (explicitAppId != null && !explicitAppId.isBlank()) {
            return explicitAppId.trim();
        }

        String envKey = System.getenv("OPEN_EXCHANGE_RATES_APP_ID");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }

        String propKey = System.getProperty("openexchangerates.app_id");
        if (propKey != null && !propKey.isBlank()) {
            return propKey.trim();
        }
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Optional<Duration> getExpiration() {
        return Optional.empty();
    }

    @Override
    public void write(ExchangeRateList value) {
        throw new UnsupportedOperationException("WebService storage is read-only");
    }

    @Override
    public Optional<ExchangeRateList> read() throws Exception {
        if (appId == null || appId.isBlank()) {
            throw new IllegalStateException("Open Exchange Rates App ID is missing. " +
                    "Configure it via OPEN_EXCHANGE_RATES_APP_ID environment variable or pass it as an argument.");
        }

        String url = "https://openexchangerates.org/api/latest.json?app_id=" + appId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Open Exchange Rates API returned HTTP "
                    + response.statusCode() + ": " + response.body());
        }

        ExchangeRateList data = objectMapper.readValue(response.body(), ExchangeRateList.class);

        return Optional.of(data);
    }
}
