package org.example.data.source.db;

public class DbConstants {
    public static final int VERSION = 1;

    public static final String TABLE_DB_VERSION = "db_version";
    public static final String COLUMN_VERSION = "version";

    public static final String TABLE_APF_TEST = "apf_tests";
    public static final String COLUMN_TEST_ID = "id";
    public static final String COLUMN_TEST_QUESTION = "question";
    public static final String COLUMN_TEST_ANSWER_1 = "answer_1";
    public static final String COLUMN_TEST_ANSWER_2 = "answer_2";
    public static final String COLUMN_TEST_ANSWER_3 = "answer_3";
    public static final String COLUMN_TEST_ANSWER_4 = "answer_4";
    public static final String COLUMN_TEST_CORRECT_ANSWER = "correct_answer";
    public static final String COLUMN_TEST_DIFFICULTY_LEVEL = "difficulty_level";

    public static final String TABLE_THEMES = "apf_themes";
    public static final String COLUMN_THEMES_ID = "id";
    public static final String COLUMN_THEMES_NAME = "name";
    public static final String COLUMN_THEMES_USAGE_COUNT = "usage_count";

    public static final String TABLE_APF_DEBATERS = "apf_debaters";
    public static final String COLUMN_DEBATERS_ID = "user_id";
    public static final String COLUMN_DEBATERS_NICKNAME = "nickname";
    public static final String COLUMN_DEBATERS_SERVER_NICKNAME = "server_nickname";
    public static final String COLUMN_DEBATERS_APF_DEBATES_IDS = "apf_debates_ids";
    public static final String COLUMN_DEBATERS_LOSSES = "losses_debates_count";
    public static final String COLUMN_DEBATERS_WINS = "win_debates_count";

    public static final String TABLE_APF_DEBATES = "apf_debates";
    public static final String COLUMN_DEBATES_ID = "id";
    public static final String COLUMN_DEBATES_THEME_ID = "theme";
    public static final String COLUMN_DEBATES_GOVERNMENT_USERS_IDS = "government_users_ids";
    public static final String COLUMN_DEBATES_JUDGES_IDS = "judges_ids";
    public static final String COLUMN_DEBATES_OPPOSITION_USERS_IDS = "opposition_users_ids";
    public static final String COLUMN_DEBATES_DATE_TIME = "date_time";
    public static final String COLUMN_DEBATES_IS_GOVERNMENT_WINNER = "is_government_winner";

    public static final String TABLE_AWAITING_TESTS = "awaiting_tests";
    public static final String COLUMN_AWAITING_ID = "id";
    public static final String COLUMN_AWAITING_USER_ID = "user_id";
    public static final String COLUMN_AWAITING_TEST_NAME = "test_name";
    public static final String COLUMN_AWAITING_TEST_TIME = "test_time";


    private DbConstants() {
        // Приватный конструктор, чтобы предотвратить создание экземпляров класса
    }
}

