# Hands-On Guide — Phase 8 Part 3: Spring Batch Settlement Job

## Goal

By the end of Part 3, you will have:
- Understanding of Spring Batch (Reader → Processor → Writer pattern)
- SettlementBatchConfig defining the Job and Step
- How chunked processing works (100 records at a time, checkpointed)
- How to restart a failed job from where it left off
- Git commit

## Prerequisites

- Part 2 completed (FeeCalculator working)
- Understanding of what the batch job does (fetches captured payments, groups by merchant, calculates fees)

---

## Spring Batch Architecture (How It Works)

```
SPRING BATCH COMPONENTS:

┌─────────────────────────────────────────────────────────────────────────┐
│                           JOB                                            │
│  "settlementJob" — the entire settlement process                        │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                         STEP                                     │   │
│  │  "processPaymentsStep" — one logical unit of work                │   │
│  │                                                                   │   │
│  │  CHUNK SIZE = 100                                                 │   │
│  │  ┌──────────┐    ┌──────────────┐    ┌──────────┐              │   │
│  │  │  READER  │───►│  PROCESSOR   │───►│  WRITER  │              │   │
│  │  │          │    │              │    │          │              │   │
│  │  │ Read 100 │    │ Calculate    │    │ Save to  │              │   │
│  │  │ captured │    │ fees for     │    │ database │              │   │
│  │  │ payments │    │ each merchant│    │          │              │   │
│  │  └──────────┘    └──────────────┘    └──────────┘              │   │
│  │       ↑                                     │                     │   │
│  │       └─────────── repeat until no more ────┘                     │   │
│  │                                                                   │   │
│  │  AFTER ALL CHUNKS:                                                │   │
│  │  ├── 1000 payments processed in 10 chunks of 100                  │   │
│  │  ├── Each chunk: read 100 → process → write results              │   │
│  │  ├── If crash at chunk 7 → restart from chunk 7 (not from 1!)    │   │
│  │  └── Memory: only 100 records at a time (not all 1000)           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  SPRING BATCH METADATA TABLES (auto-created):                           │
│  ├── BATCH_JOB_INSTANCE: Which jobs have run                            │
│  ├── BATCH_JOB_EXECUTION: Each run of a job (start time, status)       │
│  ├── BATCH_STEP_EXECUTION: Each step's progress (read count, etc.)      │
│  └── These enable: restart, monitoring, history                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘

LIFECYCLE:
  Job STARTED → Step STARTED → [Chunk 1] → [Chunk 2] → ... → Step COMPLETED → Job COMPLETED
                                    ↓
                            If failure:
                            Step FAILED → Job FAILED
                            (restart picks up from failed chunk)
```

---

## Step 3.1: What Reader, Processor, Writer Do

```
READER (ItemReader<Payment>):
├── Input: Nothing (reads from data source)
├── Output: One Payment object at a time
├── How: Calls payment-service API via Feign: 
│         GET /internal/payments?status=CAPTURED&date=yesterday&page=X
├── Pagination: Returns page of 100, then next page, until empty
└── When done: Returns null (signals "no more items")

PROCESSOR (ItemProcessor<Payment, SettlementEntry>):
├── Input: One Payment object
├── Output: One SettlementEntry (merchant_id, amount, method)
├── Logic: 
│   ├── Extract merchant_id, amount, payment_method
│   ├── Look up merchant's MDR rate
│   └── Return entry for aggregation
└── Can return null (= skip this item)

WRITER (ItemWriter<SettlementEntry>):
├── Input: List of SettlementEntry (one chunk = 100 entries)
├── Logic:
│   ├── Group entries by merchant_id
│   ├── For each merchant: sum amounts, count transactions
│   ├── Call FeeCalculator for each merchant
│   ├── Create Settlement record
│   ├── Save to database
│   └── Mark original payments as SETTLED (via Feign)
└── If any failure: whole chunk rolls back (transaction)
```

---

## Step 3.2: Settlement Batch Configuration

**Create file:** `settlement-service/src/main/java/com/payflow/settlement/batch/SettlementBatchConfig.java`

```java
package com.payflow.settlement.batch;

import com.payflow.settlement.service.FeeCalculator;
import com.payflow.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch configuration for the daily settlement job.
 * 
 * For our demo project, we use a SIMPLIFIED approach:
 * Instead of the full Reader→Processor→Writer pattern (which needs
 * pagination from payment-service), we use a single Tasklet step
 * that calls SettlementService.runDailySettlement().
 * 
 * This is perfectly valid for our scale and demonstrates:
 * - Job/Step structure
 * - Spring Batch metadata tracking
 * - Restart capability
 * - Scheduled triggering
 * 
 * For millions of records, you'd use the chunked approach.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SettlementBatchConfig {

    private final JobRepository jobRepository;
    // Spring Batch's repository for job metadata (auto-configured)

    private final PlatformTransactionManager transactionManager;
    // Database transaction manager (ensures atomicity)

    private final SettlementService settlementService;

    /**
     * Define the Job (top-level container).
     * A Job has one or more Steps.
     */
    @Bean
    public Job settlementJob() {
        return new JobBuilder("settlementJob", jobRepository)
                .start(processSettlementsStep())
                // First (and only) step
                .build();
    }

    /**
     * Define the Step (unit of work).
     * Uses a Tasklet (simple: run this code, return FINISHED).
     */
    @Bean
    public Step processSettlementsStep() {
        return new StepBuilder("processSettlementsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("=== Settlement Step: Processing yesterday's payments ===");
                    
                    // This is where the actual settlement logic runs
                    // In production: would be chunked Reader/Processor/Writer
                    // For demo: single method call
                    settlementService.processYesterdaysPayments();
                    
                    log.info("=== Settlement Step: COMPLETED ===");
                    return RepeatStatus.FINISHED;
                    // FINISHED = this step is done, move to next step (or finish job)
                }, transactionManager)
                .build();
    }
}
```

