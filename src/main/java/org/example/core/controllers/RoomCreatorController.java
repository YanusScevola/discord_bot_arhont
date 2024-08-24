package org.example.core.controllers;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.example.core.constants.CategoriesID;
import org.example.core.models.CreatedRoomData;
import org.example.domain.UseCase;
import org.example.resources.Colors;
import org.example.resources.StringRes;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class RoomCreatorController {
    private static final String CONTROL_PANEL = "controlPanel";
    private static final String CHOSE_USER = "choseUser";
    private static final String MICRO = "micro";
    private static final String SOUND = "sound";
    private static final String KICK = "kick";
    private static final String BLOCK = "block";
    private static final String RENAME = "rename";
    private static final String LIMIT = "limit";
    private static final String TRANSFER_RIGHTS = "transferRights";
    private static final String FORGIVE = "forgive";

    private static RoomCreatorController instance;
    private UseCase useCase;
    private VoiceChannel roomCreatorVoiceChannel;
    private List<CreatedRoomData> createdRooms = new ArrayList<>();


    private RoomCreatorController(UseCase useCase) {
        this.useCase = useCase;

        useCase.isVoiceChannelExist(StringRes.CHANNEL_ROOM_CREATOR).thenAccept(isExist -> {
            if (isExist) {
                useCase.getVoiceChannel(StringRes.CHANNEL_ROOM_CREATOR).thenAccept(voiceChannel -> {
                    roomCreatorVoiceChannel = voiceChannel;
                });
            } else {
                useCase.createVoiceChannel(StringRes.CHANNEL_ROOM_CREATOR, CategoriesID.CONVERSATION).thenAccept(voiceChannel -> {
                    roomCreatorVoiceChannel = voiceChannel;
                });
            }
        });
    }

    public static synchronized RoomCreatorController getInstance(UseCase useCase) {
        if (instance == null) {
            instance = new RoomCreatorController(useCase);
        }
        return instance;
    }

    public void onJoinVoiceChannel(GuildVoiceUpdateEvent event) {
        AudioChannel audioChannel = event.getChannelJoined();
        if (audioChannel == null) return;

        VoiceChannel voiceChannel = (VoiceChannel) audioChannel;
        String channelName = voiceChannel.getName();

        if (channelName.equals(StringRes.CHANNEL_ROOM_CREATOR)) {
            Member currentMember = event.getMember();
            String currentMemberName = currentMember.getUser().getGlobalName();
            useCase.createVoiceChannel(currentMemberName, CategoriesID.CONVERSATION).thenAccept(createdVoiceChannel -> {
                CreatedRoomData createdRoomData = new CreatedRoomData(currentMember.getUser(), currentMember.getUser(), createdVoiceChannel, new ArrayList<>());
                createdRooms.add(createdRoomData);
                showControlPanelMessage(currentMember, createdVoiceChannel, createdRoomData);
                useCase.moveMembers(Collections.singletonList(currentMember), createdVoiceChannel);
            });
        }
    }

    public void onLeaveVoiceChannel(GuildVoiceUpdateEvent event) {
        AudioChannel audioChannel = event.getChannelLeft();
        if (audioChannel == null) return;

        Member currentMember = event.getMember();
        long currentMemberId = currentMember.getIdLong();

        VoiceChannel voiceChannel = (VoiceChannel) audioChannel;
        createdRooms.stream().filter(createdRoomData -> createdRoomData.getVoiceChannel().equals(voiceChannel)).findFirst().ifPresent(createdRoomData -> {
            //Создатель покинул комнату и комната пустая.
            if (audioChannel.getMembers().isEmpty()) {
                useCase.deleteVoiceChannels(Collections.singletonList(voiceChannel)).thenAccept(isDeleted -> {
                    if (isDeleted) {
                        createdRooms.remove(createdRoomData);
                    }
                });
            } else {
                //Cоздатель покинул комнату и комната НЕ пустая.
                boolean isMemberCreator = createdRoomData.getUserCreator().getIdLong() == currentMemberId;
                if (isMemberCreator) {
                    Random random = new Random();
                    List<Member> members = new ArrayList<>(audioChannel.getMembers());
                    int randomIndex = random.nextInt(members.size());
                    Member randomMember = members.get(randomIndex);
                    createdRoomData.setUserOwner(randomMember.getUser());
                    createdRoomData.addInHistory(getCurrentTime() + " - <@" + randomMember.getId() + "> стал владельцем комнаты.\n");
                }
            }
        });
    }

    public void onClickSelectorItem(StringSelectInteractionEvent event) {
        String buttonId = event.getSelectedOptions().get(0).getValue();
        CreatedRoomData createdRoomData = createdRooms.stream().filter(roomData -> roomData.getUserCreator().getIdLong() == event.getUser().getIdLong()).findFirst().orElse(null);
        if (createdRoomData == null) return;

        User eventUser = event.getUser();
        boolean isOwner = eventUser.getIdLong() == createdRoomData.getUserOwner().getIdLong();

        if (!isOwner) {
            useCase.showEphemeralShortLoading(event).thenAccept(interactionHook -> {
                interactionHook.editOriginal("Вы не являетесь владельцем комнаты").queue();
            });

            return;
        }

        handleChoseControlPanelItem(event, createdRoomData, buttonId);
        handeProcessActionForChoseUserItem(event, createdRoomData, buttonId);
        updateControlPanelMessage(createdRoomData);
    }

    private void updateControlPanelMessage(CreatedRoomData createdRoomData) {
        StringSelectMenu updatedMenu = StringSelectMenu.create(CONTROL_PANEL)
                .setPlaceholder("Выберите действие")
                .addOption("Микрофон вкл/выкл.", MICRO)
                .addOption("Звук вкл/выкл.", SOUND)
                .addOption("Выгнать.", KICK)
                .addOption("Заблокировать.", BLOCK)
                .addOption("Переименовать комнату.", RENAME)
                .addOption("Ограничить количество участников.", LIMIT)
                .addOption("Передать права.", TRANSFER_RIGHTS)
                .addOption("Понять и простить.", FORGIVE)
                .build();

        createdRoomData.getControlPanelMessage().editMessageComponents(ActionRow.of(updatedMenu)).queue();
    }

    private void handleChoseControlPanelItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData, String itemId) {
        switch (itemId) {
            case MICRO:
                onClickMicroItem(event, createdRoomData);
                break;
            case SOUND:
                onClickSoundItem(event, createdRoomData);
                break;
            case KICK:
                onClickKickItem(event, createdRoomData);
                break;
            case BLOCK:
                onClickBlockItem(event, createdRoomData);
                break;
            case RENAME:
                onClickRenameItem(event, createdRoomData);
                break;
            case LIMIT:
                onClickLimitItem(event, createdRoomData);
                break;
            case TRANSFER_RIGHTS:
                onClickTransferRightsItem(event, createdRoomData);
                break;
            case FORGIVE:
                onClickForgiveItem(event, createdRoomData);
                break;
        }

    }
    private void handeProcessActionForChoseUserItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData, String userId) {
        switch (userId) {
            case MICRO:
                useCase.enabledMicrophone(Collections.singletonList(event.getMember())).thenAccept(isEnabled -> {
                    if (isEnabled) {
                        useCase.showEphemeral(event).thenAccept(interactionHook -> {
                            interactionHook.editOriginal("Микрофон включен").queue();
                        });
                    } else {
                        useCase.showEphemeral(event).thenAccept(interactionHook -> {
                            interactionHook.editOriginal("Микрофон выключен").queue();
                        });
                    }
                });
                break;
            case SOUND:
                useCase.showEphemeral(event).thenAccept(interactionHook -> {
                    interactionHook.editOriginal("Звук включен/выключен").queue();
                });
                break;
            case KICK:
                useCase.showEphemeral(event).thenAccept(interactionHook -> {
                    interactionHook.editOriginal("Пользователь выгнан").queue();
                });
                break;
            case BLOCK:
                useCase.showEphemeral(event).thenAccept(interactionHook -> {
                    interactionHook.editOriginal("Пользователь заблокирован").queue();
                });
                break;
            case RENAME:
                useCase.showEphemeral(event).thenAccept(interactionHook -> {
                    createdRoomData.getVoiceChannel().getManager().setName("New name").queue();
                    interactionHook.editOriginal("Комната переименована").queue();
                });
                break;
            case LIMIT:
                useCase.showEphemeral(event).thenAccept(interactionHook -> {
                    interactionHook.editOriginal("Количество участников ограничено").queue();
                });
                break;
            case TRANSFER_RIGHTS:
                useCase.showEphemeral(event).thenAccept(interactionHook -> {
                    interactionHook.editOriginal("Права переданы").queue();
                });
                break;
            case FORGIVE:
                useCase.showEphemeral(event).thenAccept(interactionHook -> {
                    interactionHook.editOriginal("Понял и простил").queue();
                });
                break;
        }
    }


    private void onClickMicroItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData) {
        useCase.showEphemeral(event).thenAccept(interactionHook -> {
            showChoseUserMenuMessage(interactionHook, createdRoomData, "**Вкл/Выкл микрофон пользователю:**");
        });
    }



    private void onClickSoundItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData) {
        useCase.showEphemeral(event).thenAccept(interactionHook -> {
            interactionHook.editOriginal("Звук включен/выключен").queue();
        });
    }


    private void onClickKickItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData) {
        useCase.showEphemeral(event).thenAccept(interactionHook -> {
            interactionHook.editOriginal("Пользователь выгнан").queue();
        });
    }


    private void onClickBlockItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData) {
        useCase.showEphemeral(event).thenAccept(interactionHook -> {
            interactionHook.editOriginal("Пользователь заблокирован").queue();
        });
    }

    private void onClickRenameItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData) {
        useCase.showEphemeral(event).thenAccept(interactionHook -> {
            createdRoomData.getVoiceChannel().getManager().setName("New name").queue();
            interactionHook.editOriginal("Комната переименована").queue();
        });
    }

    private void onClickLimitItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData) {
        useCase.showEphemeral(event).thenAccept(interactionHook -> {
            interactionHook.editOriginal("Количество участников ограничено").queue();
        });
    }

    private void onClickTransferRightsItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData) {
        useCase.showEphemeral(event).thenAccept(interactionHook -> {
            interactionHook.editOriginal("Права переданы").queue();
        });
    }

    private void onClickForgiveItem(StringSelectInteractionEvent event, CreatedRoomData createdRoomData) {
        useCase.showEphemeral(event).thenAccept(interactionHook -> {
            interactionHook.editOriginal("Понял и простил").queue();
        });
    }


    private void showControlPanelMessage(Member currentMember, VoiceChannel createdVoiceChannel, CreatedRoomData createdRoomData) {
        User user = currentMember.getUser();

        String currentOwner = "Текущий владелец: <@" + user.getId() + "> \nㅤ\n";
        String history = "**История активности:**\n";
        String createRoomRow = getCurrentTime() + " - <@" + user.getId() + "> создал комнату и стал владельцем.\n";

        EmbedBuilder embed = new EmbedBuilder();
        embed.setDescription(currentOwner + history + createRoomRow);
        embed.setColor(Colors.BLUE);

        createdRoomData.addInHistory(createRoomRow);

        StringSelectMenu selectSoundMenu = StringSelectMenu.create(CONTROL_PANEL)
                .setPlaceholder("Выберите действие")
                .addOption("Микрофон вкл/выкл.", MICRO)
                .addOption("Звук вкл/выкл.", SOUND)
                .addOption("Выгнать.", KICK)
                .addOption("Заблокировать.", BLOCK)
                .addOption("Переименовать комнату.", RENAME)
                .addOption("Ограничить количество участников.", LIMIT)
                .addOption("Передать права.",  TRANSFER_RIGHTS)
                .addOption("Понять и простить.", FORGIVE)
                .build();

        createdVoiceChannel.sendMessageEmbeds(embed.build())
                .addActionRow(selectSoundMenu)
                .queue(createdRoomData::setControlPanelMessage);

    }

    public String getCurrentTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private void showChoseUserMenuMessage(InteractionHook interactionHook, CreatedRoomData createdRoomData, String msg) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setDescription(msg);
        embed.setColor(Colors.BLUE);
        List<User> usersInCurrentChannel = createdRoomData.getVoiceChannel().getMembers()
                .stream()
                .map(Member::getUser)
                .collect(Collectors.toList());

        List<SelectOption> options = usersInCurrentChannel.stream()
                .map(user -> SelectOption.of(user.getName(), user.getId()))
                .collect(Collectors.toList());

        StringSelectMenu selectSoundMenu = StringSelectMenu.create(CHOSE_USER)
                .setPlaceholder("Выберите пользователя")
                .addOptions(options)
                .build();

        interactionHook.editOriginalEmbeds(embed.build())
                .setActionRow(selectSoundMenu).queue();
    }
}
