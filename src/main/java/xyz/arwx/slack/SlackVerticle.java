package xyz.arwx.slack;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.arwx.SlackRunBot;
import xyz.arwx.config.SlackConfig;
import xyz.arwx.livetrack.LiveTrackVerticle;
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
    private              Map<String, Handler<JsonObject>> slashHandlers = new HashMap<>();
    private static final Logger                           logger        = LoggerFactory.getLogger(SlackVerticle.class);
    private LocalMap<String, JsonObject> slackUserMap;

    // Requests come here
    public static final String InboundSlashCommand  = "Slack.Slash.In";
    // Replies go here
    public static final String OutboundSlashCommand = "Slack.Slash.Out";
    public static final String OutboundAnnounce     = "Slack.Announce.Out";
    public static final String SlackUserMap         = "slack.users";

    public void start()
    {
        SharedData sd = vertx.sharedData();
        slackUserMap = sd.getLocalMap(SlackUserMap);

        config = Json.objectFromJsonObject(config(), SlackConfig.class);
        registerHandlers();
        vertx.eventBus().consumer(InboundSlashCommand, this::onSlashCommand);
        vertx.eventBus().consumer(OutboundSlashCommand, this::onSlashReply);
        vertx.eventBus().consumer(OutboundAnnounce, this::onOutboundAnnounce);
        vertx.setPeriodic(60 * 5 * 1000, this::onUserNameTimer);
        fetchUsers();
    }

    private void onOutboundAnnounce(Message<JsonObject> msg)
    {
        vertx.createHttpClient(new HttpClientOptions().setSsl(true).setTrustAll(true)).postAbs(config.webhookUrl, res -> {
            logger.info("Announced to Slack, received {}", res.statusCode());
        }).putHeader("Content-type", "application/json").end(msg.body().encode());
    }

    private void fetchUsers()
    {
        logger.info("Fetching users");
        vertx.createHttpClient(
                new HttpClientOptions().setSsl(true).setTrustAll(true)).getAbs(apiUrl("users.list", config.oauthToken), response -> {
            if (response.statusCode() != 200)
                return;
            else
            {
                response.bodyHandler(buf -> this.updateUserMap(buf.toJsonObject()));
            }
        }).end();

    }

    private void onUserNameTimer(Long timerId)
    {
        logger.info("Username Timer");
        fetchUsers();
    }

    private void updateUserMap(JsonObject userMap)
    {
        if (userMap.getBoolean("ok") != true)
            return;

        // Not bothering with pagination right now.
        JsonArray memberList = userMap.getJsonArray("members");
        logger.info("Processing user map, {} users total", memberList.size());

        memberList.forEach(member -> {
            JsonObject mem = (JsonObject) member;
            JsonObject user = new JsonObject()
                    .put("nick", mem.getString("name"))
                    .put("realName", mem.getString("real_name"))
                    .put("userId", mem.getString("id"))
                    .put("tz", mem.getString("tz"));
            slackUserMap.putIfAbsent(mem.getString("name"), user);
        });
    }

    private String apiUrl(String method, String oauth)
    {
        return String.format("https://slack.com/api/%s?token=%s", method, oauth);
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
        slashHandlers.put("/livetrack", jso -> vertx.eventBus().publish(LiveTrackVerticle.InboundSlashCommand, jso));
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
