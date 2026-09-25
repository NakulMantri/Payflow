package com.payflow.dto;

import com.payflow.enums.BillerCategory;
import java.time.LocalDateTime;

public class BillerAccountDto {
    private Long id;
    private Long billerId;
    private String billerName;
    private String billerCode;
    private BillerCategory category;
    private String consumerNumber;
    private String nickname;
    private LocalDateTime createdAt;

    public BillerAccountDto() {}

    public BillerAccountDto(Long id, Long billerId, String billerName, String billerCode,
                            BillerCategory category, String consumerNumber, String nickname, LocalDateTime createdAt) {
        this.id = id;
        this.billerId = billerId;
        this.billerName = billerName;
        this.billerCode = billerCode;
        this.category = category;
        this.consumerNumber = consumerNumber;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBillerId() { return billerId; }
    public void setBillerId(Long billerId) { this.billerId = billerId; }

    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }

    public String getBillerCode() { return billerCode; }
    public void setBillerCode(String billerCode) { this.billerCode = billerCode; }

    public BillerCategory getCategory() { return category; }
    public void setCategory(BillerCategory category) { this.category = category; }

    public String getConsumerNumber() { return consumerNumber; }
    public void setConsumerNumber(String consumerNumber) { this.consumerNumber = consumerNumber; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
