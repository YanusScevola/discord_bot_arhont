package org.example.core.models;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.List;

public class CreatedRoomData {
    private User userCreator;
    private User userOwner;
    private VoiceChannel voiceChannel;
    private List<String> history;
    private Message controlPanelMessage;
    private String lastAction;

    public CreatedRoomData(User userCreator, User userOwner, VoiceChannel voiceChannel, List<String> history) {
        this.userCreator = userCreator;
        this.userOwner = userOwner;
        this.voiceChannel = voiceChannel;
        this.history = history;
    }

    public User getUserCreator() {
        return userCreator;
    }

    public void setUserCreator(User userCreator) {
        this.userCreator = userCreator;
    }

    public User getUserOwner() {
        return userOwner;
    }

    public void setUserOwner(User userOwner) {
        this.userOwner = userOwner;
    }

    public VoiceChannel getVoiceChannel() {
        return voiceChannel;
    }

    public void setVoiceChannel(VoiceChannel voiceChannel) {
        this.voiceChannel = voiceChannel;
    }

    public List<String> getHistory() {
        return history;
    }

    public void addInHistory(String message) {
        if (history != null) history.add(message);
    }

    public Message getControlPanelMessage() {
        return controlPanelMessage;
    }

    public void setControlPanelMessage(Message controlPanelMessage) {
        this.controlPanelMessage = controlPanelMessage;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }
}
