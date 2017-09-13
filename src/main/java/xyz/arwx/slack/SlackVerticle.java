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
import xyz.arwx.trigger.SlashCommandResponse;
import xyz.arwx.trigger.TextTriggerCommand;
import xyz.arwx.trigger.TriggerCommand;
import xyz.arwx.util.Json;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by macobas on 19/07/17.
 */
public class SlackVerticle extends AbstractVerticle
{
    private SlackConfig config;
    private              Map<String, Handler<TriggerCommand>> slashHandlers = new HashMap<>();
    private static final Logger                               logger        = LoggerFactory.getLogger(SlackVerticle.class);
    private LocalMap<String, JsonObject> slackUserMap;

    // Requests come here
    public static final String InboundSlashCommand  = "Slack.Slash.In";
    public static final String InboundEvent         = "Slack.Event.In";
    public static final String GetHandlerList       = "SlackVerticle.GetHandlerList";
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
        vertx.eventBus().consumer(InboundEvent, this::onInboundEvent);
        vertx.eventBus().consumer(GetHandlerList, this::getHandlers);
        vertx.setPeriodic(60 * 5 * 1000, this::onUserNameTimer);
        fetchUsers();
    }

    private void onInboundEvent(Message<JsonObject> message)
    {
        JsonObject body = message.body();
        if (!body.getJsonObject("event").getString("type").equals("message"))
            return;
        body.put("type", "text");
        // TRIGGER WARNING. lolz.
        String text = body.getJsonObject("event").getString("text");
        if (text == null || !text.startsWith(config.textTriggerPrefix))
            return;

        String replaced = text.replaceFirst("!", "/");
        String[] split = replaced.split(" ");
        body.put("command", split[0]);
        body.getJsonObject("event").put("text", String.join(" ", Arrays.asList(split).subList(1, split.length)));
        TextTriggerCommand ttc = (TextTriggerCommand) Json.objectFromJsonObject(body, TriggerCommand.class);
        ttc.setPostAuthToken(config.botUserAuthToken);
        handleTheCommand(ttc);
    }

    private void getHandlers(Message msg)
    {
        JsonArray ret = new JsonArray();
        slashHandlers.keySet().forEach(ret::add);
        msg.reply(ret);
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
        event.put("type", "slash");
        handleTheCommand(Json.objectFromJsonObject(event, TriggerCommand.class));
    }

    public void handleTheCommand(TriggerCommand command)
    {
        slashHandlers.getOrDefault(command.trigger, v -> {
            command.makeReply().setResponseText("Err, I didn't quite get that?").setIsError(true).send(vertx);
        }).handle(command);
    }

    public void registerHandlers()
    {
        slashHandlers.put("/strava", new StravaHandler(SlackRunBot.config.stravaConfig));
        slashHandlers.put("/livetrack", triggerCommand -> vertx.eventBus().publish(LiveTrackVerticle.InboundSlashCommand,
                Json.objectToJsonObject(triggerCommand)));
    }
}
