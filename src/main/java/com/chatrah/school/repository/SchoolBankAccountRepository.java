package com.chatrah.school.repository;

import com.chatrah.school.entity.SchoolBankAccount;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for school bank accounts.
 */
@ApplicationScoped
public class SchoolBankAccountRepository implements PanacheRepository<SchoolBankAccount> {

    public List<SchoolBankAccount> findActiveAccounts() {
        return list("active", true);
    }
}
