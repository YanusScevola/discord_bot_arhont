package org.example.data.models;

import java.util.List;

public class QuestionModel {
    private int id;
    private String text;
    private List<String> answers;
    private String correctAnswer;

    public QuestionModel(int id, String text, List<String> answers, String correctAnswer) {
        this.id = id;
        this.text = text;
        this.answers = answers;
        this.correctAnswer = correctAnswer;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public void setAnswers(List<String> answers) {
        this.answers = answers;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }
}
