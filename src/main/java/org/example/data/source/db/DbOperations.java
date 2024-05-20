package org.example.data.source.db;

import org.example.data.models.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DbOperations {
    private final Database db;

    public DbOperations() {
        this.db = Database.getInstance();
    }

    public CompletableFuture<List<QuestionModel>> getRandomQuestions(String testName, Integer levelsLimit) {
        return db.executeWithConnection().thenCompose(connectionResult -> {
            if (!connectionResult) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            } else {
                return CompletableFuture.supplyAsync(() -> {
                    List<QuestionModel> results = new ArrayList<>();
                    try (Connection connection = db.getConnection()) {
                        String sqlTemplate = "SELECT * FROM " + testName + " WHERE " +
                                DbConstants.COLUMN_TEST_DIFFICULTY_LEVEL + " = ? ORDER BY RAND() LIMIT " + levelsLimit + ";";
                        try (PreparedStatement statement = connection.prepareStatement(sqlTemplate)) {
                            for (int difficulty = 1; difficulty <= levelsLimit; difficulty++) {
                                statement.setInt(1, difficulty);
                                try (ResultSet rs = statement.executeQuery()) {
                                    while (rs.next()) {
                                        QuestionModel result = new QuestionModel(
                                                rs.getInt(DbConstants.COLUMN_TEST_ID),
                                                rs.getString(DbConstants.COLUMN_TEST_QUESTION),
                                                Arrays.asList(
                                                        rs.getString(DbConstants.COLUMN_TEST_ANSWER_1),
                                                        rs.getString(DbConstants.COLUMN_TEST_ANSWER_2),
                                                        rs.getString(DbConstants.COLUMN_TEST_ANSWER_3),
                                                        rs.getString(DbConstants.COLUMN_TEST_ANSWER_4)
                                                ),
                                                rs.getString(DbConstants.COLUMN_TEST_CORRECT_ANSWER)
                                        );
                                        results.add(result);
                                    }
                                }
                            }
                        }
                    } catch (SQLException e) {
                        db.getLogger().error("Ошибка при получении случайных вопросов", e);
                        throw new RuntimeException(e);
                    }
                    return results;
                });
            }
        });
    }


    public CompletableFuture<List<QuestionModel>> getAllQuestions(String testName) {
        return db.executeWithConnection().thenCompose(connectionResult -> {
            if (!connectionResult) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            } else {
                return CompletableFuture.supplyAsync(() -> {
                    String sql = "SELECT * FROM " + testName + ";";
                    List<QuestionModel> results = new ArrayList<>();
                    try (PreparedStatement statement = db.getConnection().prepareStatement(sql)) {
                        try (ResultSet rs = statement.executeQuery()) {
                            while (rs.next()) {
                                QuestionModel result = new QuestionModel(
                                        rs.getInt(DbConstants.COLUMN_TEST_ID),
                                        rs.getString(DbConstants.COLUMN_TEST_QUESTION),
                                        Arrays.asList(
                                                rs.getString(DbConstants.COLUMN_TEST_ANSWER_1),
                                                rs.getString(DbConstants.COLUMN_TEST_ANSWER_2),
                                                rs.getString(DbConstants.COLUMN_TEST_ANSWER_3),
                                                rs.getString(DbConstants.COLUMN_TEST_ANSWER_4)
                                        ),
                                        rs.getString(DbConstants.COLUMN_TEST_CORRECT_ANSWER)
                                );
                                results.add(result);
                            }
                        }
                    } catch (SQLException e) {
                        System.err.println("Ошибка при получении списка вопросов: " + e.getMessage());
                        db.getLogger().error("Ошибка при получении списка вопросов", e);
                        throw new RuntimeException(e);
                    }
                    System.out.println("Вопросы получены");
                    return results;
                });
            }
        });
    }

    public CompletableFuture<Boolean> addAwaitingTestUser(AwaitingTestUserModel awaitingTestUser) {
        return db.executeWithConnection().thenCompose(connectionResult -> {
            if (!connectionResult) {
                CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
                failedFuture.complete(false);
                return failedFuture;
            } else {
                return CompletableFuture.supplyAsync(() -> {
                    String sql = "INSERT INTO " + DbConstants.TABLE_AWAITING_TESTS + " (" +
                            DbConstants.COLUMN_AWAITING_USER_ID + ", " +
                            DbConstants.COLUMN_AWAITING_TEST_NAME + ", " +
                            DbConstants.COLUMN_AWAITING_TEST_TIME + ") VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE " +
                            DbConstants.COLUMN_AWAITING_USER_ID + " = VALUES(" + DbConstants.COLUMN_AWAITING_USER_ID + "), " +
                            DbConstants.COLUMN_AWAITING_TEST_NAME + " = VALUES(" + DbConstants.COLUMN_AWAITING_TEST_NAME + "), " +
                            DbConstants.COLUMN_AWAITING_TEST_TIME + " = VALUES(" + DbConstants.COLUMN_AWAITING_TEST_TIME + ");";
                    try (PreparedStatement statement = db.getConnection().prepareStatement(sql)) {
                        statement.setLong(1, awaitingTestUser.getUserId());
                        statement.setString(2, awaitingTestUser.getTestName());
                        statement.setTimestamp(3, awaitingTestUser.getTime());
                        statement.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        System.err.println("Ошибка при добавлении теста в ожидании: " + e.getMessage());
                        throw new RuntimeException(e);
                    }
                });
            }
        });
    }

    public CompletableFuture<List<AwaitingTestUserModel>> getAwaitingTestUser(long userId, String testName) {
        return db.executeWithConnection().thenCompose(connectionResult -> {
            if (!connectionResult) {
                CompletableFuture<List<AwaitingTestUserModel>> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new SQLException("Не удалось восстановить соединение с базой данных"));
                return failedFuture;
            } else {
                return CompletableFuture.supplyAsync(() -> {
                    String sql = "SELECT * FROM " + DbConstants.TABLE_AWAITING_TESTS + " WHERE " +
                            DbConstants.COLUMN_AWAITING_USER_ID + " = ? AND " +
                            DbConstants.COLUMN_AWAITING_TEST_NAME + " = ?;";
                    List<AwaitingTestUserModel> awaitingTests = new ArrayList<>();
                    try (PreparedStatement statement = db.getConnection().prepareStatement(sql)) {
                        statement.setLong(1, userId);
                        statement.setString(2, testName);
                        try (ResultSet rs = statement.executeQuery()) {
                            while (rs.next()) {
                                AwaitingTestUserModel testUser = new AwaitingTestUserModel(
                                        rs.getLong(DbConstants.COLUMN_AWAITING_USER_ID),
                                        rs.getString(DbConstants.COLUMN_AWAITING_TEST_NAME),
                                        rs.getTimestamp(DbConstants.COLUMN_AWAITING_TEST_TIME)
                                );
                                awaitingTests.add(testUser);
                            }
                            return awaitingTests;
                        }
                    } catch (SQLException e) {
                        db.getLogger().error("Ошибка при получении данных ожидания теста для пользователя: " + e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                });
            }
        }).exceptionally(e -> {
            db.getLogger().error("Не удалось получить данные ожидания теста для пользователя", e);
            return Collections.emptyList(); // Возвращаем пустой список в случае ошибки
        });
    }


    public CompletableFuture<Boolean> removeOverdueAwaitingTestUser() {
        return db.executeWithConnection().thenCompose(connectionResult -> {
            if (!connectionResult) {
                CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new SQLException("Не удалось восстановить соединение с базой данных"));
                return failedFuture;
            } else {
                return CompletableFuture.supplyAsync(() -> {
                    // Запрос на удаление записей с просроченной датой теста
                    String sql = "DELETE FROM " + DbConstants.TABLE_AWAITING_TESTS +
                            " WHERE " + DbConstants.COLUMN_AWAITING_TEST_TIME + " < CURRENT_TIMESTAMP;";
                    try (PreparedStatement statement = db.getConnection().prepareStatement(sql)) {
                        int affectedRows = statement.executeUpdate();
                        return affectedRows > 0; // Возвращаем true, если записи были удалены
                    } catch (SQLException e) {
                        db.getLogger().error("Ошибка при удалении просроченных тестов из базы данных: " + e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                });
            }
        }).exceptionally(e -> {
            db.getLogger().error("Не удалось удалить просроченные тесты из базы данных", e);
            return false; // Возвращаем false в случае ошибки
        });
    }

    public CompletableFuture<List<AwaitingTestUserModel>> searchAwaitingTestUsers(long userId, String testName) {
        return db.executeWithConnection().thenCompose(connectionResult -> {
            if (!connectionResult) {
                CompletableFuture<List<AwaitingTestUserModel>> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new SQLException("Не удалось восстановить соединение с базой данных"));
                return failedFuture;
            } else {
                return CompletableFuture.supplyAsync(() -> {
                    String sql = "SELECT * FROM " + DbConstants.TABLE_AWAITING_TESTS + " WHERE " +
                            DbConstants.COLUMN_AWAITING_USER_ID + " = ? AND " +
                            DbConstants.COLUMN_AWAITING_TEST_NAME + " LIKE ?;";
                    List<AwaitingTestUserModel> awaitingTests = new ArrayList<>();
                    try (PreparedStatement statement = db.getConnection().prepareStatement(sql)) {
                        statement.setLong(1, userId);
                        statement.setString(2, "%" + testName + "%"); // Используем LIKE для более гибкого поиска по имени теста
                        try (ResultSet rs = statement.executeQuery()) {
                            while (rs.next()) {
                                AwaitingTestUserModel testUser = new AwaitingTestUserModel(
                                        rs.getLong(DbConstants.COLUMN_AWAITING_USER_ID),
                                        rs.getString(DbConstants.COLUMN_AWAITING_TEST_NAME),
                                        rs.getTimestamp(DbConstants.COLUMN_AWAITING_TEST_TIME)
                                );
                                awaitingTests.add(testUser);
                            }
                            return awaitingTests;
                        }
                    } catch (SQLException e) {
                        db.getLogger().error("Ошибка при поиске данных ожидания теста для пользователя: " + e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                });
            }
        }).exceptionally(e -> {
            db.getLogger().error("Не удалось получить данные ожидания теста для пользователя", e);
            return Collections.emptyList(); // Возвращаем пустой список в случае ошибки
        });
    }

    public CompletableFuture<Boolean> removeOverdueAwaitingTestUsers() {
        return db.executeWithConnection().thenCompose(connectionResult -> {
            if (!connectionResult) {
                CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new SQLException("Не удалось восстановить соединение с базой данных"));
                return failedFuture;
            } else {
                return CompletableFuture.supplyAsync(() -> {
                    String sql = "DELETE FROM " + DbConstants.TABLE_AWAITING_TESTS +
                            " WHERE " + DbConstants.COLUMN_AWAITING_TEST_TIME + " < CURRENT_TIMESTAMP;";
                    try (PreparedStatement statement = db.getConnection().prepareStatement(sql)) {
                        int affectedRows = statement.executeUpdate();
                        return affectedRows > 0; // Возвращаем true, если были удалены какие-либо записи
                    } catch (SQLException e) {
                        db.getLogger().error("Ошибка при удалении просроченных записей ожидания теста: " + e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                });
            }
        }).exceptionally(e -> {
            db.getLogger().error("Не удалось удалить просроченные записи ожидания теста", e);
            return false; // Возвращаем false в случае ошибки
        });
    }

    public CompletableFuture<Integer> getQuestionCountByTableName(String tableName) {
        return db.executeWithConnection().thenCompose(connectionResult -> {
            if (!connectionResult) {
                return CompletableFuture.completedFuture(0);
            } else {
                return CompletableFuture.supplyAsync(() -> {
                    String sql = "SELECT COUNT(*) FROM " + tableName + ";";
                    try (PreparedStatement statement = db.getConnection().prepareStatement(sql);
                         ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        } else {
                            return 0;
                        }
                    } catch (SQLException e) {
                        db.getLogger().error("Ошибка при подсчете строк в таблице", e);
                        throw new RuntimeException(e);
                    }
                });
            }
        });
    }


}

