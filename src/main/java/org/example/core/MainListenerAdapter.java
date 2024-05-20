package org.example.core;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.example.core.constants.TextChannelsID;
import org.example.core.controllers.SecurityController;
import org.example.core.controllers.RolesController;
import org.example.data.source.ApiService;
import org.example.data.source.db.DbOperations;
import org.example.domain.UseCase;
import org.example.resources.StringRes;
import org.jetbrains.annotations.NotNull;

public class MainListenerAdapter extends ListenerAdapter {

    RolesController rolesController;
    SecurityController securityController;

    public void onReady(@NotNull ReadyEvent event) {

        ApiService apiService = ApiService.getInstance(event.getJDA());
        UseCase useCase = UseCase.getInstance(apiService, new DbOperations());

        rolesController = RolesController.getInstance(useCase);
        securityController = SecurityController.getInstance(useCase);
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        AudioChannel channelJoined = event.getChannelJoined();
        AudioChannel channelLeft = event.getChannelLeft();

        String channelJoinedName = channelJoined != null ? channelJoined.getName() : "";
        long channelJoinedId = channelJoined != null ? channelJoined.getIdLong() : -1;

        String channelLeftName = channelLeft != null ? channelLeft.getName() : "";
        long channelLeftId = channelLeft != null ? channelLeft.getIdLong() : -1;

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
            } else if (channelName.equals(StringRes.CHANNEL_TRIBUNE)) {
            }
        } else {
            //тут войс чат
        }
    }


}
