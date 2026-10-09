package app.nzyme.core.events.types;

public enum SystemEventType {

    AUTHENTICATION_PASSWORD_CHANGED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.ORGANIZATION,
            "A user's password was changed",
            "The password for an user has been modified, either by the user themselves or by an administrator."
    ),

    AUTHENTICATION_MFA_RESET(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.ORGANIZATION,
            "A user's MFA method was reset",
            "Multi-factor method for an user has been reset, either by the user themselves or by an " +
                    "administrator."
    ),

    AUTHENTICATION_MFA_RECOVERY_CODE_USED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.ORGANIZATION,
            "A user's MFA recovery code was used",
            "A user has successfully utilized one of their MFA recovery codes to log in."
    ),

    AUTHENTICATION_MFA_RECOVERY_CODE_REUSED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.ORGANIZATION,
            "A user's MFA recovery code was re-used",
            "A user attempted to reuse one of their previously utilized MFA recovery codes for login, which " +
                    "was unsuccessful."
    ),

    AUTHENTICATION_MFA_DISABLED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.ORGANIZATION,
            "A user's MFA was disabled",
            "A user's MFA requirement was disabled."
    ),

    AUTHENTICATION_USER_LOGIN_THROTTLED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.ORGANIZATION,
            "A user's login attempts were throttled",
            "Due to multiple unsuccessful login attempts by a user, their subsequent login attempts have been " +
                    "intentionally delayed and slowed down until a successful login is achieved."
    ),

    AUTHENTICATION_SUPERADMIN_CREATED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.SYSTEM,
            "New super administrator created",
            "A new user with super administrator permissions was created."
    ),

    AUTHENTICATION_SUPERADMIN_DELETED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.SYSTEM,
            "A super administrator was deleted",
            "A super administrator was deleted by another super administrator."
    ),

    AUTHENTICATION_SUPERADMIN_PASSWORD_CHANGED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.SYSTEM,
            "A super administrator password was changed",
            "The password for a super administrator user has been modified, either by the user themselves or " +
                    "by another super administrator."
    ),

    AUTHENTICATION_SUPERADMIN_MFA_RESET(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.SYSTEM,
            "A super administrator MFA method was reset",
            "Multi-factor method for a super administrator user has been reset, either by the user themselves " +
                    "or by an administrator."
    ),

    AUTHENTICATION_SUPERADMIN_MFA_RECOVERY_CODE_USED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.SYSTEM,
            "A super administrator MFA recovery code was used",
            "A super administrator has successfully utilized one of their MFA recovery codes to log in."
    ),

    AUTHENTICATION_SUPERADMIN_MFA_RECOVERY_CODE_REUSED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.SYSTEM,
            "A super administrator MFA recovery code was re-used",
            "A user attempted to reuse one of their previously utilized MFA recovery codes for login, which " +
                    "was unsuccessful."
    ),

    AUTHENTICATION_SUPERADMIN_MFA_DISABLED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.ORGANIZATION,
            "A super administrator MFA was disabled",
            "A super administrator MFA requirement was disabled."
    ),

    AUTHENTICATION_SUPERADMIN_LOGIN_THROTTLED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.SYSTEM,
            "A super administrator's login attempts were throttled",
            "Due to multiple unsuccessful login attempts by a super administrator, their subsequent login " +
                    "attempts have been intentionally delayed and slowed down until a successful login is achieved."
    ),

    AUTHENTICATION_USER_API_KEY_CREATED(
            SystemEventCategory.AUTHENTICATION,
            SystemEventScope.SYSTEM,
            "An API for a user was created",
            "A user created an API key that is valid for their scope of access."
    ),

    HEALTH_INDICATOR_CRYPTO_SYNC_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Crypto Sync\" Health Indicator was toggled",
            "The state of the \"Crypto Sync\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_DB_CLOCK_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Database Clock\" Health Indicator was toggled",
            "The state of the \"Database Clock\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_NODE_CLOCK_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Node Clock\" Health Indicator was toggled",
            "The state of the \"Node Clock\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TAP_CLOCK_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Tap Clock\" Health Indicator was toggled",
            "The state of the \"Tap Clock\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_NODE_OFFLINE_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Node Offline\" Health Indicator was toggled",
            "The state of the \"Node Offline\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TLS_EXPIRATION_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"TLS Expiration\" Health Indicator was toggled",
            "The state of the \"TLS Expiration\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TAP_OFFLINE_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Tap Offline\" Health Indicator was toggled",
            "The state of the \"Tap Offline\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TAP_TPX_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Tap Throughput\" Health Indicator was toggled",
            "The state of the \"Tap Throughput\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TAP_DROP_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Tap Message Drop\" Health Indicator was toggled",
            "The state of the \"Tap Message Drop\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TAP_BUFFER_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Tap Buffer\" Health Indicator was toggled",
            "The state of the \"Tap Buffer\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TAP_ERROR_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Tap Error\" Health Indicator was toggled",
            "The state of the \"Tap Error\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_NODE_FILE_PERMISSIONS_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Node File Permissions\" Health Indicator was toggled",
            "The state of the \"Node File Permissions\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TAP_FILE_PERMISSIONS_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Tap File Permissions\" Health Indicator was toggled",
            "The state of the \"Tap File Permissions\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TASK_STUCK_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Task Stuck\" Health Indicator was toggled",
            "The state of the \"Task Stuck\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_TASK_FAILURE_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Task Failure\" Health Indicator was toggled",
            "The state of the \"Task Failure\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_MESSAGE_FAILURE_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Message Failure\" Health Indicator was toggled",
            "The state of the \"Message Failure\" system health indicator has changed from a previously different state."
    ),

    HEALTH_INDICATOR_MESSAGE_STUCK_TOGGLED(
            SystemEventCategory.HEALTH_INDICATOR,
            SystemEventScope.SYSTEM,
            "\"Message Stuck\" Health Indicator was toggled",
            "The state of the \"Message Stuck\" system health indicator has changed from a previously different state."
    );

    private final SystemEventCategory category;
    private final SystemEventScope scope;
    private final String humanReadableName;
    private final String description;

    SystemEventType(SystemEventCategory category, SystemEventScope scope, String humanReadableName, String description) {
        this.category = category;
        this.scope = scope;
        this.humanReadableName = humanReadableName;
        this.description = description;
    }

    public SystemEventCategory getCategory() {
        return category;
    }

    public SystemEventScope getScope() {
        return scope;
    }

    public String getHumanReadableName() {
        return humanReadableName;
    }

    public String getDescription() {
        return description;
    }

}
