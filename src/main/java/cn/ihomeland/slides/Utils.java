package cn.ihomeland.slides;

import io.vertx.core.json.JsonObject;

class Utils {

    static String configString(JsonObject config, String key, String dft) {
        return config.getString(key, System.getProperty(key, dft));
    }

}
