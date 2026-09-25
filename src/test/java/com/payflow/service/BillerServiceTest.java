package com.payflow.service;

import com.payflow.dto.BillerAccountDto;
import com.payflow.dto.BillerDto;
import com.payflow.dto.CreateBillerAccountRequest;
import com.payflow.dto.CreateBillerRequest;
import com.payflow.entity.Biller;
import com.payflow.entity.BillerAccount;
import com.payflow.entity.User;
import com.payflow.enums.BillerCategory;
import com.payflow.enums.Role;
import com.payflow.exception.PayflowException;
import com.payflow.repository.BillerAccountRepository;
import com.payflow.repository.BillerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillerServiceTest {

    @Mock
    private BillerRepository billerRepository;

    @Mock
    private BillerAccountRepository billerAccountRepository;

    private BillerService billerService;
    private User testUser;
    private Biller testBiller;

    @BeforeEach
    void setUp() {
        billerService = new BillerService(billerRepository, billerAccountRepository);

        testUser = new User("test@biller.com", "pass", "Biller Tester", "1234567890", Role.ROLE_USER);
        testUser.setId(5L);

        testBiller = new Biller("JIO_PRE", "Jio Prepaid", BillerCategory.MOBILE_PREPAID, "^[6-9][0-9]{9}$", "Mobile No", BigDecimal.ZERO, "ESC_JIO");
        testBiller.setId(50L);
    }

    @Test
    @DisplayName("Should create biller successfully")
    void testCreateBiller_success() {
        CreateBillerRequest request = new CreateBillerRequest();
        request.setCode("VI_PRE");
        request.setName("Vodafone Idea Prepaid");
        request.setCategory(BillerCategory.MOBILE_PREPAID);
        request.setEscrowAccountId("ESC_VI");

        when(billerRepository.findByCode("VI_PRE")).thenReturn(Optional.empty());
        when(billerRepository.save(any(Biller.class))).thenAnswer(i -> {
            Biller b = i.getArgument(0);
            b.setId(51L);
            return b;
        });

        BillerDto dto = billerService.createBiller(request);

        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("VI_PRE");
        assertThat(dto.getName()).isEqualTo("Vodafone Idea Prepaid");
    }

    @Test
    @DisplayName("Should throw conflict when creating biller with existing code")
    void testCreateBiller_duplicateCode_throwsException() {
        CreateBillerRequest request = new CreateBillerRequest();
        request.setCode("JIO_PRE");
        request.setName("Jio");
        request.setCategory(BillerCategory.MOBILE_PREPAID);
        request.setEscrowAccountId("ESC_JIO");

        when(billerRepository.findByCode("JIO_PRE")).thenReturn(Optional.of(testBiller));

        assertThatThrownBy(() -> billerService.createBiller(request))
                .isInstanceOf(PayflowException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should validate consumer number against regex and save biller account")
    void testSaveBillerAccount_success() {
        CreateBillerAccountRequest request = new CreateBillerAccountRequest(50L, "9823456789", "My Jio SIM");

        when(billerRepository.findById(50L)).thenReturn(Optional.of(testBiller));
        when(billerAccountRepository.findByUserIdAndBillerIdAndConsumerNumber(5L, 50L, "9823456789"))
                .thenReturn(Optional.empty());
        when(billerAccountRepository.save(any(BillerAccount.class))).thenAnswer(i -> {
            BillerAccount acc = i.getArgument(0);
            acc.setId(501L);
            return acc;
        });

        BillerAccountDto accountDto = billerService.saveBillerAccount(testUser, request);

        assertThat(accountDto).isNotNull();
        assertThat(accountDto.getConsumerNumber()).isEqualTo("9823456789");
    }

    @Test
    @DisplayName("Should reject consumer number if regex validation fails")
    void testValidateConsumerNumber_regexFailure() {
        CreateBillerAccountRequest request = new CreateBillerAccountRequest(50L, "12345", "Invalid Number");

        when(billerRepository.findById(50L)).thenReturn(Optional.of(testBiller));

        assertThatThrownBy(() -> billerService.saveBillerAccount(testUser, request))
                .isInstanceOf(PayflowException.class)
                .hasMessageContaining("Invalid Mobile No format");
    }
}
