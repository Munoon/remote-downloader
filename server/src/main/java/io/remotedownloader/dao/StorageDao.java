package io.remotedownloader.dao;

import com.fasterxml.jackson.databind.ObjectReader;
import io.remotedownloader.ServerProperties;
import io.remotedownloader.model.StorageAction;
import io.remotedownloader.model.StorageModel;
import io.remotedownloader.model.StorageRecord;
import io.remotedownloader.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class StorageDao {
    private static final Logger log = LogManager.getLogger(StorageDao.class);
    private final Path storagePath;
    private final ThreadPoolsHolder threadPoolsHolder;

    public StorageDao(ServerProperties serverProperties,
                      ThreadPoolsHolder threadPoolsHolder) {
        this.storagePath = Path.of(serverProperties.getStorageFile());
        this.threadPoolsHolder = threadPoolsHolder;

        if (!Files.exists(storagePath)) {
            try {
                Files.createFile(storagePath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create storage file " + storagePath, e);
            }
        }
    }

    public void saveRecord(StorageRecord<?> record) {
        saveEntry(new StorageAction.Save(record));
    }

    public void deleteRecord(StorageModel model, Object id) {
        saveEntry(new StorageAction.Delete(model, id));
    }

    private void saveEntry(StorageAction action) {
        threadPoolsHolder.storageThreadPoolExecutor.execute(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(storagePath, StandardOpenOption.APPEND)) {
                String json = JsonUtil.writeValueAsString(action);
                if (json != null) {
                    writer.write(json);
                    writer.write('\n');
                }
            } catch (Exception e) {
                log.warn("Failed to save the record to the storage file", e);
                throw new RuntimeException(e);
            }
        });
    }

    public <I, T extends StorageRecord<I>> Map<I, T> readAllRecords(StorageModel model) {
        Map<I, T> result = new HashMap<>();

        ObjectReader reader = JsonUtil.MAPPER.readerFor(StorageAction.class);
        try (BufferedReader br = new BufferedReader(new FileReader(storagePath.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                StorageAction action = reader.readValue(line);
                switch (action) {
                    case StorageAction.Save(StorageRecord<?> record) -> {
                        if (record.getModel() == model) {
                            //noinspection unchecked
                            result.put((I) record.getId(), (T) record);
                        }
                    }

                    case StorageAction.Delete(StorageModel deleteModel, Object id) -> {
                        if (deleteModel == model) {
                            //noinspection SuspiciousMethodCalls
                            result.remove(id);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to read the storage file.", e);
        }

        return result;
    }
}
