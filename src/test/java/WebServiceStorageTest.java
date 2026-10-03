import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebServiceStorageTest {

    @Test
    void shouldReportExpectedMetadata() {
        WebServiceStorage storage = new WebServiceStorage("dummy-key");

        assertThat(storage.getName()).isEqualTo("WebService");
        assertThat(storage.isReadOnly()).isTrue();
        assertThat(storage.getExpiration()).isEmpty();
    }

    @Test
    void shouldRejectWritesSinceItIsReadOnly() {
        WebServiceStorage storage = new WebServiceStorage("dummy-key");
        ExchangeRateList dummy = new ExchangeRateList("d", "l", 0L, "USD", Map.of());

        assertThatThrownBy(() -> storage.write(dummy))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void shouldFailWhenAppIdIsMissing() {
        WebServiceStorage storage = new WebServiceStorage("");

        assertThatThrownBy(storage::read)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("App ID is missing");
    }

    @Test
    void shouldResolveAppIdFromSystemProperty() {
        String propKey = "openexchangerates.app_id";
        System.setProperty(propKey, "sys-prop-app-id");
        try {
            String resolved = WebServiceStorage.resolveAppId(null);
            assertThat(resolved).isEqualTo("sys-prop-app-id");
        } finally {
            System.clearProperty(propKey);
        }
    }

    @Test
    void shouldPreferExplicitAppIdOverSystemProperty() {
        String propKey = "openexchangerates.app_id";
        System.setProperty(propKey, "sys-prop-app-id");
        try {
            String resolved = WebServiceStorage.resolveAppId("explicit-app-id");
            assertThat(resolved).isEqualTo("explicit-app-id");
        } finally {
            System.clearProperty(propKey);
        }
    }
}
