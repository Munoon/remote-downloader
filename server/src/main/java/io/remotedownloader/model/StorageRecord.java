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
        @JsonSubTypes.Type(value = DownloadingFile.class, name = "downloading_file"),
        @JsonSubTypes.Type(value = User.class, name = "user")
})
public sealed interface StorageRecord<T> permits DownloadingFile, User {
    @JsonIgnore
    T getId();
}
