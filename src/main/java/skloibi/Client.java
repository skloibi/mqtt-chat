package skloibi;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.mqtt.MqttClient;
import skloibi.utils.TopicParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client extends AbstractVerticle {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private String username;
    private String channel;

    private MqttClient publish(MqttClient client, String channel, String message, MqttQoS qos) {
        return client.publish(
                TopicParser.toAbsoluteTopic(channel) + Properties.SEPARATOR + username,
                Buffer.buffer(message),
                qos,
                false,
                false
        );
    }

    private MqttClient handleMessage(MqttClient client, String message) {
        if (message.isEmpty())
            return client;

        // check for commands
        if (message.startsWith(Properties.COMMAND_PREFIX)) {
            var space1 = message.indexOf(' ');
            var space2 = message.indexOf(' ', space1 + 1);
            space2 = space2 == -1 ? message.length() : space2;

            var cmd = message.substring(1, space1);

            switch (cmd) {
                case Properties.COMMAND_SWITCH:
                    var channel = message.substring(space1 + 1, space2);
                    changeChannel(client, channel);
                    break;
                case Properties.COMMAND_PRIVATE:
                    var user = message.substring(space1 + 1, space2);
                    var msg = message.substring(space2 + 1);
                    publish(client, "user" + Properties.SEPARATOR + user, msg, Properties.QOS_MESSAGE);
                default:
                    break;
            }

            return client;
        }

        publish(
                client,
                channel,
                message,
                Properties.QOS_MESSAGE
        );

        return client;
    }


    private MqttClient changeChannel(MqttClient client, String channel) {
        Optional.ofNullable(this.channel)
                .map(TopicParser::toAbsoluteTopic)
                .map(topic -> {
                    client.unsubscribe(topic + Properties.SEPARATOR + "+");
                    return topic;
                })
                .ifPresent(topic -> publish(
                        client,
                        topic,
                        "switched channel",
                        Properties.QOS_SYSTEM)
                );

        this.channel = channel;

        return client.subscribe(
                TopicParser.toAbsoluteTopic(channel) + "/+",
                Properties.QOS_MESSAGE.value(),
                sub -> sub
                        .map(__ -> {
                            publish(client, channel, "/entered channel", Properties.QOS_SYSTEM);
                            return __;
                        })
                        .otherwise(e -> {
                            logger.log(Level.SEVERE, "Could not switch to channel '" + channel + "'", e);
                            return sub.result();
                        }));
    }

    @Override
    public void start(Future<Void> startFuture) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        Observable
                .<String>create(sub -> {
                    System.out.println("LOG IN: ");
                    final var username = reader.readLine();
                    if (!username.isEmpty())
                        sub.onNext(username);
                    sub.onComplete();
                })
                .subscribeOn(Schedulers.io())
                .subscribe(user -> {
                    this.username = user;

                    var opts = new MqttClientOptions()
                            .setClientId(user)
                            .setAutoKeepAlive(true);

                    var client = MqttClient.create(vertx, opts);

                    Observable
                            .<String>generate(gen -> {
                                final String in = reader.readLine();

                                if (in == null || in.equals("exit"))
                                    gen.onComplete();
                                else if (!in.isEmpty())
                                    gen.onNext(in);
                            })
                            .subscribeOn(Schedulers.io())
                            .subscribe(
                                    msg -> handleMessage(client, msg),
                                    e -> logger.log(Level.SEVERE, "Subscription error", e),
                                    () -> {
                                        publish(
                                                client,
                                                channel,
                                                "/left",
                                                Properties.QOS_SYSTEM
                                        );
                                        logger.info("Closing connection");
                                    });

                    client.connect(Properties.MQTT_PORT, Properties.BROKER, ch -> {
                        client.publishHandler(msg -> System.out.printf(
                                "[%s] /%-5s %s: %s\n",
                                formatter.format(LocalDateTime.now()),
                                TopicParser.getTargetTopic(msg.topicName()),
                                TopicParser.userFromTopic(msg.topicName()),
                                msg.payload())
                        )
                                // subscribe to personal profiles
                                .subscribe(Properties.TOPIC_USER + user + "/+", Properties.QOS_MESSAGE.value());
                        // subscribe to global channel
                        changeChannel(client, Properties.TOPIC_ALL);

                        startFuture.complete();
                    });
                });
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(Client.class.getCanonicalName());
    }
}
