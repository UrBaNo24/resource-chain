import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        String appId = WebServiceStorage.resolveAppId(args.length > 0 ? args[0] : null);

        if (appId == null || appId.isBlank()) {
            System.err.println("Error: Open Exchange Rates app_id not provided.");
            System.err.println("Usage: pass app_id as argument or set OPEN_EXCHANGE_RATES_APP_ID env variable.");
            return;
        }

        Storage<ExchangeRateList> memory = new MemoryStorage<>();
        Storage<ExchangeRateList> fileSystem = new FileSystemStorage<>(Path.of("rates-cache.json"), ExchangeRateList.class);
        Storage<ExchangeRateList> webService = new WebServiceStorage(appId);

        ChainResource<ExchangeRateList> chain = new ChainResource<>(List.of(memory, fileSystem, webService));

        // 1. Cold start: hits web service and populates caches
        System.out.println("Fetching rates (cold cache)...");
        Instant t0 = Instant.now();
        ExchangeRateList rates = chain.getValue().join();
        long d1 = Duration.between(t0, Instant.now()).toMillis();

        System.out.printf("Retrieved in %d ms | Base: %s | EUR: %s | GBP: %s | JPY: %s%n",
                d1, rates.base(),
                rates.getRate("EUR").orElse(null),
                rates.getRate("GBP").orElse(null),
                rates.getRate("JPY").orElse(null));

        // 2. Second request: served from memory cache
        System.out.println("\nFetching rates again (in-memory cache)...");
        Instant t1 = Instant.now();
        rates = chain.getValue().join();
        long d2 = Duration.between(t1, Instant.now()).toMillis();

        System.out.printf("Retrieved in %d ms (memory hit)%n", d2);
    }
}