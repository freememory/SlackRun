package xyz.arwx.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;

import java.io.IOException;

/**
 * Created by macobas on 23/05/17.
 */
public class Json
{
    private        ObjectMapper mapper;
    private static Json         instance;

    private Json()
    {
        mapper = new ObjectMapper();
    }

    public static Json Instance()
    {
        if (instance == null)
            instance = new Json();
        return instance;
    }

    public static <T> T objectFromJsonObject(JsonObject obj, Class<T> clazz)
    {
        try
        {
            return Instance().mapper.readValue(obj.toString(), clazz);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static <T> JsonObject objectToJsonObject(T obj)
    {
        try
        {
            return new JsonObject(Instance().mapper.writeValueAsString(obj));
        }
        catch (JsonProcessingException e)
        {
            e.printStackTrace();
        }

        return null;
    }
}
