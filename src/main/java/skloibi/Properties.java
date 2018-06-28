package skloibi;

import io.netty.handler.codec.mqtt.MqttQoS;

public class Properties {
    public static final String DB_URL  = "jdbc:postgresql://localhost:5432/logdb";
    public static final String DB_USER = "logbot";
    public static final String DB_PASS = "logpass";

    public static final String  BROKER      = "localhost";
    public static final int     MQTT_PORT   = 1883;
    public static final MqttQoS QOS_MESSAGE = MqttQoS.AT_MOST_ONCE;
    public static final MqttQoS QOS_SYSTEM  = MqttQoS.EXACTLY_ONCE;

    public static final String SERVER_COMMAND = "chatserver";

    public static final String COMMAND_PREFIX  = ":";
    public static final String COMMAND_BOT     = "bot";
    public static final String COMMAND_SWITCH  = "goto";
    public static final String COMMAND_PRIVATE = "to";

    public static final String SEPARATOR  = "/";
    public static final String TOPIC      = "mqtt-chat";
    public static final String TOPIC_BOT  = TOPIC + SEPARATOR + "bot";
    public static final String TOPIC_ALL  = "all";
    public static final String TOPIC_USER = TOPIC + SEPARATOR + "user" + SEPARATOR;
}
