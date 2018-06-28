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
import skloibi.utils.T;
import skloibi.utils.TopicParser;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static skloibi.Properties.*;

public class LogBot extends AbstractVerticle {
    private static final Logger logger = Logger.getLogger(LogBot.class.getName());

    private static final String NAME          = LogBot.class.getSimpleName();
    private static final String PUBLISH_TOPIC = TOPIC + SEPARATOR + TOPIC_ALL + SEPARATOR + NAME;
    private static final long   INTERVAL      = 10;

    private static ConnectionProvider connectionProvider = new ConnectionProviderFromUrl(
            DB_URL,
            DB_USER,
            DB_PASS
    );

    @Override
    public void start(Future<Void> startFuture) {

        var db = Database.from(connectionProvider);

        var opts = new MqttClientOptions()
                .setClientId(LogBot.class.getSimpleName())
                .setAutoKeepAlive(true);

        var client = MqttClient.create(vertx, opts);

        client.subscribeCompletionHandler(ack ->
                client.publish(
                        PUBLISH_TOPIC,
                        Buffer.buffer("LogBot available"),
                        QOS_SYSTEM,
                        false,
                        false)
        );

        client.connect(Properties.MQTT_PORT, Properties.BROKER, ch -> {
            client
                    .subscribe(TOPIC + "/#", 2)
                    .publishHandler(msg -> {
                        var user = TopicParser.userFromTopic(msg.topicName());
                        db
                                .update(String.format(
                                        "INSERT INTO messages VALUES ('%s', '%s', '%s')",
                                        user,
                                        Instant.now(),
                                        msg.payload()))
                                .count()
                                .subscribe(
                                        __ -> logger.info("message saved"),
                                        e -> logger.log(Level.SEVERE, "logging failed", e)
                                );
                    });

            Observable
                    .interval(INTERVAL, TimeUnit.SECONDS)
                    .subscribe(__ -> db
                            .select("SELECT username, date :: text, message FROM messages")
                            .autoMap(MessageLog.class)
                            .map(MessageLog::getUsername)
                            .groupBy(m -> m)
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
