package skloibi.props;

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * Holds application wide properties and constants.
 */
public class Properties {
    public static final String DB_URL  = "jdbc:postgresql://localhost:5432/logdb";
    public static final String DB_USER = "logbot";
    public static final String DB_PASS = "logpass";

    public static final String  BROKER      = "localhost";
    public static final int     MQTT_PORT   = 1883;
    public static final MqttQoS QOS_MESSAGE = MqttQoS.AT_MOST_ONCE;
    public static final MqttQoS QOS_SYSTEM  = MqttQoS.EXACTLY_ONCE;

    public static final String SERVER_COMMAND = "chatserver";
}
