package dev.jaczerob.discord;

import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "discord")
public class DiscordProperties {
    private String token;
    private List<GatewayIntent> intents = new ArrayList<>();
    private Long guildId;

    public @NonNull String getToken() {
        return this.token;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    public @NonNull List<GatewayIntent> getIntents() {
        return this.intents;
    }

    public void setIntents(final List<GatewayIntent> intents) {
        this.intents = intents;
    }

    public @NonNull Long getGuildId() {
        return this.guildId;
    }

    public void setGuildId(final long guildId) {
        this.guildId = guildId;
    }
}
