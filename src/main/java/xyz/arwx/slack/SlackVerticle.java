package xyz.arwx.slack;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import xyz.arwx.SlackRunBot;
import xyz.arwx.config.SlackConfig;
import xyz.arwx.strava.StravaHandler;
import xyz.arwx.util.Json;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by macobas on 19/07/17.
 */
public class SlackVerticle extends AbstractVerticle
{
    private SlackConfig config;
    private             Map<String, Handler<JsonObject>> slashHandlers       = new HashMap<>();
    // Requests come here
    public static final String                           InboundSlashCommand = "Slack.Slash.In";

    // Replies go here
    public static final String OutboundSlashCommand = "Slack.Slash.Out";

    public void start()
    {
        config = Json.objectFromJsonObject(config(), SlackConfig.class);
        registerHandlers();
        vertx.eventBus().consumer(InboundSlashCommand, this::onSlashCommand);
        vertx.eventBus().consumer(OutboundSlashCommand, this::onSlashReply);
    }

    private void onSlashReply(Message<JsonObject> msg)
    {
        SlashCommandResponse scr = Json.objectFromJsonObject(msg.body(), SlashCommandResponse.class);
        scr.send(vertx);
    }

    public void onSlashCommand(Message<JsonObject> msg)
    {
        JsonObject event = msg.body();
        if (!event.getString("token").equals(config.verificationToken))
            throw new RuntimeException("Bad token - does not match verificaiton token?");

        slashHandlers.getOrDefault(event.getString("command"), v -> {
            SlashCommandResponse scr = SlashCommandResponse.of(event);
            scr.responseText = "Err, I didn't quite get that?";
            vertx.eventBus().publish(SlackVerticle.OutboundSlashCommand, Json.objectToJsonObject(scr));
        }).handle(event);
    }

    public void registerHandlers()
    {
        slashHandlers.put("/strava", new StravaHandler(SlackRunBot.config.stravaConfig));
        //slashHandlers.put("/botstat", this::statHandler);
        //slashHandlers.put("/8ball", this::fortuneHandler);
    }

    private void fortuneHandler(JsonObject entries)
    {

    }

    private void statHandler(JsonObject entries)
    {

    }

    private void stravaHandler(JsonObject entries)
    {
        // right now just handle the naked Strava command
    }
}
