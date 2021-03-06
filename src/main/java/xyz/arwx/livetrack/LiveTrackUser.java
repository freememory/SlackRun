package xyz.arwx.livetrack;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import xyz.arwx.SlackRunBot;
import xyz.arwx.slack.SlackVerticle;

import java.time.Duration;

import static xyz.arwx.livetrack.LiveTrackUser.TrackStatus.*;
import static xyz.arwx.livetrack.LiveTrackVerticle.apiUrl;
import static xyz.arwx.livetrack.LiveTrackVerticle.garminUrl;

/**
 * Created by macobas on 24/07/17.
 */
public class LiveTrackUser
{
    public enum TrackStatus
    {
        StartedNotAnnounced,
        InProgress,
        DoneNotAnnounced,
        Done,
        Dead
    }

    private String nick;
    private String sessionId;
    private String token;
    private int    failedFetches;

    private JsonObject userInfo;
    private JsonObject sessionInfo;
    private JsonObject lastTracklog;

    Long timeCreated;
    private TrackStatus trackStatus;

    public LiveTrackUser(String nick, JsonObject userInfo, String sessionId, String token)
    {
        this.nick = nick;
        this.userInfo = userInfo;
        this.sessionId = sessionId;
        this.token = token;
        this.timeCreated = System.currentTimeMillis();
        this.trackStatus = StartedNotAnnounced;
        this.sessionInfo = new JsonObject();
        this.lastTracklog = new JsonObject();
        this.failedFetches = 0;
    }

    public String nick()
    {
        return nick;
    }

    public JsonObject userInfo()
    {
        return userInfo;
    }

    public String sesh()
    {
        return sessionId;
    }

    public String tok()
    {
        return token;
    }

    public JsonObject sessionInfo()
    {
        return sessionInfo;
    }

    public JsonObject trackLog()
    {
        return lastTracklog;
    }

    public boolean inProgress()
    {
        return trackStatus == InProgress;
    }

    public LiveTrackUser nick(String nick)
    {
        this.nick = nick;
        return this;
    }

    public LiveTrackUser sesh(String sessionId)
    {
        this.sessionId = sessionId;
        return this;
    }

    public LiveTrackUser tok(String tok)
    {
        this.token = tok;
        return this;
    }

    public LiveTrackUser userInfo(JsonObject ui)
    {
        this.userInfo = ui;
        return this;
    }

    public void updateTrackLog()
    {
        SlackRunBot.vertx.createHttpClient(new HttpClientOptions().setSsl(true).setTrustAll(true))
                         .getAbs(apiUrl("trackLog", sessionId, token), resp -> {
                             if (resp.statusCode() != 200)
                                 return;
                             else
                                 resp.bodyHandler(buf -> handleTracklog(buf.toJsonArray()));
                         }).end();
    }

    private void handleTracklog(JsonArray objects)
    {
        JsonObject lastLog = objects.getJsonObject(objects.size() - 1);

        // If we've been paused, don't update the trackLog.
        if(lastTracklog == null || trackStatus == DoneNotAnnounced ||
            (!lastLog.getJsonObject("metaData").getString("TOTAL_DURATION")
            .equals(lastTracklog.getJsonObject("metaData").getString("TOTAL_DURATION")) ||
            !lastLog.getJsonArray("events").equals(lastTracklog.getJsonArray("events"))))
            lastTracklog = lastLog;

        // If you've been paused for 30 minutes, or we contain an END event or we're expired
        if(System.currentTimeMillis() - lastTracklog.getLong("timestamp") > 30 * 60 * 1000 ||
                lastTracklog.getJsonArray("events").contains("END") ||
                trackStatus == DoneNotAnnounced)
            announceEnd();
    }

    public void updateStatus()
    {
        if (isDone())
            return;
        SlackRunBot.vertx.createHttpClient(new HttpClientOptions().setSsl(true).setTrustAll(true))
                         .getAbs(apiUrl("session", sessionId, token), resp -> {
                             if (resp.statusCode() != 200)
                             {
                                 failedFetches++;
                                 if (failedFetches == 5)
                                     trackStatus = Dead;
                             }
                             else
                                 resp.bodyHandler(buf -> this.handleStatus(buf.toJsonObject()));
                         }).end();
    }

    public boolean isPurgeable()
    {
        switch(trackStatus)
        {
            case Done:
            case Dead:
                return true;
            default:
                return false;
        }
    }

    public boolean isDone()
    {
        return isPurgeable() || trackStatus == DoneNotAnnounced;
    }

    private void handleStatus(JsonObject obj)
    {
        Long now = System.currentTimeMillis();
        failedFetches = 0;
        String status = obj.getString("sessionStatus");
        sessionInfo = obj;
        if (!status.equals("InProgress"))
        {
            // If we're not in progress - don't announce the end
            if (trackStatus == InProgress)
                trackStatus = DoneNotAnnounced;
        }
        else
        {
            // 2 minutes grace for mistaken presses.
            if (trackStatus == StartedNotAnnounced && (now - timeCreated) >= 2 * 60 * 1000)
                announceStart();
        }
    }

    private void announceStart()
    {
        String url = garminUrl("https://livetrack.garmin.com/session", sessionId, token);
        SlackRunBot.vertx.eventBus().publish(SlackVerticle.OutboundAnnounce, new JsonObject()
                .put("text", String.format("%s has started a LiveTrack activity! You can watch <%s|here>, or use /livetrack or /livetrack <nick> to monitor!", nick, url)));
        trackStatus = InProgress;
    }

    public void announceEnd()
    {
        Duration d = Duration.ofMillis(new Double(Double.parseDouble(lastTracklog.getJsonObject("metaData").getString("TOTAL_DURATION"))).longValue());
        trackStatus = Done;
        SlackRunBot.vertx.eventBus().publish(SlackVerticle.OutboundAnnounce, new JsonObject()
                .put("text", String.format("<@%s|%s> has completed a LiveTrack activity!\nTotal time: %s, total distance: %.2f km",
                        userInfo.getString("userId"),
                        nick, String.format("%d:%02d:%02d",
                                d.toHours(), d.minusHours(d.toHours()).toMinutes(),
                                d.minusMinutes(d.toMinutes()).getSeconds()), Double.parseDouble(lastTracklog.getJsonObject("metaData").getString("TOTAL_DISTANCE")) / 1000.)));
    }
}
