package skloibi.props;

import io.reactivex.Maybe;
import skloibi.utils.T;

/**
 * Utilities and constants for messages and commands.
 */
public class Messages {

    /**
     * Notification when user has left a channel.
     */
    public static final String LEFT = ":left";

    /**
     * Notification when user enters a channel.
     */
    public static final String JOINED = ":joined";

    /**
     * Response for invalid commands.
     */
    public static final String INVALID = ":invalid command";

    /**
     * Prefix for declared commands.
     */
    public static final String COMMAND_PREFIX = ":";

    /**
     * Checks whether the given message is in fact a command.
     *
     * @param msg The message
     * @return {@code true} if the message is a command (starts with the
     * corresponding prefix); {@code false} otherwise
     */
    public static boolean isCommand(String msg) {
        return msg.startsWith(COMMAND_PREFIX);
    }

    /**
     * Retrieves the optional command (and its parameters) from the given
     * message.
     *
     * @param msg The message
     * @return an optional pairing of a command and its parameters
     */
    public static Maybe<T._2<Command, String>> getCommand(String msg) {
        // Wrap the whole message in a Maybe.
        // A Maybe here allows to simply pass on erroneous commands
        // and treat them in the onError handler.
        return Maybe.just(msg)
                // verify that message is command
                .filter(Messages::isCommand)
                // if so, remove the prefix
                .map(m -> m.substring(1))
                // remove any leading / trailing whitespaces
                .map(String::trim)
                // filter empty commands
                .filter(m -> !m.isEmpty())
                // split into command and parameter string
                .map(m -> m.split(" ", 2))
                // map to pair and retrieve actual command
                .map(ms -> T.of(Command.valueOf(ms[0].toUpperCase()), ms[1]));
    }
}
