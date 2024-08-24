package org.example.core.controllers;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.example.core.constants.RolesID;
import org.example.core.constants.TextChannelsID;
import org.example.domain.UseCase;
import org.jetbrains.annotations.NotNull;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SecurityController {
    private static SecurityController instance;
    private TextChannel channel;
    private UseCase useCase;

    private SecurityController(UseCase useCase) {
        this.useCase = useCase;
        this.channel = useCase.getTextChannel(TextChannelsID.ROLES);
    }

    public static synchronized SecurityController getInstance(UseCase useCase) {
        if (instance == null) {
            instance = new SecurityController(useCase);
        }
        return instance;
    }

    public void onMessageListener(@NotNull MessageReceivedEvent event) {
        Message message = event.getMessage();
        String text = message.getContentRaw();
        boolean isOrganizer = Objects.requireNonNull(event.getMember()).getRoles().stream().map(ISnowflake::getIdLong).collect(Collectors.toList()).contains(RolesID.ORGANIZER);
        boolean isBot = event.getAuthor().isBot();

        if (!isOrganizer && !isBot) {
            handleForDiscordServerUrl(event, text);
        }

    }

    private void handleForDiscordServerUrl(MessageReceivedEvent event, String text) {
        long oneWeek = 60 * 24 * 7;
        if (event.getChannel() instanceof TextChannel) {
            if (isDiscordServerUrl(text)) {
                purgeMessages(event.getMember(), event.getChannel().asTextChannel());
                sendMessageToMember(Objects.requireNonNull(event.getMember()), "Публикова ссылки на другие сервера запрещены. Вам выдан тайм-аут на 1 неделю.");
                giveTimeout(oneWeek, event);
            }
        } else if (event.getChannel() instanceof VoiceChannel) {
            if (isDiscordServerUrl(text)) {
                purgeMessages(event.getMember(), event.getChannel().asVoiceChannel());
                sendMessageToMember(Objects.requireNonNull(event.getMember()), "Публикова ссылки на другие сервера запрещены. Вам выдан тайм-аут на 1 неделю.");
                giveTimeout(oneWeek, event);
            }
        }
    }

    private boolean isDiscordServerUrl(String text) {
        if (text.contains("discord.gg")) {
            return true;
        } else {
            return false;
        }
    }

    private void giveTimeout(long timeoutMinutes, MessageReceivedEvent event) {
        Message message = event.getMessage();
        Member member = message.getMember();
        TextChannel textChannel = event.getGuild().getTextChannelById(TextChannelsID.SECURITY);

        if (member != null) {
            TimeUnit timeoutUnit = TimeUnit.MINUTES;
            try {
                AuditableRestAction<Void> timeoutAction = member.timeoutFor(timeoutMinutes, timeoutUnit);


                timeoutAction.queue(
                        success -> {
                            if (textChannel != null) {
                                textChannel.sendMessage("Участнику <@" + member.getIdLong() + "> был дан тайм-аут на " + timeoutMinutes + " минут за \"Размещение рекламы другого сервера\".").queue();
                            }
                        },
                        error -> {
                            if (textChannel != null) {
                                textChannel.sendMessage("Не удалось дать тайм-аут <@" + member.getIdLong() + "> на " + timeoutMinutes + " минут.").queue();
                            }
                            error.printStackTrace();
                        }
                );
            } catch (IllegalArgumentException | InsufficientPermissionException e) {
                if (textChannel != null) {
                    textChannel.sendMessage("Не удалось дать тайм-аут <@" + member.getIdLong() + "> " + timeoutMinutes + " минут.").queue();
                }
                e.printStackTrace();
            }
        }
    }

    private void purgeMessages(Member member, TextChannel channel) {
        OffsetDateTime thirtySecondsAgo = OffsetDateTime.now().minusSeconds(30);
        ArrayList<Message> messagesToDelete = new ArrayList<>();

        channel.getIterableHistory().cache(false).forEachAsync(message -> {
            if (Objects.equals(message.getAuthor(), member.getUser()) && message.getTimeCreated().isAfter(thirtySecondsAgo)) {
                messagesToDelete.add(message);
            }

            return true;
        }).thenRun(() -> {
            if (!messagesToDelete.isEmpty()) {
                channel.purgeMessages(messagesToDelete);
            }
        });
    }

    private void purgeMessages(Member member, VoiceChannel channel) {
        OffsetDateTime thirtySecondsAgo = OffsetDateTime.now().minusSeconds(30);
        ArrayList<Message> messagesToDelete = new ArrayList<>();

        channel.getIterableHistory().cache(false).forEachAsync(message -> {
            if (Objects.equals(message.getAuthor(), member.getUser()) && message.getTimeCreated().isAfter(thirtySecondsAgo)) {
                messagesToDelete.add(message);
            }

            return true;
        }).thenRun(() -> {
            if (!messagesToDelete.isEmpty()) {
                channel.purgeMessages(messagesToDelete);
            }
        });
    }

    public void sendMessageToMember(Member member, String messageContent) {
        User user = member.getUser();

        // Открываем приватный канал с пользователем и отправляем сообщение
        user.openPrivateChannel().queue(privateChannel -> {
            privateChannel.sendMessage(messageContent).queue(
                    success -> {
                        // Сообщение успешно отправлено
                        System.out.println("Сообщение успешно отправлено пользователю " + user.getName());
                    },
                    error -> {
                        // Произошла ошибка при отправке сообщения
                        System.err.println("Не удалось отправить сообщение пользователю " + user.getName());
                        error.printStackTrace();
                    }
            );
        });
    }


}
