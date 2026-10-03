import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeRateList (
        @JsonProperty("disclaimer") String disclaimer,
        @JsonProperty("license") String license,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("base") String base,
        @JsonProperty("rates") Map<String, BigDecimal> rates
) {
    @JsonIgnore
    public Optional<BigDecimal> getRate(String currency) {
        if (currency == null || rates == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rates.get(currency.toUpperCase()));
    }
}
