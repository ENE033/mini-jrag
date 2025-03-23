package org.ene.minijrag.component.splitter;

import java.util.List;

public interface TextSplitter {

    // Minimum chunk size ratio (percentage relative to chunk size)
    double MIN_CHUNK_SIZE_RATIO = 0.5;

    List<String> split(String text, int chunkSize, int overlapSize);
}
