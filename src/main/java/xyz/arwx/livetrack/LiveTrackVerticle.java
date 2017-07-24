package xyz.arwx.livetrack;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.arwx.mail.MailVerticle;
import xyz.arwx.slack.SlackVerticle;
import xyz.arwx.slack.SlashCommandResponse;
import xyz.arwx.util.Json;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by macobas on 23/07/17.
 */
public class LiveTrackVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(LiveTrackVerticle.class);
    public static final String InboundSlashCommand = "LiveTrackVerticle.Slack.Slash.In";
    private LocalMap<String, JsonObject> slackUserMap;
    private Map<String, LiveTrackUser> nickToUserMap = new HashMap<>();

    public void start()
    {
        vertx.eventBus().consumer(MailVerticle.OutboundAddress, this::onNewMail);
        vertx.eventBus().consumer(LiveTrackVerticle.InboundSlashCommand, this::onSlashCommand);
        vertx.setPeriodic(30 * 1000, this::onMonitorActivityTimer);
        slackUserMap = vertx.sharedData().getLocalMap(SlackVerticle.SlackUserMap);
    }

    private List<LiveTrackUser> runningUsers()
    {
        return nickToUserMap.values().stream().filter(l -> l.inProgress()).collect(Collectors.toList());
    }

    public void onSlashCommand(Message<JsonObject> slashCommandMsg)
    {
        JsonObject slashCommand = slashCommandMsg.body();
        String text = slashCommand.getString("text");
        if(text != null && text.toLowerCase().equals("help"))
        {
            SlashCommandResponse scr = SlashCommandResponse.of(slashCommand);
            String email = "challenge.runbot+" + slashCommand.getString("user_name") + "@gmail.com";
            scr.responseType = SlashCommandResponse.ResponseType.Ephemeral;
            scr.responseText = "Use /livetrack with no options to see who's currently running, and where they are.\n" +
                    "Use /livetrack <nick> to see some more detailed output for the specific person who's running.\n\n" +
                    "If you own a Garmin appliance that can use the LiveConnect feature, you can be tracked by me. Set up is as follows:\n " +
                    "1. Go into Garmin Connect on your phone.\n" +
                    "2. Bring up the menu and scroll down until you see LiveTrack.\n" +
                    "3. Turn on 'Auto Start.' This will automatically start LiveTracking when you start running.\n" +
                    "4. Turn on E-mail for Share Session.\n" +
                    String.format("5. Add the following address to the list of recipients: <mailto:%s|%s>\n", email, email) +
                    "6. That's it. Now when you start running, your e-mail will be sent to the bot. You can add other recipients as well; each person gets an individual e-mail.";
            vertx.eventBus().publish(SlackVerticle.OutboundSlashCommand, Json.objectToJsonObject(scr));
            return;
        }

        if(text == null || text.length() == 0)
        {
            List<LiveTrackUser> users = runningUsers();
            SlashCommandResponse scr = SlashCommandResponse.of(slashCommand);
            scr.responseText = String.format("%d users total currently running", users.size());

            HashMap<String, Object> attachments = new HashMap<>();
            attachments.put("color", "#36a64f");
            StringBuffer runnersTxt = new StringBuffer();

            for (int i = 0; i < users.size(); i++)
            {
                LiveTrackUser u = users.get(i);
                String shortText = getSessionInfoText(u);
                runnersTxt.append(String.format("%d. %s\n", i + 1, shortText));
            }

            attachments.put("text", runnersTxt.toString());
            scr.attachments.add(attachments);
            scr.send(vertx);

            return;
        } else
        {
            LiveTrackUser ltu = nickToUserMap.get(text);
            if (ltu == null)
            {
                SlashCommandResponse scr = SlashCommandResponse.of(slashCommand);
                scr.responseText = "I don't know who that user is - try again.";
                scr.responseType = SlashCommandResponse.ResponseType.Ephemeral;
                scr.send(vertx);
            }
            else
            {
                String shortText = getSessionInfoText(ltu);
                String tlText = getTrackLogText(ltu);
                SlashCommandResponse scr = SlashCommandResponse.of(slashCommand);
                scr.responseText = shortText;
                scr.attachments.add(new HashMap<String, Object>()
                {{
                    put("text", tlText);
                }});

                scr.send(vertx);
            }
        }
    }

    private String getTrackLogText(LiveTrackUser ltu)
    {
        JsonObject md = ltu.trackLog().getJsonObject("metaData");
        Duration d = Duration.ofMillis(new Double(Double.parseDouble(md.getString("TOTAL_DURATION"))).longValue());
        Double distance = Double.parseDouble(md.getString("TOTAL_DISTANCE"));
        Double metersPerSec = Double.parseDouble(md.getString("SPEED"));
        Integer bpm = Integer.parseInt(md.getString("HEART_RATE"));
        return String.format("Distance: %.2f km\nTime:%s\nHR:%d\nSpeed:%.2f kmh",
                distance.doubleValue() / 1000.,
                String.format("%d:%02d:%02d",
                        d.toHours(), d.minusHours(d.toHours()).toMinutes(),
                        d.minusMinutes(d.toMinutes()).getSeconds()),
                bpm.intValue(),
                ( metersPerSec * 60. * 60. ) / 1000.);
    }

    public String getSessionInfoText(LiveTrackUser user)
    {
        long epochStart = user.sessionInfo().getLong("startTime") / 1000L;
        LocalDateTime ldt = LocalDateTime.ofEpochSecond(epochStart, 0, ZoneOffset.UTC);
        return String.format("<@%s|%s> activity titled <%s|%s> <!date^%d^Started {date_num} at {time_secs}|Started %s>",
                user.userInfo().getString("userId"), user.userInfo().getString("nick"),
                garminUrl("https://livetrack.garmin.com/session", user.sesh(), user.tok()),
                user.sessionInfo().getString("sessionName"), epochStart, ldt.format(DateTimeFormatter.ISO_DATE_TIME));
    }

    public void onMonitorActivityTimer(Long timerId)
    {
        // Cull the list of dead and done users
        nickToUserMap.entrySet().removeIf(e->e.getValue().isDone());
        Set<String> nicks = nickToUserMap.keySet();
        nicks.forEach(this::updateActivityStatus);
        nicks.forEach(this::updateTracklog);
    }

    private void updateTracklog(String nick)
    {
        LiveTrackUser ltu = nickToUserMap.get(nick);
        if (ltu == null)
            return;

        ltu.updateTrackLog();
    }

    public static String apiUrl(String service, String sessionId, String token)
    {
        String base = "https://livetrack.garmin.com/services";
        return garminUrl(String.format("%s/%s", base, service), sessionId, token);
    }

    public static String garminUrl(String base, String sessionId, String token)
    {
        return String.format("%s/%s/token/%s", base, sessionId, token);
    }

    private void updateActivityStatus(String nick)
    {
        LiveTrackUser ltu = nickToUserMap.get(nick);
        if(ltu == null)
            return;

        ltu.updateStatus();
    }

    public void onNewMail(Message<JsonObject> emailMsg)
    {
        JsonObject msg = emailMsg.body();

        // Is this from Garmin?
        if(!msg.getString("from").toLowerCase().endsWith("@garmin.com"))
            return;

        // Is this sent to an address like challenge.runbot+YOURNICK@gmail.com?
        JsonArray to = msg.getJsonArray("to");
        List<String> recipients = to.stream().map(String.class::cast).filter(s->s.toLowerCase().startsWith("challenge.runbot+")).collect(Collectors.toList());
        if(recipients.size() == 0)
            return;

        // Get the nicks
        String nick = null;
        for(String recipient : recipients)
        {
            String lc = recipient.toLowerCase();
            String[] split = lc.split("@");
            if(split.length != 2)
                continue;
            String name = split[0];
            nick = name.replace("challenge.runbot+", "");
        }

        // Can we resolve this nick? If you're not in the Slack group, gtfo.
        JsonObject nickInfo = resolveNick(nick);
        if(nickInfo == null)
            return;

        // Get the sesh and token ids
        String body = msg.getString("body");
        Pattern p = Pattern.compile("href=\"(?:http|https)://livetrack\\.garmin\\.com/session/(.*)/token/([^\"]*)\"");
        Matcher m = p.matcher(body);
        Set<String> urls = new HashSet<>();
        String sesh = null, tok = null;
        while(m.find())
        {
            sesh = m.group(1);
            tok = m.group(2);
        }

        LiveTrackUser ltu = new LiveTrackUser(nick, nickInfo, sesh, tok);
        nickToUserMap.put(ltu.nick(), ltu);
    }

    private JsonObject resolveNick(String nick)
    {
        JsonObject user = slackUserMap.get(nick);
        if(user == null)
        {
            Collection<JsonObject> members = slackUserMap.values();
            for(JsonObject jso : members)
            {
                if(jso.getString("userId").equals(nick))
                {
                    user = jso;
                    break;
                }
            }
        }

        return user;
    }
}
