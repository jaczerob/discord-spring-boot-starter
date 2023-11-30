package dev.jaczerob.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

@Configuration
@EnableConfigurationProperties(DiscordProperties.class)
@ConditionalOnClass(JDA.class)
public class DiscordAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DiscordAutoConfiguration.class);

    private final DiscordProperties properties;

    public DiscordAutoConfiguration(final DiscordProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public JDA jda(
            @Value("${spring.threads.virtual.enabled:false}") final boolean virtualThreadsEnabled,
            final @NonNull List<ListenerAdapter> listenerAdapters,
            final @NonNull List<SlashCommand> slashCommands,
            final @NonNull List<MessageContextCommand> messageContextCommands,
            final @NonNull List<UserContextCommand> userContextCommands
    ) throws InterruptedException {
        final JDABuilder builder = JDABuilder.create(this.properties.getToken(), this.properties.getIntents());

        for (final ListenerAdapter listenerAdapter : listenerAdapters) {
            builder.addEventListeners(listenerAdapter);
            log.info("Registered listener: {}", listenerAdapter.getClass().getSimpleName());
        }

        if (virtualThreadsEnabled) {
            log.info("Virtual threads enabled, creating virtual thread pool for JDA.");

            final ThreadFactory factory = Thread.ofVirtual().factory();
            final int cores = Runtime.getRuntime().availableProcessors();
            final ScheduledExecutorService virtualThreadExecutorService = Executors.newScheduledThreadPool(cores, factory);

            builder.setEventPool(virtualThreadExecutorService)
                    .setGatewayPool(virtualThreadExecutorService)
                    .setCallbackPool(virtualThreadExecutorService)
                    .setRateLimitScheduler(virtualThreadExecutorService);
        }

        final JDA jda = builder.build().awaitReady();
        log.info("Logged into Discord as {}", jda.getSelfUser().getName());

        final Guild guild = jda.getGuildById(this.properties.getGuildId());
        if (guild == null)
            throw new IllegalStateException("Guild with ID " + this.properties.getGuildId() + " not found.");

        final List<CommandData> commandData = new ArrayList<>();
        commandData.addAll(this.getSlashCommandData(slashCommands));
        commandData.addAll(this.getMessageContextCommandData(messageContextCommands));
        commandData.addAll(this.getUserContextCommandData(userContextCommands));
        guild.updateCommands().addCommands(commandData).queue();

        final InteractionRegistry<SlashCommandInteractionEvent, SlashCommand> slashCommandRegistry = new InteractionRegistry<>();
        for (final SlashCommand slashCommand : slashCommands)
            slashCommandRegistry.register(slashCommand);

        final InteractionRegistry<MessageContextInteractionEvent, MessageContextCommand> messageContextCommandRegistry = new InteractionRegistry<>();
        for (final MessageContextCommand messageContextCommand : messageContextCommands)
            messageContextCommandRegistry.register(messageContextCommand);

        final InteractionRegistry<UserContextInteractionEvent, UserContextCommand> userContextCommandRegistry = new InteractionRegistry<>();
        for (final UserContextCommand userContextCommand : userContextCommands)
            userContextCommandRegistry.register(userContextCommand);

        final InteractionEventManager interactionEventManager = new InteractionEventManager(
                slashCommandRegistry,
                messageContextCommandRegistry,
                userContextCommandRegistry
        );

        jda.addEventListener(interactionEventManager);
        return jda;
    }

    private @NonNull List<CommandData> getUserContextCommandData(
            final @NonNull List<UserContextCommand> userContextCommands
    ) {
        final List<CommandData> userContextCommandData = new ArrayList<>();

        for (final UserContextCommand userContextCommand : userContextCommands) {
            final CommandData data = Commands.context(Command.Type.USER, userContextCommand.getName());
            userContextCommandData.add(data);
            log.info("Registered user context command: {}", userContextCommand.getName());
        }

        return userContextCommandData;
    }

    private @NonNull List<CommandData> getMessageContextCommandData(
            @NonNull final List<MessageContextCommand> messageContextCommands
    ) {
        final List<CommandData> messageContextCommandData = new ArrayList<>();

        for (final MessageContextCommand messageContextCommand : messageContextCommands) {
            final CommandData data = Commands.context(Command.Type.MESSAGE, messageContextCommand.getName());
            messageContextCommandData.add(data);
            log.info("Registered message context command: {}", messageContextCommand.getName());
        }

        return messageContextCommandData;
    }

    private @NonNull List<CommandData> getSlashCommandData(
            final @NonNull List<SlashCommand> slashCommands
    ) {
        final List<CommandData> slashCommandData = new ArrayList<>();

        for (final SlashCommand slashCommand : slashCommands) {
            final SlashCommandData data = Commands.slash(slashCommand.getName(), slashCommand.getDescription());
            data.addOptions(slashCommand.getOptions());

            final List<SlashCommand> commandSubCommands = slashCommand.getSubCommands();
            if (!commandSubCommands.isEmpty()) {
                final List<SubcommandData> subCommands = new ArrayList<>();
                for (final SlashCommand subCommand : commandSubCommands) {
                    final SubcommandData subCommandData = new SubcommandData(subCommand.getName(), subCommand.getDescription());
                    subCommandData.addOptions(subCommand.getOptions());
                    subCommands.add(subCommandData);
                    log.info("Registered subcommand {} for slash command {}", subCommand.getName(), slashCommand.getName());
                }

                data.addSubcommands(subCommands);
            }

            log.info("Registered slash command: {}", slashCommand.getName());
            slashCommandData.add(data);
        }

        return slashCommandData;
    }
}
