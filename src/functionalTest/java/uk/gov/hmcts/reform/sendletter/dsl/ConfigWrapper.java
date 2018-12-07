package uk.gov.hmcts.reform.sendletter.dsl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.function.Function;

import static com.typesafe.config.ConfigException.Missing;

class ConfigWrapper {

    private final Config config;

    ConfigWrapper(Config config) {
        this.config = config;
    }

    static ConfigWrapper load() {
        return new ConfigWrapper(ConfigFactory.load());
    }

    Boolean getBoolean(String path) {
        return nullWrapper(config::getBoolean, path);
    }

    Integer getInt(String path) {
        return nullWrapper(config::getInt, path);
    }

    String getString(String path) {
        return nullWrapper(config::getString, path);
    }

    private <T> T nullWrapper(Function<String, T> getter, String path) {
        try {
            return getter.apply(path);
        } catch (Missing missingException) {
            return null;
        }
    }
}