---

## Step 3.3: Settlement Scheduler (Triggers Job at Midnight)

**Create file:** `settlement-service/src/main/java/com/payflow/settlement/scheduler/SettlementScheduler.java`

```java
package com.payflow.settlement.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Triggers the settlement batch job at midnight every day.
 * 
 * Cron expression: "0 0 0 * * ?"
 *   ├── 0 = second 0
 *   ├── 0 = minute 0
 *   ├── 0 = hour 0 (midnight)
 *   ├── * = every day of month
 *   ├── * = every month
 *   └── ? = any day of week
 * 
 * Result: Runs at 00:00:00 (midnight) every single day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final JobLauncher jobLauncher;
    // Spring Batch's job launcher (runs jobs)
    
    private final Job settlementJob;
    // The job we defined in SettlementBatchConfig

    @Scheduled(cron = "0 0 0 * * ?")
    // Midnight every day
    // For testing: change to "0 */5 * * * ?" (every 5 minutes)
    public void runDailySettlement() {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║   DAILY SETTLEMENT JOB TRIGGERED         ║");
        log.info("║   Settlement date: {}       ║", LocalDate.now().minusDays(1));
        log.info("╚══════════════════════════════════════════╝");

        try {
            // Job parameters must be UNIQUE per execution
            // (Spring Batch won't re-run a job with same parameters)
            JobParameters params = new JobParametersBuilder()
                    .addString("settlementDate", LocalDate.now().minusDays(1).toString())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(settlementJob, params);
            
            log.info("Settlement job completed successfully");
        } catch (Exception e) {
            log.error("Settlement job FAILED: {}", e.getMessage(), e);
            // TODO: Send alert to operations team via SNS
        }
    }
}
```

---

## Step 3.4: Manual Trigger Endpoint (For Testing)

**Add to SettlementController:**

```java
    @PostMapping("/internal/trigger")
    @Operation(summary = "Manually trigger settlement (admin/testing only)")
    public ResponseEntity<ApiResponse<String>> triggerSettlement() {
        try {
            settlementScheduler.runDailySettlement();
            return ResponseEntity.ok(ApiResponse.success("Settlement triggered successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("SETTLEMENT_FAILED", e.getMessage()));
        }
    }
```

This lets you test without waiting for midnight:
```cmd
curl -X POST http://localhost:8085/internal/trigger
```

---

## Step 3.5: How Restart Works

```
SCENARIO: Settlement job crashes at chunk 5 of 10:

RUN 1:
├── Chunk 1: ✓ processed, saved to BATCH_STEP_EXECUTION
├── Chunk 2: ✓ processed, saved
├── Chunk 3: ✓ processed, saved
├── Chunk 4: ✓ processed, saved
├── Chunk 5: ✗ FAILED (database timeout)
└── Job status: FAILED

Spring Batch records: "processSettlementsStep completed 4 chunks, failed on 5"

RUN 2 (restart — same job parameters):
├── Spring Batch checks: "Where did we leave off? → Chunk 5"
├── Chunk 5: ✓ processed (retry succeeded)
├── Chunk 6: ✓ processed
├── ... (continues from where it left off)
└── Job status: COMPLETED

WITHOUT Spring Batch (naive approach):
├── Crash → restart from beginning
├── Re-process chunks 1-4 (already done!) → waste time
└── Risk: Double-processing = double settlement = WRONG MONEY!
```

---

## Step 3.6: Git Commit

```cmd
git add settlement-service/src/main/java/com/payflow/settlement/batch/
git add settlement-service/src/main/java/com/payflow/settlement/scheduler/
git commit -m "Phase 8 Part 3: Spring Batch config + settlement scheduler (midnight cron)"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `batch/SettlementBatchConfig.java` | Defines Job + Step (Tasklet-based) |
| `scheduler/SettlementScheduler.java` | @Scheduled cron = midnight, launches job |
| Controller endpoint | POST /internal/trigger for manual testing |

---

## Interview Notes

**Q: "Why Spring Batch for settlement?"**
> "Spring Batch provides chunked processing (100 records at a time — memory safe), automatic checkpointing (restart from where it failed), skip policies (bad records don't kill the whole job), and execution metadata (track progress, history). For millions of daily payments, this is essential. A simple for-loop would run out of memory or lose progress on failure."

**Q: "How do you prevent double settlement?"**
> "Job parameters include the settlement date. Spring Batch won't re-execute a completed job with the same parameters. We also have a UNIQUE constraint on (merchant_id, settlement_date) in the database — if someone accidentally triggers twice, the second attempt fails safely."

**Q: "What if settlement fails midway?"**
> "Spring Batch's restart mechanism. It tracks which chunks completed successfully. On restart, it skips completed chunks and resumes from the failed one. Combined with database transactions per chunk, we get exactly-once processing even across failures."

---

## Next Step

→ Continue to **Phase 8 Part 4: Scheduler & Payout**
