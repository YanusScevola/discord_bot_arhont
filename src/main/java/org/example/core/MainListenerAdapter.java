package org.example.core;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.example.core.constants.TextChannelsID;
import org.example.core.controllers.RoomCreatorController;
import org.example.core.controllers.SecurityController;
import org.example.core.controllers.RolesController;
import org.example.data.source.ApiService;
import org.example.data.source.db.DbOperations;
import org.example.domain.UseCase;
import org.jetbrains.annotations.NotNull;

public class MainListenerAdapter extends ListenerAdapter {

    RolesController rolesController;
    SecurityController securityController;
    RoomCreatorController roomCreatorController;

    public void onReady(@NotNull ReadyEvent event) {

        ApiService apiService = ApiService.getInstance(event.getJDA());
        UseCase useCase = UseCase.getInstance(apiService, new DbOperations());

        rolesController = RolesController.getInstance(useCase);
        securityController = SecurityController.getInstance(useCase);
        roomCreatorController = RoomCreatorController.getInstance(useCase);
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        AudioChannel channelJoined = event.getChannelJoined();
        AudioChannel channelLeft = event.getChannelLeft();

        if (channelJoined != null) {
            roomCreatorController.onJoinVoiceChannel(event);
        }
        if (channelLeft != null) {
            roomCreatorController.onLeaveVoiceChannel(event);
        }
    }


    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        securityController.onMessageListener(event);
        if (event.getChannel().getType() == ChannelType.TEXT) {
            long channelId = event.getChannel().asTextChannel().getIdLong();

            if (channelId == TextChannelsID.ROLES) {

            }

        }

    }

    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String channelName = event.getChannel().getName();
        if (event.getChannelType().equals(ChannelType.TEXT)) {
            long channelId = event.getChannel().asTextChannel().getIdLong();
            if (channelId == TextChannelsID.ROLES) {
                rolesController.onButtonListener(event);
            }
        } else {
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        roomCreatorController.onClickSelectorItem(event);
    }
}
