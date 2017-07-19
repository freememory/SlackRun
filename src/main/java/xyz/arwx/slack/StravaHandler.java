package xyz.arwx.slack;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import xyz.arwx.SlackRunBot;
import xyz.arwx.async.AsyncTransaction;
import xyz.arwx.async.AsyncWork;
import xyz.arwx.config.SlackConfig;
import xyz.arwx.config.StravaConfig;
import xyz.arwx.util.Json;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.vertx.core.http.HttpMethod.GET;

/**
 * Created by macobas on 19/07/17.
 */
public class StravaHandler implements Handler<JsonObject>
{
    private StravaConfig stravaConfig;

    public StravaHandler(StravaConfig config)
    {
        stravaConfig = config;
    }

    @Override
    public void handle(JsonObject request)
    {
        AsyncTransaction txn = buildTxn(request);
        txn.onComplete(result -> this.clubResultOK(result, request));
        txn.onFailure(result -> this.clubResultFail(result, request));
        txn.execute();
    }

    private AsyncTransaction buildTxn(JsonObject request)
    {
        AsyncTransaction txn = new AsyncTransaction(SlackRunBot.vertx);
        String clubUrl = clubUrl();
        txn.addWork(txn.httpWork(GET, clubUrl, new HttpClientOptions().setSsl(true).setTrustAll(true))
                 .setBodyType(AsyncWork.HttpCall.BodyType.JSONOBJECT)
                 .setName("clubInfo"));
        txn.addWork(txn.httpWork(GET, clubLbUrl(), new HttpClientOptions().setSsl(true).setTrustAll(true))
                 .setBodyType(AsyncWork.HttpCall.BodyType.JSONOBJECT)
                 .setName("clubLb"));

        AsyncWork.HttpCall lb = (AsyncWork.HttpCall)txn.getWorker("clubLb");
        lb.getRequest().putHeader("Accept", "text/javascript");
        return txn;
    }

    private void clubResultOK(JsonObject result, JsonObject request)
    {
        SlashCommandResponse response = SlashCommandResponse.of(request);
        JsonObject clubInfo = result.getJsonObject("clubInfo").getJsonObject("body");
        JsonArray clubLeaderBoard = result.getJsonObject("clubLb").getJsonObject("body").getJsonArray("data");

        response.responseUrl = request.getString("response_url");
        StringBuffer responseText = new StringBuffer();
        responseText.append(String.format("<http://www.strava.com/clubs/%s|%s>: %s club with %d members\n", clubInfo.getString("url"), clubInfo.getString("name"),
                clubInfo.getString("sport_type"),
                clubInfo.getInteger("member_count")));
        responseText.append(String.format("%d members on the leaderboard, ranging from %.2f km - %.2f km", clubLeaderBoard.size(),
                clubLeaderBoard.getJsonObject(clubLeaderBoard.size() - 1).getDouble("distance") / 1000., clubLeaderBoard.getJsonObject(0).getDouble("distance") / 1000.));

        StringBuffer lbTxt = new StringBuffer();
        for(int i = 0; i < Math.min(clubLeaderBoard.size(), 10); i++)
        {
            JsonObject lbEntry = clubLeaderBoard.getJsonObject(i);
            Duration d = Duration.ofSeconds(lbEntry.getInteger("moving_time"));
            Duration perK = Duration.ofSeconds(new Double(lbEntry.getInteger("moving_time") / ( lbEntry.getDouble("distance") / 1000. )).longValue());

            lbTxt.append(String.format("%d. *%s %s*: %.2f km (^%.2f m) in %s (%s/km)\n",
                    i + 1,
                    lbEntry.getString("athlete_firstname"), lbEntry.getString("athlete_lastname"), lbEntry.getDouble("distance") / 1000.,
                    lbEntry.getDouble("elev_gain"),
                    String.format("%d:%02d:%02d",
                            d.toHours(), d.minusHours(d.toHours()).toMinutes(),
                            d.minusMinutes(d.toMinutes()).getSeconds()),
                    String.format("%d:%02d", perK.toMinutes(), perK.minusMinutes(perK.toMinutes()).getSeconds())));
        }

        response.responseText = responseText.toString();
        response.attachments.add(new HashMap<String, Object>() {{
            put("color", "#36a64f");
            put("text", lbTxt.toString());
            put("mrkdwn_in", new ArrayList<String>() {{
                add("text");
            }});
        }});

        SlackRunBot.vertx.eventBus().publish(SlackVerticle.OutboundSlashCommand, Json.objectToJsonObject(response));
    }

    private void clubResultFail(JsonObject result, JsonObject request)
    {
        SlashCommandResponse response = SlashCommandResponse.of(request);
        response.responseType = SlashCommandResponse.ResponseType.Ephemeral;
        response.responseText = "Unable to retrieve Strava details - raw message follows";
        response.attachments.add(new HashMap<String, Object>() {{
            put("text", result.encode());
        }});
        SlackRunBot.vertx.eventBus().publish(SlackVerticle.OutboundSlashCommand, Json.objectToJsonObject(response));
    }

    private String clubUrl()
    {
        return clubUrl(stravaConfig.defaultClubId);
    }

    private String clubUrl(String clubId)
    {
        return apiUrl("https://www.strava.com/api/v3/clubs/{0}", true, clubId);
    }

    private String clubLbUrl()
    {
        return clubLbUrl(stravaConfig.defaultClubId);
    }

    private String clubLbUrl(String clubId)
    {
        return apiUrl("https://www.strava.com/clubs/{0}/leaderboard", false, clubId);
    }

    private String apiUrl(String url, boolean addToken, String... allArgs)
    {
        String formatted = MessageFormat.format(url, allArgs);
        return addToken ? formatted + "?access_token=" + stravaConfig.accessToken : formatted;
    }

}
