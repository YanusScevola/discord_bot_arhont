package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.example.core.MainListenerAdapter;

public class Main {
    public static void main(String[] args) {
        JDA jda;

        try {
//            String token = new Properties().getProperty("token");
            String token = "MTI0MTA5MzAwMzIxNzQ3MzU3Ng.GbsS92.AYPSiffswYuQ8HxGMqhtQ40AdrfaoKziYihyaI";

            jda = JDABuilder.createDefault(token)
                    .setActivity(Activity.playing("Дебаты"))
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(new MainListenerAdapter())
                    .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
                    .enableIntents(GatewayIntent.GUILD_PRESENCES)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES)
                    .enableIntents(GatewayIntent.DIRECT_MESSAGES)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .setAutoReconnect(true)
                    .build();
            jda.awaitReady();



        } catch (Exception e) {
            System.err.println("Ошибка при создании JDA");
        }

    }

}