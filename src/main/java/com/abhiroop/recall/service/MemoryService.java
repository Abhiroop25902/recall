package com.abhiroop.recall.service;

import com.abhiroop.recall.dto.SaveMemoryRequestDto;
import com.abhiroop.recall.entity.Memory;
import com.abhiroop.recall.repository.MemoryRepository;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.VectorValue;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public MemoryService(MemoryRepository memoryRepository, EmbeddingModel embeddingModel) {
        this.memoryRepository = memoryRepository;
        this.embeddingModel = embeddingModel;
    }

    @Tool(description = "Save Memory")
    Memory saveMemory(SaveMemoryRequestDto dto) {
        final float[] embedding = embeddingModel.embed(dto.text());

        final VectorValue embeddingVector = FieldValue.vector(
                Doubles.toArray(Floats.asList(embedding))
        );

        final var memory = Memory
                .builder()
                .appId(dto.appId())
                .text(dto.text())
                .embedding(embeddingVector)
                .createdAt(Timestamp.now())
                .build();


        return memoryRepository.save(memory);
    }

    @Tool(description = "Get memories for an app")
    List<Memory> getMemories(String appId) {
        return memoryRepository.findByAppId(appId);
    }

    @Tool(description = "Delete a memory by id")
    void deleteMemory(String id) {
        memoryRepository.deleteById(id);
    }
}
