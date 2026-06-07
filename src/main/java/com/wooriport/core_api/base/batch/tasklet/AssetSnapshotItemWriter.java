package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.AssetSnapshots;
import com.wooriport.core_api.repository.AssetSnapshotsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetSnapshotItemWriter implements ItemWriter<AssetSnapshots> {

    private final AssetSnapshotsRepository assetSnapshotsRepository;

    @Override
    public void write(Chunk<? extends AssetSnapshots> chunk) {
        assetSnapshotsRepository.saveAll(chunk.getItems());
        log.info("[AssetSnapshotJob] chunk 저장 완료 — {}건", chunk.size());
    }
}
