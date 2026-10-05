package me.aboulfalah.khadija.ebankservice.repository;

import me.aboulfalah.khadija.ebankservice.entities.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankAccountRepository extends JpaRepository<BankAccount, String > {
    List<BankAccount> findByCustomerId(String id);
}
