package me.aboulfalah.khadija.ebankservice.entities;

import jakarta.persistence.*;
import lombok.*;
import me.aboulfalah.khadija.ebankservice.model.Customer;

import java.util.Date;

import org.springframework.data.domain.Persistable;
import jakarta.persistence.Transient;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BankAccount implements Persistable<String> {

    @Id
    private String id;
    private Date createdAt;
    private double balance;
    private String type;
    private Long customerId;

    @Transient
    private Customer customer;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }
}
