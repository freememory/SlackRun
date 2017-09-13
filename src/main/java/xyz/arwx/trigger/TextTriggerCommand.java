package xyz.arwx.trigger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by macobas on 12/09/17.
 */
public class TextTriggerCommand extends TriggerCommand
{
    public  String ts;
    private String postAuthToken;

    @JsonCreator
    public TextTriggerCommand(@JsonProperty("token") String token, @JsonProperty("team_id") String teamId,
                              @JsonProperty("command") String trigger, // we artificially hack this into the JSON
                              @JsonProperty("event") Map<String, String> event)
    {
        super(token, teamId, event.getOrDefault("channel", ""),
                event.getOrDefault("user", ""), trigger, event.getOrDefault("text", ""));
        this.ts = event.getOrDefault("ts", "");
    }

    @JsonProperty("event")
    public Map<String, String> getEvent()
    {
        Map<String, String> ret = new HashMap<>();
        ret.put("channel", channelId);
        ret.put("user", user);
        ret.put("text", text);
        ret.put("ts", ts);

        return ret;
    }

    @JsonProperty("postAuthToken")
    public String getPostAuthToken()
    {
        return postAuthToken;
    }

    public TextTriggerCommand setPostAuthToken(String postAuthToken)
    {
        this.postAuthToken = postAuthToken;
        return this;
    }

    @Override
    public TriggerResponse makeReply()
    {
        TextCommandResponse tcr = new TextCommandResponse();
        tcr.thread_ts = ts;
        tcr.channel = channelId;
        tcr.token = postAuthToken;
        tcr.responseUrl = "https://slack.com/api/chat.postMessage";
        return tcr;
    }
}
