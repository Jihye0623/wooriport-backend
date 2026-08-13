package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.AssetSnapshots;
import com.wooriport.core_api.repository.AssetSnapshotsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssetSnapshotItemWriterTest {

    @Mock AssetSnapshotsRepository assetSnapshotsRepository;
    @InjectMocks AssetSnapshotItemWriter writer;

    @Test
    @DisplayName("chunk에 담긴 스냅샷을 그대로 saveAll에 위임한다")
    void write_delegatesChunkItemsToSaveAll() {
        AssetSnapshots snapshot1 = snapshot();
        AssetSnapshots snapshot2 = snapshot();
        Chunk<AssetSnapshots> chunk = new Chunk<>(List.of(snapshot1, snapshot2));

        writer.write(chunk);

        ArgumentCaptor<List<AssetSnapshots>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetSnapshotsRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(snapshot1, snapshot2);
    }

    @Test
    @DisplayName("빈 chunk도 예외 없이 saveAll을 호출한다")
    void write_emptyChunk_noException() {
        Chunk<AssetSnapshots> chunk = new Chunk<>(List.of());

        writer.write(chunk);

        verify(assetSnapshotsRepository).saveAll(List.of());
    }

    private AssetSnapshots snapshot() {
        return AssetSnapshots.builder()
                .snapshotAt(LocalDateTime.now())
                .totalAmount(100_000L)
                .savingsAmount(60_000L)
                .investAmount(40_000L)
                .build();
    }
}
