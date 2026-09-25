package com.payflow.service;

import com.payflow.dto.PaymentInitiateRequest;
import com.payflow.dto.PaymentResponse;
import com.payflow.dto.ScheduleBillRequest;
import com.payflow.dto.ScheduledBillDto;
import com.payflow.entity.Biller;
import com.payflow.entity.ScheduledBill;
import com.payflow.entity.User;
import com.payflow.enums.ScheduledBillStatus;
import com.payflow.exception.PayflowException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.repository.ScheduledBillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScheduledBillService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledBillService.class);

    private final ScheduledBillRepository scheduledBillRepository;
    private final BillerService billerService;
    private final PaymentService paymentService;

    public ScheduledBillService(ScheduledBillRepository scheduledBillRepository,
                                BillerService billerService,
                                PaymentService paymentService) {
        this.scheduledBillRepository = scheduledBillRepository;
        this.billerService = billerService;
        this.paymentService = paymentService;
    }

    @Transactional
    public ScheduledBillDto scheduleBill(User user, ScheduleBillRequest request) {
        Biller biller = billerService.getBillerEntity(request.getBillerId());
        billerService.validateConsumerNumber(biller, request.getConsumerNumber());

        LocalDateTime nextExec = request.getNextExecutionDate();
        if (nextExec == null) {
            if (request.getDueDate() != null) {
                nextExec = request.getDueDate().atTime(9, 0); // 9:00 AM on due date
            } else {
                nextExec = LocalDateTime.now().plusMinutes(1);
            }
        }

        ScheduledBill bill = new ScheduledBill(
                user,
                biller,
                request.getConsumerNumber().trim(),
                request.getAmount(),
                request.getFrequency(),
                request.getDueDate(),
                nextExec
        );

        bill = scheduledBillRepository.save(bill);
        log.info("Scheduled bill id={} created for user={}, nextExec={}", bill.getId(), user.getId(), bill.getNextExecutionDate());
        return toDto(bill);
    }

    @Transactional(readOnly = true)
    public List<ScheduledBillDto> getUserScheduledBills(Long userId) {
        return scheduledBillRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelScheduledBill(User user, Long billId) {
        ScheduledBill bill = scheduledBillRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled bill not found: " + billId));

        if (!bill.getUser().getId().equals(user.getId())) {
            throw new PayflowException("Unauthorized to cancel this scheduled bill");
        }

        bill.setStatus(ScheduledBillStatus.CANCELLED);
        scheduledBillRepository.save(bill);
        log.info("Cancelled scheduled bill id={}", billId);
    }

    /**
     * Periodic runner evaluating and executing due scheduled/recurring bills.
     */
    @Scheduled(cron = "${payflow.scheduled-bills.cron:0 */1 * * * *}")
    public void processDueScheduledBills() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledBill> dueBills = scheduledBillRepository.findDueBills(ScheduledBillStatus.ACTIVE, now);

        if (!dueBills.isEmpty()) {
            log.info("Processing {} due scheduled bills...", dueBills.size());
        }

        for (ScheduledBill bill : dueBills) {
            try {
                executeSingleScheduledBill(bill);
            } catch (Exception e) {
                log.error("Failed to execute scheduled bill id={}: {}", bill.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void executeSingleScheduledBill(ScheduledBill bill) {
        String timestampKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        String idempotencyKey = "sched_" + bill.getId() + "_" + timestampKey;

        PaymentInitiateRequest paymentRequest = new PaymentInitiateRequest(
                bill.getBiller().getId(),
                bill.getConsumerNumber(),
                bill.getAmount(),
                false,
                null
        );

        log.info("Auto-executing scheduled bill id={} for user={}", bill.getId(), bill.getUser().getId());
        try {
            PaymentResponse response = paymentService.processPayment(bill.getUser(), idempotencyKey, paymentRequest);
            bill.setLastTransactionId(response.getId());
            bill.advanceNextExecution();
            scheduledBillRepository.save(bill);
            log.info("Scheduled bill id={} executed successfully, new status={}, nextExec={}",
                    bill.getId(), bill.getStatus(), bill.getNextExecutionDate());
        } catch (Exception e) {
            log.error("Auto-execution failed for scheduled bill id={}: {}", bill.getId(), e.getMessage());
            // Retry on next cycle
            bill.setNextExecutionDate(LocalDateTime.now().plusMinutes(5));
            scheduledBillRepository.save(bill);
        }
    }

    public ScheduledBillDto toDto(ScheduledBill bill) {
        ScheduledBillDto dto = new ScheduledBillDto();
        dto.setId(bill.getId());
        dto.setBillerId(bill.getBiller().getId());
        dto.setBillerName(bill.getBiller().getName());
        dto.setBillerCode(bill.getBiller().getCode());
        dto.setConsumerNumber(bill.getConsumerNumber());
        dto.setAmount(bill.getAmount());
        dto.setFrequency(bill.getFrequency());
        dto.setDueDate(bill.getDueDate());
        dto.setNextExecutionDate(bill.getNextExecutionDate());
        dto.setStatus(bill.getStatus());
        dto.setLastExecutionDate(bill.getLastExecutionDate());
        dto.setLastTransactionId(bill.getLastTransactionId());
        dto.setCreatedAt(bill.getCreatedAt());
        return dto;
    }
}
