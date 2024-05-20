package org.example.domain;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.example.core.models.*;
import org.example.core.utils.DateTimeUtils;
import org.example.data.models.AwaitingTestUserModel;
import org.example.data.models.DebateModel;
import org.example.data.models.DebaterModel;
import org.example.data.models.ThemeModel;
import org.example.data.source.ApiService;
import org.example.data.source.db.DbOperations;
import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UseCase {

    private static UseCase instance;

    private final DbOperations dataBase;
    private final ApiService apiService;

    private UseCase(ApiService apiService, DbOperations database) {
        this.apiService = apiService;
        this.dataBase = database;
    }

    public static synchronized UseCase getInstance(ApiService apiService, DbOperations database) {
        if (instance == null) {
            instance = new UseCase(apiService, database);
        }
        return instance;
    }

    public TextChannel getTextChannel(long channelId) {
        return apiService.getTextChannel(channelId);
    }

    public Member getBotMember() {
        return apiService.getBotMember();
    }

    public CompletableFuture<List<Member>> getMembersByRoleIDs(List<Long> ids) {
        return apiService.getMembersByRolesIds(ids);
    }

    public CompletableFuture<VoiceChannel> getVoiceChannel(long channelId) {
        return apiService.getVoiceChannelById(channelId);
    }

    public CompletableFuture<Message> getMessageByIndex(TextChannel channel, int index) {
        return apiService.getMessageByIndex(channel, index);
    }

    public CompletableFuture<Category> getCategoryByID(long id) {
        return apiService.getCategoryByID(id);
    }

    public CompletableFuture<Role> getRoleByID(long id) {
        return apiService.getRoleByID(id);
    }

    public CompletableFuture<Boolean> addRoleToMembers(Map<Member, Long> memberToRoleMap) {
        return apiService.addRoleToMembers(memberToRoleMap);
    }

    public CompletableFuture<Boolean> removeRoleFromUsers(Map<Member, Long> memberToRoleMap) {
        return apiService.removeRoleFromUsers(memberToRoleMap);
    }

    public CompletableFuture<Boolean> moveMembers(List<Member> members, VoiceChannel targetChannel) {
        return apiService.moveMembers(members, targetChannel);
    }

    public CompletableFuture<Boolean> disabledMicrophone(List<Member> members) {
        return apiService.processingMicrophone(members, true);
    }

    public CompletableFuture<Boolean> enabledMicrophone(List<Member> members) {
        return apiService.processingMicrophone(members, false);
    }

    public CompletableFuture<InteractionHook> showEphemeralShortLoading(@NotNull ButtonInteractionEvent event) {
        return apiService.showEphemeralShortLoading(event);
    }

    public CompletableFuture<InteractionHook> showEphemeral(@NotNull ButtonInteractionEvent event) {
        return apiService.showEphemeral(event);
    }

    public CompletableFuture<Boolean> deleteVoiceChannels(List<VoiceChannel> channels) {
        return apiService.deleteVoiceChannels(channels);
    }

    public CompletableFuture<List<Question>> getAllTestQuestions(String testName) {
        return dataBase.getAllQuestions(testName).thenApply(questionModels -> questionModels.stream()
                .map(questionModel -> {
                    return new Question(
                            questionModel.getId(),
                            questionModel.getText(),
                            questionModel.getAnswers(),
                            questionModel.getCorrectAnswer()
                    );
                })
                .collect(Collectors.toList()));
    }


    public CompletableFuture<List<Question>> getRandomQuestions(String testName, Integer levelsLimit) {
        return dataBase.getRandomQuestions(testName, levelsLimit).thenApply(questionModels -> questionModels.stream()
                .map(questionModel -> new Question(
                        questionModel.getId(),
                        questionModel.getText(),
                        questionModel.getAnswers(),
                        questionModel.getCorrectAnswer()
                ))
                .collect(Collectors.toList()));
    }

    public CompletableFuture<Boolean> addAwaitingTest(AwaitingTestUser testUser, Integer awaitingTimeMinutes) {
        LocalDateTime localDateTime = testUser.getTime().toLocalDateTime();
        LocalDateTime updatedDateTime = localDateTime.plusMinutes(awaitingTimeMinutes);
        LocalDateTime utcDateTime = DateTimeUtils.toUtc(updatedDateTime);
        Timestamp utcTimestamp = Timestamp.valueOf(utcDateTime);

        testUser.setTime(utcTimestamp);

        AwaitingTestUserModel testModel = new AwaitingTestUserModel(
                testUser.getMember().getIdLong(),
                testUser.getTestName(),
                testUser.getTime()
        );

        return dataBase.addAwaitingTestUser(testModel);
    }

    public CompletableFuture<AwaitingTestUser> getAwaitingTestUser(Long userId, String testName) {
        return dataBase.getAwaitingTestUser(userId, testName).thenCompose(testUserModels -> {
            if (testUserModels.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            AwaitingTestUserModel model = testUserModels.get(0);

            return apiService.getMembersByIds(Collections.singletonList(model.getUserId())).thenApply(members -> {
                if (members.isEmpty()) {
                    return null;
                }

                Member member = members.get(0);
                LocalDateTime utcDateTime = model.getTime().toLocalDateTime();
                LocalDateTime localDateTime = DateTimeUtils.fromUtc(utcDateTime);
                Timestamp utcTimestamp = Timestamp.valueOf(localDateTime);
                return new AwaitingTestUser(member, model.getTestName(), utcTimestamp);
            });
        });
    }

    public CompletableFuture<List<AwaitingTestUser>> searchAwaitingTestUsers(long userId, String testName) {
        return dataBase.searchAwaitingTestUsers(userId, testName).thenApply(testUserModels -> {
            List<AwaitingTestUser> testUsers = new ArrayList<>();
            for (AwaitingTestUserModel model : testUserModels) {
                Member member = apiService.getMemberById(model.getUserId()).join();  // Получаем пользователя по ID
                AwaitingTestUser testUser = new AwaitingTestUser(member, model.getTestName(), model.getTime());
                testUsers.add(testUser);
            }
            return testUsers;
        });
    }

    public CompletableFuture<Boolean> removeOverdueAwaitingTestUser() {
        return dataBase.removeOverdueAwaitingTestUser().thenApply(result -> {
            if (result) {
                System.out.println("Удалены просроченные записи");
                return true;
            } else {
                System.out.println("Нет просроченных записей");
                return false;
            }
        });
    }

    public CompletableFuture<Integer> getQuestionCountByTableName(String tableName) {
        return dataBase.getQuestionCountByTableName(tableName);
    }


}
