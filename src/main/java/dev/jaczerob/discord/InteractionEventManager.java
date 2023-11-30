package dev.jaczerob.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class InteractionEventManager extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(InteractionEventManager.class);

    private final InteractionRegistry<SlashCommandInteractionEvent, SlashCommand> slashCommands;
    private final InteractionRegistry<MessageContextInteractionEvent, MessageContextCommand> messageContextCommands;
    private final InteractionRegistry<UserContextInteractionEvent, UserContextCommand> userContextCommands;

    public InteractionEventManager(
            final @NonNull InteractionRegistry<SlashCommandInteractionEvent, SlashCommand> slashCommands,
            final @NonNull InteractionRegistry<MessageContextInteractionEvent, MessageContextCommand> messageContextCommands,
            final @NonNull InteractionRegistry<UserContextInteractionEvent, UserContextCommand> userContextCommands
    ) {
        this.slashCommands = slashCommands;
        this.messageContextCommands = messageContextCommands;
        this.userContextCommands = userContextCommands;
    }

    @Override
    public void onGenericCommandInteraction(final @NonNull GenericCommandInteractionEvent event) {
        switch (event) {
            case SlashCommandInteractionEvent slashCommandInteractionEvent -> this.onSlashCommand(slashCommandInteractionEvent);
            case MessageContextInteractionEvent messageContextInteractionEvent -> this.onMessageContextCommand(messageContextInteractionEvent);
            case UserContextInteractionEvent userContextInteractionEvent -> this.onUserContextCommand(userContextInteractionEvent);
            default -> log.warn("Unknown interaction event type: {}", event.getClass().getName());
        }
    }

    private void onSlashCommand(final @NonNull SlashCommandInteractionEvent event) {
        final String commandName = event.getName();
        final Optional<SlashCommand> slashCommand = this.slashCommands.get(commandName);
        if (slashCommand.isEmpty()) {
            log.error("Unknown slash command: {}", commandName);
            return;
        }

        final SlashCommand command = slashCommand.get();
        final String subCommandName = event.getSubcommandName();
        if (subCommandName != null) {
            final Optional<SlashCommand> subCommand = command.getSubCommands().stream()
                    .filter(sub -> sub.getName().equals(subCommandName))
                    .findFirst();

            if (subCommand.isEmpty()) {
                log.error("Unknown subcommand: {}", subCommandName);
                return;
            }

            this.onInteraction(subCommand.get(), event);
        } else {
            this.onInteraction(command, event);
        }
    }

    private void onMessageContextCommand(final @NonNull MessageContextInteractionEvent event) {
        final String commandName = event.getName();
        this.messageContextCommands.get(commandName).ifPresentOrElse(
                messageContextCommand -> this.onInteraction(messageContextCommand, event),
                () -> log.warn("Unknown message context command: {}", commandName)
        );
    }

    private void onUserContextCommand(final @NonNull UserContextInteractionEvent event) {
        final String commandName = event.getName();
        this.userContextCommands.get(commandName).ifPresentOrElse(
                userContextCommand -> this.onInteraction(userContextCommand, event),
                () -> log.warn("Unknown user context command: {}", commandName)
        );
    }

    private @NonNull <R extends GenericCommandInteractionEvent, T extends Interaction<R>> void onInteraction(
            final @NonNull T interaction,
            final @NonNull R event
    ) {
        event.deferReply().queue();

        if (event.getGuild() == null || event.getChannel() == null || event.getMember() == null) {
            event.getHook().sendMessage("This command is not available in DMs.").setEphemeral(true).queue();
            return;
        }

        if (!this.passesChannelCheck(interaction, interaction.getRequiredChannels(), event.getChannel())) {
            event.getHook().sendMessage("This command is not available in this channel.").setEphemeral(true).queue();
            return;
        }

        final List<Role> requiredRoles = interaction.getRequiredRoles().stream()
                .flatMap(roleId -> Optional.ofNullable(event.getGuild().getRoleById(roleId)).stream())
                .toList();

        final Set<Role> memberRoles = new HashSet<>(event.getMember().getRoles());

        if (!this.passesRoleCheck(interaction, requiredRoles, memberRoles)) {
            event.getHook().sendMessage("You do not have the required role to use this command.").setEphemeral(true).queue();
            return;
        }

        if (!this.passesPermissionsCheck(interaction, interaction.getRequiredPermissions(), event.getMember())) {
            event.getHook().sendMessage("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        final MessageCreateData messageCreateData = interaction.execute(event);

        event.getHook().sendMessage(messageCreateData).setEphemeral(interaction.isEphemeral()).queue();
    }

    private @NonNull <T extends Interaction<?>> boolean passesChannelCheck(final @NonNull T interaction, final @NonNull List<Long> requiredChannels, final @NonNull ISnowflake channel) {
        if (interaction.getRequiredChannels().isEmpty())
            return true;

        return requiredChannels.contains(channel.getIdLong());
    }

    private@NonNull  <T extends Interaction<?>> boolean passesPermissionsCheck(final @NonNull T interaction, final @NonNull List<Permission> requiredPermissions, final @NonNull Member member) {
        if (interaction.getRequiredPermissions().isEmpty())
            return true;

        return member.hasPermission(requiredPermissions);
    }

    private @NonNull <T extends Interaction<?>> boolean passesRoleCheck(final @NonNull T interaction, final @NonNull List<Role> requiredRoles, final @NonNull Set<Role> memberRoles) {
        if (interaction.getRequiredRoles().isEmpty())
            return true;

        for (final Role role : requiredRoles)
            if (memberRoles.contains(role))
                return true;

        return false;
    }
}
