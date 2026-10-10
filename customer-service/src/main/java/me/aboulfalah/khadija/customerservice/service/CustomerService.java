package me.aboulfalah.khadija.customerservice.service;


import me.aboulfalah.khadija.customerservice.entities.Customer;
import me.aboulfalah.khadija.customerservice.repository.CustomerRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private static CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @McpTool(description = "Get all customers")
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @McpTool(description = "Find customer by id")
    public Customer findCustomerById(@McpToolParam(description = "The customer id") Long id) {
        return customerRepository.findById(id)
                .orElseThrow(()->new RuntimeException("Customer not found!"));
    }

    @McpTool(description = "Save new customer")
    public static Customer saveCustomer(@McpToolParam(description = "The customer to save (name , email)") Customer customer) {
        return customerRepository.save(customer);
    }
}
