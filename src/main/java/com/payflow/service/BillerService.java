package com.payflow.service;

import com.payflow.dto.BillerAccountDto;
import com.payflow.dto.BillerDto;
import com.payflow.dto.CreateBillerAccountRequest;
import com.payflow.dto.CreateBillerRequest;
import com.payflow.entity.Biller;
import com.payflow.entity.BillerAccount;
import com.payflow.entity.User;
import com.payflow.enums.BillerCategory;
import com.payflow.exception.PayflowException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.repository.BillerAccountRepository;
import com.payflow.repository.BillerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BillerService {

    private static final Logger log = LoggerFactory.getLogger(BillerService.class);

    private final BillerRepository billerRepository;
    private final BillerAccountRepository billerAccountRepository;

    public BillerService(BillerRepository billerRepository, BillerAccountRepository billerAccountRepository) {
        this.billerRepository = billerRepository;
        this.billerAccountRepository = billerAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<BillerDto> getAllBillers() {
        return billerRepository.findAll().stream()
                .filter(b -> !"PAYFLOW_TOPUP".equals(b.getCode()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BillerDto> getBillersByCategory(BillerCategory category) {
        return billerRepository.findByCategory(category).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Biller getBillerEntity(Long id) {
        return billerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Biller not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public BillerDto getBillerById(Long id) {
        return toDto(getBillerEntity(id));
    }

    @Transactional
    public BillerDto createBiller(CreateBillerRequest request) {
        if (billerRepository.findByCode(request.getCode()).isPresent()) {
            throw new PayflowException("Biller with code " + request.getCode() + " already exists", HttpStatus.CONFLICT);
        }

        Biller biller = new Biller(
                request.getCode().trim().toUpperCase(),
                request.getName().trim(),
                request.getCategory(),
                request.getAccountNumberRegex(),
                request.getAccountNumberLabel(),
                request.getCommissionRate(),
                request.getEscrowAccountId()
        );

        biller = billerRepository.save(biller);
        log.info("Created new biller: code={}, name={}, category={}", biller.getCode(), biller.getName(), biller.getCategory());
        return toDto(biller);
    }

    @Transactional(readOnly = true)
    public List<BillerAccountDto> getUserBillerAccounts(Long userId) {
        return billerAccountRepository.findByUserId(userId).stream()
                .map(this::toAccountDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public BillerAccountDto saveBillerAccount(User user, CreateBillerAccountRequest request) {
        Biller biller = getBillerEntity(request.getBillerId());
        validateConsumerNumber(biller, request.getConsumerNumber());

        billerAccountRepository.findByUserIdAndBillerIdAndConsumerNumber(user.getId(), biller.getId(), request.getConsumerNumber())
                .ifPresent(existing -> {
                    throw new PayflowException("Biller account already saved for this consumer number", HttpStatus.CONFLICT);
                });

        BillerAccount account = new BillerAccount(
                user,
                biller,
                request.getConsumerNumber().trim(),
                request.getNickname() != null ? request.getNickname().trim() : biller.getName(),
                "{}"
        );

        account = billerAccountRepository.save(account);
        log.info("Saved biller account id={} for user={}", account.getId(), user.getId());
        return toAccountDto(account);
    }

    public void validateConsumerNumber(Biller biller, String consumerNumber) {
        if (biller.getAccountNumberRegex() != null && !biller.getAccountNumberRegex().isBlank()) {
            if (!Pattern.matches(biller.getAccountNumberRegex(), consumerNumber)) {
                throw new PayflowException("Invalid " + biller.getAccountNumberLabel() + " format for " + biller.getName());
            }
        }
    }

    public BillerDto toDto(Biller biller) {
        return new BillerDto(
                biller.getId(),
                biller.getCode(),
                biller.getName(),
                biller.getCategory(),
                biller.getAccountNumberRegex(),
                biller.getAccountNumberLabel(),
                biller.getCommissionRate(),
                biller.getEscrowAccountId(),
                biller.getStatus()
        );
    }

    public BillerAccountDto toAccountDto(BillerAccount account) {
        return new BillerAccountDto(
                account.getId(),
                account.getBiller().getId(),
                account.getBiller().getName(),
                account.getBiller().getCode(),
                account.getBiller().getCategory(),
                account.getConsumerNumber(),
                account.getNickname(),
                account.getCreatedAt()
        );
    }
}
