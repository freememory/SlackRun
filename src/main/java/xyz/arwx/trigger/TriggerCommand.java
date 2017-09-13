package xyz.arwx.trigger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Created by macobas on 12/09/17.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "slash", value = SlashTriggerCommand.class),
        @JsonSubTypes.Type(name = "text", value = TextTriggerCommand.class)
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class TriggerCommand
{
    public String token;
    public String teamId;
    public String channelId;
    public String user;
    public String text;
    public String trigger;

    protected TriggerCommand(String token, String teamId, String channelId, String user, String trigger, String text)
    {
        this.token = token;
        this.teamId = teamId;
        this.channelId = channelId;
        this.user = user;
        this.text = text;
        this.trigger = trigger;
    }

    public abstract TriggerResponse makeReply();

}
