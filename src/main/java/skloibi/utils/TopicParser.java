package skloibi.utils;

import skloibi.Properties;

public class TopicParser {
    public static String userFromTopic(String topic) {
        var i = topic.lastIndexOf('/');
        return topic.substring(i + 1);
    }

    public static String toAbsoluteTopic(String topic) {
        return Properties.TOPIC + Properties.SEPARATOR + topic;
    }

    public static String getTargetTopic(String absTopic) {
        var words = absTopic.split(Properties.SEPARATOR);

        return words[words.length - 2];
    }
}
