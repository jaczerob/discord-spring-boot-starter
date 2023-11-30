package dev.jaczerob.discord;

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InteractionRegistry<R extends GenericCommandInteractionEvent, T extends Interaction<R>> {
    private static final Logger log = LoggerFactory.getLogger(InteractionRegistry.class);

    private final Map<String, T> interactions = new ConcurrentHashMap<>();

    public void register(final @NonNull T interaction) {
        final String interactionName = interaction.getName();
        if (this.interactions.containsKey(interactionName)) {
            throw new IllegalArgumentException("Interaction with name " + interactionName + " already registered.");
        }

        this.interactions.put(interactionName, interaction);
        log.info("Registered interaction: {}", interactionName);
    }

    public @NonNull Optional<T> get(final @NonNull String interactionName) {
        return Optional.ofNullable(this.interactions.get(interactionName));
    }
}
