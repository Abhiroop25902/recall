package com.abhiroop.recall.service;

import com.abhiroop.recall.dto.SaveMemoryRequestDto;
import com.abhiroop.recall.entity.Memory;
import com.abhiroop.recall.repository.MemoryRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryService {

    private final MemoryRepository memoryRepository;

    public MemoryService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @Tool(description = "Save Memory")
    Memory saveMemory(SaveMemoryRequestDto dto) {
        return memoryRepository.save(
                Memory
                        .builder()
                        .appId(dto.appId())
                        .text(dto.text())
                        .build()
        );
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
