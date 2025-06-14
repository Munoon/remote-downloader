package io.remotedownloader.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StorageAction.Save.class, name = "save"),
        @JsonSubTypes.Type(value = StorageAction.Delete.class, name = "delete")
})
public sealed interface StorageAction {
    record Save(StorageRecord<?> record) implements StorageAction {
    }

    record Delete(StorageModel model, Object id) implements StorageAction {
    }
}
