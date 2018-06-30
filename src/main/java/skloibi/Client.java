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
import skloibi.props.Messages;
import skloibi.props.Properties;
import skloibi.utils.Topics;

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

    /**
     * Utility to publish MQTT messages with predefined options.
     *
     * @param client   The client that manages the connection
     * @param channel  The channel name to publish the message into
     *                 (is converted to corresponding topic)
     * @param username The user that sent the message
     * @param message  The actual message
     * @param qos      The MQTT Quality-of-Service to use
     * @return the client instance
     */
    private MqttClient publish(MqttClient client, String channel, String username, String message, MqttQoS qos) {
        return client.publish(
                Topics.publishTo(channel, username),
                Buffer.buffer(message),
                qos,
                false,
                false
        );
    }

    /**
     * Function that handles user input.
     * This is not a pure function as some commands may alter the corresponding
     * user object.
     *
     * @param client  The client that manages the connection
     * @param user    The current user
     * @param message The input message
     * @return the client instance
     */
    private MqttClient handleInput(MqttClient client, User user, String message) {
        // retrieve optional command
        Messages.getCommand(message)
                .subscribe(
                        // value was emitted => command found
                        cmd -> {
                            switch (cmd._1) {
                                case GOTO:
                                    var target = cmd._2.split(" ", 2)[0];
                                    changeChannel(client, user, target);
                                    break;
                                case TO:
                                    var params = cmd._2.split(" ", 2);
                                    if (params.length < 2)
                                        break;
                                    var targetUser = params[0];
                                    var msg = params[1];
                                    client.publish(
                                            Topics.publishToPersonal(user.getName(), targetUser),
                                            Buffer.buffer(msg),
                                            Properties.QOS_MESSAGE,
                                            false,
                                            false
                                    );
                                default:
                                    break;
                            }
                        },
                        // error => invalid command
                        e -> System.out.println(Messages.INVALID),
                        // complete / no value => common message
                        () -> publish(client, user.getChannel(), user.getName(), message, Properties.QOS_MESSAGE)
                );

        return client;
    }

    /**
     * Utility that changes the given user's main channel to the corresponding
     * target.
     *
     * @param client The MQTT client that manages the connection
     * @param user   The corresponding user
     * @param next   The target topic
     * @return the client instance
     */
    private MqttClient changeChannel(MqttClient client, User user, String next) {
        // usage of optional to check if user already has a channel
        Optional
                .ofNullable(user.getChannel())
                .ifPresent(channel -> {
                    // if user is already in channel, unsubscribe to it before
                    // subscribing to the new topic
                    client.unsubscribe(Topics.listenTo(user.getChannel()));
                    // publish a goodbye message to the previous channel
                    publish(
                            client,
                            user.getChannel(),
                            user.getName(),
                            Messages.LEFT,
                            Properties.QOS_SYSTEM
                    );
                });

        // subscribe to new topic
        return client.subscribe(
                Topics.listenTo(next),
                Properties.QOS_MESSAGE.value(),
                sub -> sub
                        // on success of subscription
                        .map(s -> {
                            // set the new channel
                            user.setChannel(next);
                            // send notification about new user to channel
                            publish(
                                    client,
                                    next,
                                    user.getName(),
                                    Messages.JOINED,
                                    Properties.QOS_SYSTEM
                            );
                            return s;
                        })
                        // error handler if subscription fails
                        .otherwise(e -> {
                            logger.log(Level.SEVERE, "Could not switch to channel '" + next + "'", e);
                            return sub.result();
                        })
        );
    }

    @Override
    public void start(Future<Void> startFuture) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        // observable that "blocks" / does not emit values until
        // the user enters his name
        Observable
                .<String>create(sub -> {
                    System.out.println("LOG IN: ");
                    final var username = reader.readLine();

                    // if a username is found, emit it
                    if (!username.isEmpty())
                        sub.onNext(username);

                    // afterwards observable is finished
                    sub.onComplete();
                })
                // map to user object an initialize current topic with null
                // (is set when the channel is entered)
                .map(username -> new User(username, null))
                // use IO scheduler to allow blocking operation
                .subscribeOn(Schedulers.io())
                .subscribe(user -> {

                    var opts = new MqttClientOptions()
                            .setClientId(user.getName())
                            .setAutoKeepAlive(true);

                    var client = MqttClient.create(vertx, opts);

                    // This generator usage basically blocks until user
                    // input and then passes it on.
                    // If the user enters "exit", the observable is completed
                    Observable
                            .<String>generate(gen -> {
                                final String in = reader.readLine();

                                if (in == null || in.equals("exit"))
                                    gen.onComplete();
                                else if (!in.isEmpty())
                                    gen.onNext(in);
                            })
                            // again IO scheduler for blocking operation
                            .subscribeOn(Schedulers.io())
                            .subscribe(
                                    msg -> handleInput(client, user, msg),
                                    e -> logger.log(Level.SEVERE, "Subscription error", e),
                                    () -> {
                                        publish(
                                                client,
                                                user.getChannel(),
                                                user.getName(),
                                                Messages.LEFT,
                                                Properties.QOS_SYSTEM
                                        );
                                        logger.info("Closing connection");
                                    });

                    // attempt connect
                    client.connect(Properties.MQTT_PORT, Properties.BROKER, ch -> {
                        // set publish callback (for each message in a subscribed topic)
                        client
                                // subscribe to personal profiles
                                .subscribe(Topics.listenToOwn(user.getName()), Properties.QOS_MESSAGE.value());
                        // subscribe to global channel
                        // here I use the helper function that also signals
                        // a user's presence ("previous" topic here of course is null)
                        changeChannel(client, user, Topics.ALL)
                                .subscribeCompletionHandler(__ ->
                                        client.publishHandler(msg -> System.out.printf(
                                                "[%s] /%-5s %s: %s\n",
                                                formatter.format(LocalDateTime.now()),
                                                Topics.shortName(msg.topicName()),
                                                Topics.userFromTopic(msg.topicName()),
                                                msg.payload())
                                        )
                                );

                        startFuture.complete();
                    });
                });
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(Client.class.getCanonicalName());
    }
}
