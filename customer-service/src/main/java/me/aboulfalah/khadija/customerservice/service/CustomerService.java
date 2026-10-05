package me.aboulfalah.khadija.customerservice.service;


import me.aboulfalah.khadija.customerservice.entities.Customer;
import me.aboulfalah.khadija.customerservice.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private static CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
    public Customer findCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(()->new RuntimeException("Customer not found!"));
    }
    public static Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }
}
