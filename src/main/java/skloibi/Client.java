package skloibi;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.reactivex.Observable;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.mqtt.MqttClient;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.Logger;

public class Client {

    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    public static void main(String[] args) {
        final String  username = "TESTY";
        final Scanner scanner  = new Scanner(System.in);

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_TIME;

        Vertx vertx = Vertx.vertx();

        MqttClientOptions opts = new MqttClientOptions()
                .setClientId(username);

        MqttClient client = MqttClient.create(vertx, opts);

        client.connect(1883, "localhost", __ -> {
            client.publishHandler(msg ->
                    System.out.println("[" + formatter.format(Instant.now()) + "]" + msg.payload()))
                    .subscribe(Properties.TOPIC_USER + username + "/#", 2)
                    .subscribe(Properties.TOPIC_ALL, 2);

            Observable.<String>defer(() -> sub -> {
                try {
                    final String in = scanner.nextLine();

                    if (in.equals("exit"))
                        sub.onComplete();
                    else
                        sub.onNext(in);
                } catch (NoSuchElementException e) {

                }
            })
                    .map(Buffer::buffer)
                    .blockingSubscribe(b ->
                                    client.publish(
                                            Properties.TOPIC_ALL,
                                            b,
                                            MqttQoS.EXACTLY_ONCE,
                                            false,
                                            false),
                            e -> {
                                LOGGER.severe(e.getMessage());
                                e.printStackTrace();
                            },
                            () -> LOGGER.info("Closing connection"));
        });
    }
}
