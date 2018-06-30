package skloibi;

import com.github.davidmoten.rx.jdbc.ConnectionProvider;
import com.github.davidmoten.rx.jdbc.ConnectionProviderFromUrl;
import com.github.davidmoten.rx.jdbc.Database;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.mqtt.MqttClient;
import rx.Observable;
import skloibi.props.Properties;
import skloibi.utils.T;
import skloibi.utils.Topics;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static skloibi.props.Properties.*;

/**
 * Simple chat "bot" that logs all messages on every topic, saves them in a
 * database and periodically prints a summary of each user and his number of
 * published messages.
 */
public class LogBot extends AbstractVerticle {
    private static final Logger logger = Logger.getLogger(LogBot.class.getName());

    /**
     * The name of the bot.
     */
    private static final String NAME = LogBot.class.getSimpleName();

    /**
     * The topic to which the bot publishes his messages.
     */
    private static final String PUBLISH_TOPIC = Topics.publishTo(Topics.ALL, NAME);

    /**
     * Time period after which the message summary is logged.
     */
    private static final long INTERVAL = 10;

    /**
     * Initiate connection.
     */
    private static ConnectionProvider connectionProvider = new ConnectionProviderFromUrl(
            DB_URL,
            DB_USER,
            DB_PASS
    );

    @Override
    public void start(Future<Void> startFuture) {

        // the database manager that is used throughout the workflow
        var db = Database.from(connectionProvider);

        // simply set the client ID and other options (not mandatory)
        var opts = new MqttClientOptions()
                .setClientId(LogBot.class.getSimpleName())
                .setAutoKeepAlive(true);

        var client = MqttClient.create(vertx, opts);

        // attempt to connect
        client.connect(Properties.MQTT_PORT, Properties.BROKER, ch -> {
            // this handler is called upon successful connection
            // (and after every successful retry)
            client
                    // subscribe to "all" topics (all under the main topic)
                    .subscribe(Topics.LISTEN_TO_ALL, Properties.QOS_SYSTEM.value())
                    // callback when subscription is complete
                    .subscribeCompletionHandler(__ -> {
                        logger.info("now listening");
                        // send notification to users
                        client.publish(
                                PUBLISH_TOPIC,
                                Buffer.buffer("LogBot available"),
                                QOS_SYSTEM,
                                false,
                                false);
                    })
                    .publishHandler(msg -> {
                        logger.info("recevied");
                        // retrieve the user from the topic
                        var user = Topics.userFromTopic(msg.topicName());
                        db
                                .update(String.format(
                                        "INSERT INTO messages VALUES ('%s', '%s', '%s')",
                                        user,
                                        Instant.now(),
                                        msg.payload()))
                                // as I want to track when the insert is complete
                                // and "execute" only returns an exit code,
                                // I use count which returns an Observable
                                .count()
                                // this is basically a Single, therefore a
                                // onComplete observer does not make much sense
                                .subscribe(
                                        __ -> logger.info("message saved"),
                                        e -> logger.log(Level.SEVERE, "logging failed", e)
                                );
                    });

            // periodically run a database query
            Observable
                    .interval(INTERVAL, TimeUnit.SECONDS)
                    .subscribe(__ -> db
                            // The cast here is necessary as the auto-mapping is restricted to
                            // simple types and java.sql.* types.
                            // Since only the username is used anyway, I simply take the string representation
                            // of the timestamp to prevent errors.
                            .select("SELECT username, date :: text, message FROM messages")
                            // map to target class
                            .autoMap(MessageLog.class)
                            .map(MessageLog::getUsername)
                            .groupBy(m -> m)
                            // basically a "GROUP BY username" with a "COUNT(*)" column
                            .flatMap(group ->
                                    group.count()
                                            .map(count -> T.of(group.getKey(), count)))
                            .subscribe(
                                    p -> System.out.println(p._1 + ": " + p._2),
                                    e -> logger.log(Level.SEVERE, "highscore query failed", e)
                            )
                    );

            startFuture.complete();
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        super.stop(stopFuture);
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(LogBot.class.getCanonicalName());
    }
}
