package com.claimflow.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Reference data: a market and its currency. Read-only. */
@Entity
@Immutable
@Table(name = "markets")
public class Market {

    @Id
    @Column(length = 2)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    protected Market() {
    }

    /** For tests; markets are seeded by Flyway. */
    Market(String code, String name, String currency) {
        this.code = code;
        this.name = name;
        this.currency = currency;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
}
