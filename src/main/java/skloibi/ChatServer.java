package skloibi;

import io.vertx.mqtt.MqttServerOptions;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.mqtt.MqttServer;
import org.apache.commons.cli.*;
import skloibi.utils.T;
import skloibi.utils.TFunction;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static skloibi.Properties.MQTT_PORT;
import static skloibi.Properties.SERVER_COMMAND;

public class ChatServer {

    private static final Logger LOGGER = Logger.getLogger(ChatServer.class.getName());

    public final  int        port;
    private final MqttServer instance;

    private ChatServer(int port, MqttServer instance) {
        this.port = port;
        this.instance = instance;
    }

    public ChatServer(int port) {
        this(port, null);
    }

    private static Optional<ChatServer> init(String[] args) throws ParseException {
        Option opt = Option.builder("p")
                .longOpt("port")
                .desc("The port for the server to listen to")
                .hasArg(true)
                .type(Number.class)
                .build();

        Options cliOptions = new Options()
                .addOption(opt)
                .addOption("h", "help", false, "Print command help")
                .addOption("v", "verbose", false, "Print detailed logging information");

        return Optional.of(new DefaultParser().parse(cliOptions, args))
                .flatMap(cmd -> {
                    if (cmd.hasOption('h')) {
                        new HelpFormatter().printHelp(SERVER_COMMAND, cliOptions);
                        return Optional.empty();
                    }

                    int port = Optional.of(cmd)
                            .filter(c -> c.hasOption('h'))
                            .map((TFunction<CommandLine, Object>) c ->
                                    (Number) c.getParsedOptionValue("h"))
                            .map(Number.class::cast)
                            .map(Number::intValue)
                            .orElse(MQTT_PORT);

                    Level level = Optional.of(cmd)
                            .filter(c -> c.hasOption('v'))
                            .map(__ -> Level.ALL)
                            .orElse(Level.INFO);

                    return Optional.of(T.of(port, level));
                })
                .map(p -> {
                    LOGGER.setLevel(p._2);
                    return p._1;
                })
                .map(ChatServer::new);
    }

    public static void main(String[] args) throws ParseException {
        ChatServer.init(args)
                .map(ChatServer::start);
    }

    public ChatServer start() {
        if (instance != null)
            return this;

        Vertx vertx = Vertx.vertx();

        MqttServerOptions options = new MqttServerOptions()
                .setPort(MQTT_PORT);

        MqttServer server = MqttServer.create(vertx, options)
                .endpointHandler(endpoint -> {
                    // shows main connect info
                    LOGGER.info("MQTT client [" + endpoint.clientIdentifier() + "] request to connect, clean session = " + endpoint.isCleanSession());

                    if (endpoint.auth() != null) {
                        LOGGER.info("[username = " + endpoint.auth().userName() + ", password = " + endpoint.auth().password() + "]");
                    }
                    if (endpoint.will() != null) {
                        LOGGER.info("[will topic = " + endpoint.will().willTopic() + " msg = " + endpoint.will().willMessage() +
                                " QoS = " + endpoint.will().willQos() + " isRetain = " + endpoint.will().isWillRetain() + "]");
                    }

                    LOGGER.info("[keep alive timeout = " + endpoint.keepAliveTimeSeconds() + "]");

                    // accept connection from the remote client
                    endpoint.accept(false);
                })
                .exceptionHandler(e -> {
                    LOGGER.info("[critical] " + e.getMessage());
                    e.printStackTrace();
                })
                .listen(result -> {
                    if (result.succeeded())
                        LOGGER.info("MQTT server is listening on port " + result.result().actualPort());
                    else {
                        LOGGER.info("Error on starting the server");
                        result.cause().printStackTrace();
                    }
                });

        return new ChatServer(port, server);
    }

    // TODO invoke
    public ChatServer stop() {
        this.instance.close(result -> LOGGER.info("Shutting down chat server"));
        return new ChatServer(port);
    }
}
