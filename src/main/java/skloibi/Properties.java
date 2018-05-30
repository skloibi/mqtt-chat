package skloibi;

public class Properties {
    public static final String SEPARATOR      = "/";
    public static final int    MQTT_PORT      = 1883;
    public static final String SERVER_COMMAND = "chatserver";
    public static final String TOPIC          = "mqtt-chat";
    public static final String TOPIC_ALL      = TOPIC + SEPARATOR + "all";
    public static final String TOPIC_USER     = TOPIC + SEPARATOR + "user" + SEPARATOR;
}
