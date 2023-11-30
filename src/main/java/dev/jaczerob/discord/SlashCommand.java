package dev.jaczerob.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.lang.NonNull;

import java.util.List;

public abstract class SlashCommand extends Interaction<SlashCommandInteractionEvent> {
    public abstract @NonNull String getDescription();

    public @NonNull List<OptionData> getOptions() {
        return List.of();
    }

    public @NonNull List<SlashCommand> getSubCommands() {
        return List.of();
    }
}
