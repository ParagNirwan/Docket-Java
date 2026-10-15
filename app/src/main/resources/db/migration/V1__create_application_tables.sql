CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE UNIQUE INDEX uk_users_email_lower ON users (LOWER(email));

CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255),
    slug VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE tenant_membership (
    tenant_membership_id UUID PRIMARY KEY,
    tenant_id UUID,
    user_id UUID,
    user_type VARCHAR(255),
    status VARCHAR(255),
    role VARCHAR(255)
);
