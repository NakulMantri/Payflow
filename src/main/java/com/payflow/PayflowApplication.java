package com.payflow;

import com.payflow.entity.Biller;
import com.payflow.entity.BillerAccount;
import com.payflow.entity.User;
import com.payflow.entity.Wallet;
import com.payflow.enums.BillerCategory;
import com.payflow.enums.Role;
import com.payflow.repository.BillerAccountRepository;
import com.payflow.repository.BillerRepository;
import com.payflow.repository.UserRepository;
import com.payflow.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class PayflowApplication {

    private static final Logger log = LoggerFactory.getLogger(PayflowApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PayflowApplication.class, args);
    }

    /**
     * Seeds initial production catalog billers and demo test users with active wallets.
     */
    @Bean
    public CommandLineRunner initDatabase(
            UserRepository userRepository,
            WalletRepository walletRepository,
            BillerRepository billerRepository,
            BillerAccountRepository billerAccountRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            log.info("Initializing PayFlow seed data...");

            // 1. Seed Billers
            if (billerRepository.count() == 0) {
                List<Biller> billers = List.of(
                        new Biller("TATA_POWER_MUMBAI", "Tata Power - Mumbai", BillerCategory.ELECTRICITY,
                                "^[0-9]{10,12}$", "Consumer Number (10-12 digits)", new BigDecimal("0.0050"), "ESCROW_TATA_POWER"),
                        new Biller("ADANI_ELECTRICITY", "Adani Electricity Mumbai", BillerCategory.ELECTRICITY,
                                "^[0-9]{9}$", "9-digit CA Number", new BigDecimal("0.0050"), "ESCROW_ADANI_ELEC"),
                        new Biller("AIRTEL_PREPAID", "Airtel Prepaid Mobile", BillerCategory.MOBILE_PREPAID,
                                "^[6-9][0-9]{9}$", "10-digit Mobile Number", new BigDecimal("0.0100"), "ESCROW_AIRTEL_PRE"),
                        new Biller("JIO_PREPAID", "Reliance Jio Prepaid", BillerCategory.MOBILE_PREPAID,
                                "^[6-9][0-9]{9}$", "10-digit Mobile Number", new BigDecimal("0.0100"), "ESCROW_JIO_PRE"),
                        new Biller("AIRTEL_POSTPAID", "Airtel Postpaid Mobile", BillerCategory.MOBILE_POSTPAID,
                                "^[6-9][0-9]{9}$", "10-digit Mobile Number", new BigDecimal("0.0075"), "ESCROW_AIRTEL_POST"),
                        new Biller("TATA_PLAY_DTH", "Tata Play (Tata Sky) DTH", BillerCategory.DTH,
                                "^[0-9]{10}$", "10-digit Subscriber ID", new BigDecimal("0.0150"), "ESCROW_TATAPLAY"),
                        new Biller("ACT_FIBERNET", "ACT Fibernet Broadband", BillerCategory.BROADBAND,
                                "^[A-Z0-9]{8,14}$", "Account / User ID", new BigDecimal("0.0050"), "ESCROW_ACT_FIBER"),
                        new Biller("MAHANAGAR_GAS", "Mahanagar Gas Limited (MGL)", BillerCategory.PIPED_GAS,
                                "^[0-9]{12}$", "12-digit CA Number", new BigDecimal("0.0020"), "ESCROW_MGL_GAS"),
                        new Biller("DELHI_JAL_BOARD", "Delhi Jal Board (DJB)", BillerCategory.WATER,
                                "^[0-9]{10}$", "10-digit K No", new BigDecimal("0.0000"), "ESCROW_DJB_WATER"),
                        new Biller("HDFC_CREDIT_CARD", "HDFC Bank Credit Card", BillerCategory.CREDIT_CARD,
                                "^[0-9]{16}$", "16-digit Card Number", new BigDecimal("0.0025"), "ESCROW_HDFC_CC")
                );
                billerRepository.saveAll(billers);
                log.info("Seeded {} billers into catalog", billers.size());
            }

            // 2. Seed Demo User
            if (userRepository.findByEmail("alex@example.com").isEmpty()) {
                User demoUser = new User(
                        "alex@example.com",
                        passwordEncoder.encode("password123"),
                        "Alex Morgan",
                        "+919876543210",
                        Role.ROLE_USER
                );
                demoUser = userRepository.save(demoUser);

                Wallet demoWallet = new Wallet(demoUser, "INR", new BigDecimal("10000.0000"));
                walletRepository.save(demoWallet);

                // Add sample biller accounts for Alex
                Biller tataPower = billerRepository.findByCode("TATA_POWER_MUMBAI").orElse(null);
                if (tataPower != null) {
                    BillerAccount acc1 = new BillerAccount(demoUser, tataPower, "900012345678", "Home Electricity", "{}");
                    billerAccountRepository.save(acc1);
                }

                Biller airtelMobile = billerRepository.findByCode("AIRTEL_PREPAID").orElse(null);
                if (airtelMobile != null) {
                    BillerAccount acc2 = new BillerAccount(demoUser, airtelMobile, "9876543210", "Personal Phone", "{}");
                    billerAccountRepository.save(acc2);
                }

                log.info("Seeded demo user: alex@example.com / password123 with ₹10,000 balance");
            }

            // 3. Seed Admin User
            if (userRepository.findByEmail("admin@payflow.com").isEmpty()) {
                User adminUser = new User(
                        "admin@payflow.com",
                        passwordEncoder.encode("admin123"),
                        "Payflow Admin",
                        "+919999999999",
                        Role.ROLE_ADMIN
                );
                adminUser = userRepository.save(adminUser);

                Wallet adminWallet = new Wallet(adminUser, "INR", new BigDecimal("50000.0000"));
                walletRepository.save(adminWallet);

                log.info("Seeded admin user: admin@payflow.com / admin123");
            }
        };
    }
}
