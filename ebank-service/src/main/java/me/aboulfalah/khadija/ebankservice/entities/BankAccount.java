package me.aboulfalah.khadija.ebankservice.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.aboulfalah.khadija.ebankservice.model.Customer;
import org.springframework.data.domain.Persistable;

import java.util.Date;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount implements Persistable<String> {

    @Id
    private String id;
    private Date createdAt;
    private double balance;
    private String type;
    private Long customerId;

    @Transient
    private Customer customer;

    @Override
    @JsonIgnore
    public boolean isNew() {
        // Comme vous faites bankAccount.setCreatedAt(new Date()) dans le service,
        // à l'arrivée depuis Swagger createdAt est null, ce qui garantit qu'il sera persisté sans NullPointerException.
        return this.createdAt == null;
    }
}