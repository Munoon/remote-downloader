package io.remotedownloader.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.Validatable;
import io.remotedownloader.util.JsonUtil;

public record StringMessage(
        int id,
        short command,
        String data
) {
    public <T extends Validatable> T parseJsonAndValidate(Class<T> clazz) {
        try {
            T t = JsonUtil.MAPPER.readValue(data, clazz);
            t.validate();
            return t;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T parseJson(Class<T> clazz) {
        try {
            return JsonUtil.MAPPER.readValue(data, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static StringMessage ok(StringMessage req) {
        return json(req.id, req.command(), null);
    }

    public static StringMessage error(StringMessage req, Error.ErrorTypes type, String message) {
        return json(req.id, ProtocolCommands.ERROR, new Error(type, message));
    }

    public static StringMessage json(StringMessage req, Object response) {
        return json(req.id, req.command, response);
    }

    public static StringMessage json(int id, short responseCommand, Object response) {
        String json = response != null ? JsonUtil.writeValueAsString(response) : null;
        return new StringMessage(id, responseCommand, json);
    }
}
