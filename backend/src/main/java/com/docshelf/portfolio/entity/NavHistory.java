// Daily NAV per scheme (composite key isin + nav_date), filled from AMFI
package com.docshelf.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "nav_history")
@IdClass(NavHistory.Key.class)
public class NavHistory {

    public static class Key implements Serializable {
        private String isin;
        private LocalDate navDate;

        public Key() {
        }

        public Key(String isin, LocalDate navDate) {
            this.isin = isin;
            this.navDate = navDate;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(isin, k.isin) && Objects.equals(navDate, k.navDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(isin, navDate);
        }
    }

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "isin", length = 12)
    private String isin;

    @Id
    @Column(name = "nav_date")
    private LocalDate navDate;

    @Column(name = "nav", nullable = false, precision = 12, scale = 4)
    private BigDecimal nav;

    public NavHistory() {
    }

    public NavHistory(String isin, LocalDate navDate, BigDecimal nav) {
        this.isin = isin;
        this.navDate = navDate;
        this.nav = nav;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public LocalDate getNavDate() {
        return navDate;
    }

    public void setNavDate(LocalDate navDate) {
        this.navDate = navDate;
    }

    public BigDecimal getNav() {
        return nav;
    }

    public void setNav(BigDecimal nav) {
        this.nav = nav;
    }
}
