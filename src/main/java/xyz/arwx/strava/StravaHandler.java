package xyz.arwx.strava;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import xyz.arwx.SlackRunBot;
import xyz.arwx.async.AsyncTransaction;
import xyz.arwx.async.AsyncWork;
import xyz.arwx.config.StravaConfig;
import xyz.arwx.trigger.TriggerCommand;
import xyz.arwx.trigger.TriggerResponse;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static io.vertx.core.http.HttpMethod.GET;
import static xyz.arwx.strava.Units.UnitType.Imperial;
import static xyz.arwx.strava.Units.UnitType.Metric;

/**
 * Created by macobas on 19/07/17.
 */
public class StravaHandler implements Handler<TriggerCommand>
{
    private StravaConfig stravaConfig;

    public StravaHandler(StravaConfig config)
    {
        stravaConfig = config;
    }

    @Override
    public void handle(TriggerCommand request)
    {
        AsyncTransaction txn = buildTxn(request);
        txn.onComplete(result -> this.clubResultOK(result, request));
        txn.onFailure(result -> this.clubResultFail(result, request));
        txn.execute();
    }

    private AsyncTransaction buildTxn(TriggerCommand request)
    {
        AsyncTransaction txn = new AsyncTransaction(SlackRunBot.vertx);
        String clubUrl = clubUrl();
        txn.addWork(txn.httpWork(GET, clubUrl, new HttpClientOptions().setSsl(true).setTrustAll(true))
                       .setBodyType(AsyncWork.HttpCall.BodyType.JSONOBJECT)
                       .setName("clubInfo"));
        txn.addWork(txn.httpWork(GET, clubLbUrl(), new HttpClientOptions().setSsl(true).setTrustAll(true))
                       .setBodyType(AsyncWork.HttpCall.BodyType.JSONOBJECT)
                       .setName("clubLb"));

        AsyncWork.HttpCall lb = (AsyncWork.HttpCall) txn.getWorker("clubLb");
        lb.getRequest().putHeader("Accept", "text/javascript");
        return txn;
    }

    private void clubResultOK(JsonObject result, TriggerCommand request)
    {
        TriggerResponse response = request.makeReply();
        JsonObject clubInfo = result.getJsonObject("clubInfo").getJsonObject("body");
        JsonArray clubLeaderBoard = result.getJsonObject("clubLb").getJsonObject("body").getJsonArray("data");

        String text = request.text;
        Units uconv = Units.of(text != null && text.toLowerCase().contains("murica") ? Imperial : Metric);

        String sortBy = "distance";
        if(text != null)
        {
            if (text.toLowerCase().contains("elev"))
                sortBy = "elev_gain";
            else if (text.toLowerCase().contains("pace"))
                sortBy = "pace";
            else
                sortBy = "distance";
        }

        StringBuffer responseText = new StringBuffer();
        responseText.append(String.format("<http://www.strava.com/clubs/%s|%s>: %s club with %d members\n", clubInfo.getString("url"), clubInfo.getString("name"),
                clubInfo.getString("sport_type"),
                clubInfo.getInteger("member_count")));
        responseText.append(String.format("%d members on the leaderboard, ranging from %.2f %s - %.2f %s", clubLeaderBoard.size(),
                uconv.getDistance(clubLeaderBoard.getJsonObject(clubLeaderBoard.size() - 1).getDouble("distance")), uconv.distanceUnit().abbrev(),
                uconv.getDistance(clubLeaderBoard.getJsonObject(0).getDouble("distance")), uconv.distanceUnit().abbrev()));

        clubLeaderBoard.forEach(o -> {
            JsonObject jso = (JsonObject)o;
            jso.put("pace", jso.getInteger("moving_time") / uconv.getDistance(jso.getDouble("distance")));
        });

        final String sortParam = sortBy;
        clubLeaderBoard.getList().sort((a,b) -> (int)(((JsonObject)b).getDouble(sortParam) - ((JsonObject)a).getDouble(sortParam)));
        if(sortParam.equals("pace"))
            Collections.reverse(clubLeaderBoard.getList());

        StringBuffer lbTxt = new StringBuffer();
        for (int i = 0; i < Math.min(clubLeaderBoard.size(), 10); i++)
        {
            JsonObject lbEntry = clubLeaderBoard.getJsonObject(i);
            Duration d = Duration.ofSeconds(lbEntry.getInteger("moving_time"));
            Duration perK = Duration.ofSeconds(lbEntry.getDouble("pace").longValue());

            lbTxt.append(String.format("%d. *%s %s*: %.2f %s (^%.2f %s) in %s (%s/%s)\n",
                    i + 1,
                    lbEntry.getString("athlete_firstname"), lbEntry.getString("athlete_lastname"), uconv.getDistance(lbEntry.getDouble("distance")),
                    uconv.distanceUnit().abbrev(),
                    uconv.getElevation(lbEntry.getDouble("elev_gain")),
                    uconv.elevUnit().abbrev(),
                    String.format("%d:%02d:%02d",
                            d.toHours(), d.minusHours(d.toHours()).toMinutes(),
                            d.minusMinutes(d.toMinutes()).getSeconds()),
                    String.format("%d:%02d", perK.toMinutes(), perK.minusMinutes(perK.toMinutes()).getSeconds()),
                    uconv.distanceUnit().abbrev()));
        }

        response.responseText = responseText.toString();
        response.attachments.add(new HashMap<String, Object>()
        {{
            put("color", "#36a64f");
            put("author_icon", clubInfo.getString("profile_medium"));
            put("author_name", "Top 10 Leaders" + (uconv.toType() == Imperial ? " (of FREEDOM - FUCK YEAH!)" : ""));
            put("text", lbTxt.toString());
            put("mrkdwn_in", new ArrayList<String>()
            {{
                add("text");
            }});
        }});

        response.send(SlackRunBot.vertx);
    }

    private void clubResultFail(JsonObject result, TriggerCommand request)
    {
        TriggerResponse response = request.makeReply();
        response.setIsError(true);
        response.responseText = "Unable to retrieve Strava details - raw message follows";
        response.attachments.add(new HashMap<String, Object>()
        {{
            put("text", result.encode());
        }});
        response.send(SlackRunBot.vertx);
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
