package uk.gov.hmcts.reform.sendletter.dsl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

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
        String value = nullWrapper(path);

        return value == null ? null : Boolean.valueOf(value);
    }

    Integer getInt(String path) {
        String value = nullWrapper(path);

        return value == null ? null : Integer.valueOf(value);
    }

    String getString(String path) {
        return nullWrapper(path);
    }

    private String nullWrapper(String path) {
        try {
            return config.getString(path);
        } catch (Missing missingException) {
            return null;
        }
    }
}
