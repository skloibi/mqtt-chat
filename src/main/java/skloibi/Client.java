package skloibi;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.mqtt.MqttClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public class Client {

    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    public static void main(String[] args) {
        final String         username = "TESTY";
        final BufferedReader reader   = new BufferedReader(new InputStreamReader(System.in));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        Vertx vertx = Vertx.vertx();

        MqttClientOptions opts = new MqttClientOptions()
                .setClientId(username)
                .setAutoKeepAlive(true);

        MqttClient client = MqttClient.create(vertx, opts);

        client.subscribeCompletionHandler(___ -> {
            System.out.println("Subscribed!");
            Observable.create(sub -> {
                final String in = reader.readLine();

                if (in == null || in.equals("exit"))
                    sub.onComplete();
                else if (!in.isEmpty())
                    sub.onNext(in);
            })
                    .subscribeOn(Schedulers.io())
                    .map(msg -> username + ": " + msg)
                    .map(Buffer::buffer)
                    .subscribe(b -> {
                                LOGGER.info("MESSAGE: " + b);
                                client.publish(
                                        Properties.TOPIC_ALL,
                                        b,
                                        MqttQoS.AT_MOST_ONCE,
                                        false,
                                        false);
                            },
                            e -> {
                                LOGGER.severe(e.getMessage());
                                e.printStackTrace();
                            },
                            () -> LOGGER.info("Closing connection"));
        });

        client.connect(1883, "localhost", ch ->
                client.publishHandler(msg ->
                        System.out.println("[" + formatter.format(LocalDateTime.now()) + "]" + msg.payload()))
                        .subscribe(Properties.TOPIC_USER + username, 2)
                        .subscribe(Properties.TOPIC_ALL, 2)
        );
    }
}
