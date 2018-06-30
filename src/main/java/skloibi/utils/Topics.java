package skloibi.utils;

/**
 * Utility to handle topics.
 * This provides methods to generate the required strings to maintain the chat
 * order and also append the required wildcards for open topic subscriptions.
 */
public class Topics {

    /**
     * Global topic prefix.
     */
    public static final String GLOBAL = "mqtt-chat";

    /**
     * Default chat channel.
     */
    public static final String ALL = "all";

    /**
     * A user's "personal" channel (e.g. ...user/username)
     */
    public static final String USER = "user";

    /**
     * Topic separator.
     */
    public static final String SEPARATOR = "/";

    /**
     * Topic descriptor to listen to all subtopics.
     */
    public static final String LISTEN_TO_ALL = String.format("%s/#", GLOBAL);

    /**
     * Get the user from a given topic.
     * As the topic conventions put the username last,
     * this simply extracts this last part.
     *
     * @param topic The full topic name
     * @return the username that was embedded in the topic name
     */
    public static String userFromTopic(String topic) {
        var i = topic.lastIndexOf('/');
        return topic.substring(i + 1);
    }

    /**
     * Retrieves the short name / identifier of a given topic string.
     * As every topic in this system appends the username,
     * the current topic is the second-to-last segment in the topic string.
     *
     * @param topic The full topic name
     * @return the short name that was extracted from the topic
     */
    public static String shortName(String topic) {
        var words = topic.split(SEPARATOR);

        return words[words.length - 2];
    }

    /**
     * Returns the topic string that is required to listen to the corresponding
     * user's "personal" topic.
     *
     * @param user The username
     * @return the valid topic string
     */
    public static String listenToOwn(String user) {
        return String.format("%s/%s/%s/+", GLOBAL, USER, user);
    }

    /**
     * Returns the topic string that is required to listen to the topic
     * that is identified by the short name
     * (appends / prepends the corresponding absolute topic names and
     * wildcards).
     *
     * @param topic The topic short name
     * @return the valid topic string
     */
    public static String listenTo(String topic) {
        return String.format("%s/%s/+", GLOBAL, topic);
    }

    /**
     * Returns the topic string that allows publishing to the selected
     * topic by the given user.
     *
     * @param topic The topic short name
     * @param user  The username
     * @return the valid topic string
     */
    public static String publishTo(String topic, String user) {
        return String.format("%s/%s/%s", GLOBAL, topic, user);
    }

    /**
     * Returns the topic string that allows publishing to the given user's
     * "personal" channel.
     *
     * @param userFrom The sender
     * @param userTo   The recipient
     * @return the valid topic string
     */
    public static String publishToPersonal(String userFrom, String userTo) {
        return String.format("%s/%s/%s/%s", GLOBAL, USER, userTo, userFrom);
    }
}
