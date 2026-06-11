package com.hris.tenancy;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Establishes the Postgres setting that RLS policies read
 * ({@code app.current_tenant}) on every connection checkout.
 *
 * <p>Checkout-normalization instead of reset-on-release: each borrowed
 * connection first gets {@code SET app.current_tenant} (context present) or
 * {@code RESET app.current_tenant} (no context), so a stale setting left by a
 * previous borrower can never leak into this transaction. The setting value is
 * always a server-validated UUID literal, never user input.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return prepare(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return prepare(super.getConnection(username, password));
    }

    private Connection prepare(Connection connection) throws SQLException {
        UUID tenantId = TenantContext.get();
        try (Statement statement = connection.createStatement()) {
            if (tenantId != null) {
                statement.execute("SET app.current_tenant = '" + tenantId + "'");
            } else {
                statement.execute("RESET app.current_tenant");
            }
        } catch (SQLException ex) {
            connection.close();
            throw ex;
        }
        return connection;
    }
}
