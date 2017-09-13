package xyz.arwx.trigger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;

/**
 * Created by macobas on 12/09/17.
 */
public class SlashTriggerCommand extends TriggerCommand
{
    public String responseUrl;

    @JsonCreator
    public SlashTriggerCommand(@JsonProperty("token") String token, @JsonProperty("team_id") String teamId,
                               @JsonProperty("channel_id") String channelId, @JsonProperty("user_id") String user,
                               @JsonProperty("command") String trigger,
                               @JsonProperty("text") String text, @JsonProperty("response_url") String responseUrl)
    {
        super(token, teamId, channelId, user, trigger, text);
        this.responseUrl = responseUrl;
    }

    @Override
    public TriggerResponse makeReply()
    {
        return SlashCommandResponse.of(new JsonObject().put("response_url", responseUrl));
    }
}
