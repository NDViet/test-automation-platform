package com.platform.ingestion.trace;

import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.common.storage.BlobStoreBuckets;
import com.platform.core.repository.TestCaseResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class TraceStorageService {

    private static final Logger log = LoggerFactory.getLogger(TraceStorageService.class);

    private final BlobStore blobStore;
    private final TestCaseResultRepository resultRepo;

    public TraceStorageService(BlobStore blobStore, TestCaseResultRepository resultRepo) {
        this.blobStore  = blobStore;
        this.resultRepo = resultRepo;
    }

    @Transactional
    public void storeTrace(String runId, String testId, MultipartFile file) throws IOException {
        log.info("Storing trace runId={} testId={} size={}b", runId, testId, file.getSize());

        BlobRef ref = blobStore.storeBytes(BlobStoreBuckets.TRACES, file.getBytes(), "application/zip");
        log.debug("Trace blob stored key={}", ref.key());

        var matched = resultRepo.findFirstByExecution_RunIdAndTestId(runId, testId);
        if (matched.isEmpty()) {
            log.warn("No TestCaseResult found for runId={} testId={} — trace stored in blob but not linked", runId, testId);
            return;
        }
        matched.get().setTraceStorePath(ref.key());
        resultRepo.save(matched.get());
        log.info("Trace linked to resultId={} key={}", matched.get().getId(), ref.key());
    }
}
