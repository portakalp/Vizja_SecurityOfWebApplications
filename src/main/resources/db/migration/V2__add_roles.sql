-- SecureNotes Manager Database Schema
-- Version 2: Add roles table and admin user

-- Roles table
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE
);

-- User-Roles junction table
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Insert default roles
INSERT INTO roles (id, name) VALUES 
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'ROLE_USER'),
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'ROLE_ADMIN');

-- Insert default admin user
-- Password: Admin@SecureNotes2024! (BCrypt hashed with cost 10)
-- IMPORTANT: Change this password immediately after first login in production!
INSERT INTO users (id, username, email, password, created_at, updated_at) VALUES 
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 'admin', 'admin@securenotes.com', 
     '$2a$10$UpJF10Lc7ZTCJ7fT0guRgeSawXlJ.b/pcHarW1Xz4m5FN.yZ3FyZa', 
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Assign ROLE_ADMIN to admin user
INSERT INTO user_roles (user_id, role_id) VALUES 
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22');

-- Assign ROLE_USER to existing users
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
FROM users u
WHERE u.id != 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33';

-- Index for performance
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
