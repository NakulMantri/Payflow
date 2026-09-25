package com.payflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateBillerAccountRequest {

    @NotNull(message = "Biller ID is required")
    private Long billerId;

    @NotBlank(message = "Consumer number is required")
    private String consumerNumber;

    private String nickname;

    public CreateBillerAccountRequest() {}

    public CreateBillerAccountRequest(Long billerId, String consumerNumber, String nickname) {
        this.billerId = billerId;
        this.consumerNumber = consumerNumber;
        this.nickname = nickname;
    }

    public Long getBillerId() { return billerId; }
    public void setBillerId(Long billerId) { this.billerId = billerId; }

    public String getConsumerNumber() { return consumerNumber; }
    public void setConsumerNumber(String consumerNumber) { this.consumerNumber = consumerNumber; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
