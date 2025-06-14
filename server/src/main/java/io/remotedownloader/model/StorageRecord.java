package io.remotedownloader.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        // name should match with StorageModel
        @JsonSubTypes.Type(value = DownloadingFile.class, name = "DOWNLOADING_FILE"),
        @JsonSubTypes.Type(value = User.class, name = "USER")
})
public sealed interface StorageRecord<T> permits DownloadingFile, User {
    @JsonIgnore
    T getId();

    @JsonIgnore
    StorageModel getModel();
}
