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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client extends AbstractVerticle {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    private String username;
    private String channel;

    private String topic(String channel) {
        return Properties.TOPIC + Properties.SEPARATOR + channel;
    }

    private MqttClient publish(MqttClient client, String channel, String message, MqttQoS qos) {
        return client.publish(
                topic(channel) + Properties.SEPARATOR + username,
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
            var words = message.split("\\s");

            var cmd = words[0].substring(1);

            switch (cmd) {
                case Properties.COMMAND_SWITCH:
                    var channel = words[1];
                    changeChannel(client, channel);
                    break;
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

    private String usernameFromTopic(String topic) {
        var i = topic.lastIndexOf('/');
        return topic.substring(i + 1);
    }

    private MqttClient changeChannel(MqttClient client, String channel) {
        Optional.ofNullable(this.channel)
                .map(this::topic)
                .map(topic -> {
                    client.unsubscribe(topic);
                    return topic;
                })
                .ifPresent(topic -> publish(
                        client,
                        topic,
                        username + "switched channel",
                        Properties.QOS_SYSTEM)
                );

        this.channel = channel;

        return client.subscribe(topic(channel) + "/#", Properties.QOS_MESSAGE.value(), subscription ->
                subscription
                        .map(__ -> {
                            LOGGER.info("Switched to channel '" + channel + "'");
                            publish(client, channel, "entered channel", Properties.QOS_SYSTEM);
                            return __;
                        })
                        .otherwise(e -> {
                            LOGGER.log(Level.SEVERE, "Could not switch to channel '" + channel + "'", e);
                            return subscription.result();
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
                .subscribe(username -> {
                    this.username = username;

                    var opts = new MqttClientOptions()
                            .setClientId(username)
                            .setAutoKeepAlive(true);

                    var client = MqttClient.create(vertx, opts);

                    client.subscribeCompletionHandler(ack -> {
                        Observable
                                .<String>create(sub -> {
                                    final String in = reader.readLine();

                                    if (in == null || in.equals("exit"))
                                        sub.onComplete();
                                    else if (!in.isEmpty())
                                        sub.onNext(in);
                                })
                                .subscribeOn(Schedulers.io())
                                .subscribe(
                                        msg -> handleMessage(client, msg),
                                        e -> LOGGER.log(Level.SEVERE, "Subscription error", e),
                                        () -> {
                                            publish(
                                                    client,
                                                    channel,
                                                    "left",
                                                    Properties.QOS_SYSTEM
                                            );
                                            LOGGER.info("Closing connection");
                                        });
                    });

                    client.connect(1883, "localhost", ch -> {
                        client.publishHandler(msg -> System.out.printf(
                                "[%s] /%-20s %-20s %s\n",
                                formatter.format(LocalDateTime.now()),
                                msg.topicName(),
                                usernameFromTopic(msg.topicName()) + ":",
                                msg.payload())
                        )
                                // subscribe to personal profiles
                                .subscribe(Properties.TOPIC_USER + username, Properties.QOS_MESSAGE.value());
                        // subscribe to global channel
                        changeChannel(client, Properties.TOPIC_ALL);

                        startFuture.complete();
                    });
                });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        super.stop(stopFuture);
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(Client.class.getCanonicalName());
    }
}
