package com.payflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "biller_accounts",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_biller_consumer", columnNames = {"user_id", "biller_id", "consumer_number"})
    },
    indexes = {
        @Index(name = "idx_biller_acc_user", columnList = "user_id")
    }
)
public class BillerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "biller_id", nullable = false)
    private Biller biller;

    @Column(name = "consumer_number", nullable = false, length = 100)
    private String consumerNumber;

    @Column(length = 100)
    private String nickname;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public BillerAccount() {}

    public BillerAccount(User user, Biller biller, String consumerNumber, String nickname, String metadataJson) {
        this.user = user;
        this.biller = biller;
        this.consumerNumber = consumerNumber;
        this.nickname = nickname;
        this.metadataJson = metadataJson;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Biller getBiller() { return biller; }
    public void setBiller(Biller biller) { this.biller = biller; }

    public String getConsumerNumber() { return consumerNumber; }
    public void setConsumerNumber(String consumerNumber) { this.consumerNumber = consumerNumber; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
