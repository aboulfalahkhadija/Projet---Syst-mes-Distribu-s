package me.aboulfalah.khadija.ebankservice.services;

import me.aboulfalah.khadija.ebankservice.entities.BankAccount;
import me.aboulfalah.khadija.ebankservice.feign.CustomerRestClient;
import me.aboulfalah.khadija.ebankservice.model.Customer;
import me.aboulfalah.khadija.ebankservice.repository.BankAccountRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class EbankService {

    private final BankAccountRepository accountRepository;
    private final CustomerRestClient customerRestClient;

    // Injection des deux dépendances par le constructeur
    public EbankService(BankAccountRepository accountRepository, CustomerRestClient customerRestClient) {
        this.accountRepository = accountRepository;
        this.customerRestClient = customerRestClient;
    }

    @McpTool(description = "Get all bank accounts")
    public List<BankAccount> getAllBankAccounts() {
        List<BankAccount> bankAccounts = accountRepository.findAll();
        // Optionnel : enrichir chaque compte de la liste avec son Customer
        bankAccounts.forEach(acc -> {
            if (acc.getCustomerId() != null) {
                try {
                    acc.setCustomer(customerRestClient.getCustomerById(acc.getCustomerId()));
                } catch (Exception e) {
                    acc.setCustomer(null);
                }
            }
        });
        return bankAccounts;
    }

    @McpTool(description = "get a bank account by id")
    public BankAccount getBankAccountById(@McpToolParam(description = "The bank account id") String id) {
        BankAccount bankAccount = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (bankAccount.getCustomerId() != null) {
            try {
                bankAccount.setCustomer(customerRestClient.getCustomerById(bankAccount.getCustomerId()));
            } catch (Exception e) {
                System.err.println("Erreur récupération client : " + e.getMessage());
                bankAccount.setCustomer(null);
            }
        }

        return bankAccount;
    }

    @McpTool(description ="Save new bank account")
    public BankAccount save(@McpToolParam(description = "the bank account to save(balance,type,customerId)")
                                BankAccount bankAccount) {
        try {
            Customer customer = customerRestClient.getCustomerById(bankAccount.getCustomerId() );
            bankAccount.setId(UUID.randomUUID().toString());
            bankAccount.setCreatedAt(new Date());
            return accountRepository.save(bankAccount);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }


    }
}