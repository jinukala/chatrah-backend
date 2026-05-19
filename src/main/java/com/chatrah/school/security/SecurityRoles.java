// src/main/java/com/chatrah/school/security/SecurityRoles.java
package com.chatrah.school.security;

/**
 * Central place for all application role names used in security annotations.
 */
public final class SecurityRoles {

    private SecurityRoles() {}

    public static final String PRINCIPAL = "PRINCIPAL";
    public static final String CLERK = "CLERK";
    public static final String TEACHER = "TEACHER";
    public static final String STUDENT = "STUDENT";
    public static final String SYS_ADMIN = "SYS_ADMIN"; // Tech Admin
}
