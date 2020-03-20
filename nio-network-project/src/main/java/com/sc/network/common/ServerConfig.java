package com.sc.network.common;

import org.apache.commons.lang3.StringUtils;

import java.util.Properties;

public class ServerConfig extends Properties {

    private Properties props;

    public ServerConfig(Properties props) {
        this.props = props;
    }

    public String getAsString(String configKey) {
        return props.getProperty(configKey);
    }

    public String getAsString(String configKey,String defaultValue) {
        String configValue = getAsString(configKey);
        return StringUtils.isBlank(configKey) ? defaultValue : configValue;
    }

    public Integer getAsInt(String configKey) {
        try {
            return Integer.valueOf(props.getProperty(configKey));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Integer getAsInt(String configKey,Integer defaultValue) {
        try {
            String configValue = getAsString(configKey);
            return StringUtils.isBlank(configValue) ? defaultValue : Integer.valueOf(configValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
