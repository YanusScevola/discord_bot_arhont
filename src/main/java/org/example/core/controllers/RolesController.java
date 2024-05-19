package org.example.core.controllers;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.example.core.constants.TestsID;
import org.example.core.constants.RolesID;
import org.example.core.constants.TextChannelsID;
import org.example.core.models.AwaitingTestUser;
import org.example.core.models.Question;
import org.example.core.models.TestDataByUser;
import org.example.domain.UseCase;
import org.example.resources.Colors;
import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RolesController {
    private static final int TEST_TIMER = 30;
    private static final int MAX_QUESTIONS = 6;
    private static final int MAX_CACHE_SIZE = 20;

    private static final String GET_ROLE_BTN_ID = "get_role";
    private static final String START_TEST_BTN_ID = "start_test";
    private static final String ANSWER_A_BTN_ID = "answer_a";
    private static final String ANSWER_B_BTN_ID = "answer_b";
    private static final String ANSWER_C_BTN_ID = "answer_c";
    private static final String ANSWER_D_BTN_ID = "answer_d";
    private static final String CLOSE_TEST_BTN_ID = "close_test";

    private static RolesController instance;
    private TextChannel channel;
    private UseCase useCase;

    private List<Long> debatersRuleIds = new ArrayList<>(Arrays.asList(
            RolesID.DEBATER_APF_1,
            RolesID.DEBATER_APF_2,
            RolesID.DEBATER_APF_3,
            RolesID.DEBATER_APF_4,
            RolesID.DEBATER_APF_5
    ));

    private final LinkedHashMap<Member, String> selectedTestByMemberMap = new LinkedHashMap<>();
    private final LinkedHashMap<Member, InteractionHook> testInformationHookByMemberMap = new LinkedHashMap<>();
    private final LinkedHashMap<Member, TestDataByUser> testDataByMemberMap = new LinkedHashMap<>();
    private static final Map<String, Integer> answerButtonIdByAnswersIndex = new HashMap<>();

    static {
        answerButtonIdByAnswersIndex.put(ANSWER_A_BTN_ID, 0);
        answerButtonIdByAnswersIndex.put(ANSWER_B_BTN_ID, 1);
        answerButtonIdByAnswersIndex.put(ANSWER_C_BTN_ID, 2);
        answerButtonIdByAnswersIndex.put(ANSWER_D_BTN_ID, 3);
    }

    private RolesController(UseCase useCase) {
        this.useCase = useCase;
        this.channel = useCase.getTextChannel(TextChannelsID.ROLES);
        showAllEmbedRoles();
    }

    public static synchronized RolesController getInstance(UseCase useCase) {
        if (instance == null) {
            instance = new RolesController(useCase);
        }
        return instance;
    }

    public void onButtonListener(@NotNull ButtonInteractionEvent event) {
        Member member = Objects.requireNonNull(event.getMember());
        switch (event.getComponentId()) {
            case GET_ROLE_BTN_ID:
                onClickGetRole(event, member);
                break;
            case START_TEST_BTN_ID:
                onClickStartTest(event, member);
                break;
            case ANSWER_A_BTN_ID:
            case ANSWER_C_BTN_ID:
            case ANSWER_B_BTN_ID:
            case ANSWER_D_BTN_ID:
                onClickAnswer(event, member);
                break;
            case CLOSE_TEST_BTN_ID:
                onClickCloseTest(event);
                break;
            default:
                // Можно добавить обработку неизвестных или неожиданных ID компонентов
                break;
        }
    }

    private void onClickGetRole(ButtonInteractionEvent event, Member member) {
        if (event.getMember() == null) return;

        String description = getEmbedDescriptionByEvent(event);
        if (description == null) return;

        if (description.contains("Роль дебатер")) {
            if (isMemberHasRole(debatersRuleIds, event.getMember())) {
                showShortEphemeral(event, "У вас уже есть данная роль");
                return;
            }

            useCase.showEphemeral(event).thenAccept(hook -> {
                removeFirstEntry(testInformationHookByMemberMap, MAX_CACHE_SIZE);
                selectedTestByMemberMap.put(member, TestsID.APF_TEST);
                editEphemeralDebaterRoleInformation(hook, member);
            });
        } else if (description.contains("Роль историк")) {
            event.reply("Роль историк").setEphemeral(true).queue();
        } else if (description.contains("Роль логик")) {
            event.reply("Роль логик").setEphemeral(true).queue();
        } else {
            event.reply("Неизвестная роль. Пожалуйста, обратитесь к администратору.").setEphemeral(true).queue();
        }
    }

    private void onClickStartTest(ButtonInteractionEvent event, Member member) {
        String selectedTestName = selectedTestByMemberMap.get(member);
        useCase.removeOverdueAwaitingTestUser().join();
        useCase.getAwaitingTestUser(member.getIdLong(), selectedTestName).thenAccept(awaitingTestUser -> {
            boolean isUserAwaitingTest = awaitingTestUser != null; // ожидает ли пользователь тест
            Timestamp coolDownEndTimestamp = isUserAwaitingTest ? awaitingTestUser.getTime() : null; // время окончания кулдауна типа Timestamp
            boolean isCoolDownPassed = coolDownEndTimestamp == null || coolDownEndTimestamp.getTime() <= System.currentTimeMillis(); // прошел ли кулдаун

            if (isUserAwaitingTest && !isCoolDownPassed) {
                showEphemeralWaitingTestCoolDown(event, coolDownEndTimestamp.getTime());
                return;
            }

            if (testDataByMemberMap.containsKey(event.getMember())) {
                showShortEphemeral(event, "Тест уже начат, подождите пока он закончится");
                return;
            }

            startTest(useCase, event, member);
        });
    }

    private void onClickAnswer(ButtonInteractionEvent event, Member member) {
        TestDataByUser currentTestData = testDataByMemberMap.get(event.getMember());
        ScheduledFuture<?> currentTimer = currentTestData.getTimers().remove(member);
        Question currentQuestion = currentTestData.getCurrentQuestion();
        String selectedAnswer = currentQuestion.getAnswers().get(answerButtonIdByAnswersIndex.get(event.getComponentId()));
        boolean isSelectedAnswerCorrect = currentQuestion.getCorrectAnswer().equals(selectedAnswer);

        if (isSelectedAnswerCorrect) {
            if (currentTimer != null) currentTimer.cancel(false);
            if (currentTestData.getCurrentQuestionNumber() == MAX_QUESTIONS) {
                showTestSuccess(event);
                return;
            }
            showNextQuestion(event, currentTestData.getCurrentQuestionNumber() + 1);
        } else {
            showTestFailed(event);
        }
    }

    private void onClickCloseTest(ButtonInteractionEvent event) {
        event.deferEdit().queue();
        event.getHook().deleteOriginal().queue(
                success -> System.out.println("Сообщение о закрытии теста удалено"),
                failure -> System.err.println("Не удалось удалить сообщение о закрытии теста: " + failure.getMessage())
        );
    }

    private void showAllEmbedRoles() {
        String debaterAPFTitle = "Роль дебатер";
        String historianTitle = "Роль историк";
        String logicTitle = "Роль логик";

        channel.getIterableHistory().queue(messages -> {
            boolean debaterAPFExists = messages.stream().anyMatch(message -> message.getEmbeds().stream().anyMatch(embed -> debaterAPFTitle.equals(embed.getDescription())));
            boolean historianExists = messages.stream().anyMatch(message -> message.getEmbeds().stream().anyMatch(embed -> historianTitle.equals(embed.getDescription())));
            boolean logicExists = messages.stream().anyMatch(message -> message.getEmbeds().stream().anyMatch(embed -> logicTitle.equals(embed.getDescription())));

            Button getRoleButton = Button.primary(GET_ROLE_BTN_ID, "Получить роль");

            if (!debaterAPFExists) {
                EmbedBuilder embedRoleDebaterAPF = getEmbedBuilderRoleDebateAPF();
                channel.sendMessageEmbeds(embedRoleDebaterAPF.build()).setActionRow(getRoleButton).queue(
                        success -> System.out.println("Embed сообщение о роли дебатер отправлено"),
                        failure -> System.err.println("Не удалось отправить embed сообщение о роли дебатер: " + failure.getMessage())
                );
            }

            if (!historianExists) {
                EmbedBuilder embedRoleHistorian = getEmbedBuilderRoleHistorian();
                channel.sendMessageEmbeds(embedRoleHistorian.build()).setActionRow(getRoleButton).queue(
                        success -> System.out.println("Embed сообщение о роли историк отправлено"),
                        failure -> System.err.println("Не удалось отправить embed сообщение о роли историк: " + failure.getMessage())
                );
            }

            if (!logicExists) {
                EmbedBuilder embedRoleLogic = getEmbedBuilderRoleLogic();
                channel.sendMessageEmbeds(embedRoleLogic.build()).setActionRow(getRoleButton).queue(
                        success -> System.out.println("Embed сообщение о роли логик отправлено"),
                        failure -> System.err.println("Не удалось отправить embed сообщение о роли логик: " + failure.getMessage())
                );
            }
        });
    }

    public void editEphemeralDebaterRoleInformation(InteractionHook hook, Member member) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Colors.BLUE);
        embed.setDescription("Чтобы получить роль <@& + RolesID.DEBATER_APF_1 + > необходимо пройти тест на знание правил дебатов АПФ.**\n\n" +
                "- Чтобы подготовиться к тесту пройдите в канал <#" + TextChannelsID.RULES_APF + ">.\n" +
                "- В тесте будут " + MAX_QUESTIONS + " вопросов и 4 варианта ответа.\n" +
                "- На каждый вопрос выделяется " + TEST_TIMER + " секунд.\n");

        hook.editOriginalEmbeds(embed.build())
                .setActionRow(Button.primary(START_TEST_BTN_ID, "Начать тест"))
                .queue(message -> {
                    removeFirstEntry(testInformationHookByMemberMap, MAX_CACHE_SIZE);
                    testInformationHookByMemberMap.put(member, hook);
                });

    }

    public void startTest(UseCase useCase, ButtonInteractionEvent event, Member member) {
        event.deferReply(true).queue(loadingHook -> {
            String selectedTestName = selectedTestByMemberMap.get(member);
            useCase.getAllTestQuestions(selectedTestName).thenAccept(questions -> {
                loadingHook.deleteOriginal().queue();
                TestDataByUser currentTestData = new TestDataByUser(member, questions);
                removeFirstEntry(testDataByMemberMap, MAX_CACHE_SIZE);
                testDataByMemberMap.put(member, currentTestData);
                showNextQuestion(event, 1);
            });
        });
    }

    public void showNextQuestion(ButtonInteractionEvent event, int questionNumber) {
        Member member = Objects.requireNonNull(event.getMember());
        TestDataByUser currentTestData = testDataByMemberMap.get(member);
        InteractionHook needToStartTestHook = testInformationHookByMemberMap.get(member);
        boolean isFirstQuestion = questionNumber == 1;

        if (currentTestData.getQuestions().isEmpty()) {
            if (isFirstQuestion) {
                testInformationHookByMemberMap.remove(member);
                showTestFailed(member, needToStartTestHook);
            } else {
                showTestFailed(event);
            }

            System.err.println("Список вопросов пуст");
            return;
        }

        ScheduledFuture<?> previousTimer = currentTestData.getTimers().remove(member);
        if (previousTimer != null) {
            previousTimer.cancel(false);
        }

        Question currentQuestion = currentTestData.getQuestions().get(questionNumber - 1);
        long currentTimeInSeconds = System.currentTimeMillis() / 1000L;
        long twentySecondsLater = currentTimeInSeconds + TEST_TIMER;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Colors.BLUE);
        embed.setFooter((questionNumber) + " из " + MAX_QUESTIONS);

        String timerText = ":stopwatch: <t:" + twentySecondsLater + ":R>\n\n";
        String questionText = "**" + currentQuestion.getText() + "**\n\n";
        String answersText = "**A**. " + currentQuestion.getAnswers().get(0) + "\n" +
                "**B**. " + currentQuestion.getAnswers().get(1) + "\n" +
                "**C**. " + currentQuestion.getAnswers().get(2) + "\n" +
                "**D**. " + currentQuestion.getAnswers().get(3) + "\n";

        embed.setDescription(timerText + questionText + answersText);

        Button a = Button.primary(ANSWER_A_BTN_ID, "A");
        Button b = Button.primary(ANSWER_B_BTN_ID, "B");
        Button c = Button.primary(ANSWER_C_BTN_ID, "C");
        Button d = Button.primary(ANSWER_D_BTN_ID, "D");

        if (isFirstQuestion) {
            needToStartTestHook.editOriginalEmbeds(embed.build())
                    .setActionRow(a, b, c, d)
                    .queue(message -> {
                                ScheduledFuture<?> timer = currentTestData
                                        .getScheduler()
                                        .schedule(() -> {
                                            showTestFailed(member, message);
                                        }, TEST_TIMER, TimeUnit.SECONDS);

                                currentTestData.setCurrentQuestion(currentQuestion);
                                currentTestData.setCurrentQuestionNumber(1);
                                currentTestData.getTimers().put(member, timer);

                                testDataByMemberMap.put(member, currentTestData);

                                System.out.println("Сообщение о первом вопросе изменено");
                            },
                            failure -> System.err.println("Не удалось изменить сообщение о первом вопросе: " + failure.getMessage())
                    );
        } else {
            event.editMessageEmbeds(embed.build())
                    .setActionRow(a, b, c, d)
                    .queue(message -> {
                                ScheduledFuture<?> timer = currentTestData
                                        .getScheduler()
                                        .schedule(() -> {
                                            showTestFailed(event.getMember(), message);
                                        }, TEST_TIMER, TimeUnit.SECONDS);

                                currentTestData.setCurrentQuestion(currentQuestion);
                                currentTestData.setCurrentQuestionNumber(questionNumber);
                                currentTestData.getTimers().put(event.getMember(), timer);

                                System.out.println("Сообщение о следующем вопросе изменено");
                            },
                            failure -> System.err.println("Не удалось изменить сообщение о следующем вопросе: " + failure.getMessage())
                    );
        }

    }

    public void showTestSuccess(ButtonInteractionEvent event) {
        Map<Member, Long> members = new HashMap<>();
        members.put(Objects.requireNonNull(event.getMember()), RolesID.DEBATER_APF_1);
        useCase.addRoleToMembers(members).thenAccept(success -> {
            EmbedBuilder winEmbed = new EmbedBuilder();
            winEmbed.setColor(Colors.GREEN);
            winEmbed.setTitle("Тест пройден  :partying_face:");
            winEmbed.setDescription("Вы получили роль дебатер.");
            event.editMessageEmbeds(winEmbed.build()).setActionRow(Button.success(CLOSE_TEST_BTN_ID, "Закончить")).queue(
                    success1 -> {
                        testDataByMemberMap.remove(event.getMember());
                        System.out.println("Сообщение о успешном прохождении теста изменено");
                    },
                    failure -> System.err.println("Не удалось изменить сообщение о успешном прохождении теста: " + failure.getMessage()));
        });
    }

    public void showTestFailed(ButtonInteractionEvent event) {
        TestDataByUser currentTestData = testDataByMemberMap.get(event.getMember());
        EmbedBuilder lossEmbed = getTestFailedEmbed(currentTestData);
        event.editMessageEmbeds(lossEmbed.build()).setActionRow(Button.danger(CLOSE_TEST_BTN_ID, "Закрыть")).queue(
                success -> {
                    useCase.addAwaitingTest(new AwaitingTestUser(event.getMember(), TestsID.APF_TEST, new Timestamp(System.currentTimeMillis())));
                    testDataByMemberMap.remove(event.getMember());
                    System.out.println("Сообщение о неудачном прохождении теста изменено");
                },
                failure -> System.err.println("Не удалось изменить сообщение о неудачном прохождении теста: " + failure.getMessage()));
    }

    public void showTestFailed(Member member, InteractionHook hook) {
        TestDataByUser currentTestData = testDataByMemberMap.get(member);
        EmbedBuilder lossEmbed = getTestFailedEmbed(currentTestData);
        hook.editOriginalEmbeds(lossEmbed.build()).setActionRow(Button.danger(CLOSE_TEST_BTN_ID, "Закрыть")).queue(
                success -> {
                    useCase.addAwaitingTest(new AwaitingTestUser(member, TestsID.APF_TEST, new Timestamp(System.currentTimeMillis())));
                    testDataByMemberMap.remove(member);
                    System.out.println("Сообщение о неудачном прохождении теста изменено");
                },
                failure -> System.err.println("Не удалось изменить сообщение о неудачном прохождении теста: " + failure.getMessage()));
    }

    public void showTestFailed(Member member, Message message) {
        TestDataByUser currentTestData = testDataByMemberMap.get(member);
        EmbedBuilder lossEmbed = getTestFailedEmbed(currentTestData);
        message.editMessageEmbeds(lossEmbed.build()).setActionRow(Button.danger(CLOSE_TEST_BTN_ID, "Закрыть")).queue(
                success -> {
                    useCase.addAwaitingTest(new AwaitingTestUser(member, TestsID.APF_TEST, new Timestamp(System.currentTimeMillis())));
                    testDataByMemberMap.remove(member);
                    System.out.println("Сообщение о неудачном прохождении теста изменено");
                },
                failure -> System.err.println("Не удалось изменить сообщение о неудачном прохождении теста: " + failure.getMessage()));

    }

    public EmbedBuilder getTestFailedEmbed(TestDataByUser currentTestData) {
        EmbedBuilder lossEmbed = new EmbedBuilder();
        lossEmbed.setColor(Colors.RED);
        lossEmbed.setTitle("Тест провален :cry:");
        lossEmbed.setDescription("- Вы ответили правильно на " + (currentTestData.getCurrentQuestionNumber() - 1) + " из " + MAX_QUESTIONS + " вопросов.\n" +
                "- Перепройди тест через 30 минут.");
        return lossEmbed;
    }

    private void showEphemeralWaitingTestCoolDown(ButtonInteractionEvent event, long coolDownEndTime) {
        long coolDownEndUnix = coolDownEndTime / 1000;
        String textMessage = String.format("Вы можете начать тест заново только <t:%d:R> после последней попытки.", coolDownEndUnix);
        useCase.showEphemeralShortLoading(event).thenAccept(hook -> {
            hook.editOriginal(textMessage).queue();
        });
    }

    private void showShortEphemeral(ButtonInteractionEvent event, String text) {
        useCase.showEphemeralShortLoading(event).thenAccept(message -> {
            message.editOriginal(text).queue();
        });
    }


    private boolean isMemberHasRole(List<Long> roleIds, Member member) {
        return member.getRoles().stream().anyMatch(role -> roleIds.contains(role.getIdLong()));
    }

    private static <K, V> void removeFirstEntry(LinkedHashMap<K, V> map, int maxEntries) {
        if (map.size() > maxEntries) {
            Iterator<K> iterator = map.keySet().iterator();
            if (iterator.hasNext()) {
                K firstKey = iterator.next();
                map.remove(firstKey);
            }
        }
    }

    private EmbedBuilder getEmbedBuilderRoleDebateAPF() {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Colors.BLUE);
        embedBuilder.setDescription("Роль дебатер ");
        embedBuilder.addField("Уровни роли", "1-й уровень - пройти тест на знания правил дебатов АПФ.\n" +
                "2-й уровень - победить 2 раза в дебатах.\n" +
                "3-й уровень - победить 4 раза в дебатах.\n" +
                "4-й уровень - победить 8 раз в дебатах.\n" +
                "5-й уровень - победить 16 раз в дебатах.", true);

        return embedBuilder;
    }

    private EmbedBuilder getEmbedBuilderRoleHistorian() {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Colors.BLUE);
        embedBuilder.setDescription("Роль историк ");
        embedBuilder.addField("Уровни роли", "1-й уровень - пройти тест на знание истории \n" +
                "2-й уровень - победить 2 раза в тестах.\n" +
                "3-й уровень - победить 4 раза в тестах.\n" +
                "4-й уровень - победить 8 раз в тестах.\n" +
                "5-й уровень - победить 16 раз в тестах.", true);

        return embedBuilder;
    }

    private EmbedBuilder getEmbedBuilderRoleLogic() {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Colors.BLUE);
        embedBuilder.setDescription("Роль логик");
        embedBuilder.addField("Уровни роли", "1-й уровень - пройти тест на знание логики АПФ.\n" +
                "2-й уровень - победить 2 раза в тестах.\n" +
                "3-й уровень - победить 4 раза в тестах.\n" +
                "4-й уровень - победить 8 раз в тестах.\n" +
                "5-й уровень - победить 16 раз в тестах.", true);

        return embedBuilder;
    }

    private String getEmbedDescriptionByEvent(ButtonInteractionEvent event) {
        List<MessageEmbed> embeds = event.getMessage().getEmbeds();
        if (embeds.isEmpty()) return null;
        MessageEmbed embed = embeds.get(0);
        return embed.getDescription();
    }


}
