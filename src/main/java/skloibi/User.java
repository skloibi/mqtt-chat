package skloibi;

/**
 * Model of the logged in user.
 */
public class User {

    /**
     * The username.
     */
    private final String name;

    /**
     * The currently subscribed channel.
     */
    private String channel;

    public User(String name, String channel) {
        this.name = name;
        this.channel = channel;
    }

    public String getName() {
        return name;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
